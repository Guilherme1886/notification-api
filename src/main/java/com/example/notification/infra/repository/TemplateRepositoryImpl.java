package com.example.notification.infra.repository;

import com.example.notification.domain.model.Template;
import com.example.notification.domain.repository.TemplateRepository;
import com.example.notification.infra.entity.TemplateEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class TemplateRepositoryImpl implements TemplateRepository {

    private final JpaTemplateRepository jpa;

    public TemplateRepositoryImpl(JpaTemplateRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Template save(Template template) {
        return jpa.save(TemplateEntity.fromDomain(template)).toDomain();
    }

    @Override
    public Optional<Template> findById(String id) {
        return jpa.findById(id).map(TemplateEntity::toDomain);
    }
}
