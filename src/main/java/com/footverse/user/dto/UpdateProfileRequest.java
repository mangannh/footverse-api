package com.footverse.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code PUT /users/me}. Validation follows validation-spec §4. Only the caller's
 * editable profile fields are accepted; email, password, role, and the enabled flag are
 * deliberately absent, so this endpoint can never change them. Phone uniqueness (a phone taken by
 * another account) is a business rule enforced by the service.
 *
 * @param fullName  required, the caller's full name
 * @param phone     required, Vietnamese format (10 digits, starts with 0)
 * @param avatarUrl optional, at most 512 characters
 */
public record UpdateProfileRequest(
        @NotBlank String fullName,
        @NotBlank @Pattern(regexp = "^0\\d{9}$") String phone,
        @Size(max = 512) String avatarUrl) {
}
