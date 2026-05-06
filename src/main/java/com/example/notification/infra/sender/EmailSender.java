package com.example.notification.infra.sender;

import com.example.notification.application.port.NotificationSender;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification, Template template) {
        var subject = template.renderSubject(notification.variables());
        var body = template.renderBody(notification.variables());
        log.info("[EMAIL] to={} subject={} body={}", notification.recipientId(), subject, body);
    }
}
