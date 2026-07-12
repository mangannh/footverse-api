package com.footverse.review.dto;

import java.math.BigDecimal;

/**
 * A product's rating aggregate as the {@code review} module exposes it to consumers: the average
 * rating already normalized to scale 2 ({@code HALF_UP}) and the review count. Owned by
 * {@code ReviewService} (the review aggregate's owner); {@code ProductService} consumes it to fill
 * {@code averageRating} / {@code reviewCount} on its responses (architecture-spec §7).
 *
 * <p>This is an internal service-to-service contract, never serialized and never reaching a
 * controller — the {@code WishlistAddResult} / {@code CheckoutCartLine} precedent. A product with no
 * reviews is represented by {@link #empty()}: {@code 0.00} / {@code 0} (business-rules → Review), the
 * single place that default value is defined.</p>
 *
 * @param averageRating the average rating at scale 2 ({@code HALF_UP}), or {@code 0.00} when there
 *                      are no reviews
 * @param reviewCount   the number of reviews, or {@code 0} when there are none
 */
public record RatingSummary(BigDecimal averageRating, int reviewCount) {

    /** The aggregate of a product that has no reviews: {@code 0.00} / {@code 0}. */
    private static final RatingSummary EMPTY = new RatingSummary(new BigDecimal("0.00"), 0);

    /**
     * Returns the aggregate for a product with no reviews — {@code 0.00} average and {@code 0}
     * count. This is the sole definition of the unreviewed default, so consumers select it rather
     * than inventing their own zero.
     *
     * @return the shared empty rating aggregate
     */
    public static RatingSummary empty() {
        return EMPTY;
    }
}
