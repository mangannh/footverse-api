package com.footverse.common.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-request filter that authenticates a Bearer access token.
 *
 * <p>When a valid token is present, the user is loaded through the {@link UserDetailsService}
 * (security-spec §1.4) and placed in the {@link SecurityContextHolder} as an authenticated
 * principal carrying its granted authority (role), so the authorization layer can enforce the
 * endpoint matrix. When a token is present but invalid — or valid yet the user no longer exists
 * or has been disabled — the request is rejected through the {@link RestAuthenticationEntryPoint}
 * with the enveloped 401. Requests without a Bearer token are passed through untouched, so the
 * authorization layer decides (public served, protected → enveloped 401). The filter performs
 * authentication only — no business logic.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final UserDetailsService userDetailsService;

    /**
     * Authenticates the request from its Bearer token, if any.
     *
     * @param request     the current request
     * @param response    the current response
     * @param filterChain the remaining filter chain
     * @throws ServletException if the downstream chain fails
     * @throws IOException      if writing the rejection response fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token != null) {
            if (!jwtUtil.isValid(token)) {
                reject(request, response, new BadCredentialsException("Invalid JWT"), "Rejected invalid JWT");
                return;
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    setAuthentication(jwtUtil.getSubject(token));
                } catch (AuthenticationException e) {
                    reject(request, response, e, "Rejected JWT for unknown or disabled user");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void setAuthentication(String subject) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(subject);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException cause, String reason) throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        log.warn("{} for {} {}", reason, request.getMethod(), request.getRequestURI());
        authenticationEntryPoint.commence(request, response, cause);
    }
}
