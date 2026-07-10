package com.footverse.address.entity;

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

import lombok.Getter;
import lombok.Setter;

/**
 * A customer's shipping address. Maps to the {@code address} table (database-spec §10.5); audit
 * timestamps are inherited from {@link BaseEntity}.
 *
 * <p>Owns its {@link User} through a unidirectional lazy {@code @ManyToOne} on the
 * {@code fk_address_user} foreign key (CASCADE on user delete, database-spec §11). The
 * exactly-one-default-per-user rule carried by {@link #isDefault} is enforced at the service
 * layer (architecture-spec §13), not here.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "address")
public class Address extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(nullable = false, length = 100)
    private String ward;

    @Column(name = "street_address", nullable = false, length = 255)
    private String streetAddress;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
