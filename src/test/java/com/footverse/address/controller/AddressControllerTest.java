package com.footverse.address.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.footverse.address.dto.AddressResponse;
import com.footverse.address.service.AddressService;
import com.footverse.common.config.SecurityConfig;
import com.footverse.common.exception.BusinessException;
import com.footverse.common.exception.InvalidOperationException;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.common.security.JwtUtil;
import com.footverse.common.security.RestAccessDeniedHandler;
import com.footverse.common.security.RestAuthenticationEntryPoint;
import com.footverse.support.AuthFixtures;
import com.footverse.user.entity.Role;

/**
 * Web-slice tests for {@link AddressController}: the four CUSTOMER endpoints, the role denial for
 * an ADMIN token, request-body validation, and the service's ownership {@code 403} / not-found
 * {@code 404} / default-delete {@code 409} rendered through the standard envelope. The security
 * filter chain is imported; the service is mocked, so no ownership logic runs here.
 */
@WebMvcTest(AddressController.class)
@Import({SecurityConfig.class, JwtUtil.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AddressControllerTest {

    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String VALID_BODY = """
            {"recipientName":"Nguyen Van A","recipientPhone":"0912345678","province":"Ha Noi",
             "district":"Cau Giay","ward":"Dich Vong","streetAddress":"1 Pham Van Bach","isDefault":true}
            """;

    private final MockMvc mockMvc;
    private final JwtUtil jwtUtil;

    @MockitoBean
    private AddressService addressService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    AddressControllerTest(@Autowired MockMvc mockMvc, @Autowired JwtUtil jwtUtil) {
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

    private AddressResponse response() {
        return new AddressResponse(1L, "Nguyen Van A", "0912345678", "Ha Noi", "Cau Giay",
                "Dich Vong", "1 Pham Van Bach", true);
    }

    /**
     * {@code GET /addresses} returns the caller's own addresses in the success envelope.
     */
    @Test
    void listAddressesAsCustomerReturns200() throws Exception {
        when(addressService.getMyAddresses()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/addresses").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].recipientName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    /**
     * {@code GET /addresses} with an ADMIN token is denied the enveloped {@code 403}: the path is
     * CUSTOMER-only.
     */
    @Test
    void listAddressesAsAdminReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/addresses").header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(addressService, never()).getMyAddresses();
    }

    /**
     * An anonymous request is denied the enveloped {@code 401} (security-spec §6).
     */
    @Test
    void listAddressesAnonymouslyReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verify(addressService, never()).getMyAddresses();
    }

    /**
     * {@code POST /addresses} creates the address and returns {@code 201}.
     */
    @Test
    void createAddressAsCustomerReturns201() throws Exception {
        when(addressService.createAddress(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Created"))
                .andExpect(jsonPath("$.data.recipientName").value("Nguyen Van A"));
    }

    /**
     * A blank recipient name fails Bean Validation with the enveloped {@code 400}.
     */
    @Test
    void createAddressWithBlankRecipientNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"\",\"recipientPhone\":\"0912345678\",\"province\":\"Ha Noi\","
                                + "\"district\":\"Cau Giay\",\"ward\":\"Dich Vong\",\"streetAddress\":\"1 PVB\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("recipientName"));

        verify(addressService, never()).createAddress(any());
    }

    /**
     * A recipient phone outside the Vietnamese format fails Bean Validation (validation-spec §5).
     */
    @Test
    void createAddressWithInvalidPhoneReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"A\",\"recipientPhone\":\"12345\",\"province\":\"Ha Noi\","
                                + "\"district\":\"Cau Giay\",\"ward\":\"Dich Vong\",\"streetAddress\":\"1 PVB\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("recipientPhone"));

        verify(addressService, never()).createAddress(any());
    }

    /**
     * {@code PUT /addresses/{id}} updates and returns {@code 200}.
     */
    @Test
    void updateAddressAsCustomerReturns200() throws Exception {
        when(addressService.updateAddress(eq(1L), any())).thenReturn(response());

        mockMvc.perform(put("/api/v1/addresses/1")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    /**
     * Updating another user's address surfaces the service's {@code 403 ADDRESS_FORBIDDEN}.
     */
    @Test
    void updateAnotherUsersAddressReturns403() throws Exception {
        when(addressService.updateAddress(eq(1L), any()))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "ADDRESS_FORBIDDEN",
                        "You cannot access this address"));

        mockMvc.perform(put("/api/v1/addresses/1")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ADDRESS_FORBIDDEN"));
    }

    /**
     * Updating an unknown address surfaces the service's {@code 404 ADDRESS_NOT_FOUND}.
     */
    @Test
    void updateMissingAddressReturns404() throws Exception {
        when(addressService.updateAddress(eq(9L), any()))
                .thenThrow(new ResourceNotFoundException("ADDRESS_NOT_FOUND", "Address not found"));

        mockMvc.perform(put("/api/v1/addresses/9")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ADDRESS_NOT_FOUND"));
    }

    /**
     * {@code DELETE /addresses/{id}} returns the enveloped {@code 200 Void}.
     */
    @Test
    void deleteAddressAsCustomerReturns200() throws Exception {
        mockMvc.perform(delete("/api/v1/addresses/1").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(addressService).deleteAddress(1L);
    }

    /**
     * Deleting the default address while others remain surfaces the service's
     * {@code 409 ADDRESS_DEFAULT_NOT_DELETABLE}.
     */
    @Test
    void deleteDefaultAddressWithOthersReturns409() throws Exception {
        doThrow(new InvalidOperationException("ADDRESS_DEFAULT_NOT_DELETABLE",
                "Choose another default address before deleting the current default"))
                .when(addressService).deleteAddress(1L);

        mockMvc.perform(delete("/api/v1/addresses/1").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ADDRESS_DEFAULT_NOT_DELETABLE"));
    }
}
