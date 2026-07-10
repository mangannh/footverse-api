package com.footverse.wishlist.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.footverse.common.config.SecurityConfig;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.common.security.JwtUtil;
import com.footverse.common.security.RestAccessDeniedHandler;
import com.footverse.common.security.RestAuthenticationEntryPoint;
import com.footverse.support.AuthFixtures;
import com.footverse.user.entity.Role;
import com.footverse.wishlist.dto.WishlistAddResult;
import com.footverse.wishlist.dto.WishlistItemResponse;
import com.footverse.wishlist.service.WishlistService;

/**
 * Web-slice tests for {@link WishlistController}: the three CUSTOMER endpoints, the {@code 201}
 * versus idempotent {@code 200} split on add, request-body validation, the role denial for an ADMIN
 * token, the anonymous {@code 401}, and the service's {@code 404} rendered through the standard
 * envelope. The security filter chain is imported; the service is mocked, so no business rule runs
 * here.
 */
@WebMvcTest(WishlistController.class)
@Import({SecurityConfig.class, JwtUtil.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class WishlistControllerTest {

    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    private final MockMvc mockMvc;
    private final JwtUtil jwtUtil;

    @MockitoBean
    private WishlistService wishlistService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    WishlistControllerTest(@Autowired MockMvc mockMvc, @Autowired JwtUtil jwtUtil) {
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

    private WishlistItemResponse line() {
        return new WishlistItemResponse(50L, 7L, "Air Force 1", "primary.png", new BigDecimal("100.00"), true);
    }

    // ----- GET /wishlist -----

    /**
     * {@code GET /wishlist} returns the caller's lines in the enveloped list.
     */
    @Test
    void getWishlistAsCustomerReturns200() throws Exception {
        when(wishlistService.getMyWishlist()).thenReturn(List.of(line()));

        mockMvc.perform(get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value(50))
                .andExpect(jsonPath("$.data[0].productId").value(7))
                .andExpect(jsonPath("$.data[0].productName").value("Air Force 1"))
                .andExpect(jsonPath("$.data[0].primaryImageUrl").value("primary.png"))
                .andExpect(jsonPath("$.data[0].basePrice").value(100.00))
                .andExpect(jsonPath("$.data[0].available").value(true));
    }

    /**
     * An empty wishlist is an empty enveloped list, not a {@code 404}.
     */
    @Test
    void getEmptyWishlistReturnsEmptyList() throws Exception {
        when(wishlistService.getMyWishlist()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * {@code GET /wishlist} with an ADMIN token is denied the enveloped {@code 403}: the path is
     * CUSTOMER-only (security-spec §6).
     */
    @Test
    void getWishlistAsAdminReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(wishlistService, never()).getMyWishlist();
    }

    /**
     * An anonymous request is denied the enveloped {@code 401}.
     */
    @Test
    void getWishlistAnonymouslyReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist"))
                .andExpect(status().isUnauthorized());

        verify(wishlistService, never()).getMyWishlist();
    }

    // ----- POST /wishlist -----

    /**
     * Adding a product the caller has not wishlisted returns {@code 201 Created} with the new line
     * (dto-spec §18, api-guidelines).
     */
    @Test
    void addNewProductReturns201Created() throws Exception {
        when(wishlistService.addToWishlist(any())).thenReturn(new WishlistAddResult(line(), true));

        mockMvc.perform(post("/api/v1/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Created"))
                .andExpect(jsonPath("$.data.id").value(50))
                .andExpect(jsonPath("$.data.productId").value(7));
    }

    /**
     * Adding a duplicate is an idempotent no-op: {@code 200 OK} carrying the existing line, never an
     * error (business-rules → Wishlist).
     */
    @Test
    void addDuplicateProductReturns200Ok() throws Exception {
        when(wishlistService.addToWishlist(any())).thenReturn(new WishlistAddResult(line(), false));

        mockMvc.perform(post("/api/v1/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.id").value(50));
    }

    /**
     * A missing {@code productId} fails Bean Validation with the enveloped {@code 400}
     * (validation-spec §8).
     */
    @Test
    void addWithoutProductIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("productId"));

        verify(wishlistService, never()).addToWishlist(any());
    }

    /**
     * A non-positive {@code productId} fails Bean Validation.
     */
    @Test
    void addWithNonPositiveProductIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("productId"));

        verify(wishlistService, never()).addToWishlist(any());
    }

    /**
     * Adding an unknown or soft-deleted product surfaces the catalog's {@code 404 PRODUCT_NOT_FOUND}.
     */
    @Test
    void addUnknownProductReturns404() throws Exception {
        when(wishlistService.addToWishlist(any()))
                .thenThrow(new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        mockMvc.perform(post("/api/v1/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":9}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    /**
     * {@code POST /wishlist} with an ADMIN token is denied the enveloped {@code 403}.
     */
    @Test
    void addAsAdminReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":7}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(wishlistService, never()).addToWishlist(any());
    }

    // ----- DELETE /wishlist/{productId} -----

    /**
     * Removing a product returns the enveloped empty body and delegates the product id to the
     * service, which scopes the delete to the caller.
     */
    @Test
    void removeProductReturns200WithEmptyEnvelope() throws Exception {
        mockMvc.perform(delete("/api/v1/wishlist/7").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(wishlistService).removeFromWishlist(eq(7L));
    }

    /**
     * Removing a product the caller has not wishlisted is idempotent: the service raises nothing and
     * the endpoint still answers {@code 200}.
     */
    @Test
    void removeNotWishlistedProductReturns200() throws Exception {
        mockMvc.perform(delete("/api/v1/wishlist/999").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(wishlistService).removeFromWishlist(eq(999L));
    }

    /**
     * {@code DELETE /wishlist/{productId}} with an ADMIN token is denied the enveloped {@code 403}.
     */
    @Test
    void removeAsAdminReturns403() throws Exception {
        mockMvc.perform(delete("/api/v1/wishlist/7").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(wishlistService, never()).removeFromWishlist(any());
    }
}
