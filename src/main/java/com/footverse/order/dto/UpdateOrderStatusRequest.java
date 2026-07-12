package com.footverse.order.dto;

import com.footverse.order.entity.OrderStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Payload for {@code PATCH /orders/{id}/status} (dto-spec §17). An admin advances an order to the
 * target {@code status}; field validation follows validation-spec §12, while the allowed transitions
 * ({@code PENDING→CONFIRMED→SHIPPING→DELIVERED} and {@code PENDING→CANCELLED}) are a business rule
 * enforced by the service (business-rules → Order Status Transitions), not by Bean Validation.
 *
 * @param status required target status; must be a valid {@link OrderStatus}. Marking an order
 *               {@code DELIVERED} additionally flips its payment to {@code PAID} and records
 *               {@code deliveredAt}; an admin {@code PENDING→CANCELLED} runs the same compensation as
 *               a customer cancellation.
 */
public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status) {
}
