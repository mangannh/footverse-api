package com.footverse.review.entity;

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
 * One customer's review of a product: a 1–5 rating with an optional comment. Maps to the
 * {@code review} table (database-spec §10.13); audit timestamps are inherited from
 * {@link BaseEntity}.
 *
 * <p>Owns its {@link User} through a unidirectional lazy {@code @ManyToOne} on the
 * {@code fk_review_user} foreign key (CASCADE on user delete, database-spec §11). This association
 * exists only to supply the review author's display data ({@code userFullName},
 * {@code userAvatarUrl}) to {@code ReviewResponse}. The product is referenced by
 * {@link #productId} as a plain {@code Long}, never as a JPA association: the {@code review} module
 * may not reach into the {@code product} module's entities, so eligibility and rating aggregation
 * flow through {@code OrderService} and {@code ReviewService} instead (architecture-spec §8,
 * sprint-5-plan item 02) — mirroring the {@code CartItem} / {@code OrderItem} / {@code WishlistItem}
 * precedent. The database still enforces referential integrity through the
 * {@code fk_review_product} RESTRICT foreign key.</p>
 *
 * <p>The {@code (user_id, product_id)} unique constraint keeps a customer to at most one review per
 * product (database-spec §15); rejecting a duplicate is a service rule, not modelled here. Listing
 * order and rating aggregation are likewise repository/service concerns, not entity state.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "review", uniqueConstraints = {
        @UniqueConstraint(name = "uk_review_user_id_product_id",
                columnNames = {"user_id", "product_id"})
})
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;
}
