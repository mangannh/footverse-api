package com.footverse.address.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.footverse.address.dto.AddressResponse;
import com.footverse.address.dto.CreateAddressRequest;
import com.footverse.address.dto.UpdateAddressRequest;
import com.footverse.address.entity.Address;
import com.footverse.address.mapper.AddressMapper;
import com.footverse.address.repository.AddressRepository;
import com.footverse.common.exception.BusinessException;
import com.footverse.common.exception.InvalidOperationException;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.common.security.CurrentUserProvider;
import com.footverse.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Default {@link AddressService} implementation backed by {@link AddressRepository} and
 * {@link AddressMapper}. It owns the address business rules and is the only layer that knows who
 * the caller is: the acting user comes from {@link CurrentUserProvider}, never from a controller
 * argument, so ownership cannot be spoofed (security-spec §7).
 *
 * <p>The exactly-one-default-per-user invariant (architecture-spec §13) is enforced here rather
 * than by the database: MySQL has no partial unique index, and every write path that can create a
 * default runs inside a single transaction that clears the previous one first.</p>
 */
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private static final String ADDRESS_NOT_FOUND_CODE = "ADDRESS_NOT_FOUND";
    private static final String ADDRESS_NOT_FOUND_MESSAGE = "Address not found";
    private static final String ADDRESS_FORBIDDEN_CODE = "ADDRESS_FORBIDDEN";
    private static final String ADDRESS_FORBIDDEN_MESSAGE = "You cannot access this address";
    private static final String ADDRESS_DEFAULT_NOT_DELETABLE_CODE = "ADDRESS_DEFAULT_NOT_DELETABLE";
    private static final String ADDRESS_DEFAULT_NOT_DELETABLE_MESSAGE =
            "Choose another default address before deleting the current default";

    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses() {
        return addressRepository.findByUserId(currentUserId()).stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse createAddress(CreateAddressRequest request) {
        User currentUser = currentUserProvider.getCurrentUser();
        boolean firstAddress = addressRepository.countByUserId(currentUser.getId()) == 0;
        boolean becomesDefault = firstAddress || Boolean.TRUE.equals(request.isDefault());
        if (becomesDefault && !firstAddress) {
            clearCurrentDefault(currentUser.getId());
        }

        Address address = new Address();
        address.setUser(currentUser);
        address.setRecipientName(request.recipientName());
        address.setRecipientPhone(request.recipientPhone());
        address.setProvince(request.province());
        address.setDistrict(request.district());
        address.setWard(request.ward());
        address.setStreetAddress(request.streetAddress());
        address.setDefault(becomesDefault);
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(Long id, UpdateAddressRequest request) {
        Long userId = currentUserId();
        Address address = requireOwnedAddress(id, userId);
        address.setRecipientName(request.recipientName());
        address.setRecipientPhone(request.recipientPhone());
        address.setProvince(request.province());
        address.setDistrict(request.district());
        address.setWard(request.ward());
        address.setStreetAddress(request.streetAddress());

        // An address is only ever promoted to default, never demoted: demoting the current default
        // would leave the caller with none, breaking the invariant. Another address is promoted
        // instead, which clears this one.
        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
            clearCurrentDefault(userId);
            address.setDefault(true);
        }
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    public void deleteAddress(Long id) {
        Long userId = currentUserId();
        Address address = requireOwnedAddress(id, userId);
        if (address.isDefault() && addressRepository.countByUserId(userId) > 1) {
            throw new InvalidOperationException(ADDRESS_DEFAULT_NOT_DELETABLE_CODE,
                    ADDRESS_DEFAULT_NOT_DELETABLE_MESSAGE);
        }
        addressRepository.delete(address);
    }

    /**
     * Returns the id of the authenticated caller.
     *
     * @return the caller's user id
     */
    private Long currentUserId() {
        return currentUserProvider.getCurrentUser().getId();
    }

    /**
     * Resolves an address that belongs to the caller. The user-scoped query is the ownership check:
     * it hides another user's row in the same read that fetches the caller's own.
     *
     * @param id     the requested address id
     * @param userId the caller's user id
     * @return the caller's address
     * @throws BusinessException {@code 403 ADDRESS_FORBIDDEN} when the address exists but belongs to
     *                           another user, {@code 404 ADDRESS_NOT_FOUND} when no such address
     *                           exists at all
     */
    private Address requireOwnedAddress(Long id, Long userId) {
        return addressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> unresolvableAddress(id));
    }

    /**
     * Distinguishes the two reasons a user-scoped read can come back empty, as the frozen matrix
     * requires: another user's address is a {@code 403}, an absent address is a {@code 404}.
     *
     * @param id the requested address id
     * @return the exception to throw
     */
    private BusinessException unresolvableAddress(Long id) {
        if (addressRepository.existsById(id)) {
            return new BusinessException(HttpStatus.FORBIDDEN, ADDRESS_FORBIDDEN_CODE, ADDRESS_FORBIDDEN_MESSAGE);
        }
        return new ResourceNotFoundException(ADDRESS_NOT_FOUND_CODE, ADDRESS_NOT_FOUND_MESSAGE);
    }

    /**
     * Clears the caller's current default address, if they have one. The entity is managed inside
     * the caller's transaction, so the change is flushed without an explicit save.
     *
     * @param userId the caller's user id
     */
    private void clearCurrentDefault(Long userId) {
        addressRepository.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(current -> current.setDefault(false));
    }
}
