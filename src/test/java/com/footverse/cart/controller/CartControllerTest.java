package com.footverse.cart.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.footverse.cart.dto.CartItemResponse;
import com.footverse.cart.dto.CartResponse;
import com.footverse.cart.service.CartService;
import com.footverse.common.config.SecurityConfig;
import com.footverse.common.exception.BusinessException;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.common.security.JwtUtil;
import com.footverse.common.security.RestAccessDeniedHandler;
import com.footverse.common.security.RestAuthenticationEntryPoint;
import com.footverse.support.AuthFixtures;
import com.footverse.user.entity.Role;

/**
 * Web-slice tests for {@link CartController}: the four CUSTOMER endpoints, the role denial for an
 * ADMIN token, request-body validation, and the service's business errors rendered through the
 * standard envelope. The security filter chain is imported; the service is mocked, so no business
 * rule runs here.
 */
@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, JwtUtil.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class CartControllerTest {

    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    private final MockMvc mockMvc;
    private final JwtUtil jwtUtil;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    CartControllerTest(@Autowired MockMvc mockMvc, @Autowired JwtUtil jwtUtil) {
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

    private CartResponse cartWithOneLine() {
        CartItemResponse item = new CartItemResponse(10L, 7L, 100L, "Air Force 1", "primary.png", "42",
                new BigDecimal("111.00"), 3, new BigDecimal("333.00"), true);
        return new CartResponse(List.of(item), new BigDecimal("333.00"), 3);
    }

    /**
     * {@code GET /cart} returns the caller's cart with its server-computed aggregates.
     */
    @Test
    void getCartAsCustomerReturns200() throws Exception {
        when(cartService.getMyCart()).thenReturn(cartWithOneLine());

        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.items[0].id").value(10))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(333.00))
                .andExpect(jsonPath("$.data.items[0].available").value(true))
                .andExpect(jsonPath("$.data.subtotal").value(333.00))
                .andExpect(jsonPath("$.data.itemCount").value(3));
    }

    /**
     * {@code GET /cart} with an ADMIN token is denied the enveloped {@code 403}: the path is
     * CUSTOMER-only.
     */
    @Test
    void getCartAsAdminReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(cartService, never()).getMyCart();
    }

    /**
     * An anonymous request is denied the enveloped {@code 401} (security-spec §6).
     */
    @Test
    void getCartAnonymouslyReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(cartService, never()).getMyCart();
    }

    /**
     * {@code GET /cart} for a caller with no cart yet returns the enveloped empty cart.
     */
    @Test
    void getCartWithoutCartReturnsEmptyCart() throws Exception {
        when(cartService.getMyCart()).thenReturn(new CartResponse(List.of(), BigDecimal.ZERO, 0));

        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.itemCount").value(0));
    }

    /**
     * {@code POST /cart/items} returns the whole cart with {@code 200 OK} — the response body is the
     * cart, and a repeated add creates nothing.
     */
    @Test
    void addItemAsCustomerReturns200WithTheCart() throws Exception {
        when(cartService.addItem(any())).thenReturn(cartWithOneLine());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productVariantId\":7,\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.itemCount").value(3));
    }

    /**
     * A quantity below 1 fails Bean Validation with the enveloped {@code 400} (validation-spec §7).
     */
    @Test
    void addItemWithZeroQuantityReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productVariantId\":7,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));

        verify(cartService, never()).addItem(any());
    }

    /**
     * A missing {@code productVariantId} fails Bean Validation.
     */
    @Test
    void addItemWithoutVariantIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("productVariantId"));

        verify(cartService, never()).addItem(any());
    }

    /**
     * Adding an unknown variant surfaces the product module's {@code 404 PRODUCT_VARIANT_NOT_FOUND}.
     */
    @Test
    void addItemOfUnknownVariantReturns404() throws Exception {
        when(cartService.addItem(any()))
                .thenThrow(new ResourceNotFoundException("PRODUCT_VARIANT_NOT_FOUND", "Product variant not found"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productVariantId\":7,\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_VARIANT_NOT_FOUND"));
    }

    /**
     * Adding an inactive variant surfaces the service's {@code 400 PRODUCT_VARIANT_INACTIVE}.
     */
    @Test
    void addItemOfInactiveVariantReturns400() throws Exception {
        when(cartService.addItem(any()))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "PRODUCT_VARIANT_INACTIVE",
                        "Product variant is not available for purchase"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productVariantId\":7,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_VARIANT_INACTIVE"));
    }

    /**
     * Exceeding the variant's stock surfaces the service's
     * {@code 400 PRODUCT_VARIANT_INSUFFICIENT_STOCK}.
     */
    @Test
    void addItemBeyondStockReturns400() throws Exception {
        when(cartService.addItem(any()))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "PRODUCT_VARIANT_INSUFFICIENT_STOCK",
                        "Requested quantity exceeds available stock"));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productVariantId\":7,\"quantity\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_VARIANT_INSUFFICIENT_STOCK"));
    }

    /**
     * {@code PUT /cart/items/{id}} updates the quantity and returns the whole cart.
     */
    @Test
    void updateItemAsCustomerReturns200() throws Exception {
        when(cartService.updateItemQuantity(eq(10L), any())).thenReturn(cartWithOneLine());

        mockMvc.perform(put("/api/v1/cart/items/10")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtotal").value(333.00));
    }

    /**
     * Updating another user's cart line surfaces the service's {@code 403 CART_ITEM_FORBIDDEN}.
     */
    @Test
    void updateAnotherUsersItemReturns403() throws Exception {
        when(cartService.updateItemQuantity(eq(10L), any()))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "CART_ITEM_FORBIDDEN",
                        "You cannot access this cart item"));

        mockMvc.perform(put("/api/v1/cart/items/10")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CART_ITEM_FORBIDDEN"));
    }

    /**
     * Updating an unknown cart line surfaces the service's {@code 404 CART_ITEM_NOT_FOUND}.
     */
    @Test
    void updateMissingItemReturns404() throws Exception {
        when(cartService.updateItemQuantity(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("CART_ITEM_NOT_FOUND", "Cart item not found"));

        mockMvc.perform(put("/api/v1/cart/items/99")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CART_ITEM_NOT_FOUND"));
    }

    /**
     * {@code DELETE /cart/items/{id}} removes the line and returns the remaining cart.
     */
    @Test
    void removeItemAsCustomerReturns200WithTheCart() throws Exception {
        when(cartService.removeItem(10L)).thenReturn(new CartResponse(List.of(), BigDecimal.ZERO, 0));

        mockMvc.perform(delete("/api/v1/cart/items/10").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(cartService).removeItem(10L);
    }

    /**
     * Removing another user's cart line surfaces the service's {@code 403 CART_ITEM_FORBIDDEN}.
     */
    @Test
    void removeAnotherUsersItemReturns403() throws Exception {
        when(cartService.removeItem(10L))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "CART_ITEM_FORBIDDEN",
                        "You cannot access this cart item"));

        mockMvc.perform(delete("/api/v1/cart/items/10").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CART_ITEM_FORBIDDEN"));
    }
}
