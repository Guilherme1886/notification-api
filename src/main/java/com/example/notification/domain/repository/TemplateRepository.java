package com.example.notification.domain.repository;

import com.example.notification.domain.model.Template;

import java.util.Optional;

public interface TemplateRepository {
    Template save(Template template);
    Optional<Template> findById(String id);
}
