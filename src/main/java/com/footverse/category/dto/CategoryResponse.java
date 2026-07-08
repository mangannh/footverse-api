package com.footverse.category.dto;

/**
 * A product category returned to clients (dto-spec §10).
 *
 * @param id          the category id
 * @param name        the unique category name
 * @param description the description, if any
 */
public record CategoryResponse(
        Long id,
        String name,
        String description) {
}
