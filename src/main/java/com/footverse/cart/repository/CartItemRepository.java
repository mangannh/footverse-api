package com.footverse.cart.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.footverse.cart.entity.CartItem;

/**
 * Data access for {@link CartItem}. Standard CRUD is inherited from {@link JpaRepository}; the
 * cart-scoped reads below serve {@code CartService}, which is the only caller.
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /**
     * Returns every line of a cart, for assembling the cart response.
     *
     * @param cartId the owning cart id
     * @return the cart's lines (empty when the cart holds none)
     */
    List<CartItem> findByCartId(Long cartId);

    /**
     * Returns the line holding a given variant in a cart, so the service can merge a repeated add
     * into it. At most one exists, by the {@code (cart_id, product_variant_id)} unique constraint.
     *
     * @param cartId           the owning cart id
     * @param productVariantId the product variant id
     * @return the cart line, or empty when the cart does not hold that variant
     */
    Optional<CartItem> findByCartIdAndProductVariantId(Long cartId, Long productVariantId);

    /**
     * Returns a line only when it belongs to the given cart, so the service can resolve and
     * ownership-check in one read.
     *
     * @param id     the cart item id
     * @param cartId the owning cart id
     * @return the cart line, or empty when it does not exist or belongs to another cart
     */
    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);
}
