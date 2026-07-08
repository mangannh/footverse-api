package com.footverse.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link JwtFilter} SecurityContext population (with role authority) and rejection
 * behaviour.
 */
class JwtFilterTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long";
    private static final String SUBJECT = "user@example.com";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 900);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final JwtFilter jwtFilter =
            new JwtFilter(jwtUtil, new RestAuthenticationEntryPoint(objectMapper), userDetailsService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UserDetails userWithAuthority(String authority) {
        return User.builder()
                .username(SUBJECT)
                .password("$2a$10$hashedpassword")
                .authorities(new SimpleGrantedAuthority(authority))
                .build();
    }

    private MockHttpServletRequest requestWithToken(String subject) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.createAccessToken(subject));
        return request;
    }

    /**
     * A CUSTOMER token authenticates the caller and puts {@code ROLE_CUSTOMER} in the
     * SecurityContext, then lets the request proceed.
     *
     * @throws Exception if filtering fails
     */
    @Test
    void customerTokenPopulatesRoleCustomer() throws Exception {
        when(userDetailsService.loadUserByUsername(SUBJECT)).thenReturn(userWithAuthority("ROLE_CUSTOMER"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilter(requestWithToken(SUBJECT), response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo(SUBJECT);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
        assertThat(chain.getRequest()).as("request should proceed down the chain").isNotNull();
    }

    /**
     * An ADMIN token puts {@code ROLE_ADMIN} in the SecurityContext.
     *
     * @throws Exception if filtering fails
     */
    @Test
    void adminTokenPopulatesRoleAdmin() throws Exception {
        when(userDetailsService.loadUserByUsername(SUBJECT)).thenReturn(userWithAuthority("ROLE_ADMIN"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilter(requestWithToken(SUBJECT), response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    /**
     * A valid token whose user no longer exists (or is disabled) is rejected with the enveloped
     * 401 and does not proceed.
     *
     * @throws Exception if filtering fails
     */
    @Test
    void unknownUserTokenIsRejectedWithEnveloped401() throws Exception {
        when(userDetailsService.loadUserByUsername(SUBJECT))
                .thenThrow(new UsernameNotFoundException("User not found"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilter(requestWithToken(SUBJECT), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("request should not proceed").isNull();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("errorCode").asText()).isEqualTo("UNAUTHORIZED");
    }

    /**
     * An invalid Bearer token is rejected with the enveloped 401 and does not proceed.
     *
     * @throws Exception if filtering fails
     */
    @Test
    void invalidTokenIsRejectedWithEnveloped401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cart");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("request should not proceed").isNull();

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("errorCode").asText()).isEqualTo("UNAUTHORIZED");
    }
}
