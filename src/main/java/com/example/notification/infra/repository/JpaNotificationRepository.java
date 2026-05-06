package com.example.notification.infra.repository;

import com.example.notification.domain.model.NotificationStatus;
import com.example.notification.infra.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaNotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findAllByStatus(NotificationStatus status);
}
