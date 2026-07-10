package com.footverse.brand.controller;

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

import com.footverse.brand.dto.BrandResponse;
import com.footverse.brand.dto.CreateBrandRequest;
import com.footverse.brand.dto.UpdateBrandRequest;
import com.footverse.brand.service.BrandService;
import com.footverse.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Brand endpoints: a public listing plus admin create/update/delete. The controller only maps
 * HTTP to the {@link BrandService} and wraps results in the response envelope — it holds no
 * business logic. Role authorization is enforced by the security filter chain (security-spec §6),
 * so the admin operations carry only the Swagger security requirement here.
 *
 * <p>The Swagger annotation {@code io.swagger.v3.oas.annotations.responses.ApiResponse} is written
 * fully qualified throughout, because its simple name collides with the project's response envelope
 * {@link ApiResponse} that every method returns. Error responses declare the envelope explicitly,
 * since the {@code GlobalExceptionHandler} returns it rather than the success payload
 * (error-spec §2). The public listing is anonymous (security-spec §6), so it declares neither
 * {@code 401} nor {@code 403}.</p>
 */
@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    /**
     * Lists every brand. Public endpoint.
     *
     * @return {@code 200 OK} with the list of brands
     */
    @Operation(summary = "List all brands")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The list of brands")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAllBrands() {
        return ResponseEntity.ok(ApiResponse.ok(brandService.getAllBrands()));
    }

    /**
     * Creates a new brand. Admin only.
     *
     * @param request the validated create payload
     * @return {@code 201 Created} with the created brand
     */
    @Operation(summary = "Create a new brand")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "The created brand"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - a field failed validation, or the body is malformed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not an ADMIN",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "BRAND_NAME_DUPLICATED - the name is already taken",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(
            @Valid @RequestBody CreateBrandRequest request) {
        BrandResponse response = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /**
     * Updates an existing brand. Admin only.
     *
     * @param id      the id of the brand to update
     * @param request the validated update payload
     * @return {@code 200 OK} with the updated brand
     */
    @Operation(summary = "Update an existing brand")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The updated brand"),
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
                    description = "FORBIDDEN - the caller is not an ADMIN",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "BRAND_NOT_FOUND - no brand has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "BRAND_NAME_DUPLICATED - the name is already taken",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBrandRequest request) {
        BrandResponse response = brandService.updateBrand(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Deletes a brand. Admin only; a brand still referenced by a product cannot be removed
     * (guarded by the database foreign key, reported as {@code 409 BRAND_IN_USE}).
     *
     * @param id the id of the brand to delete
     * @return {@code 200 OK} with an empty envelope
     */
    @Operation(summary = "Delete a brand")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "The brand was deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR - id is not a valid number",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHORIZED - missing, invalid, or expired access token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "FORBIDDEN - the caller is not an ADMIN",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "BRAND_NOT_FOUND - no brand has this id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "BRAND_IN_USE - the brand is still referenced by products",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable Long id) {
        brandService.deleteBrand(id);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null));
    }
}
