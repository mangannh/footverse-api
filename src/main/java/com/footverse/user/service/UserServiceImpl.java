package com.footverse.user.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.footverse.common.exception.DuplicateResourceException;
import com.footverse.common.security.CurrentUserProvider;
import com.footverse.user.dto.UpdateProfileRequest;
import com.footverse.user.dto.UserResponse;
import com.footverse.user.entity.Role;
import com.footverse.user.entity.User;
import com.footverse.user.mapper.UserMapper;
import com.footverse.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Default {@link UserService} implementation backed by {@link UserRepository} and
 * {@link UserMapper}. The current-user lookup reads the authenticated user through
 * {@link CurrentUserProvider}, never the security context directly.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USER_PHONE_DUPLICATED_CODE = "USER_PHONE_DUPLICATED";
    private static final String USER_PHONE_DUPLICATED_MESSAGE = "Phone already exists";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public UserResponse getCurrentUser() {
        return userMapper.toResponse(currentUserProvider.getCurrentUser());
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User user = currentUserProvider.getCurrentUser();
        // Only a phone that changed and is held by another account is a conflict; keeping one's own
        // phone is not (the coupon-update precedent), so the existing exists-check is guarded by the
        // change and never trips on the caller's current phone.
        if (!user.getPhone().equals(request.phone()) && userRepository.existsByPhone(request.phone())) {
            throw new DuplicateResourceException(USER_PHONE_DUPLICATED_CODE, USER_PHONE_DUPLICATED_MESSAGE);
        }
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setAvatarUrl(request.avatarUrl());
        return userMapper.toResponse(persistProfile(user));
    }

    /**
     * Persists the profile change, forcing the flush now so the {@code uk_user_phone} unique
     * constraint is checked inside this call: a race that beats the service-level
     * {@code existsByPhone} guard surfaces as a {@link DataIntegrityViolationException}, which is
     * translated to the same enveloped {@code 409 USER_PHONE_DUPLICATED} rather than leaking a
     * database error as a {@code 500} (the {@code ReviewServiceImpl.persistNew} precedent). Phone is
     * the only unique column this update can touch — email is never changed here — so the constraint
     * violation is unambiguously the duplicate phone.
     *
     * @param user the caller's profile to persist
     * @return the persisted user
     * @throws DuplicateResourceException {@code 409 USER_PHONE_DUPLICATED} when the unique constraint fires
     */
    private User persistProfile(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicate) {
            throw new DuplicateResourceException(USER_PHONE_DUPLICATED_CODE, USER_PHONE_DUPLICATED_MESSAGE);
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public boolean existsByPhone(String phone) {
        return userRepository.existsByPhone(phone);
    }

    @Override
    public User createUser(String email, String encodedPassword, String fullName, String phone) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(encodedPassword);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setRole(Role.CUSTOMER);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    @Override
    public UserResponse toResponse(User user) {
        return userMapper.toResponse(user);
    }
}
