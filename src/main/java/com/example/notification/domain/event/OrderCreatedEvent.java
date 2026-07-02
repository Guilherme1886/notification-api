package com.example.notification.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Emitted on the {@code orders.created} topic when a new order is placed.
 * Consumed by the notification service to inform the customer.
 */
public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount
) {
}
