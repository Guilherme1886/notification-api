package com.example.notification.application.port;

import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.Template;

public interface NotificationSender {
    NotificationChannel channel();
    void send(Notification notification, Template template);
}
