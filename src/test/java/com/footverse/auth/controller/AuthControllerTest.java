package com.footverse.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footverse.auth.dto.AuthResponse;
import com.footverse.auth.dto.LoginRequest;
import com.footverse.auth.dto.RefreshTokenRequest;
import com.footverse.auth.dto.RegisterRequest;
import com.footverse.auth.service.AuthService;
import com.footverse.common.config.SecurityConfig;
import com.footverse.common.exception.BusinessException;
import com.footverse.common.exception.DuplicateResourceException;
import com.footverse.common.security.JwtUtil;
import com.footverse.common.security.RestAccessDeniedHandler;
import com.footverse.common.security.RestAuthenticationEntryPoint;
import com.footverse.support.AuthFixtures;
import com.footverse.user.dto.UserResponse;
import com.footverse.user.entity.Role;

/**
 * Web-slice tests for {@link AuthController} (register, login, refresh, logout). The security
 * filter chain is imported so authentication/authorization behaviour is exercised while the
 * service layer is mocked.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtUtil.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuthControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    AuthControllerTest(@Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper,
                       @Autowired JwtUtil jwtUtil) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @BeforeEach
    void stubUserLookup() {
        when(userDetailsService.loadUserByUsername(AuthFixtures.EMAIL))
                .thenReturn(AuthFixtures.userDetails(AuthFixtures.EMAIL, Role.CUSTOMER));
    }

    private String bearerToken() {
        return "Bearer " + jwtUtil.createAccessToken(AuthFixtures.EMAIL);
    }

    private AuthResponse authResponse() {
        return new AuthResponse("access-token", "raw-refresh", 900L, "Bearer",
                AuthFixtures.userResponse(1L, AuthFixtures.EMAIL));
    }

    // --- Register ---------------------------------------------------------------------------

    /**
     * A valid registration returns 201 with the enveloped {@link AuthResponse} and no password.
     */
    @Test
    void registerReturns201WithAuthResponseEnvelope() throws Exception {
        when(authService.register(any())).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.registerRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("raw-refresh"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.user.email").value(AuthFixtures.EMAIL))
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }

    /**
     * An invalid body is rejected with 400 VALIDATION_ERROR before reaching the service.
     */
    @Test
    void invalidRequestReturns400() throws Exception {
        RegisterRequest invalid = new RegisterRequest("not-an-email", "short", "", "abc");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(authService, never()).register(any());
    }

    /**
     * A duplicate is surfaced as the enveloped 409 from the service exception.
     */
    @Test
    void duplicateReturns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new DuplicateResourceException("USER_EMAIL_DUPLICATED", "Email already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.registerRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_EMAIL_DUPLICATED"));
    }

    // --- Login ------------------------------------------------------------------------------

    /**
     * A valid login returns 200 with the enveloped {@link AuthResponse} and no password.
     */
    @Test
    void loginReturns200WithAuthResponseEnvelope() throws Exception {
        when(authService.login(any())).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.loginRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.user.email").value(AuthFixtures.EMAIL))
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }

    /**
     * An invalid login body is rejected with 400 VALIDATION_ERROR before reaching the service.
     */
    @Test
    void invalidLoginRequestReturns400() throws Exception {
        LoginRequest invalid = new LoginRequest("not-an-email", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(authService, never()).login(any());
    }

    /**
     * Bad credentials surface as the enveloped 401 from the service exception.
     */
    @Test
    void badCredentialsReturns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.loginRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    /**
     * A disabled account surfaces as the enveloped 401 {@code ACCOUNT_DISABLED} from the service.
     */
    @Test
    void disabledAccountReturns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED",
                        "Account is disabled"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.loginRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
    }

    // --- Refresh ----------------------------------------------------------------------------

    /**
     * A valid refresh returns 200 with the enveloped rotated {@link AuthResponse}.
     */
    @Test
    void refreshReturns200WithAuthResponseEnvelope() throws Exception {
        when(authService.refresh(any()))
                .thenReturn(new AuthResponse("new-access", "new-refresh", 900L, "Bearer",
                        AuthFixtures.userResponse(1L, AuthFixtures.EMAIL)));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.refreshRequest("raw-refresh"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    /**
     * A blank refresh token is rejected with 400 VALIDATION_ERROR before reaching the service.
     */
    @Test
    void invalidRefreshRequestReturns400() throws Exception {
        RefreshTokenRequest invalid = new RefreshTokenRequest("");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(authService, never()).refresh(any());
    }

    /**
     * An unknown or already-rotated refresh token surfaces as the enveloped 401
     * {@code REFRESH_TOKEN_INVALID} from the service exception.
     */
    @Test
    void unknownRefreshTokenReturns401() throws Exception {
        when(authService.refresh(any()))
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID",
                        "Refresh token is invalid"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.refreshRequest("raw-refresh"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("REFRESH_TOKEN_INVALID"));
    }

    // --- Logout -----------------------------------------------------------------------------

    /**
     * An authenticated logout returns 200 with the enveloped empty body and delegates to the
     * service.
     */
    @Test
    void logoutAuthenticatedReturns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.refreshRequest("raw-refresh"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout(any());
    }

    /**
     * An anonymous logout (no access token) is rejected by the security chain with the enveloped
     * 401, never reaching the service.
     */
    @Test
    void logoutAnonymousReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.refreshRequest("raw-refresh"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(authService, never()).logout(any());
    }

    /**
     * Revoking another user's refresh token surfaces as the enveloped 403
     * {@code REFRESH_TOKEN_FORBIDDEN} from the service ownership check.
     */
    @Test
    void logoutAnotherUsersTokenReturns403() throws Exception {
        doThrow(new BusinessException(HttpStatus.FORBIDDEN, "REFRESH_TOKEN_FORBIDDEN",
                "You cannot revoke this refresh token")).when(authService).logout(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.refreshRequest("raw-refresh"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("REFRESH_TOKEN_FORBIDDEN"));
    }

    /**
     * An unknown/already-revoked token still returns 200 (idempotent) — the service no-ops and no
     * error is surfaced.
     */
    @Test
    void logoutUnknownTokenReturns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuthFixtures.refreshRequest("raw-refresh"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
