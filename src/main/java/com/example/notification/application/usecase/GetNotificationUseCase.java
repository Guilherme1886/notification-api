package com.example.notification.application.usecase;

import com.example.notification.domain.model.Notification;
import com.example.notification.domain.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GetNotificationUseCase {

    private final NotificationRepository notifications;

    public GetNotificationUseCase(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public Notification byId(UUID id) {
        return notifications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
    }

    public List<Notification> deadLettered() {
        return notifications.findDeadLettered();
    }
}
