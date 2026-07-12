package com.footverse.order.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.footverse.order.entity.OrderItem;

/**
 * Data access for {@link OrderItem}. Standard CRUD is inherited from {@link JpaRepository}; the
 * order-scoped reads below let {@code OrderService} assemble an order's detail and item counts.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Returns every line of an order.
     *
     * @param orderId the owning order id
     * @return the order's lines (empty when the order has none)
     */
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * Returns every line of the given orders in a single query, so the service can compute each
     * order's {@code itemCount} (Σ quantity) for an order-history page without a per-order N+1 —
     * mirroring the batch {@code findByProductIdIn} precedent used by catalog search.
     *
     * @param orderIds the owning order ids
     * @return the lines of those orders (empty when none have any)
     */
    List<OrderItem> findByOrderIdIn(Collection<Long> orderIds);

    /**
     * Reports whether the given user has any {@code DELIVERED} order whose lines include one of the
     * given product-variant ids — the exists-style read behind
     * {@code OrderService.hasDeliveredOrderForProduct}. It joins each line to its owning order and
     * filters on owner and {@code DELIVERED} status in a single aggregate query (no entity is loaded
     * and no per-order N+1 arises). The caller passes only its own resolved variant ids, so a
     * non-empty collection is required (an empty product is short-circuited to {@code false} before
     * this read).
     *
     * @param userId           the owning user id
     * @param productVariantIds the product's variant ids to match a delivered line against
     * @return {@code true} when a {@code DELIVERED} order of the user contains one of those variants
     */
    @Query("""
            SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END
            FROM OrderItem oi
            WHERE oi.order.user.id = :userId
              AND oi.order.status = com.footverse.order.entity.OrderStatus.DELIVERED
              AND oi.productVariantId IN :productVariantIds
            """)
    boolean existsDeliveredOrderItemForUserAndProductVariants(
            @Param("userId") Long userId,
            @Param("productVariantIds") Collection<Long> productVariantIds);
}
