package com.footverse.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.footverse.cart.dto.AddCartItemRequest;
import com.footverse.cart.dto.CartResponse;
import com.footverse.cart.dto.UpdateCartItemRequest;
import com.footverse.cart.entity.Cart;
import com.footverse.cart.entity.CartItem;
import com.footverse.cart.repository.CartItemRepository;
import com.footverse.cart.repository.CartRepository;
import com.footverse.common.exception.BusinessException;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.common.security.CurrentUserProvider;
import com.footverse.product.dto.ProductVariantPurchaseSnapshot;
import com.footverse.product.service.ProductVariantService;
import com.footverse.user.entity.User;

/**
 * Unit tests for {@link CartServiceImpl}: the lazily created one-per-user cart, the quantity merge
 * on a repeated add, the inactive-variant and stock rejections, the ownership {@code 403} versus
 * not-found {@code 404} split, the surviving cart row, and the server-computed aggregates.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final Long CALLER_ID = 1L;
    private static final Long CART_ID = 5L;
    private static final Long VARIANT_ID = 7L;
    private static final Long CART_ITEM_ID = 10L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductVariantService productVariantService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private CartServiceImpl service;

    private void init() {
        service = new CartServiceImpl(cartRepository, cartItemRepository, productVariantService,
                currentUserProvider);
    }

    private User caller() {
        User user = new User();
        user.setId(CALLER_ID);
        return user;
    }

    private void withCaller() {
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
    }

    private Cart cart() {
        Cart cart = new Cart();
        cart.setId(CART_ID);
        cart.setUser(caller());
        return cart;
    }

    private CartItem cartItem(Cart cart, int quantity) {
        CartItem item = new CartItem();
        item.setId(CART_ITEM_ID);
        item.setCart(cart);
        item.setProductVariantId(VARIANT_ID);
        item.setQuantity(quantity);
        return item;
    }

    private ProductVariantPurchaseSnapshot snapshot(String unitPrice, int stock, boolean active) {
        return new ProductVariantPurchaseSnapshot(VARIANT_ID, 100L, "Air Force 1", "primary.png", "42",
                new BigDecimal(unitPrice), stock, active);
    }

    private void withSnapshot(ProductVariantPurchaseSnapshot snapshot) {
        when(productVariantService.getPurchaseSnapshot(VARIANT_ID)).thenReturn(snapshot);
    }

    // ----- Read -----

    /**
     * A caller who has never added an item has no cart row; that is an empty cart, not an error.
     */
    @Test
    void getMyCartWithoutCartRowReturnsEmptyCart() {
        init();
        withCaller();
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.empty());

        CartResponse response = service.getMyCart();

        assertThat(response.items()).isEmpty();
        assertThat(response.subtotal()).isEqualByComparingTo("0");
        assertThat(response.itemCount()).isZero();
        verify(cartRepository, never()).save(any());
    }

    /**
     * The response is assembled entirely from the variant snapshot, and the server computes
     * {@code lineTotal}, {@code subtotal} (Σ lineTotal) and {@code itemCount} (Σ quantity).
     */
    @Test
    void getMyCartAssemblesLinesAndServerComputedAggregates() {
        init();
        withCaller();
        Cart cart = cart();
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(cartItem(cart, 3)));
        withSnapshot(snapshot("111.00", 10, true));

        CartResponse response = service.getMyCart();

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo(CART_ITEM_ID);
        assertThat(response.items().get(0).productVariantId()).isEqualTo(VARIANT_ID);
        assertThat(response.items().get(0).productId()).isEqualTo(100L);
        assertThat(response.items().get(0).productName()).isEqualTo("Air Force 1");
        assertThat(response.items().get(0).productImageUrl()).isEqualTo("primary.png");
        assertThat(response.items().get(0).size()).isEqualTo("42");
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("111.00");
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
        assertThat(response.items().get(0).lineTotal()).isEqualByComparingTo("333.00");
        assertThat(response.items().get(0).available()).isTrue();
        assertThat(response.subtotal()).isEqualByComparingTo("333.00");
        assertThat(response.itemCount()).isEqualTo(3);
    }

    /**
     * A variant that has since gone inactive stays in the cart but is flagged unavailable.
     */
    @Test
    void getMyCartFlagsInactiveVariantAsUnavailable() {
        init();
        withCaller();
        Cart cart = cart();
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(cartItem(cart, 2)));
        withSnapshot(snapshot("100.00", 10, false));

        CartResponse response = service.getMyCart();

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).available()).isFalse();
    }

    /**
     * A variant that has since gone out of stock likewise stays in the cart, flagged unavailable.
     */
    @Test
    void getMyCartFlagsOutOfStockVariantAsUnavailable() {
        init();
        withCaller();
        Cart cart = cart();
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(cartItem(cart, 2)));
        withSnapshot(snapshot("100.00", 0, true));

        assertThat(service.getMyCart().items().get(0).available()).isFalse();
    }

    // ----- Add -----

    /**
     * The first add creates the caller's cart and inserts the line with the requested quantity.
     */
    @Test
    void addItemCreatesTheCartLazilyOnFirstAdd() {
        init();
        withCaller();
        withSnapshot(snapshot("100.00", 10, true));
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart created = invocation.getArgument(0);
            created.setId(CART_ID);
            return created;
        });
        when(cartItemRepository.findByCartIdAndProductVariantId(CART_ID, VARIANT_ID)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of());

        service.addItem(new AddCartItemRequest(VARIANT_ID, 3));

        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(cartCaptor.capture());
        assertThat(cartCaptor.getValue().getUser().getId()).isEqualTo(CALLER_ID);

        ArgumentCaptor<CartItem> itemCaptor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getProductVariantId()).isEqualTo(VARIANT_ID);
        assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(3);
    }

    /**
     * A second add of the same variant never inserts a second line: the requested quantity is added
     * to the existing one.
     */
    @Test
    void addItemMergesQuantityIntoTheExistingLine() {
        init();
        withCaller();
        Cart cart = cart();
        CartItem existing = cartItem(cart, 2);
        withSnapshot(snapshot("100.00", 10, true));
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductVariantId(CART_ID, VARIANT_ID))
                .thenReturn(Optional.of(existing));
        when(cartItemRepository.save(existing)).thenReturn(existing);
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(existing));

        CartResponse response = service.addItem(new AddCartItemRequest(VARIANT_ID, 3));

        assertThat(existing.getQuantity()).isEqualTo(5);
        assertThat(response.items()).hasSize(1);
        assertThat(response.itemCount()).isEqualTo(5);
        verify(cartRepository, never()).save(any());
    }

    /**
     * An inactive variant cannot be added; nothing is persisted.
     */
    @Test
    void addItemRejectsInactiveVariant() {
        init();
        withCaller();
        withSnapshot(snapshot("100.00", 10, false));

        assertThatThrownBy(() -> service.addItem(new AddCartItemRequest(VARIANT_ID, 1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_INACTIVE")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
        verify(cartRepository, never()).save(any());
        verify(cartItemRepository, never()).save(any());
    }

    /**
     * An unknown variant surfaces the product module's existing {@code 404}; the cart introduces no
     * error code of its own for it.
     */
    @Test
    void addItemOfUnknownVariantPropagatesProductVariantNotFound() {
        init();
        withCaller();
        when(productVariantService.getPurchaseSnapshot(VARIANT_ID))
                .thenThrow(new ResourceNotFoundException("PRODUCT_VARIANT_NOT_FOUND", "Product variant not found"));

        assertThatThrownBy(() -> service.addItem(new AddCartItemRequest(VARIANT_ID, 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_NOT_FOUND");
        verify(cartRepository, never()).save(any());
    }

    /**
     * A quantity beyond the variant's stock is rejected before anything is persisted.
     */
    @Test
    void addItemRejectsQuantityBeyondStock() {
        init();
        withCaller();
        Cart cart = cart();
        withSnapshot(snapshot("100.00", 4, true));
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductVariantId(CART_ID, VARIANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(new AddCartItemRequest(VARIANT_ID, 5)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_INSUFFICIENT_STOCK")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
        verify(cartItemRepository, never()).save(any());
    }

    /**
     * The stock ceiling applies to the <em>merged</em> quantity, not to the requested delta alone:
     * 3 already in the cart plus 3 more exceeds a stock of 5.
     */
    @Test
    void addItemRejectsWhenTheMergedQuantityExceedsStock() {
        init();
        withCaller();
        Cart cart = cart();
        CartItem existing = cartItem(cart, 3);
        withSnapshot(snapshot("100.00", 5, true));
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductVariantId(CART_ID, VARIANT_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addItem(new AddCartItemRequest(VARIANT_ID, 3)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_INSUFFICIENT_STOCK");
        assertThat(existing.getQuantity()).isEqualTo(3);
        verify(cartItemRepository, never()).save(any());
    }

    // ----- Update -----

    /**
     * Updating replaces the line's quantity outright (unlike add, which merges).
     */
    @Test
    void updateItemQuantityReplacesTheQuantity() {
        init();
        withCaller();
        Cart cart = cart();
        CartItem existing = cartItem(cart, 2);
        withSnapshot(snapshot("100.00", 10, true));
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(CART_ITEM_ID, CART_ID)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(existing)).thenReturn(existing);
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(existing));

        CartResponse response = service.updateItemQuantity(CART_ITEM_ID, new UpdateCartItemRequest(6));

        assertThat(existing.getQuantity()).isEqualTo(6);
        assertThat(response.itemCount()).isEqualTo(6);
        assertThat(response.subtotal()).isEqualByComparingTo("600.00");
    }

    /**
     * A new quantity beyond the variant's stock is rejected.
     */
    @Test
    void updateItemQuantityRejectsQuantityBeyondStock() {
        init();
        withCaller();
        Cart cart = cart();
        CartItem existing = cartItem(cart, 2);
        withSnapshot(snapshot("100.00", 4, true));
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(CART_ITEM_ID, CART_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateItemQuantity(CART_ITEM_ID, new UpdateCartItemRequest(5)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_INSUFFICIENT_STOCK");
        assertThat(existing.getQuantity()).isEqualTo(2);
        verify(cartItemRepository, never()).save(any());
    }

    /**
     * Updating a line of another user's cart is an enveloped {@code 403 CART_ITEM_FORBIDDEN}.
     */
    @Test
    void updateAnotherUsersCartItemIsForbidden() {
        init();
        withCaller();
        Cart cart = cart();
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(CART_ITEM_ID, CART_ID)).thenReturn(Optional.empty());
        when(cartItemRepository.existsById(CART_ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.updateItemQuantity(CART_ITEM_ID, new UpdateCartItemRequest(1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CART_ITEM_FORBIDDEN")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.FORBIDDEN);
        verify(cartItemRepository, never()).save(any());
    }

    /**
     * Updating a line that does not exist at all is a {@code 404 CART_ITEM_NOT_FOUND}.
     */
    @Test
    void updateMissingCartItemIsNotFound() {
        init();
        withCaller();
        Cart cart = cart();
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(CART_ITEM_ID, CART_ID)).thenReturn(Optional.empty());
        when(cartItemRepository.existsById(CART_ITEM_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.updateItemQuantity(CART_ITEM_ID, new UpdateCartItemRequest(1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CART_ITEM_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
    }

    /**
     * A caller with no cart at all cannot reach an existing line: it belongs to someone else.
     */
    @Test
    void updateCartItemWithoutOwnCartIsForbidden() {
        init();
        withCaller();
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.empty());
        when(cartItemRepository.existsById(CART_ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.updateItemQuantity(CART_ITEM_ID, new UpdateCartItemRequest(1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CART_ITEM_FORBIDDEN");
    }

    // ----- Remove -----

    /**
     * Removing the cart's last line deletes the line but leaves the cart row in place.
     */
    @Test
    void removeItemDeletesTheLineButKeepsTheCart() {
        init();
        withCaller();
        Cart cart = cart();
        CartItem existing = cartItem(cart, 2);
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(CART_ITEM_ID, CART_ID)).thenReturn(Optional.of(existing));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of());

        CartResponse response = service.removeItem(CART_ITEM_ID);

        verify(cartItemRepository).delete(existing);
        verify(cartRepository, never()).delete(any());
        verify(cartRepository, never()).deleteById(any());
        assertThat(response.items()).isEmpty();
        assertThat(response.itemCount()).isZero();
    }

    /**
     * Removing a line of another user's cart is an enveloped {@code 403 CART_ITEM_FORBIDDEN}.
     */
    @Test
    void removeAnotherUsersCartItemIsForbidden() {
        init();
        withCaller();
        Cart cart = cart();
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(CART_ITEM_ID, CART_ID)).thenReturn(Optional.empty());
        when(cartItemRepository.existsById(CART_ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.removeItem(CART_ITEM_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CART_ITEM_FORBIDDEN");
        verify(cartItemRepository, never()).delete(any());
    }

    /**
     * Removing a line that does not exist at all is a {@code 404 CART_ITEM_NOT_FOUND}.
     */
    @Test
    void removeMissingCartItemIsNotFound() {
        init();
        withCaller();
        Cart cart = cart();
        when(cartRepository.findByUserId(CALLER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCartId(CART_ITEM_ID, CART_ID)).thenReturn(Optional.empty());
        when(cartItemRepository.existsById(CART_ITEM_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.removeItem(CART_ITEM_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CART_ITEM_NOT_FOUND");
        verify(cartItemRepository, never()).delete(any());
    }
}
