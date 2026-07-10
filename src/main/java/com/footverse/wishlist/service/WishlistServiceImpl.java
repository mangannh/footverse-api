package com.footverse.wishlist.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;

/**
 * Default {@link WishlistService} implementation. It owns the wishlist business rules — the
 * idempotent add and the idempotent remove — and is the only layer that knows who the caller is,
 * resolving them through {@link CurrentUserProvider} rather than from a request field
 * (security-spec §7). Every repository call is user-scoped, so ownership is enforced by the query
 * itself and another user's line can never be read or deleted.
 *
 * <p>Product data is read through {@link ProductService} (architecture-spec §6/§7): the wishlist
 * never touches a {@code product}-module repository, so the bean graph stays acyclic and the
 * primary-image and availability rules are not duplicated. {@code WishlistItemResponse} is
 * multi-source, so it is assembled here rather than by a mapper (architecture-spec §9) — there is
 * no {@code WishlistMapper}.</p>
 *
 * <p>Listing order belongs to the wishlist, not the catalog: the rows are read in
 * {@code createdAt}-descending order and iterated in that order, while
 * {@link ProductService#getSummariesByIds(java.util.Collection)} serves purely as an unordered
 * lookup. A product missing from that lookup has been soft-deleted and its line is skipped.</p>
 */
@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private static final String PRODUCT_NOT_FOUND_CODE = "PRODUCT_NOT_FOUND";
    private static final String PRODUCT_NOT_FOUND_MESSAGE = "Product not found";

    private final WishlistRepository wishlistRepository;
    private final ProductService productService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getMyWishlist() {
        List<WishlistItem> items = wishlistRepository.findByUserIdOrderByCreatedAtDesc(currentUserId());
        Map<Long, ProductSummaryResponse> summaries = productService.getSummariesByIds(
                items.stream().map(WishlistItem::getProductId).toList());

        List<WishlistItemResponse> responses = new ArrayList<>(items.size());
        for (WishlistItem item : items) {
            ProductSummaryResponse summary = summaries.get(item.getProductId());
            if (summary != null) {
                responses.add(toResponse(item, summary));
            }
        }
        return List.copyOf(responses);
    }

    @Override
    @Transactional
    public WishlistAddResult addToWishlist(AddWishlistItemRequest request) {
        User currentUser = currentUserProvider.getCurrentUser();
        Long productId = request.productId();
        ProductSummaryResponse summary = requireProduct(productId);

        Optional<WishlistItem> existing = wishlistRepository.findByUserIdAndProductId(currentUser.getId(), productId);
        if (existing.isPresent()) {
            return new WishlistAddResult(toResponse(existing.get(), summary), false);
        }

        WishlistItem item = new WishlistItem();
        item.setUser(currentUser);
        item.setProductId(productId);
        return new WishlistAddResult(toResponse(wishlistRepository.save(item), summary), true);
    }

    @Override
    @Transactional
    public void removeFromWishlist(Long productId) {
        // The user-scoped delete is both the ownership check and the idempotency: another user's
        // line does not match, and a product the caller never wishlisted deletes nothing.
        wishlistRepository.deleteByUserIdAndProductId(currentUserId(), productId);
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
     * Resolves the summary of a product that must exist, reusing the batch catalog read for the
     * single-product case. A soft-deleted product does not resolve, so it can be neither wishlisted
     * nor listed.
     *
     * @param productId the requested product id
     * @return the product's summary
     * @throws ResourceNotFoundException {@code 404 PRODUCT_NOT_FOUND} when the product does not
     *                                   exist or has been soft-deleted
     */
    private ProductSummaryResponse requireProduct(Long productId) {
        ProductSummaryResponse summary = productService.getSummariesByIds(List.of(productId)).get(productId);
        if (summary == null) {
            throw new ResourceNotFoundException(PRODUCT_NOT_FOUND_CODE, PRODUCT_NOT_FOUND_MESSAGE);
        }
        return summary;
    }

    /**
     * Assembles a wishlist line from the stored row and the product's catalog summary. The product
     * fields are taken from the summary as-is, so the wishlist renders exactly what catalog search
     * renders.
     *
     * @param item    the stored wishlist row
     * @param summary the wishlisted product's catalog summary
     * @return the assembled wishlist line
     */
    private WishlistItemResponse toResponse(WishlistItem item, ProductSummaryResponse summary) {
        return new WishlistItemResponse(
                item.getId(),
                summary.id(),
                summary.name(),
                summary.primaryImageUrl(),
                summary.basePrice(),
                summary.available());
    }
}
