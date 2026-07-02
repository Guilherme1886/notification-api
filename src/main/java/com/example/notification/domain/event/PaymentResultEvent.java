package com.example.notification.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Emitted on {@code payments.approved} / {@code payments.failed}. {@code reason}
 * is populated only for failures; {@code status} mirrors the topic outcome.
 */
public record PaymentResultEvent(
        UUID orderId,
        UUID customerId,
        String status,
        BigDecimal amount,
        String reason
) {
}
