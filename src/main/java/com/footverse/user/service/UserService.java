package com.footverse.user.service;

import java.util.Optional;

import com.footverse.user.dto.UpdateProfileRequest;
import com.footverse.user.dto.UserResponse;
import com.footverse.user.entity.User;

/**
 * User-module façade for the operations other features need. It is the only entry point into
 * user data for cross-feature callers (architecture-spec §6/§7). Only the methods required by
 * registration, login, and the current-user endpoint are exposed in this sprint.
 */
public interface UserService {

    /**
     * Returns the authenticated caller's own profile.
     *
     * @return the current user's {@link UserResponse}
     */
    UserResponse getCurrentUser();

    /**
     * Updates the authenticated caller's own editable profile fields — {@code fullName},
     * {@code phone}, and {@code avatarUrl} (dto-spec §5; security-spec §6 — self only). The caller
     * is resolved through {@code CurrentUserProvider}, never a request field, so no user id is ever
     * accepted. Email, password, role, and the enabled flag are untouchable through this operation.
     * A phone already held by <strong>another</strong> account is rejected; keeping one's own phone
     * is not a conflict (the coupon-update precedent).
     *
     * @param request the validated profile update (full name, phone, optional avatar URL)
     * @return the caller's refreshed profile
     * @throws com.footverse.common.exception.DuplicateResourceException {@code 409
     *         USER_PHONE_DUPLICATED} when the phone belongs to another account
     */
    UserResponse updateProfile(UpdateProfileRequest request);

    /**
     * Checks whether an account with the given email already exists.
     *
     * @param email the email to check
     * @return {@code true} if the email is taken
     */
    boolean existsByEmail(String email);

    /**
     * Finds an account by email.
     *
     * @param email the email to look up
     * @return the matching user, if any
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks whether an account with the given phone already exists.
     *
     * @param phone the phone to check
     * @return {@code true} if the phone is taken
     */
    boolean existsByPhone(String phone);

    /**
     * Creates and persists a new customer account with the given already-encoded password.
     *
     * @param email           the normalized email
     * @param encodedPassword the BCrypt-encoded password
     * @param fullName        the full name
     * @param phone           the phone number
     * @return the persisted user
     */
    User createUser(String email, String encodedPassword, String fullName, String phone);

    /**
     * Maps a user to its response DTO.
     *
     * @param user the user
     * @return the response DTO
     */
    UserResponse toResponse(User user);
}
