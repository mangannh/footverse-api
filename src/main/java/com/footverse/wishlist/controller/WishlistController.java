package com.footverse.wishlist.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.footverse.common.dto.ApiResponse;
import com.footverse.wishlist.dto.AddWishlistItemRequest;
import com.footverse.wishlist.dto.WishlistAddResult;
import com.footverse.wishlist.dto.WishlistItemResponse;
import com.footverse.wishlist.service.WishlistService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Wishlist endpoints for the authenticated customer. The controller only maps HTTP to the
 * {@link WishlistService} and wraps results in the response envelope — it holds no business logic
 * and performs no ownership check. Role authorization is enforced by the security filter chain
 * (security-spec §6) and ownership by the service (security-spec §7); no endpoint accepts a user
 * id, so the caller can only ever reach their own wishlist.
 *
 * <p>The Swagger annotation {@code io.swagger.v3.oas.annotations.responses.ApiResponse} is written
 * fully qualified throughout, because its simple name collides with the project's response envelope
 * {@link ApiResponse} that every method returns. Error responses declare the envelope explicitly:
 * without it springdoc would document them with the success payload's schema, which is not what the
 * {@code GlobalExceptionHandler} returns (error-spec §2).</p>
 */
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /**
     * Lists the caller's own wishlist, most recently added first.
     *
     * @return {@code 200 OK} with the caller's wishlist lines
     */
    @Operation(summary = "List the current customer's wishlist")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The caller's wishlist lines, most recently added first"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<ApiResponse<List<WishlistItemResponse>>> getMyWishlist() {
        return ResponseEntity.ok(ApiResponse.ok(wishlistService.getMyWishlist()));
    }

    /**
     * Adds a product to the caller's wishlist. A product the caller has already wishlisted is an
     * idempotent no-op, which is reported as {@code 200 OK} carrying the existing line rather than
     * as an error (business-rules → Wishlist).
     *
     * @param request the validated add payload
     * @return {@code 201 Created} with the new line, or {@code 200 OK} with the existing one
     */
    @Operation(summary = "Add a product to the current customer's wishlist")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "The product was not wishlisted yet: the line was created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The product was already wishlisted: idempotent no-op returning the existing line"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - productId is missing or not positive, or the body is malformed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "PRODUCT_NOT_FOUND - no such product, or it has been soft-deleted",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<ApiResponse<WishlistItemResponse>> addToWishlist(
            @Valid @RequestBody AddWishlistItemRequest request) {
        WishlistAddResult result = wishlistService.addToWishlist(request);
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result.item()));
        }
        return ResponseEntity.ok(ApiResponse.ok(result.item()));
    }

    /**
     * Removes a product from the caller's wishlist. Removing a product the caller has not
     * wishlisted succeeds without error (idempotent).
     *
     * @param productId the id of the product to remove
     * @return {@code 200 OK} with an empty envelope
     */
    @Operation(summary = "Remove a product from the current customer's wishlist")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The line was removed, or the caller had not wishlisted the product (idempotent)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - productId is not a valid number",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeFromWishlist(@PathVariable Long productId) {
        wishlistService.removeFromWishlist(productId);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null));
    }
}
