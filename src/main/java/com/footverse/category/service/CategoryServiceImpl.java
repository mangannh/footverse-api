package com.footverse.category.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.footverse.category.dto.CategoryResponse;
import com.footverse.category.dto.CreateCategoryRequest;
import com.footverse.category.dto.UpdateCategoryRequest;
import com.footverse.category.entity.Category;
import com.footverse.category.mapper.CategoryMapper;
import com.footverse.category.repository.CategoryRepository;
import com.footverse.common.exception.DuplicateResourceException;
import com.footverse.common.exception.InvalidOperationException;
import com.footverse.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Default {@link CategoryService} implementation backed by {@link CategoryRepository} and
 * {@link CategoryMapper}. It owns the category business rules — name uniqueness and the
 * delete guard — and stays fully independent of the {@code product} module: the delete relies
 * on the {@code product.category_id} foreign key ({@code RESTRICT}), never on the product
 * service, so the bean graph remains acyclic (architecture-spec §6/§7).
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("CATEGORY_NAME_DUPLICATED", "Category name already exists");
        }
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Category not found"));
        if (!category.getName().equals(request.name()) && categoryRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("CATEGORY_NAME_DUPLICATED", "Category name already exists");
        }
        category.setName(request.name());
        category.setDescription(request.description());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Category not found"));
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new InvalidOperationException("CATEGORY_IN_USE", "Category is still referenced by products");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return categoryRepository.existsById(id);
    }
}
