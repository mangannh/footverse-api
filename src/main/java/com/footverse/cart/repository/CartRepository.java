package com.footverse.cart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.footverse.cart.entity.Cart;

/**
 * Data access for {@link Cart}. Standard CRUD is inherited from {@link JpaRepository}; the
 * user-scoped read below serves {@code CartService}, which is the only caller.
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Returns a user's cart. At most one exists, by the {@code uk_cart_user_id} unique constraint.
     *
     * @param userId the owning user id
     * @return the user's cart, or empty when they have none yet
     */
    Optional<Cart> findByUserId(Long userId);
}
