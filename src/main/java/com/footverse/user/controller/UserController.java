package com.footverse.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.footverse.common.dto.ApiResponse;
import com.footverse.user.dto.UserResponse;
import com.footverse.user.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

/**
 * User endpoints. This sprint task exposes the current-user lookup only.
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
}
