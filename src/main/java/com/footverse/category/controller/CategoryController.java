package com.footverse.category.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.footverse.category.dto.CategoryResponse;
import com.footverse.category.dto.CreateCategoryRequest;
import com.footverse.category.dto.UpdateCategoryRequest;
import com.footverse.category.service.CategoryService;
import com.footverse.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Category endpoints: a public listing plus admin create/update/delete. The controller only
 * maps HTTP to the {@link CategoryService} and wraps results in the response envelope — it holds
 * no business logic. Role authorization is enforced by the security filter chain
 * (security-spec §6), so the admin operations carry only the Swagger security requirement here.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Lists every category. Public endpoint.
     *
     * @return {@code 200 OK} with the list of categories
     */
    @Operation(summary = "List all categories")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getAllCategories()));
    }

    /**
     * Creates a new category. Admin only.
     *
     * @param request the validated create payload
     * @return {@code 201 Created} with the created category
     */
    @Operation(summary = "Create a new category")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /**
     * Updates an existing category. Admin only.
     *
     * @param id      the id of the category to update
     * @param request the validated update payload
     * @return {@code 200 OK} with the updated category
     */
    @Operation(summary = "Update an existing category")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryResponse response = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Deletes a category. Admin only; a category still referenced by a product cannot be
     * removed (guarded by the database foreign key, reported as {@code 409 CATEGORY_IN_USE}).
     *
     * @param id the id of the category to delete
     * @return {@code 200 OK} with an empty envelope
     */
    @Operation(summary = "Delete a category")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null));
    }
}
