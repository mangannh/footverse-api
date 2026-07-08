package com.footverse.category.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.footverse.category.entity.Category;

/**
 * Data access for {@link Category}. The {@code existsById} and {@code findById} lookups
 * required by the module are inherited from {@link JpaRepository}; only the name-uniqueness
 * check is declared here.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Checks whether a category with the given name exists.
     *
     * @param name the name to check
     * @return {@code true} if a category has the name
     */
    boolean existsByName(String name);
}
