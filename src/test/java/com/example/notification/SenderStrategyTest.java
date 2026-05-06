package com.example.notification;

import com.example.notification.application.port.NotificationSender;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.infra.sender.EmailSender;
import com.example.notification.infra.sender.PushSender;
import com.example.notification.infra.sender.SmsSender;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SenderStrategyTest {

    @Test
    void resolves_sender_by_channel() {
        List<NotificationSender> senders = List.of(new EmailSender(), new SmsSender(), new PushSender());
        Map<NotificationChannel, NotificationSender> byChannel = senders.stream()
                .collect(Collectors.toUnmodifiableMap(NotificationSender::channel, Function.identity()));

        assertThat(byChannel.get(NotificationChannel.EMAIL)).isInstanceOf(EmailSender.class);
        assertThat(byChannel.get(NotificationChannel.SMS)).isInstanceOf(SmsSender.class);
        assertThat(byChannel.get(NotificationChannel.PUSH)).isInstanceOf(PushSender.class);
    }
}
