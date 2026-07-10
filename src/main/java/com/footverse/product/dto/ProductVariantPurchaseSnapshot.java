package com.footverse.product.dto;

import java.math.BigDecimal;

/**
 * Immutable point-in-time view of everything a purchase flow needs to know about one variant
 * (architecture-spec §7: "checkout snapshot data"). Assembled by {@code ProductVariantService} and
 * consumed by the cart — and later by checkout — so those modules never touch the {@code product}
 * repositories themselves.
 *
 * <p>This is a service-level contract, not an HTTP payload: no endpoint returns it, so it carries
 * no {@code Response} suffix. The price is already <em>resolved</em> — the consumer must not
 * recompute it from a base price and an override.</p>
 *
 * @param productVariantId the variant id
 * @param productId        the owning product id
 * @param productName      the owning product's name
 * @param primaryImageUrl  the owning product's primary image URL, or {@code null} when it has none
 * @param size             the variant size
 * @param unitPrice        the effective selling price: the variant's {@code priceOverride} when
 *                         set, otherwise the owning product's {@code basePrice}
 * @param stockQuantity    the variant's stock on hand
 * @param active           whether the variant's status is {@code ACTIVE}
 */
public record ProductVariantPurchaseSnapshot(
        Long productVariantId,
        Long productId,
        String productName,
        String primaryImageUrl,
        String size,
        BigDecimal unitPrice,
        int stockQuantity,
        boolean active) {
}
