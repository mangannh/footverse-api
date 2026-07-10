package com.footverse.wishlist.service;

import java.util.List;

import com.footverse.wishlist.dto.AddWishlistItemRequest;
import com.footverse.wishlist.dto.WishlistAddResult;
import com.footverse.wishlist.dto.WishlistItemResponse;

/**
 * Wishlist business logic for the authenticated customer. Every operation is scoped to the caller,
 * who is resolved from the security context rather than from a method argument, so a caller can
 * never reach another user's wishlist (security-spec §7).
 *
 * <p>Product display data is read through {@code ProductService} only; the wishlist never touches a
 * {@code product}-module repository or entity (architecture-spec §6).</p>
 */
public interface WishlistService {

    /**
     * Lists the caller's wishlist, most recently added first (business-rules → Wishlist). A line
     * whose product has been soft-deleted no longer resolves in the catalog and is omitted.
     *
     * @return the caller's wishlist lines (empty when they have none)
     */
    List<WishlistItemResponse> getMyWishlist();

    /**
     * Adds a product to the caller's wishlist. Adding a product the caller has already wishlisted is
     * an idempotent no-op: no second row is inserted, no error is raised, and the existing line is
     * returned (business-rules → Wishlist).
     *
     * @param request the validated add payload
     * @return the caller's wishlist line for the product, and whether this call created it
     * @throws com.footverse.common.exception.ResourceNotFoundException {@code 404}
     *         {@code PRODUCT_NOT_FOUND} when no non-deleted product has the given id
     */
    WishlistAddResult addToWishlist(AddWishlistItemRequest request);

    /**
     * Removes a product from the caller's wishlist. Removing a product the caller has not wishlisted
     * is an idempotent no-op, and another user's line is never touched.
     *
     * @param productId the id of the product to remove
     */
    void removeFromWishlist(Long productId);
}
