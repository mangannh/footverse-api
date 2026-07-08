package com.footverse.support;

import java.time.LocalDateTime;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.footverse.auth.dto.LoginRequest;
import com.footverse.auth.dto.RefreshTokenRequest;
import com.footverse.auth.dto.RegisterRequest;
import com.footverse.user.dto.UserResponse;
import com.footverse.user.entity.Role;
import com.footverse.user.entity.User;

/**
 * Shared test fixtures for the authentication and user modules (testing-guidelines §Test Data).
 *
 * <p>Pure data factories only — no business logic. They centralise the common request/entity/DTO
 * shapes so tests do not duplicate fixture construction. Callers that need specific values
 * (e.g. to exercise email normalisation) still build their own inline.</p>
 */
public final class AuthFixtures {

    /** Default account email used across auth tests. */
    public static final String EMAIL = "user@example.com";
    /** Default valid password (8+ chars, letter + digit). */
    public static final String PASSWORD = "Password1";
    /** Default full name. */
    public static final String FULL_NAME = "Test User";
    /** Default Vietnamese phone number. */
    public static final String PHONE = "0912345678";

    private AuthFixtures() {
    }

    /**
     * Builds a valid registration request with the default fixture values.
     *
     * @return a valid {@link RegisterRequest}
     */
    public static RegisterRequest registerRequest() {
        return new RegisterRequest(EMAIL, PASSWORD, FULL_NAME, PHONE);
    }

    /**
     * Builds a valid registration request for the given identity.
     *
     * @param email    the email
     * @param phone    the phone
     * @param fullName the full name
     * @return a valid {@link RegisterRequest}
     */
    public static RegisterRequest registerRequest(String email, String phone, String fullName) {
        return new RegisterRequest(email, PASSWORD, fullName, phone);
    }

    /**
     * Builds a valid login request with the default fixture credentials.
     *
     * @return a valid {@link LoginRequest}
     */
    public static LoginRequest loginRequest() {
        return new LoginRequest(EMAIL, PASSWORD);
    }

    /**
     * Builds a valid login request for the given email (default password).
     *
     * @param email the email
     * @return a valid {@link LoginRequest}
     */
    public static LoginRequest loginRequest(String email) {
        return new LoginRequest(email, PASSWORD);
    }

    /**
     * Builds a refresh/logout request carrying the given raw token.
     *
     * @param refreshToken the raw refresh token
     * @return a {@link RefreshTokenRequest}
     */
    public static RefreshTokenRequest refreshRequest(String refreshToken) {
        return new RefreshTokenRequest(refreshToken);
    }

    /**
     * Builds an enabled CUSTOMER entity for the given identity.
     *
     * @param email the email
     * @param phone the phone
     * @return an enabled {@link User}
     */
    public static User customer(String email, String phone) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("$2a$10$encoded");
        user.setFullName(FULL_NAME);
        user.setPhone(phone);
        user.setRole(Role.CUSTOMER);
        user.setEnabled(true);
        return user;
    }

    /**
     * Builds an enabled ADMIN entity for the given identity.
     *
     * @param email the email
     * @param phone the phone
     * @return an enabled {@link User} with the ADMIN role
     */
    public static User admin(String email, String phone) {
        User user = customer(email, phone);
        user.setRole(Role.ADMIN);
        return user;
    }

    /**
     * Builds Spring Security {@link UserDetails} carrying the given role's authority, for tests
     * that drive the {@code UserDetailsService} used by the JWT filter.
     *
     * @param email the account email (the principal username)
     * @param role  the account role
     * @return the mapped {@link UserDetails}
     */
    public static UserDetails userDetails(String email, Role role) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("$2a$10$encoded")
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()))
                .build();
    }

    /**
     * Builds a {@link UserResponse} for the given id and email (default remaining fields).
     *
     * @param id    the user id
     * @param email the email
     * @return a {@link UserResponse}
     */
    public static UserResponse userResponse(Long id, String email) {
        return new UserResponse(id, email, FULL_NAME, PHONE, null, Role.CUSTOMER, true,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
