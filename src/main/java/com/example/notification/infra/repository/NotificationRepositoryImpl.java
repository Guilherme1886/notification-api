package com.example.notification.infra.repository;

import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationStatus;
import com.example.notification.domain.repository.NotificationRepository;
import com.example.notification.infra.entity.NotificationEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final JpaNotificationRepository jpa;

    public NotificationRepositoryImpl(JpaNotificationRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Notification save(Notification notification) {
        var saved = jpa.save(NotificationEntity.fromDomain(notification));
        return saved.toDomain();
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpa.findById(id).map(NotificationEntity::toDomain);
    }

    @Override
    public List<Notification> findDeadLettered() {
        return jpa.findAllByStatus(NotificationStatus.DEAD_LETTERED)
                .stream()
                .map(NotificationEntity::toDomain)
                .toList();
    }
}
