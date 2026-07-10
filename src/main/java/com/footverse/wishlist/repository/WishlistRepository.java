package com.footverse.wishlist.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.footverse.wishlist.entity.WishlistItem;

/**
 * Data access for {@link WishlistItem}. Standard CRUD is inherited from {@link JpaRepository}; the
 * user-scoped reads below serve {@code WishlistService}, which is the only caller.
 */
public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    /**
     * Returns every line of a user's wishlist, most recently added first (business-rules →
     * Wishlist). The listing order is owned here rather than by the catalog, which resolves product
     * data through an unordered lookup.
     *
     * @param userId the owning user id
     * @return the user's wishlist lines ordered by {@code createdAt} descending (empty when they
     *         have none)
     */
    List<WishlistItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Returns a user's wishlist line for a product, so the service can resolve and ownership-check
     * in one read. It also carries the existence answer the idempotent add needs — the existing line
     * is what that add returns — so no separate {@code existsBy} read is declared. At most one
     * exists, by the {@code (user_id, product_id)} unique constraint.
     *
     * @param userId    the owning user id
     * @param productId the product id
     * @return the wishlist line, or empty when the user has not wishlisted that product
     */
    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    /**
     * Removes a user's wishlist line for a product. Removing a product the user has not wishlisted
     * deletes nothing, which is what makes the remove idempotent (business-rules → Wishlist). Must
     * be called inside a transaction.
     *
     * @param userId    the owning user id
     * @param productId the product id
     */
    void deleteByUserIdAndProductId(Long userId, Long productId);
}
