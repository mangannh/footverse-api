package com.footverse.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.footverse.product.entity.Product;

import jakarta.persistence.LockModeType;

/**
 * Data access for {@link Product}. Reads are soft-delete-aware: they only return rows whose
 * {@code deleted_at IS NULL}, so a soft-deleted product is treated as absent (database-spec §10.6).
 * Reference integrity to categories and brands is the database foreign key's job, so no
 * {@code existsByCategory} / {@code existsByBrand} check is declared here.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Finds a non-deleted product by id.
     *
     * @param id the product id
     * @return the product if it exists and is not soft-deleted, otherwise empty
     */
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Finds a non-deleted product by id acquiring a {@code PESSIMISTIC_WRITE} row lock. The product
     * row is the per-product serialization point for the exactly-one-primary-image invariant: a
     * concurrent primary change on the same product blocks here until the holder commits, so the
     * primary set is mutated by one transaction at a time (architecture-spec §13).
     *
     * @param id the product id
     * @return the product if it exists and is not soft-deleted, otherwise empty
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

    /**
     * Loads the non-deleted products with the given ids in a single query, eagerly fetching the
     * brand and category the product summary needs so no per-product lazy load is triggered. An
     * unknown or soft-deleted id simply does not resolve and is absent from the result.
     *
     * @param productIds the product ids to load (never empty)
     * @return the non-deleted products among those ids, in no particular order
     */
    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.brand
            JOIN FETCH p.category
            WHERE p.id IN :productIds AND p.deletedAt IS NULL
            """)
    List<Product> findByIdInAndDeletedAtIsNull(@Param("productIds") Collection<Long> productIds);

    /**
     * Searches non-deleted products by an optional case-insensitive partial name and optional
     * brand / category filters. A {@code null} argument disables the corresponding filter.
     *
     * @param name       optional case-insensitive partial product name; {@code null} to skip
     * @param brandId    optional brand filter; {@code null} to skip
     * @param categoryId optional category filter; {@code null} to skip
     * @param pageable   the pagination and sort request
     * @return the matching page of products
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.deletedAt IS NULL
              AND (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:brandId IS NULL OR p.brand.id = :brandId)
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
            """)
    Page<Product> search(@Param("name") String name,
                         @Param("brandId") Long brandId,
                         @Param("categoryId") Long categoryId,
                         Pageable pageable);
}
