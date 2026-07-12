package com.footverse.review.service;

import java.util.Collection;
import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.footverse.common.dto.PageResponse;
import com.footverse.review.dto.CreateReviewRequest;
import com.footverse.review.dto.RatingSummary;
import com.footverse.review.dto.ReviewResponse;
import com.footverse.review.dto.UpdateReviewRequest;

/**
 * Service of the {@code review} module. It owns the review read/write logic that later sprint items
 * add; this item delivers only the public product-review listing.
 *
 * <p>The listing is anonymous (security-spec §6) and product-existence-agnostic: an unknown product
 * simply has no reviews, so it never touches the {@code product} module (architecture-spec §8,
 * sprint-5-plan assumption 5).</p>
 */
public interface ReviewService {

    /**
     * Returns a page of a product's reviews, most-recent-first ({@code createdAt} descending,
     * sprint-5-plan assumption 4 — the ordering is enforced by the service regardless of any
     * client-supplied sort). The listing is public and does not check that the product exists: an
     * unknown product id, or a product with no reviews, yields an empty page (assumption 5), never a
     * {@code 404}. Each {@link ReviewResponse} exposes only the author's display fields
     * ({@code userFullName}, {@code userAvatarUrl}); the {@code User} entity is never returned.
     *
     * @param productId the product whose reviews to list
     * @param pageable  the pagination request (its page and size are honoured; the sort is overridden
     *                  with {@code createdAt} descending)
     * @return the page of the product's reviews, newest first (empty when it has none)
     */
    PageResponse<ReviewResponse> getProductReviews(Long productId, Pageable pageable);

    /**
     * Creates the caller's review of a product (business-rules → Review; security-spec §7). The
     * author is always the authenticated caller (resolved through {@code CurrentUserProvider}, never
     * a request field). The caller must have received the product in a {@code DELIVERED} order —
     * verified via {@code OrderService.hasDeliveredOrderForProduct} (architecture-spec §8, so the
     * review module never touches order rows) — and must not have reviewed it before; the one
     * review per {@code (user, product)} rule is guarded at both the service and the database
     * (database-spec §15).
     *
     * @param request the validated create payload (product, rating, optional comment)
     * @return the created review
     * @throws com.footverse.common.exception.BusinessException {@code 403 REVIEW_NOT_ELIGIBLE} when
     *         the caller has no {@code DELIVERED} order containing the product
     * @throws com.footverse.common.exception.DuplicateResourceException {@code 409 REVIEW_DUPLICATED}
     *         when the caller has already reviewed the product
     */
    ReviewResponse createReview(CreateReviewRequest request);

    /**
     * Updates the caller's own review (security-spec §7). Only the {@code rating} and
     * {@code comment} change; the review's {@code productId}, author, and {@code createdAt} are
     * immutable, while {@code updatedAt} is refreshed so the client can show an "edited" indicator
     * (business-rules → Review). Ownership uses the exists-based split every owned module applies.
     *
     * @param id      the id of the review to update
     * @param request the validated update payload (rating, optional comment)
     * @return the updated review
     * @throws com.footverse.common.exception.BusinessException {@code 403 REVIEW_FORBIDDEN} when the
     *         review exists but belongs to another user
     * @throws com.footverse.common.exception.ResourceNotFoundException {@code 404 REVIEW_NOT_FOUND}
     *         when no review has the given id
     */
    ReviewResponse updateReview(Long id, UpdateReviewRequest request);

    /**
     * Permanently deletes the caller's own review (security-spec §7); there is no soft delete — the
     * frozen {@code review} table has no {@code deleted_at} (database-spec §15). A deleted review
     * immediately drops out of the public listing. Ownership uses the same exists-based split as
     * {@link #updateReview(Long, UpdateReviewRequest)}.
     *
     * @param id the id of the review to delete
     * @throws com.footverse.common.exception.BusinessException {@code 403 REVIEW_FORBIDDEN} when the
     *         review exists but belongs to another user
     * @throws com.footverse.common.exception.ResourceNotFoundException {@code 404 REVIEW_NOT_FOUND}
     *         when no review has the given id
     */
    void deleteReview(Long id);

    /**
     * Returns one product's live rating aggregate — the average rating (scale 2, {@code HALF_UP})
     * and review count computed on demand from the reviews, never stored on the product
     * (business-rules → Review). A product with no reviews yields {@link RatingSummary#empty()}
     * ({@code 0.00} / {@code 0}). Consumed by {@code ProductService} for the product detail
     * (architecture-spec §7).
     *
     * @param productId the product to aggregate
     * @return the product's rating aggregate, or the empty aggregate when it has no reviews
     */
    RatingSummary getRatingSummary(Long productId);

    /**
     * Returns the live rating aggregate for many products in a single query, so catalog search can
     * attach ratings to a page without a per-product N+1 (mirroring
     * {@code ProductVariantService.getPurchasableStateByProductIds}). Products with no reviews are
     * absent from the result — the consumer supplies {@link RatingSummary#empty()} for them.
     *
     * @param productIds the products to aggregate
     * @return a map from product id to its rating aggregate; keys are limited to products that have
     *         at least one review (empty when none do)
     */
    Map<Long, RatingSummary> getRatingSummaries(Collection<Long> productIds);
}
