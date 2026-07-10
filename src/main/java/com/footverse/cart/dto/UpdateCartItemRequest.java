package com.footverse.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for {@code PUT /cart/items/{id}}. Validation follows validation-spec §7; the stock
 * ceiling is a business rule enforced by the service.
 *
 * @param quantity required, at least 1; replaces the line's quantity and must not exceed available
 *                 stock
 */
public record UpdateCartItemRequest(
        @NotNull @Min(1) Integer quantity) {
}
