package com.footverse.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.footverse.common.dto.ApiResponse;
import com.footverse.user.dto.UpdateProfileRequest;
import com.footverse.user.dto.UserResponse;
import com.footverse.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * User endpoints for the authenticated caller: the current-user lookup and the self-service profile
 * update. Both resolve the caller from the access token (security-spec §6/§7) — no endpoint accepts
 * a user id, so a caller can only ever read or edit themselves; role authorization
 * (CUSTOMER and ADMIN alike) is enforced by the security filter chain.
 *
 * <p>The Swagger annotation {@code io.swagger.v3.oas.annotations.responses.ApiResponse} is written
 * fully qualified, because its simple name collides with the project's response envelope
 * {@link ApiResponse}. Error responses declare the envelope explicitly, since the
 * {@code GlobalExceptionHandler} returns it rather than the success payload (error-spec §2).</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Returns the authenticated caller's own profile. The caller is always resolved from the
     * access token; there is no path or query parameter, so a user can only ever read themselves.
     *
     * @return {@code 200 OK} with the caller's {@link UserResponse}
     */
    @Operation(summary = "Get the current authenticated user's profile")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The caller's own profile"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        return ResponseEntity.ok(ApiResponse.ok(userService.getCurrentUser()));
    }

    /**
     * Updates the authenticated caller's own profile ({@code fullName}, {@code phone},
     * {@code avatarUrl}). The caller is always resolved from the access token; there is no path or
     * query parameter, so a user can only ever update themselves. Email, password, role, and the
     * enabled flag cannot be changed here.
     *
     * @param request the validated profile update
     * @return {@code 200 OK} with the caller's refreshed {@link UserResponse}
     */
    @Operation(summary = "Update the current authenticated user's profile")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The caller's refreshed profile"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - a field failed validation, or the body is malformed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "USER_PHONE_DUPLICATED - the phone belongs to another account",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(request)));
    }
}
