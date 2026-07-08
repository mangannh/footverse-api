package com.footverse.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.footverse.support.AuthFixtures;
import com.footverse.user.repository.UserRepository;

/**
 * End-to-end tests exercising {@link JwtFilter} through the real security filter chain: valid
 * tokens reach protected endpoints, invalid tokens yield the enveloped 401, and public
 * endpoints and Swagger remain reachable.
 *
 * <p>Runs in a rolled-back transaction so the fixture user leaves no persisted state.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JwtAuthenticationIntegrationTest {

    private static final String SUBJECT = "user@example.com";
    private static final String PHONE = "0900000030";
    private static final String OTHER_SECRET = "a-completely-different-secret-key-32-bytes";

    private final MockMvc mockMvc;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final String configuredSecret;

    JwtAuthenticationIntegrationTest(@Autowired MockMvc mockMvc, @Autowired JwtUtil jwtUtil,
                                     @Autowired UserRepository userRepository,
                                     @Value("${footverse.jwt.secret}") String configuredSecret) {
        this.mockMvc = mockMvc;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.configuredSecret = configuredSecret;
    }

    @BeforeEach
    void persistTokenSubject() {
        userRepository.save(AuthFixtures.customer(SUBJECT, PHONE));
    }

    /**
     * A valid token whose user exists authenticates the request; with no controller yet it
     * resolves to the routing-404 envelope (proving it passed authentication rather than being
     * rejected 401).
     */
    @Test
    void validTokenReachesProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.createAccessToken(SUBJECT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    /**
     * An expired token (same secret, past expiry) is rejected with the enveloped 401.
     */
    @Test
    void expiredTokenReturns401() throws Exception {
        String expiredToken = new JwtUtil(configuredSecret, -60).createAccessToken(SUBJECT);
        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    /**
     * A token signed with a different secret is rejected with the enveloped 401.
     */
    @Test
    void badSignatureTokenReturns401() throws Exception {
        String badSignatureToken = new JwtUtil(OTHER_SECRET, 900).createAccessToken(SUBJECT);
        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + badSignatureToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    /**
     * A malformed token is rejected with the enveloped 401.
     */
    @Test
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    /**
     * A non-Bearer Authorization header carries no token, so a protected endpoint returns 401.
     */
    @Test
    void nonBearerHeaderReturns401OnProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    /**
     * A public endpoint is reachable without any token.
     */
    @Test
    void publicEndpointDoesNotRequireToken() throws Exception {
        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    /**
     * Swagger docs remain public after the JWT filter is installed.
     */
    @Test
    void swaggerRemainsAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
