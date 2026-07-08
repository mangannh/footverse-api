package com.footverse.category.service;

import java.util.List;

import com.footverse.category.dto.CategoryResponse;
import com.footverse.category.dto.CreateCategoryRequest;
import com.footverse.category.dto.UpdateCategoryRequest;

/**
 * Category-module façade for category business logic and the cross-feature existence check the
 * product module needs. This is the only entry point into category data for other features
 * (architecture-spec §6/§7); the module is deliberately independent of the {@code product}
 * module — the delete guard relies on the database foreign key, not the product service.
 */
public interface CategoryService {

    /**
     * Returns every category.
     *
     * @return the list of category responses (empty when none exist)
     */
    List<CategoryResponse> getAllCategories();

    /**
     * Creates a new category. The name must be unique across all categories.
     *
     * @param request the validated create payload
     * @return the created category
     */
    CategoryResponse createCategory(CreateCategoryRequest request);

    /**
     * Updates an existing category. The new name must not collide with a different category.
     *
     * @param id      the id of the category to update
     * @param request the validated update payload
     * @return the updated category
     */
    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);

    /**
     * Deletes a category. The delete is guarded by the {@code product.category_id} foreign key
     * ({@code RESTRICT}); a category still referenced by a product cannot be removed.
     *
     * @param id the id of the category to delete
     */
    void deleteCategory(Long id);

    /**
     * Checks whether a category with the given id exists. Exposed for the product module's
     * create-time category validation.
     *
     * @param id the category id to check
     * @return {@code true} if a category with the id exists
     */
    boolean existsById(Long id);
}
