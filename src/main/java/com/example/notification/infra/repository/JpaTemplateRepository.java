package com.example.notification.infra.repository;

import com.example.notification.infra.entity.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTemplateRepository extends JpaRepository<TemplateEntity, String> {
}
