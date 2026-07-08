package com.footverse.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code PUT /categories/{id}}. Validation follows validation-spec §12; the name's
 * uniqueness is a business rule enforced by the service, not a field-level constraint.
 *
 * @param name        required, the category name (must be unique)
 * @param description optional, at most 2000 characters
 */
public record UpdateCategoryRequest(
        @NotBlank String name,
        @Size(max = 2000) String description) {
}
