package com.footverse.review.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.footverse.common.dto.PageResponse;
import com.footverse.common.exception.BusinessException;
import com.footverse.common.exception.DuplicateResourceException;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.common.security.CurrentUserProvider;
import com.footverse.order.service.OrderService;
import com.footverse.review.dto.CreateReviewRequest;
import com.footverse.review.dto.RatingSummary;
import com.footverse.review.dto.ReviewResponse;
import com.footverse.review.dto.UpdateReviewRequest;
import com.footverse.review.entity.Review;
import com.footverse.review.mapper.ReviewMapper;
import com.footverse.review.repository.ReviewRatingSummary;
import com.footverse.review.repository.ReviewRepository;
import com.footverse.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Default {@link ReviewService} implementation. It owns the review business rules: the public
 * listing (item 04), the eligibility-gated, duplicate-guarded create, and the owner-only edit and
 * delete. The caller is always resolved through {@link CurrentUserProvider} (never a request field,
 * security-spec §7), so a review is stamped to and edited only by its author.
 *
 * <p>Review eligibility — "the caller has a {@code DELIVERED} order containing the product" — is
 * asked of {@link OrderService} (architecture-spec §7/§8), so the review module never reaches into
 * order rows or the product module. Rows map to responses through {@link ReviewMapper}, a pure
 * single-entity mapping (architecture-spec §9), so the {@code User} entity never leaves the
 * persistence layer. The listing order is enforced here rather than left to the client, mirroring
 * {@code OrderServiceImpl.getMyOrders}.</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    /** Field the public review listing is always sorted by, most-recent-first (assumption 4). */
    private static final String REVIEW_LISTING_SORT_FIELD = "createdAt";

    /** Scale of the on-demand average rating; rounded {@code HALF_UP} (assumption 6). */
    private static final int RATING_SCALE = 2;

    private static final String REVIEW_NOT_ELIGIBLE_CODE = "REVIEW_NOT_ELIGIBLE";
    private static final String REVIEW_NOT_ELIGIBLE_MESSAGE =
            "You can only review a product you have received in a delivered order";
    private static final String REVIEW_DUPLICATED_CODE = "REVIEW_DUPLICATED";
    private static final String REVIEW_DUPLICATED_MESSAGE = "You have already reviewed this product";
    private static final String REVIEW_FORBIDDEN_CODE = "REVIEW_FORBIDDEN";
    private static final String REVIEW_FORBIDDEN_MESSAGE = "You cannot access this review";
    private static final String REVIEW_NOT_FOUND_CODE = "REVIEW_NOT_FOUND";
    private static final String REVIEW_NOT_FOUND_MESSAGE = "Review not found";

    private final ReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;
    private final OrderService orderService;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getProductReviews(Long productId, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findByProductId(productId, mostRecentFirst(pageable));
        return PageResponse.from(reviews.map(reviewMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public RatingSummary getRatingSummary(Long productId) {
        return reviewRepository.findRatingSummaryByProductId(productId)
                .map(this::toRatingSummary)
                .orElseGet(RatingSummary::empty);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, RatingSummary> getRatingSummaries(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.findRatingSummariesByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ReviewRatingSummary::getProductId, this::toRatingSummary));
    }

    @Override
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request) {
        User author = currentUserProvider.getCurrentUser();
        Long productId = request.productId();

        if (!orderService.hasDeliveredOrderForProduct(productId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    REVIEW_NOT_ELIGIBLE_CODE, REVIEW_NOT_ELIGIBLE_MESSAGE);
        }
        if (reviewRepository.existsByUserIdAndProductId(author.getId(), productId)) {
            throw new DuplicateResourceException(REVIEW_DUPLICATED_CODE, REVIEW_DUPLICATED_MESSAGE);
        }

        Review review = new Review();
        review.setUser(author);
        review.setProductId(productId);
        review.setRating(request.rating());
        review.setComment(request.comment());
        return reviewMapper.toResponse(persistNew(review));
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long id, UpdateReviewRequest request) {
        Review review = reviewRepository.findByIdAndUserId(id, currentUserId())
                .orElseThrow(() -> unresolvableReview(id));
        review.setRating(request.rating());
        review.setComment(request.comment());
        return reviewMapper.toResponse(reviewRepository.saveAndFlush(review));
    }

    @Override
    @Transactional
    public void deleteReview(Long id) {
        Review review = reviewRepository.findByIdAndUserId(id, currentUserId())
                .orElseThrow(() -> unresolvableReview(id));
        reviewRepository.delete(review);
    }

    /**
     * Persists a new review, forcing the insert to flush now so the {@code (user_id, product_id)}
     * unique constraint is checked inside this call: a race that beats the service-level
     * {@code existsByUserIdAndProductId} guard surfaces as a {@link DataIntegrityViolationException},
     * which is translated to the same enveloped {@code 409 REVIEW_DUPLICATED} rather than leaking a
     * database error (sprint-5-plan item 05 — the duplicate is stopped at both layers). Flushing also
     * populates the audit timestamps the response carries.
     *
     * @param review the new review to persist
     * @return the persisted review
     * @throws DuplicateResourceException {@code 409 REVIEW_DUPLICATED} when the unique constraint fires
     */
    private Review persistNew(Review review) {
        try {
            return reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException duplicate) {
            throw new DuplicateResourceException(REVIEW_DUPLICATED_CODE, REVIEW_DUPLICATED_MESSAGE);
        }
    }

    /**
     * Normalizes a repository rating aggregate into the service contract: the raw {@code AVG} is a
     * floating-point value, so it is set to scale 2 with {@code HALF_UP} (assumption 6), and the
     * count is narrowed to {@code int}. Only rows that exist (a product with at least one review)
     * reach here — the {@code 0.00} / {@code 0} default is applied by the callers via
     * {@link RatingSummary#empty()}, never here.
     *
     * @param aggregate the grouped {@code AVG(rating)} / {@code COUNT} projection for one product
     * @return the normalized rating aggregate
     */
    private RatingSummary toRatingSummary(ReviewRatingSummary aggregate) {
        BigDecimal average = BigDecimal.valueOf(aggregate.getAverageRating())
                .setScale(RATING_SCALE, RoundingMode.HALF_UP);
        return new RatingSummary(average, (int) aggregate.getReviewCount());
    }

    /**
     * Rebuilds the request keeping the caller's page and size but forcing the newest-first sort, so
     * the listing order never depends on a client-supplied sort (assumption 4).
     *
     * @param pageable the incoming pagination request
     * @return a page request for the same page and size, sorted by {@code createdAt} descending
     */
    private Pageable mostRecentFirst(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, REVIEW_LISTING_SORT_FIELD));
    }

    /**
     * Returns the id of the authenticated caller.
     *
     * @return the caller's user id
     */
    private Long currentUserId() {
        return currentUserProvider.getCurrentUser().getId();
    }

    /**
     * Builds the exception for a review the caller could not resolve as their own, applying the
     * frozen ownership split (the {@code unresolvableOrder} precedent): a review owned by another
     * user is a {@code 403 REVIEW_FORBIDDEN}, a review that does not exist at all is a
     * {@code 404 REVIEW_NOT_FOUND}. Ownership is never hidden behind a {@code 404}.
     *
     * @param id the requested review id
     * @return the exception to throw
     */
    private RuntimeException unresolvableReview(Long id) {
        if (reviewRepository.existsById(id)) {
            return new BusinessException(HttpStatus.FORBIDDEN, REVIEW_FORBIDDEN_CODE, REVIEW_FORBIDDEN_MESSAGE);
        }
        return new ResourceNotFoundException(REVIEW_NOT_FOUND_CODE, REVIEW_NOT_FOUND_MESSAGE);
    }
}
