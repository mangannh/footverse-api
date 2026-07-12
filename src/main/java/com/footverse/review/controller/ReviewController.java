package com.footverse.review.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import com.footverse.common.dto.ApiResponse;
import com.footverse.common.dto.PageResponse;
import com.footverse.review.dto.CreateReviewRequest;
import com.footverse.review.dto.ReviewResponse;
import com.footverse.review.dto.UpdateReviewRequest;
import com.footverse.review.service.ReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Review endpoints (dto-spec §20): the public product-review listing
 * {@code GET /products/{id}/reviews} and the customer review write surface
 * {@code POST /reviews}, {@code PUT /reviews/{id}}, {@code DELETE /reviews/{id}}. The controller
 * lives in the {@code review} module even though the listing path is nested under {@code /products}
 * — the path segment does not move ownership into the product module, exactly as
 * {@code CouponController} owns {@code POST /coupons/validate} from the {@code order} module
 * (sprint-5-plan item 04/05). The class-level base is {@code /api/v1} so both the nested listing
 * path and the module's own {@code /reviews} write paths sit on this one controller.
 *
 * <p>The controller only maps HTTP to {@link ReviewService} and wraps the result in the response
 * envelope — no business logic, no exception handling; eligibility, the duplicate guard, and
 * ownership are enforced in the service, and role authorization by the security filter chain
 * (security-spec §6 — the writes require CUSTOMER). The listing is anonymous, so it carries no
 * {@code @SecurityRequirement} padlock and declares neither {@code 401} nor {@code 403}; each write
 * carries the padlock and declares both. The Swagger annotation
 * {@code io.swagger.v3.oas.annotations.responses.ApiResponse} is written fully qualified, because
 * its simple name collides with the project's response envelope {@link ApiResponse} that every
 * method returns; error responses declare the envelope explicitly (error-spec §2).</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Lists a product's reviews, paginated and newest-first. Public endpoint; an unknown product id
     * returns an empty page, never a {@code 404} (sprint-5-plan assumption 5).
     *
     * @param id       the product id
     * @param pageable the pagination request (default page 0, size 20; the sort is forced to
     *                 {@code createdAt} descending by the service)
     * @return {@code 200 OK} with the page of reviews (empty when the product has none)
     */
    @Operation(summary = "List a product's reviews")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The page of the product's reviews, newest first "
                            + "(an empty page for an unknown product or one with no reviews)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - id is not a valid number",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping("/products/{id}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getProductReviews(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getProductReviews(id, pageable)));
    }

    /**
     * Creates the caller's review of a product. Customer only; the caller must have a delivered
     * order containing the product and must not have reviewed it before.
     *
     * @param request the validated create payload
     * @return {@code 201 Created} with the created review
     */
    @Operation(summary = "Create a product review")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "The created review"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - a field failed validation, or the body is malformed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER; REVIEW_NOT_ELIGIBLE - the "
                            + "caller has no delivered order containing the product",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "REVIEW_DUPLICATED - the caller has already reviewed this product",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse response = reviewService.createReview(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /**
     * Updates the caller's own review (rating and comment only). Customer only.
     *
     * @param id      the id of the review to update
     * @param request the validated update payload
     * @return {@code 200 OK} with the updated review
     */
    @Operation(summary = "Update the caller's own review")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The updated review"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - a field failed validation, the body is malformed, "
                            + "or id is not a valid number",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER; REVIEW_FORBIDDEN - the "
                            + "review belongs to another user",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "REVIEW_NOT_FOUND - no review has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.updateReview(id, request)));
    }

    /**
     * Permanently deletes the caller's own review. Customer only.
     *
     * @param id the id of the review to delete
     * @return {@code 200 OK} with an empty envelope
     */
    @Operation(summary = "Delete the caller's own review")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The review was deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - id is not a valid number",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER; REVIEW_FORBIDDEN - the "
                            + "review belongs to another user",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "REVIEW_NOT_FOUND - no review has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null));
    }
}
