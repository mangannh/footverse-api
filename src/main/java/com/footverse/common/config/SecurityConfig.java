package com.footverse.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.footverse.common.security.JwtFilter;
import com.footverse.common.security.JwtUtil;
import com.footverse.common.security.RestAccessDeniedHandler;
import com.footverse.common.security.RestAuthenticationEntryPoint;

import lombok.RequiredArgsConstructor;

/**
 * Stateless Spring Security configuration defining the frozen endpoint authorization matrix
 * (security-spec §6), the BCrypt password encoder, and the enveloped 401/403 handlers.
 *
 * <p>The {@link JwtFilter} authenticates Bearer access tokens and populates the caller's role
 * authority. Public catalog reads stay open; the admin catalog writes and admin coupon management
 * (plus the admin order status transition) require {@code ROLE_ADMIN}; the customer-owned shopping
 * resources (address, cart, wishlist), the checkout preview, the order endpoints, and the review
 * write paths require {@code ROLE_CUSTOMER}; every other endpoint requires authentication. A denied
 * authorization is
 * rendered as the enveloped {@code 403} by the {@link RestAccessDeniedHandler}.</p>
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 10;

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    /**
     * Builds the stateless security filter chain: CSRF disabled, no HTTP session, the public
     * endpoints from security-spec §6 open, everything else authenticated, and the custom
     * 401/403 handlers wired in.
     *
     * @param http the Spring Security HTTP configuration
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/brands/**").permitAll()
                        // Admin catalog writes (security-spec §6). Method-specific, so they never
                        // overlap the public GET rules above; the broad product PUT/DELETE patterns
                        // also cover the variant/image sub-paths, whose only extra writes are the
                        // two nested POSTs declared explicitly below.
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/brands").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/brands/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/brands/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/*/variants").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/*/images").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/**").hasRole("ADMIN")
                        // Customer-owned shopping resources (security-spec §6). Every HTTP method is
                        // CUSTOMER-only — deliberately excluding ADMIN, unlike the CUSTOMER+ADMIN
                        // /users/me rows. Ownership (a caller acts only on their own rows) is enforced
                        // per-service through CurrentUserProvider, not here (security-spec §7).
                        .requestMatchers("/api/v1/addresses/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/cart/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/wishlist/**").hasRole("CUSTOMER")
                        // Coupon and order authorization (security-spec §6). The two narrow rules
                        // (the CUSTOMER checkout preview and the ADMIN order-status transition) must
                        // precede their broader siblings so the specific method/path wins: without
                        // them, POST /coupons/validate would match the ADMIN POST /coupons/** rule and
                        // PATCH /orders/*/status would match the CUSTOMER /orders/** rule. Ownership on
                        // the customer order paths is enforced per-service via CurrentUserProvider,
                        // not here (security-spec §7).
                        .requestMatchers(HttpMethod.POST, "/api/v1/coupons/validate").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/*/status").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/coupons/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/coupons/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/coupons/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/orders/**").hasRole("CUSTOMER")
                        // Review write paths (security-spec §6): POST /reviews, PUT /reviews/{id},
                        // DELETE /reviews/{id} are CUSTOMER-only. The public listing
                        // GET /products/{id}/reviews is already open via the GET /api/v1/products/**
                        // permitAll rule above, so no matcher for it is added here. This pattern does
                        // not overlap any other rule; ownership and DELIVERED-order eligibility are
                        // enforced per-service via CurrentUserProvider (security-spec §7).
                        .requestMatchers("/api/v1/reviews/**").hasRole("CUSTOMER")
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtFilter(jwtUtil, authenticationEntryPoint, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Provides the single application-wide password encoder: BCrypt with strength 10.
     *
     * @return the BCrypt {@link PasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
