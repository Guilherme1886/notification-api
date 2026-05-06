package com.example.notification.application.usecase;

import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.Template;
import com.example.notification.domain.repository.TemplateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TemplateUseCase {

    private final TemplateRepository templates;

    public TemplateUseCase(TemplateRepository templates) {
        this.templates = templates;
    }

    public Template create(String id, String name, NotificationChannel channel,
                           String subject, String body) {
        return templates.save(new Template(id, name, channel, subject, body, Instant.now()));
    }

    public Template byId(String id) {
        return templates.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
    }
}
