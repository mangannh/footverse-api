package com.footverse.wishlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.common.security.CurrentUserProvider;
import com.footverse.product.dto.ProductSummaryResponse;
import com.footverse.product.service.ProductService;
import com.footverse.user.entity.User;
import com.footverse.wishlist.dto.AddWishlistItemRequest;
import com.footverse.wishlist.dto.WishlistAddResult;
import com.footverse.wishlist.dto.WishlistItemResponse;
import com.footverse.wishlist.entity.WishlistItem;
import com.footverse.wishlist.repository.WishlistRepository;

/**
 * Unit tests for {@link WishlistServiceImpl}: the caller-scoped listing and its
 * repository-driven ordering, the skipping of soft-deleted products, the idempotent add, the
 * unknown-product {@code 404}, and the idempotent user-scoped remove.
 */
@ExtendWith(MockitoExtension.class)
class WishlistServiceImplTest {

    private static final Long CALLER_ID = 1L;

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private ProductService productService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private WishlistServiceImpl service;

    private void init() {
        service = new WishlistServiceImpl(wishlistRepository, productService, currentUserProvider);
    }

    private User caller() {
        User user = new User();
        user.setId(CALLER_ID);
        return user;
    }

    private void withCaller() {
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
    }

    private WishlistItem item(Long id, Long productId) {
        WishlistItem item = new WishlistItem();
        item.setId(id);
        item.setProductId(productId);
        return item;
    }

    private ProductSummaryResponse summary(Long productId, String name, boolean available) {
        return new ProductSummaryResponse(productId, name, new BigDecimal("100.00"), "Nike", "Sneakers",
                "primary-" + productId + ".png", new BigDecimal("0.00"), available);
    }

    // ----- List -----

    /**
     * The listing assembles every {@code WishlistItemResponse} field from the wishlist row and the
     * product's catalog summary, and reads the rows through the {@code createdAt}-descending query.
     */
    @Test
    void getMyWishlistAssemblesLinesFromRowAndProductSummary() {
        init();
        withCaller();
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(CALLER_ID))
                .thenReturn(List.of(item(50L, 7L)));
        when(productService.getSummariesByIds(List.of(7L)))
                .thenReturn(Map.of(7L, summary(7L, "Air Force 1", true)));

        List<WishlistItemResponse> result = service.getMyWishlist();

