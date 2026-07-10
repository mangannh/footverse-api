package com.footverse.wishlist.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Payload for {@code POST /wishlist}. Validation follows validation-spec §8; that the product
 * exists is a business rule enforced by the service, not a field-level constraint, and adding a
 * product the caller has already wishlisted is an idempotent no-op rather than an error
 * (business-rules → Wishlist).
 *
 * @param productId required, the product to wishlist
 */
public record AddWishlistItemRequest(
        @NotNull @Positive Long productId) {
}
