package com.example.notification.api.dto;

import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID recipientId,
        NotificationChannel channel,
        String templateId,
        Map<String, String> variables,
        NotificationStatus status,
        int attempts,
        int maxAttempts,
        String lastError,
        Instant createdAt,
        Instant deliveredAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.id(), n.recipientId(), n.channel(), n.templateId(),
                n.variables(), n.status(), n.attempts(), n.maxAttempts(),
                n.lastError(), n.createdAt(), n.deliveredAt()
        );
    }
}
