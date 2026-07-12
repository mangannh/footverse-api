package com.footverse.order.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.footverse.common.dto.ApiResponse;
import com.footverse.common.dto.PageResponse;
import com.footverse.order.dto.OrderDetailResponse;
import com.footverse.order.dto.OrderSummaryResponse;
import com.footverse.order.dto.PlaceOrderRequest;
import com.footverse.order.dto.UpdateOrderStatusRequest;
import com.footverse.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Order endpoints (dto-spec §20). The customer surface — checkout ({@code POST /orders}), the
 * caller-scoped order queries ({@code GET /orders}, {@code GET /orders/{id}}), and cancellation
 * ({@code POST /orders/{id}/cancel}) — plus the single admin operation, the order-status machine
 * ({@code PATCH /orders/{id}/status}). The
 * controller only maps HTTP to the {@link OrderService} and wraps results in the response envelope —
 * it holds no business logic, computes no money, and performs no ownership check. Role authorization
 * is enforced by the security filter chain (security-spec §6 — the customer order paths require
 * CUSTOMER, while {@code PATCH /orders/{id}/status} requires ADMIN and bypasses ownership,
 * security-spec §7) and ownership by the service on the customer paths; no customer endpoint accepts
 * a user id, so a customer can only ever reach their own resources.
 *
 * <p>The Swagger annotation {@code io.swagger.v3.oas.annotations.responses.ApiResponse} is written
 * fully qualified throughout, because its simple name collides with the project's response envelope
 * {@link ApiResponse} that every method returns. Error responses declare the envelope explicitly,
 * since the {@code GlobalExceptionHandler} returns it rather than the success payload
 * (error-spec §2). A {@code 403} has two distinct causes: the role denial {@code FORBIDDEN}, and the
 * ownership denials {@code CART_ITEM_FORBIDDEN} / {@code ADDRESS_FORBIDDEN} (error-spec §8.8/§8.9),
 * and on {@code GET /orders/{id}} the ownership denial {@code ORDER_FORBIDDEN} (error-spec §8.11).</p>
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Places an order from the caller's selected cart items (partial checkout). Customer only;
     * transactional — snapshots, stock decrement, coupon consumption, and cart-line removal commit or
     * roll back together.
     *
     * @param request the validated checkout payload
     * @return {@code 201 Created} with the created order and its checkout snapshots
     */
    @Operation(summary = "Place an order from the current customer's selected cart items")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "The created order with its checkout snapshots"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - a field failed validation (empty, non-positive, or "
                            + "duplicate cart item ids; missing address id) or the body is malformed; "
                            + "PRODUCT_VARIANT_INACTIVE / PRODUCT_VARIANT_INSUFFICIENT_STOCK - a variant is not "
                            + "purchasable; COUPON_DISABLED / COUPON_EXPIRED / COUPON_USAGE_LIMIT_EXCEEDED / "
                            + "COUPON_MIN_ORDER_AMOUNT_NOT_MET - the coupon is not applicable",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER; CART_ITEM_FORBIDDEN - a cart item "
                            + "belongs to another user; ADDRESS_FORBIDDEN - the address belongs to another user",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "CART_ITEM_NOT_FOUND - a cart item does not exist; ADDRESS_NOT_FOUND - the "
                            + "address does not exist; PRODUCT_VARIANT_NOT_FOUND - a variant does not exist; "
                            + "COUPON_NOT_FOUND - the coupon code does not exist",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderDetailResponse>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request) {
        OrderDetailResponse response = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /**
     * Lists the current customer's orders, paginated and most-recent-first. Customer only;
     * caller-scoped — only the authenticated user's own orders are returned.
     *
     * @param pageable the pagination request (default page 0, size 20; sort is fixed to
     *                 {@code createdAt} descending by the service)
     * @return {@code 200 OK} with the caller's page of order summaries
     */
    @Operation(summary = "List the current customer's orders, most-recent-first")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The caller's page of order summaries, ordered createdAt descending"),
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
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getMyOrders(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getMyOrders(pageable)));
    }

    /**
     * Returns one of the current customer's orders in full detail with its checkout snapshots.
     * Customer only; ownership-checked by the service.
     *
     * @param id the order id
     * @return {@code 200 OK} with the caller's order detail
     */
    @Operation(summary = "Get one of the current customer's orders in full detail")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The caller's order with its checkout snapshots"),
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
                            + "ORDER_FORBIDDEN - the order belongs to another user",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ORDER_NOT_FOUND - no order has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getMyOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getMyOrder(id)));
    }

    /**
     * Cancels one of the current customer's orders. Customer only; ownership-checked by the service.
     * Allowed only while the order is {@code PENDING}; transactional — the status change, stock
     * restore, and coupon-usage decrement commit or roll back together.
     *
     * @param id the order id
     * @return {@code 200 OK} with the cancelled order and its checkout snapshots
     */
    @Operation(summary = "Cancel one of the current customer's PENDING orders")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The cancelled order with its checkout snapshots"),
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
                            + "ORDER_FORBIDDEN - the order belongs to another user",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ORDER_NOT_FOUND - no order has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "ORDER_NOT_CANCELLABLE - the order is not PENDING",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> cancelMyOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.cancelMyOrder(id)));
    }

    /**
     * Advances an order's status. Admin only; ownership is bypassed (security-spec §7). Transactional
     * — a {@code PENDING→CANCELLED} transition restores stock and coupon usage together, and a
     * {@code SHIPPING→DELIVERED} transition flips the payment to {@code PAID} and records
     * {@code deliveredAt}.
     *
     * @param id      the order id
     * @param request the validated target status
     * @return {@code 200 OK} with the updated order and its snapshots
     */
    @Operation(summary = "Advance an order's status (admin), enforcing the frozen state machine")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The updated order with its snapshots"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - status is missing or not a valid OrderStatus, "
                            + "the body is malformed, or the id is not a valid number",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not an ADMIN",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ORDER_NOT_FOUND - no order has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "ORDER_NOT_CANCELLABLE - a CANCELLED target while the order is not PENDING; "
                            + "ORDER_INVALID_STATUS_TRANSITION - any other transition the machine forbids",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> updateOrderStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.updateOrderStatus(id, request)));
    }
}
