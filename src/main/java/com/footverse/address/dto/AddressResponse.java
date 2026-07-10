package com.footverse.address.dto;

/**
 * A shipping address returned to clients (dto-spec §8).
 *
 * @param id             the address id
 * @param recipientName  the recipient name
 * @param recipientPhone the recipient phone in VN format
 * @param province       the province
 * @param district       the district
 * @param ward           the ward
 * @param streetAddress  the house number / street
 * @param isDefault      whether this is the caller's default address
 */
public record AddressResponse(
        Long id,
        String recipientName,
        String recipientPhone,
        String province,
        String district,
        String ward,
        String streetAddress,
        boolean isDefault) {
}
