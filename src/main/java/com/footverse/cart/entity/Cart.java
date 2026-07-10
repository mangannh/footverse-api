package com.footverse.cart.entity;

import com.footverse.common.entity.BaseEntity;
import com.footverse.user.entity.User;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * A customer's shopping cart. Maps to the {@code cart} table (database-spec §10.9); audit
 * timestamps are inherited from {@link BaseEntity}.
 *
 * <p>Owns its {@link User} through a unidirectional lazy {@code @ManyToOne} on the
 * {@code fk_cart_user} foreign key (CASCADE on user delete, database-spec §11). The
 * one-cart-per-user rule is enforced by the database through the {@code uk_cart_user_id} unique
 * constraint; the cart row is created lazily on the first add and survives the removal of its last
 * item — both are service concerns, not modelled here.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cart", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_user_id", columnNames = {"user_id"})
})
public class Cart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
