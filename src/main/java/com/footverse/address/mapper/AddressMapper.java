package com.footverse.address.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.footverse.address.dto.AddressResponse;
import com.footverse.address.entity.Address;

/**
 * Maps {@link Address} entities to their response DTO. Pure mapping only — no business logic.
 */
@Mapper
public interface AddressMapper {

    /**
     * Maps an address to its response representation. The {@code isDefault} target is mapped
     * explicitly from the {@code default} read property, because the boolean {@code is}-prefixed
     * getter and the record component name do not match by MapStruct's default naming.
     *
     * @param address the address entity
     * @return the response DTO
     */
    @Mapping(target = "isDefault", source = "default")
    AddressResponse toResponse(Address address);
}
