package com.footverse.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.footverse.common.config.SecurityConfig;
import com.footverse.common.dto.PageResponse;
import com.footverse.common.exception.BusinessException;
import com.footverse.common.exception.DuplicateResourceException;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.common.security.JwtUtil;
import com.footverse.common.security.RestAccessDeniedHandler;
import com.footverse.common.security.RestAuthenticationEntryPoint;
import com.footverse.order.dto.CouponPreviewResponse;
import com.footverse.order.dto.CouponResponse;
import com.footverse.order.entity.DiscountType;
import com.footverse.order.service.OrderService;
import com.footverse.support.AuthFixtures;
import com.footverse.user.entity.Role;

/**
 * Web-slice tests for the {@link CouponController}: the CUSTOMER checkout preview
 * {@code POST /coupons/validate} and the ADMIN coupon CRUD ({@code GET /coupons},
 * {@code POST /coupons}, {@code PUT /coupons/{id}}). They assert the success envelopes, request-body
 * validation, the role denial for the wrong token (the preview is CUSTOMER-only, the CRUD ADMIN-only),
 * the anonymous {@code 401}, and the coupon / cart business errors rendered through the standard
 * envelope. The security filter chain is imported; the service is mocked, so no business rule runs
 * here.
 */
@WebMvcTest(CouponController.class)
@Import({SecurityConfig.class, JwtUtil.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class CouponControllerTest {

    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    private final MockMvc mockMvc;
    private final JwtUtil jwtUtil;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    CouponControllerTest(@Autowired MockMvc mockMvc, @Autowired JwtUtil jwtUtil) {
        this.mockMvc = mockMvc;
        this.jwtUtil = jwtUtil;
    }

    private String customerToken() {
        when(userDetailsService.loadUserByUsername(CUSTOMER_EMAIL))
                .thenReturn(AuthFixtures.userDetails(CUSTOMER_EMAIL, Role.CUSTOMER));
        return "Bearer " + jwtUtil.createAccessToken(CUSTOMER_EMAIL);
    }

    private String adminToken() {
        when(userDetailsService.loadUserByUsername(ADMIN_EMAIL))
                .thenReturn(AuthFixtures.userDetails(ADMIN_EMAIL, Role.ADMIN));
        return "Bearer " + jwtUtil.createAccessToken(ADMIN_EMAIL);
    }

    private CouponPreviewResponse previewWithCoupon() {
        return new CouponPreviewResponse("SAVE", "Save Now", new BigDecimal("200.00"),
                new BigDecimal("20.00"), new BigDecimal("30000.00"), new BigDecimal("30180.00"));
    }

    /**
     * {@code POST /coupons/validate} as a CUSTOMER returns the server-computed checkout summary.
     */
    @Test
    void validateAsCustomerReturns200() throws Exception {
        when(orderService.previewCoupon(any())).thenReturn(previewWithCoupon());

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE\",\"cartItemIds\":[10,11]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.code").value("SAVE"))
                .andExpect(jsonPath("$.data.subtotal").value(200.00))
                .andExpect(jsonPath("$.data.discountAmount").value(20.00))
                .andExpect(jsonPath("$.data.shippingFee").value(30000.00))
                .andExpect(jsonPath("$.data.total").value(30180.00));
    }

    /**
     * A preview without a coupon code is accepted: the code is optional.
     */
    @Test
    void validateWithoutCouponCodeReturns200() throws Exception {
        when(orderService.previewCoupon(any())).thenReturn(new CouponPreviewResponse(null, null,
                new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("30000.00"), new BigDecimal("30200.00")));

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[10]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").doesNotExist())
                .andExpect(jsonPath("$.data.discountAmount").value(0))
                .andExpect(jsonPath("$.data.total").value(30200.00));
    }

    /**
     * An empty {@code cartItemIds} fails Bean Validation with the enveloped {@code 400}
     * (validation-spec §9): a preview must select at least one line.
     */
    @Test
    void validateWithEmptyCartItemIdsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE\",\"cartItemIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("cartItemIds"));

        verify(orderService, never()).previewCoupon(any());
    }

    /**
     * Duplicate cart item ids fail Bean Validation ({@code @UniqueElements}) with the enveloped
     * {@code 400} (validation-spec §9).
     */
    @Test
    void validateWithDuplicateCartItemIdsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE\",\"cartItemIds\":[10,10]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("cartItemIds"));

        verify(orderService, never()).previewCoupon(any());
    }

    /**
     * A non-positive cart item id fails Bean Validation ({@code @Positive} on the elements).
     */
    @Test
    void validateWithNonPositiveCartItemIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE\",\"cartItemIds\":[0]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(orderService, never()).previewCoupon(any());
    }

    /**
     * {@code POST /coupons/validate} with an ADMIN token is denied the enveloped {@code 403}: the
     * preview is CUSTOMER-only (security-spec §6).
     */
    @Test
    void validateAsAdminReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE\",\"cartItemIds\":[10]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(orderService, never()).previewCoupon(any());
    }

    /**
     * An anonymous request is denied the enveloped {@code 401} (security-spec §6).
     */
    @Test
    void validateAnonymouslyReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE\",\"cartItemIds\":[10]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(orderService, never()).previewCoupon(any());
    }

    /**
     * A coupon that is not applicable surfaces the service's {@code 400 COUPON_EXPIRED} through the
     * standard envelope.
     */
    @Test
    void validateWithExpiredCouponReturns400() throws Exception {
        when(orderService.previewCoupon(any()))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED",
                        "Coupon is not valid at this time"));

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SAVE\",\"cartItemIds\":[10]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COUPON_EXPIRED"));
    }

    /**
     * An unknown coupon code surfaces the service's {@code 404 COUPON_NOT_FOUND}.
     */
    @Test
    void validateWithUnknownCouponReturns404() throws Exception {
        when(orderService.previewCoupon(any()))
                .thenThrow(new ResourceNotFoundException("COUPON_NOT_FOUND", "Coupon not found"));

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOPE\",\"cartItemIds\":[10]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COUPON_NOT_FOUND"));
    }

    /**
     * A cart item that belongs to another user surfaces the service's {@code 403 CART_ITEM_FORBIDDEN}.
     */
    @Test
    void validateWithForeignCartItemReturns403() throws Exception {
        when(orderService.previewCoupon(any()))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "CART_ITEM_FORBIDDEN",
                        "You cannot access this cart item"));

        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[10]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CART_ITEM_FORBIDDEN"));
    }

    // ----- Admin coupon CRUD (GET / POST /coupons, PUT /coupons/{id}) -----

    private static final String VALID_COUPON_BODY = """
            {"code":"SAVE","name":"Save Now","discountType":"FIXED","discountValue":50,
             "minOrderAmount":0,"startAt":"2020-01-01T00:00:00","endAt":"2030-01-01T00:00:00","enabled":true}
            """;

    private CouponResponse couponResponse() {
        return new CouponResponse(1L, "SAVE", "Save Now", null, DiscountType.FIXED, new BigDecimal("50"),
                new BigDecimal("0"), null, LocalDateTime.of(2020, 1, 1, 0, 0),
                LocalDateTime.of(2030, 1, 1, 0, 0), null, 0, true);
    }

    /**
     * {@code GET /coupons} as an ADMIN returns the page of coupons.
     */
    @Test
    void listCouponsAsAdminReturns200() throws Exception {
        when(orderService.getCoupons(any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(couponResponse()))));

        mockMvc.perform(get("/api/v1/coupons").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].code").value("SAVE"))
                .andExpect(jsonPath("$.data.content[0].discountType").value("FIXED"));
    }

    /**
     * {@code GET /coupons} with a CUSTOMER token is denied the enveloped {@code 403}: coupon CRUD is
     * ADMIN-only (security-spec §6).
     */
    @Test
    void listCouponsAsCustomerReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/coupons").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(orderService, never()).getCoupons(any());
    }

    /**
     * An anonymous {@code GET /coupons} is denied the enveloped {@code 401}.
     */
    @Test
    void listCouponsAnonymouslyReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/coupons"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(orderService, never()).getCoupons(any());
    }

    /**
     * {@code POST /coupons} as an ADMIN returns {@code 201 Created} with the created coupon.
     */
    @Test
    void createCouponAsAdminReturns201() throws Exception {
        when(orderService.createCoupon(any())).thenReturn(couponResponse());

        mockMvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_COUPON_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("SAVE"));
    }

    /**
     * A blank {@code code} fails Bean Validation with the enveloped {@code 400}.
     */
    @Test
    void createCouponWithBlankCodeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"Save Now\",\"discountType\":\"FIXED\","
                                + "\"discountValue\":50,\"minOrderAmount\":0,\"startAt\":\"2020-01-01T00:00:00\","
                                + "\"endAt\":\"2030-01-01T00:00:00\",\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(orderService, never()).createCoupon(any());
    }

    /**
     * A duplicate coupon code surfaces the service's {@code 409 COUPON_CODE_DUPLICATED}.
     */
    @Test
    void createCouponWithDuplicateCodeReturns409() throws Exception {
        when(orderService.createCoupon(any()))
                .thenThrow(new DuplicateResourceException("COUPON_CODE_DUPLICATED", "Coupon code already exists"));

        mockMvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_COUPON_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COUPON_CODE_DUPLICATED"));
    }

    /**
     * {@code POST /coupons} with a CUSTOMER token is denied the enveloped {@code 403}.
     */
    @Test
    void createCouponAsCustomerReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_COUPON_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(orderService, never()).createCoupon(any());
    }

    /**
     * {@code PUT /coupons/{id}} as an ADMIN returns {@code 200 OK} with the updated coupon.
     */
    @Test
    void updateCouponAsAdminReturns200() throws Exception {
        when(orderService.updateCoupon(eq(1L), any())).thenReturn(couponResponse());

        mockMvc.perform(put("/api/v1/coupons/1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_COUPON_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("SAVE"));
    }

    /**
     * Updating an unknown coupon surfaces the service's {@code 404 COUPON_NOT_FOUND}.
     */
    @Test
    void updateCouponOfUnknownIdReturns404() throws Exception {
        when(orderService.updateCoupon(eq(1L), any()))
                .thenThrow(new ResourceNotFoundException("COUPON_NOT_FOUND", "Coupon not found"));

        mockMvc.perform(put("/api/v1/coupons/1")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_COUPON_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COUPON_NOT_FOUND"));
    }

    /**
     * {@code PUT /coupons/{id}} with a CUSTOMER token is denied the enveloped {@code 403}.
     */
    @Test
    void updateCouponAsCustomerReturns403() throws Exception {
        mockMvc.perform(put("/api/v1/coupons/1")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_COUPON_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(orderService, never()).updateCoupon(any(), any());
    }
}
