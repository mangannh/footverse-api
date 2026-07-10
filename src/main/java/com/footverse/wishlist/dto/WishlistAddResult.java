package com.footverse.wishlist.dto;

/**
 * The outcome of {@code WishlistService.addToWishlist(...)}: the resulting wishlist line, plus
 * whether it was created by this call or already existed.
 *
 * <p>This record is internal to the {@code wishlist} module and is never serialized — it exists
 * only so the controller can pick the status code the frozen specs jointly require without
 * re-deriving the outcome itself: a created line is {@code 201} (dto-spec §18, api-guidelines) and
 * a duplicate add is an idempotent {@code 200} (business-rules → Wishlist). The public contract is
 * unchanged: both responses carry a {@link WishlistItemResponse} body (dto-spec §20).</p>
 *
 * @param item    the caller's wishlist line for the product
 * @param created {@code true} when this call inserted the row, {@code false} when it already existed
 */
public record WishlistAddResult(
        WishlistItemResponse item,
        boolean created) {
}
