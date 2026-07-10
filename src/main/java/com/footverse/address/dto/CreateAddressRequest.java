package com.footverse.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for {@code POST /addresses}. Validation follows validation-spec §5; the
 * exactly-one-default-per-user rule behind {@code isDefault} is a business rule enforced by the
 * service, not a field-level constraint.
 *
 * @param recipientName  required, the recipient's name
 * @param recipientPhone required, Vietnamese format (10 digits, starts with 0)
 * @param province       required, the province
 * @param district       required, the district
 * @param ward           required, the ward
 * @param streetAddress  required, the house number / street
 * @param isDefault      optional; when {@code true} the new address becomes the caller's default
 *                       and the previous default is cleared
 */
public record CreateAddressRequest(
        @NotBlank String recipientName,
        @NotBlank @Pattern(regexp = "^0\\d{9}$") String recipientPhone,
        @NotBlank String province,
        @NotBlank String district,
        @NotBlank String ward,
        @NotBlank String streetAddress,
        Boolean isDefault) {
}
