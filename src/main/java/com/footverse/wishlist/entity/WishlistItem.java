package com.footverse.wishlist.entity;

import com.footverse.common.entity.BaseEntity;
import com.footverse.user.entity.User;

import jakarta.persistence.Column;
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
 * One product a customer has wishlisted. Maps to the {@code wishlist_item} table (database-spec
 * §10.14); audit timestamps are inherited from {@link BaseEntity}.
 *
 * <p>Owns its {@link User} through a unidirectional lazy {@code @ManyToOne} on the
 * {@code fk_wishlist_item_user} foreign key (CASCADE on user delete, database-spec §11). The
 * product is referenced by {@link #productId} as a plain {@code Long}, never as a JPA association:
 * the wishlist may not reach into the {@code product} module's entities, so display data is
 * resolved through {@code ProductService} instead (architecture-spec §6, sprint-3-plan item 08).
 * The database still enforces referential integrity through the {@code fk_wishlist_item_product}
 * RESTRICT foreign key.</p>
 *
 * <p>The {@code (user_id, product_id)} unique constraint keeps a product to at most one line per
 * customer; treating a repeated add as an idempotent no-op is a service rule (business-rules →
 * Wishlist), not modelled here. Listing order (most recently added first) is likewise a repository
 * concern, carried by {@code createdAt}.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wishlist_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wishlist_item_user_id_product_id",
                columnNames = {"user_id", "product_id"})
})
public class WishlistItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "product_id", nullable = false)
    private Long productId;
}
