package com.footverse.review.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.footverse.review.entity.Review;

/**
 * Data access for {@link Review}. Standard CRUD (including {@code findById} and {@code existsById}
 * for the ownership 403/404 split) is inherited from {@link JpaRepository}; the reads below serve
 * {@code ReviewService}, which is the only caller. Duplicate reviews are guarded at the database by
 * the {@code uk_review_user_id_product_id} unique constraint; the {@link #existsByUserIdAndProductId}
 * read is the service-layer primary check in front of it.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Returns a page of a product's reviews, for the public review listing. The sort is supplied by
     * the caller through {@code pageable}.
     *
     * @param productId the reviewed product id
     * @param pageable  the pagination and sort request
     * @return the page of the product's reviews (empty when it has none, including an unknown id)
     */
    Page<Review> findByProductId(Long productId, Pageable pageable);

    /**
     * Returns a review only when it belongs to the given user, so the service can resolve and
     * ownership-check an edit or delete in one read. Mirrors the order module's
     * {@code findByIdAndUserId} precedent: an empty result means "not found or not owned", and the
     * service distinguishes the two via the inherited {@code existsById} to pick 404 vs 403.
     *
     * @param id     the review id
     * @param userId the owning user id
     * @return the review, or empty when it does not exist or belongs to another user
     */
    Optional<Review> findByIdAndUserId(Long id, Long userId);

    /**
     * Checks whether the user has already reviewed the product ({@code (user_id, product_id)}
     * uniqueness) — the service-layer duplicate guard in front of the unique constraint.
     *
     * @param userId    the reviewing user id
     * @param productId the reviewed product id
     * @return {@code true} if such a review already exists
     */
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /**
     * Aggregates one product's reviews into its average rating and review count. Returns empty when
     * the product has no reviews (grouped away), which the service reads as the {@code 0.00} / {@code 0}
     * default.
     *
     * @param productId the reviewed product id
     * @return the product's rating aggregate, or empty when it has no reviews
     */
    @Query("""
            SELECT r.productId AS productId, AVG(r.rating) AS averageRating, COUNT(r) AS reviewCount
            FROM Review r
            WHERE r.productId = :productId
            GROUP BY r.productId
            """)
    Optional<ReviewRatingSummary> findRatingSummaryByProductId(@Param("productId") Long productId);

    /**
     * Aggregates the reviews of many products in a single query, so catalog search can attach live
     * ratings to a page without a per-product N+1 — mirroring the batch {@code findByProductIdIn}
     * precedent. Products with no reviews are absent from the result (grouped away), which the
     * service reads as the {@code 0.00} / {@code 0} default.
     *
     * @param productIds the reviewed product ids
     * @return the rating aggregate per product that has at least one review (empty when none do)
     */
    @Query("""
            SELECT r.productId AS productId, AVG(r.rating) AS averageRating, COUNT(r) AS reviewCount
            FROM Review r
            WHERE r.productId IN :productIds
            GROUP BY r.productId
            """)
    List<ReviewRatingSummary> findRatingSummariesByProductIdIn(
            @Param("productIds") Collection<Long> productIds);
}
