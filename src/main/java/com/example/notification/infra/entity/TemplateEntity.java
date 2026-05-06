package com.example.notification.infra.entity;

import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.Template;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "templates")
public class TemplateEntity {

    @Id
    @Column(length = 128)
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationChannel channel;

    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TemplateEntity() {}

    public static TemplateEntity fromDomain(Template t) {
        var e = new TemplateEntity();
        e.id = t.id();
        e.name = t.name();
        e.channel = t.channel();
        e.subject = t.subject();
        e.body = t.body();
        e.createdAt = t.createdAt();
        return e;
    }

    public Template toDomain() {
        return new Template(id, name, channel, subject, body, createdAt);
    }
}
