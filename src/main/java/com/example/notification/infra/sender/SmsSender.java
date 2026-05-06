package com.example.notification.infra.sender;

import com.example.notification.application.port.NotificationSender;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(Notification notification, Template template) {
        var body = template.renderBody(notification.variables());
        log.info("[SMS] to={} body={}", notification.recipientId(), body);
    }
}
