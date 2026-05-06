package com.example.notification.application.usecase;

import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationStatus;
import com.example.notification.domain.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RetryNotificationUseCase {

    private final NotificationRepository notifications;
    private final SendNotificationUseCase sendUseCase;

    public RetryNotificationUseCase(NotificationRepository notifications,
                                    SendNotificationUseCase sendUseCase) {
        this.notifications = notifications;
        this.sendUseCase = sendUseCase;
    }

    public Notification retry(UUID id) {
        var current = notifications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));

        return switch (current.status()) {
            case FAILED -> {
                if (!current.hasRetriesLeft()) {
                    throw new IllegalStateException("No retries left for " + id);
                }
                var pending = notifications.save(current.markRetryPending());
                sendUseCase.processAsync(pending.id(), pending.attempts());
                yield pending;
            }
            case PENDING, PROCESSING ->
                    throw new IllegalStateException("Notification already in progress: " + current.status());
            case DELIVERED, DEAD_LETTERED ->
                    throw new IllegalStateException("Notification is terminal: " + current.status());
        };
    }
}
