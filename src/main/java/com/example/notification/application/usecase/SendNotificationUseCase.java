package com.example.notification.application.usecase;

import com.example.notification.application.port.NotificationSender;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.repository.NotificationRepository;
import com.example.notification.domain.repository.TemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class SendNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendNotificationUseCase.class);

    private final NotificationRepository notifications;
    private final TemplateRepository templates;
    private final Map<NotificationChannel, NotificationSender> senders;

    public SendNotificationUseCase(NotificationRepository notifications,
                                   TemplateRepository templates,
                                   Map<NotificationChannel, NotificationSender> senders) {
        this.notifications = notifications;
        this.templates = templates;
        this.senders = senders;
    }

    public Notification queue(UUID recipientId,
                              NotificationChannel channel,
                              String templateId,
                              Map<String, String> variables,
                              Integer maxAttempts) {
        var template = templates.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown template: " + templateId));
        if (template.channel() != channel) {
            throw new IllegalArgumentException(
                    "Template channel %s does not match requested %s".formatted(template.channel(), channel));
        }
        var notification = Notification.newPending(
                recipientId, channel, templateId, variables,
                maxAttempts == null ? 5 : maxAttempts);
        var saved = notifications.save(notification);
        processAsync(saved.id(), 0);
        return saved;
    }

    @Async("notificationExecutor")
    public void processAsync(UUID notificationId, int attemptNumber) {
        if (attemptNumber > 0) {
            sleepBackoff(attemptNumber);
        }
        var current = notifications.findById(notificationId).orElse(null);
        if (current == null) {
            log.warn("Notification {} disappeared before processing", notificationId);
            return;
        }
        dispatch(current);
    }

    public void dispatch(Notification notification) {
        if (notification.status() != com.example.notification.domain.model.NotificationStatus.PENDING) {
            log.warn("Skip dispatch of {}: status={}", notification.id(), notification.status());
            return;
        }
        var template = templates.findById(notification.templateId()).orElseThrow();
        var processing = notifications.save(notification.markProcessing());
        var sender = senders.get(processing.channel());
        if (sender == null) {
            fail(processing, "No sender for channel " + processing.channel());
            return;
        }
        try {
            sender.send(processing, template);
            notifications.save(processing.markDelivered());
        } catch (RuntimeException e) {
            log.error("Delivery failed for {}", processing.id(), e);
            fail(processing, e.getMessage());
        }
    }

    private void fail(Notification processing, String error) {
        var failed = notifications.save(processing.markFailed(error));
        if (failed.hasRetriesLeft()) {
            var retry = notifications.save(failed.markRetryPending());
            processAsync(retry.id(), retry.attempts());
        } else {
            notifications.save(failed.markDeadLettered());
        }
    }

    private void sleepBackoff(int attempts) {
        var seconds = (long) Math.pow(2, attempts);
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
