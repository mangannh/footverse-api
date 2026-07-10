package com.footverse.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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

/**
 * Unit tests for {@link AddressServiceImpl}: the caller-scoped reads, the
 * exactly-one-default-per-user invariant on create and update, the ownership {@code 403} versus
 * not-found {@code 404} split, and the default-address delete guard.
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    private static final Long CALLER_ID = 1L;
    private static final Long ADDRESS_ID = 10L;
    private static final Long OTHER_ADDRESS_ID = 99L;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private AddressMapper addressMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private AddressServiceImpl service;

    private void init() {
        service = new AddressServiceImpl(addressRepository, addressMapper, currentUserProvider);
    }

    private User caller() {
        User user = new User();
        user.setId(CALLER_ID);
        return user;
    }

    private void withCaller() {
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
    }

    private Address address(Long id, boolean isDefault) {
        Address address = new Address();
        address.setId(id);
        address.setUser(caller());
        address.setRecipientName("Nguyen Van A");
        address.setRecipientPhone("0912345678");
        address.setProvince("Ha Noi");
        address.setDistrict("Cau Giay");
        address.setWard("Dich Vong");
        address.setStreetAddress("1 Pham Van Bach");
        address.setDefault(isDefault);
        return address;
    }

    private CreateAddressRequest createRequest(Boolean isDefault) {
        return new CreateAddressRequest("Nguyen Van A", "0912345678", "Ha Noi", "Cau Giay",
                "Dich Vong", "1 Pham Van Bach", isDefault);
    }

    private UpdateAddressRequest updateRequest(Boolean isDefault) {
        return new UpdateAddressRequest("Tran Thi B", "0987654321", "Da Nang", "Hai Chau",
                "Thanh Binh", "2 Bach Dang", isDefault);
    }

    private AddressResponse response() {
        return new AddressResponse(ADDRESS_ID, "Nguyen Van A", "0912345678", "Ha Noi", "Cau Giay",
                "Dich Vong", "1 Pham Van Bach", true);
    }

    /**
     * Listing reads only the caller's rows: the repository is queried with the caller's id, which
     * comes from the security context and never from the request.
     */
    @Test
    void getMyAddressesReadsOnlyTheCallersRows() {
        init();
        withCaller();
        Address entity = address(ADDRESS_ID, true);
        AddressResponse mapped = response();
        when(addressRepository.findByUserId(CALLER_ID)).thenReturn(List.of(entity));
        when(addressMapper.toResponse(entity)).thenReturn(mapped);

        assertThat(service.getMyAddresses()).containsExactly(mapped);
        verify(addressRepository).findByUserId(CALLER_ID);
    }

    /**
     * The caller's first address becomes the default even when the request does not ask for it.
     */
    @Test
    void createFirstAddressBecomesDefault() {
        init();
        withCaller();
        when(addressRepository.countByUserId(CALLER_ID)).thenReturn(0L);
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createAddress(createRequest(false));

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
        assertThat(captor.getValue().getUser().getId()).isEqualTo(CALLER_ID);
        verify(addressRepository, never()).findByUserIdAndIsDefaultTrue(any());
    }

    /**
     * Creating with {@code isDefault = true} clears the previous default, so exactly one remains.
     */
    @Test
    void createDefaultAddressClearsThePreviousDefault() {
        init();
        withCaller();
        Address previousDefault = address(ADDRESS_ID, true);
        when(addressRepository.countByUserId(CALLER_ID)).thenReturn(1L);
        when(addressRepository.findByUserIdAndIsDefaultTrue(CALLER_ID)).thenReturn(Optional.of(previousDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createAddress(createRequest(true));

        assertThat(previousDefault.isDefault()).isFalse();
        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    /**
     * Creating a non-default address while a default exists leaves the existing default untouched.
     */
    @Test
    void createNonDefaultAddressKeepsTheExistingDefault() {
        init();
        withCaller();
        when(addressRepository.countByUserId(CALLER_ID)).thenReturn(1L);
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createAddress(createRequest(null));

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isFalse();
        verify(addressRepository, never()).findByUserIdAndIsDefaultTrue(any());
    }

    /**
     * Updating one of the caller's addresses applies every field.
     */
    @Test
    void updateAppliesTheNewFields() {
        init();
        withCaller();
        Address existing = address(ADDRESS_ID, false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        service.updateAddress(ADDRESS_ID, updateRequest(null));

        assertThat(existing.getRecipientName()).isEqualTo("Tran Thi B");
        assertThat(existing.getRecipientPhone()).isEqualTo("0987654321");
        assertThat(existing.getProvince()).isEqualTo("Da Nang");
        assertThat(existing.getStreetAddress()).isEqualTo("2 Bach Dang");
        assertThat(existing.isDefault()).isFalse();
    }

    /**
     * Promoting an address to default clears the previous default.
     */
    @Test
    void updateToDefaultClearsThePreviousDefault() {
        init();
        withCaller();
        Address target = address(ADDRESS_ID, false);
        Address previousDefault = address(OTHER_ADDRESS_ID, true);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.of(target));
        when(addressRepository.findByUserIdAndIsDefaultTrue(CALLER_ID)).thenReturn(Optional.of(previousDefault));
        when(addressRepository.save(target)).thenReturn(target);

        service.updateAddress(ADDRESS_ID, updateRequest(true));

        assertThat(previousDefault.isDefault()).isFalse();
        assertThat(target.isDefault()).isTrue();
    }

    /**
     * Updating the current default without asking for a change keeps it the default: demoting it
     * would leave the caller with none.
     */
    @Test
    void updateDoesNotDemoteTheCurrentDefault() {
        init();
        withCaller();
        Address existing = address(ADDRESS_ID, true);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        service.updateAddress(ADDRESS_ID, updateRequest(false));

        assertThat(existing.isDefault()).isTrue();
        verify(addressRepository, never()).findByUserIdAndIsDefaultTrue(any());
    }

    /**
     * Re-asserting the default flag on the address that already holds it changes nothing: the
     * previous default is this very address, so no default is cleared and none is re-promoted.
     */
    @Test
    void updateTheCurrentDefaultToDefaultAgainIsANoOp() {
        init();
        withCaller();
        Address existing = address(ADDRESS_ID, true);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        service.updateAddress(ADDRESS_ID, updateRequest(true));

        assertThat(existing.isDefault()).isTrue();
        verify(addressRepository, never()).findByUserIdAndIsDefaultTrue(any());
    }

    /**
     * Updating another user's address is an enveloped {@code 403 ADDRESS_FORBIDDEN}.
     */
    @Test
    void updateAnotherUsersAddressIsForbidden() {
        init();
        withCaller();
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.empty());
        when(addressRepository.existsById(ADDRESS_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.updateAddress(ADDRESS_ID, updateRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ADDRESS_FORBIDDEN")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.FORBIDDEN);
        verify(addressRepository, never()).save(any());
    }

    /**
     * Updating an address that does not exist at all is a {@code 404 ADDRESS_NOT_FOUND}.
     */
    @Test
    void updateMissingAddressIsNotFound() {
        init();
        withCaller();
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.empty());
        when(addressRepository.existsById(ADDRESS_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.updateAddress(ADDRESS_ID, updateRequest(null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ADDRESS_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
    }

    /**
     * Deleting the default address while other addresses exist is rejected: another default must be
     * chosen first (business-rules → Shipping Address).
     */
    @Test
    void deleteDefaultAddressWithOthersIsRejected() {
        init();
        withCaller();
        Address existing = address(ADDRESS_ID, true);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.countByUserId(CALLER_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteAddress(ADDRESS_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ADDRESS_DEFAULT_NOT_DELETABLE")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT);
        verify(addressRepository, never()).delete(any());
    }

    /**
     * Deleting the sole address succeeds even though it is the default; no other address is
     * promoted in its place.
     */
    @Test
    void deleteSoleDefaultAddressSucceeds() {
        init();
        withCaller();
        Address existing = address(ADDRESS_ID, true);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.countByUserId(CALLER_ID)).thenReturn(1L);

        service.deleteAddress(ADDRESS_ID);

        verify(addressRepository).delete(existing);
        verify(addressRepository, never()).save(any());
    }

    /**
     * Deleting a non-default address never touches the default.
     */
    @Test
    void deleteNonDefaultAddressSucceeds() {
        init();
        withCaller();
        Address existing = address(ADDRESS_ID, false);
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.of(existing));

        service.deleteAddress(ADDRESS_ID);

        verify(addressRepository).delete(existing);
        verify(addressRepository, never()).findByUserIdAndIsDefaultTrue(any());
    }

    /**
     * Deleting another user's address is an enveloped {@code 403 ADDRESS_FORBIDDEN}.
     */
    @Test
    void deleteAnotherUsersAddressIsForbidden() {
        init();
        withCaller();
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.empty());
        when(addressRepository.existsById(ADDRESS_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteAddress(ADDRESS_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ADDRESS_FORBIDDEN")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.FORBIDDEN);
        verify(addressRepository, never()).delete(any());
    }

    /**
     * Deleting an address that does not exist at all is a {@code 404 ADDRESS_NOT_FOUND}.
     */
    @Test
    void deleteMissingAddressIsNotFound() {
        init();
        withCaller();
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, CALLER_ID)).thenReturn(Optional.empty());
        when(addressRepository.existsById(ADDRESS_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteAddress(ADDRESS_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ADDRESS_NOT_FOUND");
        verify(addressRepository, never()).delete(any());
    }
}
