package com.footverse.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the <em>generated</em> OpenAPI document — not merely the presence of annotations. It
 * asserts the JWT bearer scheme, the per-endpoint status codes each operation declares
 * (sprint-3-plan item 09.5), that error responses are documented with the error envelope rather
 * than the success payload, that the padlock (security requirement) is attached to protected
 * operations only, and that the Swagger UI page is served.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiDocumentationTest {

    private static final String ERROR_ENVELOPE_SCHEMA = "#/components/schemas/ApiResponse";

    private final MockMvc mockMvc;

    OpenApiDocumentationTest(@Autowired MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /**
     * Reads the generated OpenAPI document.
     *
     * @return the {@code /v3/api-docs} result actions, ready to assert against
     */
    private org.springframework.test.web.servlet.ResultActions apiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    /**
     * The generated OpenAPI document declares the {@code bearerAuth} HTTP/JWT scheme.
     */
    @Test
    void apiDocsExposeBearerAuthScheme() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    /**
     * The Swagger UI page is served (confirming static UI resources are reachable).
     */
    @Test
    void swaggerUiPageIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    /**
     * The anonymous catalog reads document their real statuses and, being public, document neither
     * {@code 401} nor {@code 403} (security-spec §6).
     */
    @Test
    void publicCatalogOperationsDocumentTheirStatusesAndNoAuthErrors() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.responses.400").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.responses.401").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.responses.403").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].get.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories'].get.responses.401").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/brands'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/brands'].get.responses.401").doesNotExist());
    }

    /**
     * The admin catalog writes document the role denial and the registry's conflict codes
     * (error-spec §8.3, §8.4, §8.6).
     */
    @Test
    void adminCatalogOperationsDocumentAuthAndConflictStatuses() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.responses.201").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories'].post.responses.409").exists())
                .andExpect(jsonPath("$.paths['/api/v1/brands/{id}'].delete.responses.409").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}/variants'].post.responses.409").exists());
    }

    /**
     * The CUSTOMER-scoped shopping operations document the role/ownership {@code 403}, and the
     * address delete documents its {@code 409} state conflict (error-spec §8.8, §8.9).
     */
    @Test
    void customerOperationsDocumentOwnershipAndConflictStatuses() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/addresses'].get.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/addresses/{id}'].delete.responses.409").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/items/{id}'].put.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/items'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/api/v1/wishlist'].get.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/wishlist/{productId}'].delete.responses.401").exists());
    }

    /**
     * {@code POST /wishlist} documents both outcomes of the idempotent add: {@code 201} when the
     * line is created and {@code 200} when it already existed (business-rules → Wishlist,
     * dto-spec §18).
     */
    @Test
    void wishlistAddDocumentsBothCreatedAndIdempotentStatuses() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/wishlist'].post.responses.201").exists())
                .andExpect(jsonPath("$.paths['/api/v1/wishlist'].post.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/wishlist'].post.responses.404").exists());
    }

    /**
     * The auth operations document the business {@code 401}s and the register conflict; the
     * anonymous ones never document the role denial {@code 403}, while the authenticated logout
     * documents its ownership {@code 403} (error-spec §8.2).
     */
    @Test
    void authOperationsDocumentBusinessAuthenticationStatuses() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses.201").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses.409").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses.403").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses.403").exists());
    }

    /**
     * {@code GET /users/me} is open to CUSTOMER and ADMIN alike, so no role denial can occur: it
     * documents {@code 401} but not {@code 403} (security-spec §6).
     */
    @Test
    void currentUserOperationDocumentsUnauthorizedButNotForbidden() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.responses.403").doesNotExist());
    }

    /**
     * Error responses are documented with the error envelope schema, not the operation's success
     * payload — springdoc would otherwise reuse the success schema for every declared status.
     */
    @Test
    void errorResponsesAreDocumentedWithTheErrorEnvelopeSchema() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/wishlist'].post.responses.404"
                        + ".content['application/json'].schema.$ref").value(ERROR_ENVELOPE_SCHEMA))
                .andExpect(jsonPath("$.paths['/api/v1/addresses/{id}'].delete.responses.409"
                        + ".content['application/json'].schema.$ref").value(ERROR_ENVELOPE_SCHEMA))
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.responses.400"
                        + ".content['application/json'].schema.$ref").value(ERROR_ENVELOPE_SCHEMA));
    }

    /**
     * The success responses keep their own payload schema: declaring the statuses must not strip the
     * body springdoc infers from the controller's return type.
     */
    @Test
    void successResponsesKeepTheirPayloadSchema() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/wishlist'].get.responses.200.content.*.schema.$ref")
                        .value("#/components/schemas/ApiResponseListWishlistItemResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/cart'].get.responses.200.content.*.schema.$ref")
                        .value("#/components/schemas/ApiResponseCartResponse"));
    }

    /**
     * The padlock is attached to protected operations only: every operation the frozen matrix
     * restricts declares {@code bearerAuth}, and the anonymous ones declare no security requirement.
     */
    @Test
    void protectedOperationsCarryTheBearerPadlockAndPublicOnesDoNot() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/wishlist'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/addresses'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/categories'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").doesNotExist());
    }
}
