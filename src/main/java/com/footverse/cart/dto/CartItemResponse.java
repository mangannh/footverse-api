package com.footverse.cart.dto;

import java.math.BigDecimal;

/**
 * One line of the caller's cart (dto-spec §12). Every product field is resolved from the
 * {@code ProductVariantService} purchase snapshot at read time — nothing here is stored on the
 * cart row itself, so a renamed product or a changed price is reflected immediately.
 *
 * @param id               the cart item id
 * @param productVariantId the variant id this line holds
 * @param productId        the owning product id
 * @param productName      the owning product's name
 * @param productImageUrl  the owning product's primary image URL, or {@code null} when it has none
 * @param size             the variant size
 * @param unitPrice        the effective unit price, resolved by the product module
 * @param quantity         the quantity in the cart
 * @param lineTotal        {@code unitPrice × quantity}, computed by the server
 * @param available        {@code false} when the variant is inactive or out of stock
 */
public record CartItemResponse(
        Long id,
        Long productVariantId,
        Long productId,
        String productName,
        String productImageUrl,
        String size,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        boolean available) {
}
