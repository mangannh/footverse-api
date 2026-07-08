package com.footverse.category.mapper;

import org.mapstruct.Mapper;

import com.footverse.category.dto.CategoryResponse;
import com.footverse.category.entity.Category;

/**
 * Maps {@link Category} entities to their response DTO. Pure mapping only — no business logic.
 */
@Mapper
public interface CategoryMapper {

    /**
     * Maps a category to its response representation.
     *
     * @param category the category entity
     * @return the response DTO
     */
    CategoryResponse toResponse(Category category);
}
