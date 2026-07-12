package com.footverse.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.footverse.common.config.SecurityConfig;
import com.footverse.common.dto.PageResponse;
import com.footverse.common.exception.BusinessException;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.common.security.JwtUtil;
import com.footverse.common.security.RestAccessDeniedHandler;
import com.footverse.common.security.RestAuthenticationEntryPoint;
import com.footverse.order.dto.OrderDetailResponse;
import com.footverse.order.dto.OrderItemResponse;
import com.footverse.order.dto.OrderSummaryResponse;
import com.footverse.order.entity.OrderStatus;
import com.footverse.order.entity.PaymentMethod;
import com.footverse.order.entity.PaymentStatus;
import com.footverse.order.service.OrderService;
import com.footverse.support.AuthFixtures;
import com.footverse.user.entity.Role;

import org.springframework.http.HttpStatus;

/**
 * Web-slice tests for the {@link OrderController}: the customer order queries ({@code GET /orders},
 * {@code GET /orders/{id}}), checkout ({@code POST /orders}), cancellation
 * ({@code POST /orders/{id}/cancel}), and the admin status update ({@code PATCH /orders/{id}/status}).
 * They assert the success envelopes (including the order-item {@code color} snapshot), request-body
 * validation, the role denial for the wrong token (order paths are CUSTOMER-only, the status update
 * ADMIN-only), the anonymous {@code 401}, and the ownership / not-found / conflict business errors
 * rendered through the standard envelope. The security filter chain is imported; the service is
 * mocked, so no business rule runs here.
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtUtil.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class OrderControllerTest {

    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    private final MockMvc mockMvc;
    private final JwtUtil jwtUtil;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    OrderControllerTest(@Autowired MockMvc mockMvc, @Autowired JwtUtil jwtUtil) {
        this.mockMvc = mockMvc;
        this.jwtUtil = jwtUtil;
    }

    private String customerToken() {
        when(userDetailsService.loadUserByUsername(CUSTOMER_EMAIL))
                .thenReturn(AuthFixtures.userDetails(CUSTOMER_EMAIL, Role.CUSTOMER));
        return "Bearer " + jwtUtil.createAccessToken(CUSTOMER_EMAIL);
    }

    private String adminToken() {
        when(userDetailsService.loadUserByUsername(ADMIN_EMAIL))
                .thenReturn(AuthFixtures.userDetails(ADMIN_EMAIL, Role.ADMIN));
        return "Bearer " + jwtUtil.createAccessToken(ADMIN_EMAIL);
    }

    private OrderSummaryResponse summary() {
        return new OrderSummaryResponse(5L, "FV-ORDER-5", OrderStatus.PENDING, PaymentStatus.UNPAID,
                new BigDecimal("30200.00"), 5, LocalDateTime.now());
    }

    private OrderDetailResponse detail() {
        OrderItemResponse item = new OrderItemResponse(1L, 7L, "Air Force 1", "img.png", "Black", "42",
                new BigDecimal("100.00"), 2, new BigDecimal("200.00"));
        return new OrderDetailResponse(9L, "FV-ORDER-9", OrderStatus.PENDING, PaymentMethod.COD,
                PaymentStatus.UNPAID, new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("30000.00"),
                new BigDecimal("30200.00"), null, "Jane", "0900000000", "HCM", "D1", "W1", "1 Street",
                null, List.of(item), LocalDateTime.now(), null, null);
    }

    /**
     * {@code GET /orders} as a CUSTOMER returns the caller's page of order summaries with the
     * server-computed {@code itemCount}.
     */
    @Test
    void listOrdersAsCustomerReturns200() throws Exception {
        when(orderService.getMyOrders(any(Pageable.class)))
                .thenReturn(PageResponse.from(new org.springframework.data.domain.PageImpl<>(List.of(summary()))));

        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(5))
                .andExpect(jsonPath("$.data.content[0].orderCode").value("FV-ORDER-5"))
                .andExpect(jsonPath("$.data.content[0].itemCount").value(5));
    }

    /**
     * {@code GET /orders} with an ADMIN token is denied the enveloped {@code 403}: order paths are
     * CUSTOMER-only (security-spec §6).
     */
    @Test
    void listOrdersAsAdminReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(orderService, never()).getMyOrders(any());
    }

    /**
     * An anonymous {@code GET /orders} is denied the enveloped {@code 401}.
     */
    @Test
    void listOrdersAnonymouslyReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(orderService, never()).getMyOrders(any());
    }

    /**
     * {@code GET /orders/{id}} as a CUSTOMER returns the order detail with its snapshots, including
     * the order-item {@code color}.
     */
    @Test
    void getOrderDetailAsCustomerReturns200WithColorSnapshot() throws Exception {
        when(orderService.getMyOrder(9L)).thenReturn(detail());

        mockMvc.perform(get("/api/v1/orders/9").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.orderCode").value("FV-ORDER-9"))
                .andExpect(jsonPath("$.data.items[0].color").value("Black"))
                .andExpect(jsonPath("$.data.items[0].size").value("42"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(100.00));
    }

    /**
     * An order that belongs to another user surfaces the service's {@code 403 ORDER_FORBIDDEN}.
     */
    @Test
    void getOrderDetailOfAnotherUserReturns403() throws Exception {
        when(orderService.getMyOrder(9L))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "ORDER_FORBIDDEN",
                        "You cannot access this order"));

        mockMvc.perform(get("/api/v1/orders/9").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER_FORBIDDEN"));
    }

    /**
     * An unknown order id surfaces the service's {@code 404 ORDER_NOT_FOUND}.
     */
    @Test
    void getOrderDetailOfUnknownOrderReturns404() throws Exception {
        when(orderService.getMyOrder(9L))
                .thenThrow(new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found"));

        mockMvc.perform(get("/api/v1/orders/9").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }

    /**
     * A non-numeric id is a type mismatch rendered as the enveloped {@code 400 VALIDATION_ERROR}.
     */
    @Test
    void getOrderDetailWithNonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/orders/abc").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(orderService, never()).getMyOrder(eq(1L));
    }

    // ----- POST /orders (checkout) -----

    /**
     * {@code POST /orders} as a CUSTOMER returns {@code 201 Created} with the created order.
     */
    @Test
    void placeOrderAsCustomerReturns201() throws Exception {
        when(orderService.placeOrder(any())).thenReturn(detail());

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[10],\"addressId\":3,\"couponCode\":\"SAVE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.orderCode").value("FV-ORDER-9"))
                .andExpect(jsonPath("$.data.items[0].color").value("Black"));
    }

    /**
     * An empty {@code cartItemIds} fails Bean Validation with the enveloped {@code 400}.
     */
    @Test
    void placeOrderWithEmptyCartItemIdsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[],\"addressId\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("cartItemIds"));

        verify(orderService, never()).placeOrder(any());
    }

    /**
     * A missing {@code addressId} fails Bean Validation with the enveloped {@code 400}.
     */
    @Test
    void placeOrderWithoutAddressIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[10]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("addressId"));

        verify(orderService, never()).placeOrder(any());
    }

    /**
     * {@code POST /orders} with an ADMIN token is denied the enveloped {@code 403}: order paths are
     * CUSTOMER-only (security-spec §6).
     */
    @Test
    void placeOrderAsAdminReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[10],\"addressId\":3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(orderService, never()).placeOrder(any());
    }

    /**
     * An anonymous {@code POST /orders} is denied the enveloped {@code 401}.
     */
    @Test
    void placeOrderAnonymouslyReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[10],\"addressId\":3}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(orderService, never()).placeOrder(any());
    }

    /**
     * An insufficient-stock variant surfaces the service's {@code 400
     * PRODUCT_VARIANT_INSUFFICIENT_STOCK} through the standard envelope.
     */
    @Test
    void placeOrderWithInsufficientStockReturns400() throws Exception {
        when(orderService.placeOrder(any()))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "PRODUCT_VARIANT_INSUFFICIENT_STOCK",
                        "Not enough stock"));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[10],\"addressId\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_VARIANT_INSUFFICIENT_STOCK"));
    }

    /**
     * An unknown cart item surfaces the service's {@code 404 CART_ITEM_NOT_FOUND}.
     */
    @Test
    void placeOrderWithUnknownCartItemReturns404() throws Exception {
        when(orderService.placeOrder(any()))
                .thenThrow(new ResourceNotFoundException("CART_ITEM_NOT_FOUND", "Cart item not found"));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[10],\"addressId\":3}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CART_ITEM_NOT_FOUND"));
    }

    // ----- POST /orders/{id}/cancel -----

    /**
     * {@code POST /orders/{id}/cancel} as a CUSTOMER returns {@code 200 OK} with the cancelled order.
     */
    @Test
    void cancelOrderAsCustomerReturns200() throws Exception {
        when(orderService.cancelMyOrder(9L)).thenReturn(detail());

        mockMvc.perform(post("/api/v1/orders/9/cancel").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9));
    }

    /**
     * Cancelling a non-{@code PENDING} order surfaces the service's {@code 409 ORDER_NOT_CANCELLABLE}.
     */
    @Test
    void cancelOrderThatIsNotPendingReturns409() throws Exception {
        when(orderService.cancelMyOrder(9L))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE",
                        "Order can only be cancelled while PENDING"));

        mockMvc.perform(post("/api/v1/orders/9/cancel").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_CANCELLABLE"));
    }

    /**
     * Cancelling another user's order surfaces the service's {@code 403 ORDER_FORBIDDEN}.
     */
    @Test
    void cancelOrderOfAnotherUserReturns403() throws Exception {
        when(orderService.cancelMyOrder(9L))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "ORDER_FORBIDDEN",
                        "You cannot access this order"));

        mockMvc.perform(post("/api/v1/orders/9/cancel").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER_FORBIDDEN"));
    }

    /**
     * Cancelling an unknown order surfaces the service's {@code 404 ORDER_NOT_FOUND}.
     */
    @Test
    void cancelOrderOfUnknownOrderReturns404() throws Exception {
        when(orderService.cancelMyOrder(9L))
                .thenThrow(new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found"));

        mockMvc.perform(post("/api/v1/orders/9/cancel").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }

    /**
     * {@code POST /orders/{id}/cancel} with an ADMIN token is denied the enveloped {@code 403}.
     */
    @Test
    void cancelOrderAsAdminReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/orders/9/cancel").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(orderService, never()).cancelMyOrder(any());
    }

    // ----- PATCH /orders/{id}/status (admin) -----

    /**
     * {@code PATCH /orders/{id}/status} as an ADMIN returns {@code 200 OK} with the updated order.
     */
    @Test
    void updateStatusAsAdminReturns200() throws Exception {
        when(orderService.updateOrderStatus(eq(9L), any())).thenReturn(detail());

        mockMvc.perform(patch("/api/v1/orders/9/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9));
    }

    /**
     * A missing {@code status} fails Bean Validation with the enveloped {@code 400}.
     */
    @Test
    void updateStatusWithoutStatusReturns400() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/9/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("status"));

        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    /**
     * A forbidden transition surfaces the service's {@code 409 ORDER_INVALID_STATUS_TRANSITION}.
     */
    @Test
    void updateStatusWithForbiddenTransitionReturns409() throws Exception {
        when(orderService.updateOrderStatus(eq(9L), any()))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "ORDER_INVALID_STATUS_TRANSITION",
                        "Order status transition is not allowed"));

        mockMvc.perform(patch("/api/v1/orders/9/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_INVALID_STATUS_TRANSITION"));
    }

    /**
     * A status update for an unknown order surfaces the service's {@code 404 ORDER_NOT_FOUND}.
     */
    @Test
    void updateStatusOfUnknownOrderReturns404() throws Exception {
        when(orderService.updateOrderStatus(eq(9L), any()))
                .thenThrow(new ResourceNotFoundException("ORDER_NOT_FOUND", "Order not found"));

        mockMvc.perform(patch("/api/v1/orders/9/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }

    /**
     * {@code PATCH /orders/{id}/status} with a CUSTOMER token is denied the enveloped {@code 403}: the
     * status update is ADMIN-only (security-spec §6).
     */
    @Test
    void updateStatusAsCustomerReturns403() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/9/status")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    /**
     * An anonymous {@code PATCH /orders/{id}/status} is denied the enveloped {@code 401}.
     */
    @Test
    void updateStatusAnonymouslyReturns401() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/9/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(orderService, never()).updateOrderStatus(any(), any());
    }
}
