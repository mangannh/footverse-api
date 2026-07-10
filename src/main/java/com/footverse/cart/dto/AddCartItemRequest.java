package com.footverse.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Payload for {@code POST /cart/items}. Validation follows validation-spec §7; that the variant
 * exists, is {@code ACTIVE}, and has enough stock for the resulting quantity are business rules
 * enforced by the service, not field-level constraints.
 *
 * @param productVariantId required, the variant to add
 * @param quantity         required, at least 1; added to the quantity already in the cart for this
 *                         variant, and the total must not exceed available stock
 */
public record AddCartItemRequest(
        @NotNull @Positive Long productVariantId,
        @NotNull @Min(1) Integer quantity) {
}