        assertThat(result).hasSize(1);
        WishlistItemResponse line = result.get(0);
        assertThat(line.id()).isEqualTo(50L);
        assertThat(line.productId()).isEqualTo(7L);
        assertThat(line.productName()).isEqualTo("Air Force 1");
        assertThat(line.primaryImageUrl()).isEqualTo("primary-7.png");
        assertThat(line.basePrice()).isEqualByComparingTo("100.00");
        assertThat(line.available()).isTrue();
        verify(wishlistRepository).findByUserIdOrderByCreatedAtDesc(CALLER_ID);
    }

    /**
     * The listing keeps the repository's order, never the summary map's. The product ids are chosen
     * so that iterating the {@code HashMap} would sort them ascending (10, 20, 30) and therefore
     * disagree with the repository's most-recently-added-first order (30, 10, 20).
     */
    @Test
    void getMyWishlistPreservesRepositoryOrderNotMapOrder() {
        init();
        withCaller();
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(CALLER_ID))
                .thenReturn(List.of(item(53L, 30L), item(51L, 10L), item(52L, 20L)));
        when(productService.getSummariesByIds(List.of(30L, 10L, 20L))).thenReturn(Map.of(
                10L, summary(10L, "Older", true),
                20L, summary(20L, "Middle", true),
                30L, summary(30L, "Newest", true)));

        List<WishlistItemResponse> result = service.getMyWishlist();

        assertThat(result).extracting(WishlistItemResponse::productId).containsExactly(30L, 10L, 20L);
        assertThat(result).extracting(WishlistItemResponse::productName)
                .containsExactly("Newest", "Older", "Middle");
    }

    /**
     * A line whose product has been soft-deleted does not resolve in the catalog summary map and is
     * omitted; the surviving lines keep their order.
     */
    @Test
    void getMyWishlistSkipsLinesWhoseProductNoLongerResolves() {
        init();
        withCaller();
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(CALLER_ID))
                .thenReturn(List.of(item(53L, 30L), item(52L, 20L), item(51L, 10L)));
        when(productService.getSummariesByIds(List.of(30L, 20L, 10L))).thenReturn(Map.of(
                30L, summary(30L, "Newest", true),
                10L, summary(10L, "Oldest", false)));

        List<WishlistItemResponse> result = service.getMyWishlist();

        assertThat(result).extracting(WishlistItemResponse::productId).containsExactly(30L, 10L);
        assertThat(result.get(1).available()).isFalse();
    }

    /**
     * An empty wishlist yields an empty list rather than an error.
     */
    @Test
    void getMyWishlistWithNoRowsReturnsEmptyList() {
        init();
        withCaller();
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(CALLER_ID)).thenReturn(List.of());
        when(productService.getSummariesByIds(List.of())).thenReturn(Map.of());

        assertThat(service.getMyWishlist()).isEmpty();
    }

    // ----- Add -----

    /**
     * Adding a product the caller has not wishlisted inserts the row, binds it to the caller, and
     * reports that it was created.
     */
    @Test
    void addNewProductCreatesRowBoundToTheCaller() {
        init();
        withCaller();
        when(productService.getSummariesByIds(List.of(7L)))
                .thenReturn(Map.of(7L, summary(7L, "Air Force 1", true)));
        when(wishlistRepository.findByUserIdAndProductId(CALLER_ID, 7L)).thenReturn(Optional.empty());
        when(wishlistRepository.save(any(WishlistItem.class))).thenAnswer(invocation -> {
            WishlistItem saved = invocation.getArgument(0);
            saved.setId(50L);
            return saved;
        });

        WishlistAddResult result = service.addToWishlist(new AddWishlistItemRequest(7L));

        assertThat(result.created()).isTrue();
        assertThat(result.item().id()).isEqualTo(50L);
        assertThat(result.item().productId()).isEqualTo(7L);
        assertThat(result.item().productName()).isEqualTo("Air Force 1");
        ArgumentCaptor<WishlistItem> captor = ArgumentCaptor.forClass(WishlistItem.class);
        verify(wishlistRepository).save(captor.capture());
        WishlistItem saved = captor.getValue();
        assertThat(saved.getProductId()).isEqualTo(7L);
        assertThat(saved.getUser().getId()).isEqualTo(CALLER_ID);
    }

    /**
     * Adding a product the caller has already wishlisted is an idempotent no-op: the existing line is
     * returned, no second row is inserted, and no error is raised.
     */
    @Test
    void addDuplicateProductReturnsExistingLineWithoutInserting() {
        init();
        withCaller();
        when(productService.getSummariesByIds(List.of(7L)))
                .thenReturn(Map.of(7L, summary(7L, "Air Force 1", true)));
        when(wishlistRepository.findByUserIdAndProductId(CALLER_ID, 7L))
                .thenReturn(Optional.of(item(50L, 7L)));

        WishlistAddResult result = service.addToWishlist(new AddWishlistItemRequest(7L));

        assertThat(result.created()).isFalse();
        assertThat(result.item().id()).isEqualTo(50L);
        assertThat(result.item().productId()).isEqualTo(7L);
        verify(wishlistRepository, never()).save(any());
    }

    /**
     * Adding an unknown — or soft-deleted — product is a {@code 404 PRODUCT_NOT_FOUND}; nothing is
     * inserted and the wishlist is never even read.
     */
    @Test
    void addUnknownOrSoftDeletedProductThrowsProductNotFound() {
        init();
        withCaller();
        when(productService.getSummariesByIds(List.of(9L))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.addToWishlist(new AddWishlistItemRequest(9L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
        verify(wishlistRepository, never()).save(any());
        verify(wishlistRepository, never()).findByUserIdAndProductId(any(), any());
    }

    // ----- Remove -----

    /**
     * Remove deletes through the user-scoped query, so it can only ever touch the caller's own line.
     */
    @Test
    void removeDeletesOnlyTheCallersLine() {
        init();
        withCaller();

        service.removeFromWishlist(7L);

        verify(wishlistRepository).deleteByUserIdAndProductId(CALLER_ID, 7L);
    }

    /**
     * Removing a product the caller has not wishlisted deletes nothing and raises no error
     * (idempotent per business-rules → Wishlist).
     */
    @Test
    void removeOfNotWishlistedProductIsIdempotent() {
        init();
        withCaller();

        assertThatCode(() -> service.removeFromWishlist(999L)).doesNotThrowAnyException();

        verify(wishlistRepository).deleteByUserIdAndProductId(CALLER_ID, 999L);
    }
}
