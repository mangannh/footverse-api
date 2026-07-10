package com.footverse.cart.service;

import com.footverse.cart.dto.AddCartItemRequest;
import com.footverse.cart.dto.CartResponse;
import com.footverse.cart.dto.UpdateCartItemRequest;

/**
 * Cart-module façade for the caller's shopping cart. Every operation is scoped to the authenticated
 * user resolved through {@code CurrentUserProvider} (security-spec §7) — no method accepts a user
 * id or a cart id, so a caller can never reach another user's cart. Each operation returns the
 * whole cart with its server-computed {@code subtotal} and {@code itemCount} (dto-spec §1).
 */
public interface CartService {

    /**
     * Returns the caller's cart. A caller who has never added an item has no cart row yet; that is
     * reported as an empty cart, not an error.
     *
     * @return the caller's cart
     */
    CartResponse getMyCart();

    /**
     * Adds a quantity of a variant to the caller's cart, creating the cart on the first add. The
     * variant must exist and be {@code ACTIVE}. When the cart already holds the variant, the
     * requested quantity is added to the existing line rather than inserting a second one
     * (business-rules → Shopping Cart); the resulting quantity must not exceed available stock.
     *
     * @param request the validated add payload
     * @return the caller's cart after the add
     */
    CartResponse addItem(AddCartItemRequest request);

    /**
     * Replaces the quantity of one of the caller's cart lines. The new quantity must not exceed the
     * variant's available stock.
     *
     * @param cartItemId the id of the cart line to update
     * @param request    the validated update payload
     * @return the caller's cart after the update
     */
    CartResponse updateItemQuantity(Long cartItemId, UpdateCartItemRequest request);

    /**
     * Removes one of the caller's cart lines. Removing the last line leaves the cart row in place
     * (business-rules → Shopping Cart).
     *
     * @param cartItemId the id of the cart line to remove
     * @return the caller's cart after the removal
     */
    CartResponse removeItem(Long cartItemId);
}
