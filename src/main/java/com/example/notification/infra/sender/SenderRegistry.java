package com.example.notification.infra.sender;

import com.example.notification.application.port.NotificationSender;
import com.example.notification.domain.model.NotificationChannel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class SenderRegistry {

    @Bean
    public Map<NotificationChannel, NotificationSender> sendersByChannel(List<NotificationSender> senders) {
        return senders.stream()
                .collect(Collectors.toUnmodifiableMap(
                        NotificationSender::channel,
                        Function.identity()
                ));
    }
}
