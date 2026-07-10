package com.footverse.cart.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.footverse.cart.dto.AddCartItemRequest;
import com.footverse.cart.dto.CartResponse;
import com.footverse.cart.dto.UpdateCartItemRequest;
import com.footverse.cart.service.CartService;
import com.footverse.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Shopping-cart endpoints for the authenticated customer. The controller only maps HTTP to the
 * {@link CartService} and wraps results in the response envelope — it holds no business logic and
 * performs no ownership check. Role authorization is enforced by the security filter chain
 * (security-spec §6) and ownership by the service (security-spec §7); no endpoint accepts a user id
 * or a cart id, so the caller can only ever reach their own cart.
 *
 * <p>Every endpoint returns the whole {@link CartResponse} (dto-spec §20), including the
 * server-computed {@code subtotal} and {@code itemCount}, so the client never recomputes money.</p>
 *
 * <p>The Swagger annotation {@code io.swagger.v3.oas.annotations.responses.ApiResponse} is written
 * fully qualified throughout, because its simple name collides with the project's response envelope
 * {@link ApiResponse} that every method returns. Error responses declare the envelope explicitly,
 * since the {@code GlobalExceptionHandler} returns it rather than the success payload
 * (error-spec §2). A {@code 403} on the id-bearing operations has two distinct causes: the role
 * denial {@code FORBIDDEN}, and the ownership denial {@code CART_ITEM_FORBIDDEN} (error-spec
 * §8.9).</p>
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * Returns the caller's cart. A caller who has never added an item gets an empty cart.
     *
     * @return {@code 200 OK} with the caller's cart
     */
    @Operation(summary = "Get the current customer's cart")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The caller's cart; a caller who has never added an item gets an empty cart"),
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
    public ResponseEntity<ApiResponse<CartResponse>> getMyCart() {
        return ResponseEntity.ok(ApiResponse.ok(cartService.getMyCart()));
    }

    /**
     * Adds a quantity of a variant to the caller's cart, merging into the existing line when the
     * cart already holds that variant.
     *
     * @param request the validated add payload
     * @return {@code 200 OK} with the caller's cart after the add
     */
    @Operation(summary = "Add a product variant to the current customer's cart")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The caller's cart after the add; a repeated variant merges into its existing line"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - a field failed validation or the body is malformed; "
                            + "PRODUCT_VARIANT_INACTIVE - the variant is not purchasable; "
                            + "PRODUCT_VARIANT_INSUFFICIENT_STOCK - the resulting quantity exceeds stock",
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
                    description = "PRODUCT_VARIANT_NOT_FOUND - no variant has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(@Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.addItem(request)));
    }

    /**
     * Replaces the quantity of one of the caller's cart lines.
     *
     * @param id      the id of the cart line to update
     * @param request the validated update payload
     * @return {@code 200 OK} with the caller's cart after the update
     */
    @Operation(summary = "Update the quantity of one of the current customer's cart lines")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The caller's cart after the update"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - a field failed validation, the body is malformed, "
                            + "or id is not a valid number; "
                            + "PRODUCT_VARIANT_INSUFFICIENT_STOCK - the requested quantity exceeds stock",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER; "
                            + "CART_ITEM_FORBIDDEN - the line belongs to another user's cart",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "CART_ITEM_NOT_FOUND - no cart line has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.updateItemQuantity(id, request)));
    }

    /**
     * Removes one of the caller's cart lines. The cart itself remains even when its last line goes.
     *
     * @param id the id of the cart line to remove
     * @return {@code 200 OK} with the caller's cart after the removal
     */
    @Operation(summary = "Remove one of the current customer's cart lines")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The caller's cart after the removal; the cart survives its last line"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - id is not a valid number",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER; "
                            + "CART_ITEM_FORBIDDEN - the line belongs to another user's cart",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "CART_ITEM_NOT_FOUND - no cart line has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.removeItem(id)));
    }
}
