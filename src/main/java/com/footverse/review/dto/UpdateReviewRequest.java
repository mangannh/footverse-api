package com.footverse.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code PUT /reviews/{id}}. Validation follows validation-spec §11. Only the
 * {@code rating} and {@code comment} are editable; the review's {@code productId}, author, and
 * {@code createdAt} are immutable, so this DTO deliberately carries none of them. Ownership (the
 * caller must own the review) is a business rule enforced by the service.
 *
 * @param rating  required, 1–5
 * @param comment optional, at most 500 characters
 */
public record UpdateReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 500) String comment) {
}
