package com.footverse.cart.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.footverse.cart.dto.AddCartItemRequest;
import com.footverse.cart.dto.CartItemResponse;
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

import lombok.RequiredArgsConstructor;

/**
 * Default {@link CartService} implementation. It owns the cart business rules — one cart per user
 * created lazily, the quantity merge on a repeated add, the stock ceiling, and the ownership check
 * — and is the only layer that knows who the caller is, resolving them through
 * {@link CurrentUserProvider} rather than from a request field (security-spec §7).
 *
 * <p>All product data is read through {@link ProductVariantService} (architecture-spec §7): the
 * cart never touches a {@code product}-module repository, so the bean graph stays acyclic and no
 * pricing rule is duplicated. Money is computed here and never trusted from the client
 * (dto-spec §1).</p>
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final String CART_ITEM_NOT_FOUND_CODE = "CART_ITEM_NOT_FOUND";
    private static final String CART_ITEM_NOT_FOUND_MESSAGE = "Cart item not found";
    private static final String CART_ITEM_FORBIDDEN_CODE = "CART_ITEM_FORBIDDEN";
    private static final String CART_ITEM_FORBIDDEN_MESSAGE = "You cannot access this cart item";
    private static final String PRODUCT_VARIANT_INACTIVE_CODE = "PRODUCT_VARIANT_INACTIVE";
    private static final String PRODUCT_VARIANT_INACTIVE_MESSAGE = "Product variant is not available for purchase";
    private static final String INSUFFICIENT_STOCK_CODE = "PRODUCT_VARIANT_INSUFFICIENT_STOCK";
    private static final String INSUFFICIENT_STOCK_MESSAGE = "Requested quantity exceeds available stock";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantService productVariantService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getMyCart() {
        return cartRepository.findByUserId(currentUserId())
                .map(this::assembleCart)
                .orElseGet(CartServiceImpl::emptyCart);
    }

    @Override
    @Transactional
    public CartResponse addItem(AddCartItemRequest request) {
        User currentUser = currentUserProvider.getCurrentUser();
        ProductVariantPurchaseSnapshot snapshot = productVariantService.getPurchaseSnapshot(request.productVariantId());
        if (!snapshot.active()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, PRODUCT_VARIANT_INACTIVE_CODE,
                    PRODUCT_VARIANT_INACTIVE_MESSAGE);
        }

        Cart cart = cartRepository.findByUserId(currentUser.getId())
                .orElseGet(() -> createCart(currentUser));
        CartItem item = cartItemRepository.findByCartIdAndProductVariantId(cart.getId(), request.productVariantId())
                .orElseGet(() -> newLine(cart, request.productVariantId()));

        int mergedQuantity = item.getQuantity() + request.quantity();
        requireSufficientStock(snapshot, mergedQuantity);
        item.setQuantity(mergedQuantity);
        cartItemRepository.save(item);
        return assembleCart(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItemQuantity(Long cartItemId, UpdateCartItemRequest request) {
        CartItem item = requireOwnedCartItem(cartItemId);
        ProductVariantPurchaseSnapshot snapshot = productVariantService.getPurchaseSnapshot(item.getProductVariantId());
        requireSufficientStock(snapshot, request.quantity());
        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        return assembleCart(item.getCart());
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long cartItemId) {
        CartItem item = requireOwnedCartItem(cartItemId);
        Cart cart = item.getCart();
        cartItemRepository.delete(item);
        // The cart row deliberately survives its last item (business-rules → Shopping Cart).
        return assembleCart(cart);
    }

    /**
     * Returns the id of the authenticated caller.
     *
     * @return the caller's user id
     */
    private Long currentUserId() {
        return currentUserProvider.getCurrentUser().getId();
    }

    /**
     * The cart of a caller who has never added an item: no row exists, which is an empty cart
     * rather than an error.
     *
     * @return an empty cart response
     */
    private static CartResponse emptyCart() {
        return new CartResponse(List.of(), BigDecimal.ZERO, 0);
    }

    /**
     * Creates the caller's cart on their first add. Only ever reached when no cart row exists, and
     * the {@code uk_cart_user_id} unique constraint guarantees a second one can never be inserted.
     *
     * @param user the owning user
     * @return the persisted cart
     */
    private Cart createCart(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        return cartRepository.save(cart);
    }

    /**
     * Builds a new, not-yet-persisted line holding zero of the variant, so the add path can apply
     * the requested quantity through the same merge arithmetic whether or not the line existed.
     *
     * @param cart             the owning cart
     * @param productVariantId the variant the line holds
     * @return the new line
     */
    private CartItem newLine(Cart cart, Long productVariantId) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProductVariantId(productVariantId);
        item.setQuantity(0);
        return item;
    }

    /**
     * Resolves a cart line that belongs to the caller. The cart-scoped query is the ownership check:
     * a line of another user's cart is simply not found by it.
     *
     * @param cartItemId the requested cart item id
     * @return the caller's cart line
     * @throws BusinessException {@code 403 CART_ITEM_FORBIDDEN} when the line exists but belongs to
     *                           another user's cart, {@code 404 CART_ITEM_NOT_FOUND} when no such
     *                           line exists at all
     */
    private CartItem requireOwnedCartItem(Long cartItemId) {
        return cartRepository.findByUserId(currentUserId())
                .flatMap(cart -> cartItemRepository.findByIdAndCartId(cartItemId, cart.getId()))
                .orElseThrow(() -> unresolvableCartItem(cartItemId));
    }

    /**
     * Distinguishes the two reasons a cart-scoped read can come back empty: another user's line is
     * a {@code 403}, an absent line is a {@code 404} (security-spec §7).
     *
     * @param cartItemId the requested cart item id
     * @return the exception to throw
     */
    private BusinessException unresolvableCartItem(Long cartItemId) {
        if (cartItemRepository.existsById(cartItemId)) {
            return new BusinessException(HttpStatus.FORBIDDEN, CART_ITEM_FORBIDDEN_CODE, CART_ITEM_FORBIDDEN_MESSAGE);
        }
        return new ResourceNotFoundException(CART_ITEM_NOT_FOUND_CODE, CART_ITEM_NOT_FOUND_MESSAGE);
    }

    /**
     * Rejects a quantity the variant cannot currently supply (business-rules → Shopping Cart). Stock
     * is validated, never reserved — reservation belongs to checkout (architecture-spec §19).
     *
     * @param snapshot the variant's purchase snapshot
     * @param quantity the quantity the line would end up holding
     */
    private void requireSufficientStock(ProductVariantPurchaseSnapshot snapshot, int quantity) {
        if (quantity > snapshot.stockQuantity()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INSUFFICIENT_STOCK_CODE, INSUFFICIENT_STOCK_MESSAGE);
        }
    }

    /**
     * Assembles the cart response from its lines, computing the aggregates server-side: the
     * {@code subtotal} as the sum of line totals and the {@code itemCount} as the sum of quantities
     * (business-rules → Shopping Cart).
     *
     * @param cart the caller's cart
     * @return the assembled cart response
     */
    private CartResponse assembleCart(Cart cart) {
        List<CartItemResponse> items = cartItemRepository.findByCartId(cart.getId()).stream()
                .map(this::assembleItem)
                .toList();
        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int itemCount = items.stream().mapToInt(CartItemResponse::quantity).sum();
        return new CartResponse(items, subtotal, itemCount);
    }

    /**
     * Assembles one cart line from its stored quantity and the variant's purchase snapshot. The
     * unit price is taken from the snapshot as-is and never recomputed; a variant that has since
     * gone inactive or out of stock stays in the cart but is flagged unavailable.
     *
     * @param item the stored cart line
     * @return the assembled line response
     */
    private CartItemResponse assembleItem(CartItem item) {
        ProductVariantPurchaseSnapshot snapshot = productVariantService.getPurchaseSnapshot(item.getProductVariantId());
        BigDecimal lineTotal = snapshot.unitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(
                item.getId(),
                snapshot.productVariantId(),
                snapshot.productId(),
                snapshot.productName(),
                snapshot.primaryImageUrl(),
                snapshot.size(),
                snapshot.unitPrice(),
                item.getQuantity(),
                lineTotal,
                snapshot.active() && snapshot.stockQuantity() > 0);
    }
}
