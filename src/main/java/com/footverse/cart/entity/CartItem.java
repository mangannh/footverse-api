package com.footverse.cart.entity;

import com.footverse.common.entity.BaseEntity;

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
 * One line of a {@link Cart}: a quantity of a single product variant. Maps to the
 * {@code cart_item} table (database-spec §10.10); audit timestamps are inherited from
 * {@link BaseEntity}.
 *
 * <p>Owns its cart through a unidirectional lazy {@code @ManyToOne} on the
 * {@code fk_cart_item_cart} foreign key (CASCADE on cart delete). The variant is referenced by
 * {@link #productVariantId} as a plain {@code Long}, never as a JPA association: a cart may not
 * reach into the {@code product} module's entities, so variant data is resolved through
 * {@code ProductVariantService} instead (architecture-spec §6, sprint-3-plan item 05). The
 * database still enforces referential integrity through the {@code fk_cart_item_product_variant}
 * RESTRICT foreign key.</p>
 *
 * <p>The {@code (cart_id, product_variant_id)} unique constraint keeps a variant to at most one
 * line per cart; merging a repeated add into the existing line is a service rule
 * (business-rules → Shopping Cart), not modelled here.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cart_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_item_cart_id_product_variant_id",
                columnNames = {"cart_id", "product_variant_id"})
})
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_variant_id", nullable = false)
    private Long productVariantId;

    @Column(nullable = false)
    private int quantity;
}
