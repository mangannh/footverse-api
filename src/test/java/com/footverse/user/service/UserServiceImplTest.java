package com.footverse.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import com.footverse.common.exception.DuplicateResourceException;
import com.footverse.common.security.CurrentUserProvider;
import com.footverse.user.dto.UpdateProfileRequest;
import com.footverse.user.dto.UserResponse;
import com.footverse.user.entity.Role;
import com.footverse.user.entity.User;
import com.footverse.user.mapper.UserMapper;
import com.footverse.user.repository.UserRepository;

/**
 * Unit tests for {@link UserServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, userMapper, currentUserProvider);
    }

    /**
     * The current-user lookup resolves the caller via {@link CurrentUserProvider} and maps that
     * exact user to its response — it never takes any argument.
     */
    @Test
    void getCurrentUserMapsTheAuthenticatedCaller() {
        User caller = new User();
        caller.setId(7L);
        caller.setEmail("caller@example.com");
        UserResponse response = new UserResponse(7L, "caller@example.com", "Caller", "0912345678",
                null, Role.CUSTOMER, true, LocalDateTime.now(), LocalDateTime.now());
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        when(userMapper.toResponse(caller)).thenReturn(response);

        assertThat(userService.getCurrentUser()).isEqualTo(response);
        verify(currentUserProvider).getCurrentUser();
        verify(userMapper).toResponse(caller);
    }

    /**
     * A created user is a persisted, enabled CUSTOMER carrying the given fields.
     */
    @Test
    void createUserPersistsEnabledCustomer() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.createUser("user@example.com", "encoded-password", "Test User", "0912345678");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User persisted = captor.getValue();
        assertThat(persisted.getEmail()).isEqualTo("user@example.com");
        assertThat(persisted.getPassword()).isEqualTo("encoded-password");
        assertThat(persisted.getFullName()).isEqualTo("Test User");
        assertThat(persisted.getPhone()).isEqualTo("0912345678");
        assertThat(persisted.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(persisted.isEnabled()).isTrue();
    }

    /**
     * Existence checks delegate to the repository.
     */
    @Test
    void existenceChecksDelegateToRepository() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);

        assertThat(userService.existsByEmail("user@example.com")).isTrue();
        assertThat(userService.existsByPhone("0912345678")).isFalse();
    }

    private User caller() {
        User user = new User();
        user.setId(7L);
        user.setEmail("caller@example.com");
        user.setPassword("hashed-password");
        user.setFullName("Old Name");
        user.setPhone("0900000000");
        user.setAvatarUrl("http://old-avatar");
        user.setRole(Role.CUSTOMER);
        user.setEnabled(true);
        return user;
    }

    /**
     * Updating the profile writes the three editable fields to the caller (resolved via
     * {@link CurrentUserProvider}) and returns the mapped response; a changed phone free for use
     * passes the duplicate check.
     */
    @Test
    void updateProfileUpdatesEditableFields() {
        User caller = caller();
        UserResponse mapped = new UserResponse(7L, "caller@example.com", "New Name", "0912345678",
                "http://new-avatar", Role.CUSTOMER, true, LocalDateTime.now(), LocalDateTime.now());
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);
        when(userRepository.saveAndFlush(caller)).thenReturn(caller);
        when(userMapper.toResponse(caller)).thenReturn(mapped);

        UserResponse result = userService.updateProfile(
                new UpdateProfileRequest("New Name", "0912345678", "http://new-avatar"));

        assertThat(result).isEqualTo(mapped);
        assertThat(caller.getFullName()).isEqualTo("New Name");
        assertThat(caller.getPhone()).isEqualTo("0912345678");
        assertThat(caller.getAvatarUrl()).isEqualTo("http://new-avatar");
    }

    /**
     * Keeping one's own phone is not a conflict: the duplicate check is skipped entirely because the
     * phone did not change (the coupon-update precedent).
     */
    @Test
    void updateProfileKeepingOwnPhoneSkipsDuplicateCheck() {
        User caller = caller();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        when(userRepository.saveAndFlush(caller)).thenReturn(caller);
        when(userMapper.toResponse(caller)).thenReturn(new UserResponse(7L, "caller@example.com",
                "New Name", "0900000000", null, Role.CUSTOMER, true, LocalDateTime.now(), LocalDateTime.now()));

        userService.updateProfile(new UpdateProfileRequest("New Name", "0900000000", null));

        verify(userRepository, never()).existsByPhone(any());
    }

    /**
     * A phone already held by another account is the enveloped {@code 409 USER_PHONE_DUPLICATED};
     * nothing is persisted.
     */
    @Test
    void updateProfileWithPhoneTakenByAnotherUserThrowsConflict() {
        User caller = caller();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        when(userRepository.existsByPhone("0912345678")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(
                new UpdateProfileRequest("New Name", "0912345678", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "USER_PHONE_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT);
        verify(userRepository, never()).saveAndFlush(any());
    }

    /**
     * The profile update touches only {@code fullName} / {@code phone} / {@code avatarUrl}; email,
     * password, role, and the enabled flag are left exactly as they were.
     */
    @Test
    void updateProfileLeavesEmailPasswordRoleEnabledUnchanged() {
        User caller = caller();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);
        when(userRepository.saveAndFlush(caller)).thenReturn(caller);
        when(userMapper.toResponse(caller)).thenReturn(new UserResponse(7L, "caller@example.com",
                "New Name", "0912345678", "a", Role.CUSTOMER, true, LocalDateTime.now(), LocalDateTime.now()));

        userService.updateProfile(new UpdateProfileRequest("New Name", "0912345678", "a"));

        assertThat(caller.getEmail()).isEqualTo("caller@example.com");
        assertThat(caller.getPassword()).isEqualTo("hashed-password");
        assertThat(caller.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(caller.isEnabled()).isTrue();
    }

    /**
     * The database backstop: a phone that passes the service-level guard but loses a race and trips
     * the {@code uk_user_phone} unique constraint is translated to the same enveloped
     * {@code 409 USER_PHONE_DUPLICATED}, never a leaked {@code 500}.
     */
    @Test
    void updateProfileTranslatesUniqueViolationToConflict() {
        User caller = caller();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);
        when(userRepository.saveAndFlush(caller))
                .thenThrow(new DataIntegrityViolationException("uk_user_phone"));

        assertThatThrownBy(() -> userService.updateProfile(
                new UpdateProfileRequest("New Name", "0912345678", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "USER_PHONE_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT);
    }
}
