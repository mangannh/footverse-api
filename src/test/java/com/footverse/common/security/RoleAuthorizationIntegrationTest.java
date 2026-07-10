package com.footverse.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.footverse.support.AuthFixtures;
import com.footverse.user.repository.UserRepository;

/**
 * End-to-end tests for the frozen authorization matrix (security-spec §6) enforced by
 * {@link com.footverse.common.config.SecurityConfig} once {@link JwtFilter} populates the caller's
 * role. A CUSTOMER token is denied the enveloped {@code 403} on admin catalog writes, an ADMIN
 * token clears authorization (reaching the category controller, or the routing-404 for the brand /
 * product controllers that arrive in later tasks), and public catalog reads stay open without a
 * token. Symmetrically, the customer-owned shopping paths (address / cart / wishlist) deny an
 * ADMIN token and clear a CUSTOMER token; ownership itself is enforced per-service in the tasks
 * that build those modules (security-spec §7), not by this configuration.
 *
 * <p>ADMIN accounts are provisioned outside the API (user management is Future), so the fixtures
 * persist a CUSTOMER and an ADMIN directly through the repository. Runs in a rolled-back
 * transaction so no user state leaks.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleAuthorizationIntegrationTest {

    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final String CUSTOMER_PHONE = "0900000040";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PHONE = "0900000041";

    private final MockMvc mockMvc;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    RoleAuthorizationIntegrationTest(@Autowired MockMvc mockMvc, @Autowired JwtUtil jwtUtil,
                                     @Autowired UserRepository userRepository) {
        this.mockMvc = mockMvc;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @BeforeEach
    void persistUsers() {
        userRepository.save(AuthFixtures.customer(CUSTOMER_EMAIL, CUSTOMER_PHONE));
        userRepository.save(AuthFixtures.admin(ADMIN_EMAIL, ADMIN_PHONE));
    }

    private String customerToken() {
        return "Bearer " + jwtUtil.createAccessToken(CUSTOMER_EMAIL);
    }

    private String adminToken() {
        return "Bearer " + jwtUtil.createAccessToken(ADMIN_EMAIL);
    }

    /**
     * A CUSTOMER token on an admin category write is denied with the enveloped {@code 403}
     * {@code FORBIDDEN}.
     */
    @Test
    void customerOnAdminCategoryWriteReturnsEnveloped403() throws Exception {
        mockMvc.perform(post("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    /**
     * A CUSTOMER token on an admin brand delete is denied with the enveloped {@code 403}.
     */
    @Test
    void customerOnAdminBrandDeleteReturnsEnveloped403() throws Exception {
        mockMvc.perform(delete("/api/v1/brands/1").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    /**
     * A CUSTOMER token on an admin nested product-variant write is denied with the enveloped
     * {@code 403}.
     */
    @Test
    void customerOnAdminProductVariantWriteReturnsEnveloped403() throws Exception {
        mockMvc.perform(post("/api/v1/products/1/variants").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    /**
     * An ADMIN token clears authorization on an admin category write and reaches the
     * {@link com.footverse.category.controller.CategoryController}, creating the category
     * ({@code 201}) — proving authorization passed rather than {@code 403}.
     */
    @Test
    void adminOnAdminCategoryWritePassesAuthorization() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Role Auth Test Category\",\"description\":\"created by role test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Role Auth Test Category"));
    }

    /**
     * An ADMIN token clears authorization on an admin product update and reaches the
     * {@link com.footverse.product.controller.ProductController}; the empty body then fails request
     * parsing and resolves to the enveloped {@code 400 VALIDATION_ERROR} — proving authorization
     * passed rather than {@code 403}.
     */
    @Test
    void adminOnAdminProductUpdatePassesAuthorization() throws Exception {
        mockMvc.perform(put("/api/v1/products/1").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    /**
     * A public catalog GET is reachable without a token and returns the enveloped {@code 200}
     * category list, proving the public read stays open.
     */
    @Test
    void publicCategoryGetIsOpenWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * An ADMIN token on the customer-owned address path is denied with the enveloped {@code 403}:
     * the frozen matrix scopes this path to CUSTOMER only.
     */
    @Test
    void adminOnCustomerAddressPathReturnsEnveloped403() throws Exception {
        expectEnveloped403(get("/api/v1/addresses").header(HttpHeaders.AUTHORIZATION, adminToken()));
    }

    /**
     * An ADMIN token on the customer-owned cart path is denied with the enveloped {@code 403}.
     */
    @Test
    void adminOnCustomerCartPathReturnsEnveloped403() throws Exception {
        expectEnveloped403(post("/api/v1/cart/items").header(HttpHeaders.AUTHORIZATION, adminToken()));
    }

    /**
     * An ADMIN token on the customer-owned wishlist path is denied with the enveloped {@code 403}.
     */
    @Test
    void adminOnCustomerWishlistPathReturnsEnveloped403() throws Exception {
        expectEnveloped403(get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, adminToken()));
    }

    /**
     * A CUSTOMER token clears the role check on the address path and reaches the
     * {@link com.footverse.address.controller.AddressController}, which returns the caller's own
     * (still empty) address list — proving authorization passed rather than {@code 401}/{@code 403}.
     */
    @Test
    void customerOnCustomerAddressPathPassesRoleAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/addresses").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * A CUSTOMER token clears the role check on the cart path and reaches the
     * {@link com.footverse.cart.controller.CartController}, which returns the caller's own (still
     * empty) cart — proving authorization passed rather than {@code 401}/{@code 403}.
     */
    @Test
    void customerOnCustomerCartPathPassesRoleAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.itemCount").value(0));
    }

    /**
     * A CUSTOMER token clears the role check on the wishlist path and reaches the
     * {@link com.footverse.wishlist.controller.WishlistController}, which returns the caller's own
     * (still empty) wishlist — proving authorization passed rather than {@code 401}/{@code 403}.
     */
    @Test
    void customerOnCustomerWishlistPathPassesRoleAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private void expectEnveloped403(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
