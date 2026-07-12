package com.footverse.review.repository;

/**
 * Read-only aggregation projection for a product's reviews: the average rating and the review
 * count grouped by {@code product_id}. Populated directly by the {@link ReviewRepository}
 * aggregation queries (a Spring Data interface-based projection, the persistence-layer counterpart
 * of the batch-aggregate precedent {@code findByProductIdIn}); {@code ReviewService} converts these
 * raw values into the response-facing average (scale 2, {@code HALF_UP}) in a later sprint item.
 *
 * <p>{@code averageRating} is a {@code Double} because JPQL {@code AVG} yields a floating-point
 * average; a product with no reviews simply produces no row (grouped away), so this projection
 * always describes a product that has at least one review.</p>
 */
public interface ReviewRatingSummary {

    /**
     * Returns the product the aggregate belongs to.
     *
     * @return the product id
     */
    Long getProductId();

    /**
     * Returns the average of the product's review ratings.
     *
     * @return the average rating as a floating-point value
     */
    Double getAverageRating();

    /**
     * Returns how many reviews the product has.
     *
     * @return the review count
     */
    long getReviewCount();
}
