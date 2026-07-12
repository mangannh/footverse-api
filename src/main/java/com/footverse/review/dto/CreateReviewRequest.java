package com.footverse.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /reviews}. Validation follows validation-spec §11; that the caller has a
 * {@code DELIVERED} order containing the product and has not already reviewed it are business rules
 * enforced by the service, not field-level constraints. The author is always the authenticated
 * caller — never a request field.
 *
 * @param productId required, positive; the product being reviewed
 * @param rating    required, 1–5
 * @param comment   optional, at most 500 characters
 */
public record CreateReviewRequest(
        @NotNull @Positive Long productId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 500) String comment) {
}
