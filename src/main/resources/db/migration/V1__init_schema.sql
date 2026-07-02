-- V1__init_schema.sql
-- FootVerse V1 initial schema: all 15 tables per database-spec.md.
-- Tables are created in the dependency order defined in database-spec.md §9.
-- Conventions: MySQL 8, utf8mb4; BIGINT AUTO_INCREMENT primary keys; snake_case names;
-- DECIMAL(12,2) money; DATETIME(6) timestamps (matches JPA LocalDateTime); enums stored as
-- VARCHAR (EnumType.STRING); FK CASCADE/RESTRICT per database-spec.md §11.

-- 1. user
CREATE TABLE user (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    full_name  VARCHAR(100) NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    avatar_url VARCHAR(512) NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2. refresh_token
CREATE TABLE refresh_token (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_token_hash (token_hash),
    KEY idx_refresh_token_user_id (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 3. category
CREATE TABLE category (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(2000) NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 4. brand
CREATE TABLE brand (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)  NOT NULL,
    logo_url    VARCHAR(512)  NULL,
    description VARCHAR(2000) NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_brand_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 5. coupon
CREATE TABLE coupon (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)   NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    description         VARCHAR(2000) NULL,
    discount_type       VARCHAR(20)   NOT NULL,
    discount_value      DECIMAL(12, 2) NOT NULL,
    min_order_amount    DECIMAL(12, 2) NOT NULL,
    max_discount_amount DECIMAL(12, 2) NULL,
    start_at            DATETIME(6)   NOT NULL,
    end_at              DATETIME(6)   NOT NULL,
    usage_limit         INT           NULL,
    used_count          INT           NOT NULL DEFAULT 0,
    enabled             BOOLEAN       NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 6. address
CREATE TABLE address (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    province       VARCHAR(100) NOT NULL,
    district       VARCHAR(100) NOT NULL,
    ward           VARCHAR(100) NOT NULL,
    street_address VARCHAR(255) NOT NULL,
    is_default     BOOLEAN      NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_address_user_id (user_id),
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 7. product
CREATE TABLE product (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255)   NOT NULL,
    description VARCHAR(2000)  NULL,
    base_price  DECIMAL(12, 2) NOT NULL,
    category_id BIGINT         NOT NULL,
    brand_id    BIGINT         NOT NULL,
    deleted_at  DATETIME(6)    NULL,
    created_at  DATETIME(6)    NOT NULL,
    updated_at  DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_product_category_id (category_id),
    KEY idx_product_brand_id (brand_id),
    KEY idx_product_deleted_at (deleted_at),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_brand FOREIGN KEY (brand_id) REFERENCES brand (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 8. product_image
CREATE TABLE product_image (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    product_id    BIGINT       NOT NULL,
    image_url     VARCHAR(512) NOT NULL,
    display_order INT          NOT NULL,
    is_primary    BOOLEAN      NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_product_image_product_id (product_id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 9. product_variant
CREATE TABLE product_variant (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    product_id     BIGINT         NOT NULL,
    size           VARCHAR(20)    NOT NULL,
    stock_quantity INT            NOT NULL,
    sku            VARCHAR(64)    NOT NULL,
    price_override DECIMAL(12, 2) NULL,
    status         VARCHAR(20)    NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_variant_sku (sku),
    UNIQUE KEY uk_product_variant_product_id_size (product_id, size),
    CONSTRAINT fk_product_variant_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 10. cart
CREATE TABLE cart (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_id (user_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 11. cart_item
CREATE TABLE cart_item (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    cart_id            BIGINT      NOT NULL,
    product_variant_id BIGINT      NOT NULL,
    quantity           INT         NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_item_cart_id_product_variant_id (cart_id, product_variant_id),
    KEY idx_cart_item_product_variant_id (product_variant_id),
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES cart (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_product_variant FOREIGN KEY (product_variant_id) REFERENCES product_variant (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 12. orders (table name is `orders`; the JPA entity is Order — `order` is a reserved word)
CREATE TABLE orders (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    order_code              VARCHAR(30)    NOT NULL,
    user_id                 BIGINT         NOT NULL,
    coupon_id               BIGINT         NULL,
    status                  VARCHAR(20)    NOT NULL,
    payment_method          VARCHAR(20)    NOT NULL,
    payment_status          VARCHAR(20)    NOT NULL,
    subtotal                DECIMAL(12, 2) NOT NULL,
    discount_amount         DECIMAL(12, 2) NOT NULL,
    shipping_fee            DECIMAL(12, 2) NOT NULL,
    total                   DECIMAL(12, 2) NOT NULL,
    shipping_recipient_name VARCHAR(100)   NOT NULL,
    shipping_recipient_phone VARCHAR(20)   NOT NULL,
    shipping_province       VARCHAR(100)   NOT NULL,
    shipping_district       VARCHAR(100)   NOT NULL,
    shipping_ward           VARCHAR(100)   NOT NULL,
    shipping_street_address VARCHAR(255)   NOT NULL,
    note                    VARCHAR(500)   NULL,
    cancelled_at            DATETIME(6)    NULL,
    delivered_at            DATETIME(6)    NULL,
    created_at              DATETIME(6)    NOT NULL,
    updated_at              DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_code (order_code),
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_coupon_id (coupon_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 13. order_item
CREATE TABLE order_item (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    order_id           BIGINT         NOT NULL,
    product_variant_id BIGINT         NOT NULL,
    product_name       VARCHAR(255)   NOT NULL,
    product_image_url  VARCHAR(512)   NULL,
    size               VARCHAR(20)    NOT NULL,
    unit_price         DECIMAL(12, 2) NOT NULL,
    quantity           INT            NOT NULL,
    line_total         DECIMAL(12, 2) NOT NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_item_order_id (order_id),
    KEY idx_order_item_product_variant_id (product_variant_id),
    CONSTRAINT fk_order_item_orders FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product_variant FOREIGN KEY (product_variant_id) REFERENCES product_variant (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 14. review
CREATE TABLE review (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    product_id BIGINT       NOT NULL,
    rating     INT          NOT NULL,
    comment    VARCHAR(500) NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_user_id_product_id (user_id, product_id),
    KEY idx_review_product_id (product_id),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 15. wishlist_item
CREATE TABLE wishlist_item (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wishlist_item_user_id_product_id (user_id, product_id),
    KEY idx_wishlist_item_product_id (product_id),
    CONSTRAINT fk_wishlist_item_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_item_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
