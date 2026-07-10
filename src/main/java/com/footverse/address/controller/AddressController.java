package com.footverse.address.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.footverse.address.dto.AddressResponse;
import com.footverse.address.dto.CreateAddressRequest;
import com.footverse.address.dto.UpdateAddressRequest;
import com.footverse.address.service.AddressService;
import com.footverse.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Shipping-address endpoints for the authenticated customer. The controller only maps HTTP to the
 * {@link AddressService} and wraps results in the response envelope — it holds no business logic
 * and performs no ownership check. Role authorization is enforced by the security filter chain
 * (security-spec §6) and ownership by the service (security-spec §7); no endpoint accepts a user
 * id, so the caller can only ever reach their own addresses.
 *
 * <p>The Swagger annotation {@code io.swagger.v3.oas.annotations.responses.ApiResponse} is written
 * fully qualified throughout, because its simple name collides with the project's response envelope
 * {@link ApiResponse} that every method returns. Error responses declare the envelope explicitly,
 * since the {@code GlobalExceptionHandler} returns it rather than the success payload
 * (error-spec §2). A {@code 403} on the id-bearing operations has two distinct causes: the role
 * denial {@code FORBIDDEN}, and the ownership denial {@code ADDRESS_FORBIDDEN} (error-spec §8.8).</p>
 */
@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /**
     * Lists the caller's own addresses.
     *
     * @return {@code 200 OK} with the caller's addresses
     */
    @Operation(summary = "List the current customer's shipping addresses")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The caller's addresses"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getMyAddresses() {
        return ResponseEntity.ok(ApiResponse.ok(addressService.getMyAddresses()));
    }

    /**
     * Creates an address for the caller.
     *
     * @param request the validated create payload
     * @return {@code 201 Created} with the created address
     */
    @Operation(summary = "Create a shipping address for the current customer")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "The address was created and bound to the caller"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - a field failed validation, or the body is malformed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @Valid @RequestBody CreateAddressRequest request) {
        AddressResponse response = addressService.createAddress(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /**
     * Updates one of the caller's addresses.
     *
     * @param id      the id of the address to update
     * @param request the validated update payload
     * @return {@code 200 OK} with the updated address
     */
    @Operation(summary = "Update one of the current customer's shipping addresses")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The updated address"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - a field failed validation, the body is malformed, "
                            + "or id is not a valid number",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER; "
                            + "ADDRESS_FORBIDDEN - the address belongs to another user",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ADDRESS_NOT_FOUND - no address has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAddressRequest request) {
        AddressResponse response = addressService.updateAddress(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Deletes one of the caller's addresses. Deleting the default address while other addresses
     * exist is rejected with {@code 409 ADDRESS_DEFAULT_NOT_DELETABLE}.
     *
     * @param id the id of the address to delete
     * @return {@code 200 OK} with an empty envelope
     */
    @Operation(summary = "Delete one of the current customer's shipping addresses")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The address was deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - id is not a valid number",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not a CUSTOMER; "
                            + "ADDRESS_FORBIDDEN - the address belongs to another user",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "ADDRESS_NOT_FOUND - no address has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "ADDRESS_DEFAULT_NOT_DELETABLE - choose another default first",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable Long id) {
        addressService.deleteAddress(id);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null));
    }
}
