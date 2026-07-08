package com.footverse.user.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.footverse.common.config.SecurityConfig;
import com.footverse.common.security.JwtUtil;
import com.footverse.common.security.RestAccessDeniedHandler;
import com.footverse.common.security.RestAuthenticationEntryPoint;
import com.footverse.support.AuthFixtures;
import com.footverse.user.entity.Role;
import com.footverse.user.service.UserService;

/**
 * Web-slice tests for {@link UserController}. The security filter chain is imported so
 * authentication behaviour is exercised while the service layer is mocked.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtUtil.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class UserControllerTest {

    private final MockMvc mockMvc;
    private final JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    UserControllerTest(@Autowired MockMvc mockMvc, @Autowired JwtUtil jwtUtil) {
        this.mockMvc = mockMvc;
        this.jwtUtil = jwtUtil;
    }

    private String bearerToken() {
        return "Bearer " + jwtUtil.createAccessToken(AuthFixtures.EMAIL);
    }

    /**
     * An authenticated call returns 200 with the caller's enveloped profile and no password.
     */
    @Test
    void currentUserAuthenticatedReturns200() throws Exception {
        when(userDetailsService.loadUserByUsername(AuthFixtures.EMAIL))
                .thenReturn(AuthFixtures.userDetails(AuthFixtures.EMAIL, Role.CUSTOMER));
        when(userService.getCurrentUser()).thenReturn(AuthFixtures.userResponse(1L, AuthFixtures.EMAIL));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value(AuthFixtures.EMAIL))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        verify(userService).getCurrentUser();
    }

    /**
     * An anonymous call (no access token) is rejected by the security chain with the enveloped
     * 401, never reaching the service.
     */
    @Test
    void currentUserAnonymousReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(userService, never()).getCurrentUser();
    }
}
