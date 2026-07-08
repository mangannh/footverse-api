package com.footverse.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.footverse.common.entity.BaseEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * A product category. Maps to the {@code category} table (database-spec §10.2); audit
 * timestamps are inherited from {@link BaseEntity}.
 */
@Getter
@Setter
@Entity
@Table(name = "category")
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 2000)
    private String description;
}
