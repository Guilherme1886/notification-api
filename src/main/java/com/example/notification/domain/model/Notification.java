package com.example.notification.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Notification(
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
    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static Notification newPending(UUID recipientId,
                                          NotificationChannel channel,
                                          String templateId,
                                          Map<String, String> variables,
                                          int maxAttempts) {
        return new Notification(
                UUID.randomUUID(),
                recipientId,
                channel,
                templateId,
                variables,
                NotificationStatus.PENDING,
                0,
                maxAttempts,
                null,
                Instant.now(),
                null
        );
    }

    public Notification transitionTo(NotificationStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "Invalid transition: %s → %s".formatted(status, next));
        }
        return new Notification(id, recipientId, channel, templateId, variables,
                next, attempts, maxAttempts, lastError, createdAt, deliveredAt);
    }

    public Notification markProcessing() {
        return transitionTo(NotificationStatus.PROCESSING)
                .incrementAttempts();
    }

    private Notification incrementAttempts() {
        return new Notification(id, recipientId, channel, templateId, variables,
                status, attempts + 1, maxAttempts, lastError, createdAt, deliveredAt);
    }

    public Notification markDelivered() {
        var delivered = transitionTo(NotificationStatus.DELIVERED);
        return new Notification(delivered.id, delivered.recipientId, delivered.channel,
                delivered.templateId, delivered.variables, delivered.status,
                delivered.attempts, delivered.maxAttempts, null,
                delivered.createdAt, Instant.now());
    }

    public Notification markFailed(String error) {
        var failed = transitionTo(NotificationStatus.FAILED);
        return new Notification(failed.id, failed.recipientId, failed.channel,
                failed.templateId, failed.variables, failed.status,
                failed.attempts, failed.maxAttempts, error,
                failed.createdAt, failed.deliveredAt);
    }

    public Notification markRetryPending() {
        return transitionTo(NotificationStatus.PENDING);
    }

    public Notification markDeadLettered() {
        return transitionTo(NotificationStatus.DEAD_LETTERED);
    }

    public boolean hasRetriesLeft() {
        return attempts < maxAttempts;
    }
}
