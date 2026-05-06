package com.example.notification.infra.entity;

import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationStatus;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationChannel channel;

    @Column(name = "template_id", nullable = false, length = 128)
    private String templateId;

    @Type(JsonType.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> variables;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected NotificationEntity() {}

    public static NotificationEntity fromDomain(Notification n) {
        var e = new NotificationEntity();
        e.id = n.id();
        e.recipientId = n.recipientId();
        e.channel = n.channel();
        e.templateId = n.templateId();
        e.variables = n.variables();
        e.status = n.status();
        e.attempts = n.attempts();
        e.maxAttempts = n.maxAttempts();
        e.lastError = n.lastError();
        e.createdAt = n.createdAt();
        e.deliveredAt = n.deliveredAt();
        return e;
    }

    public Notification toDomain() {
        return new Notification(
                id, recipientId, channel, templateId, variables,
                status, attempts, maxAttempts, lastError, createdAt, deliveredAt
        );
    }
}
