package com.example.notification.api.dto;

import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.Template;

import java.time.Instant;

public record TemplateResponse(
        String id,
        String name,
        NotificationChannel channel,
        String subject,
        String body,
        Instant createdAt
) {
    public static TemplateResponse from(Template t) {
        return new TemplateResponse(t.id(), t.name(), t.channel(), t.subject(), t.body(), t.createdAt());
    }
}
