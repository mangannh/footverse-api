package com.footverse.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The caller's cart with its server-computed aggregates (dto-spec §12). The client never sends or
 * recomputes {@code subtotal} or {@code itemCount}: the server is the single source of truth for
 * money (dto-spec §1).
 *
 * @param items     the cart lines; empty when the caller has no cart yet, or an empty one
 * @param subtotal  the sum of every line total, computed by the server
 * @param itemCount the sum of every line's quantity (the cart badge value), <em>not</em> the number
 *                  of distinct lines (business-rules → Shopping Cart)
 */
public record CartResponse(
        List<CartItemResponse> items,
        BigDecimal subtotal,
        int itemCount) {
}
