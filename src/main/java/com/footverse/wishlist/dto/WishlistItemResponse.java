package com.footverse.wishlist.dto;

import java.math.BigDecimal;

/**
 * One line of a customer's wishlist (dto-spec §13). Multi-source: it combines the wishlist row's
 * own id with the product's catalog summary, so it is assembled by {@code WishlistService} rather
 * than by a mapper (architecture-spec §9) — there is no {@code WishlistMapper}.
 *
 * @param id              the wishlist item id
 * @param productId       the wishlisted product's id
 * @param productName     the product name
 * @param primaryImageUrl the URL of the product's primary image, or {@code null} when it has none
 * @param basePrice       the product's base price
 * @param available       whether the product has at least one purchasable variant
 */
public record WishlistItemResponse(
        Long id,
        Long productId,
        String productName,
        String primaryImageUrl,
        BigDecimal basePrice,
        boolean available) {
}
