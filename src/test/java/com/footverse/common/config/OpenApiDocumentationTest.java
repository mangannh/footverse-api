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

    /**
     * The coupon operations document exactly the statuses each can really return: the CUSTOMER
     * checkout preview ({@code 200}/{@code 400}/{@code 403}/{@code 404}, never {@code 409} — it
     * mutates nothing), the ADMIN list ({@code 200}, no {@code 400}/{@code 404} — it has no request
     * body and no typed path parameter), the ADMIN create ({@code 201}/{@code 409}, no {@code 404}),
     * and the ADMIN update ({@code 200}/{@code 404}/{@code 409}); all protected paths declare
     * {@code 401}/{@code 403} (dto-spec §20, error-spec §8.10, security-spec §6, validation-spec §9).
     */
    @Test
    void couponOperationsDocumentTheirStatuses() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/coupons/validate'].post.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/validate'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/validate'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/validate'].post.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/validate'].post.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/validate'].post.responses.409").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].get.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].get.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].get.responses.400").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].get.responses.404").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].post.responses.201").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].post.responses.409").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].post.responses.404").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/{id}'].put.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/{id}'].put.responses.400").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/{id}'].put.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/{id}'].put.responses.409").exists());
    }

    /**
     * The customer order operations document exactly their statuses: checkout ({@code 201}, never
     * {@code 409}), the caller-scoped list ({@code 200}, no {@code 400}/{@code 404}), the
     * ownership-checked detail ({@code 200}/{@code 400}/{@code 403}/{@code 404}), and the
     * cancellation ({@code 200}/{@code 403}/{@code 404}/{@code 409}) (dto-spec §20, error-spec §8.11,
     * security-spec §6/§7).
     */
    @Test
    void orderCustomerOperationsDocumentTheirStatuses() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses.201").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses.400").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses.409").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get.responses.400").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get.responses.404").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}'].get.responses.400").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}'].get.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}'].get.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/cancel'].post.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/cancel'].post.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/cancel'].post.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/cancel'].post.responses.409").exists());
    }

    /**
     * The admin order-status operation documents the {@code 409} state-conflict pair
     * ({@code ORDER_NOT_CANCELLABLE} / {@code ORDER_INVALID_STATUS_TRANSITION}) alongside the
     * validation {@code 400}, the role {@code 403}, and the {@code 404}. Being an admin operation it
     * bypasses ownership, so its {@code 403} is only the role denial and its {@code 404} the only
     * not-found (error-spec §8.11, security-spec §7, validation-spec §12).
     */
    @Test
    void orderAdminStatusOperationDocumentsItsStatuses() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].patch.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].patch.responses.400").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].patch.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].patch.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].patch.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].patch.responses.409").exists());
    }

    /**
     * Every coupon and order operation is restricted by the frozen matrix (none is public), so each
     * carries the bearer padlock (security-spec §6).
     */
    @Test
    void couponAndOrderOperationsCarryTheBearerPadlock() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/coupons/validate'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/{id}'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/cancel'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].patch.security[0].bearerAuth").exists());
    }

    /**
     * Coupon and order error responses bind the shared error envelope schema, while their success
     * responses keep the payload schema springdoc infers from the controller return type
     * (api-guidelines §Swagger).
     */
    @Test
    void couponAndOrderResponsesUseTheExpectedSchemas() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses.404"
                        + ".content['application/json'].schema.$ref").value(ERROR_ENVELOPE_SCHEMA))
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].patch.responses.409"
                        + ".content['application/json'].schema.$ref").value(ERROR_ENVELOPE_SCHEMA))
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].post.responses.409"
                        + ".content['application/json'].schema.$ref").value(ERROR_ENVELOPE_SCHEMA))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses.201.content.*.schema.$ref")
                        .value("#/components/schemas/ApiResponseOrderDetailResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].get.responses.200.content.*.schema.$ref")
                        .value("#/components/schemas/ApiResponsePageResponseCouponResponse"));
    }
}
