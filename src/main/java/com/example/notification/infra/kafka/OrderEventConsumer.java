package com.example.notification.infra.kafka;

import com.example.notification.application.usecase.SendNotificationUseCase;
import com.example.notification.domain.event.OrderCreatedEvent;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.infra.auth.AuthServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes {@code orders.created} and notifies the customer that their order was
 * received (Saga: order placed → "seu pedido foi recebido").
 */
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final SendNotificationUseCase sendNotification;
    private final AuthServiceClient authServiceClient;

    public OrderEventConsumer(ObjectMapper objectMapper,
                              SendNotificationUseCase sendNotification,
                              AuthServiceClient authServiceClient) {
        this.objectMapper = objectMapper;
        this.sendNotification = sendNotification;
        this.authServiceClient = authServiceClient;
    }

    @KafkaListener(topics = "orders.created", groupId = "notification-api")
    public void onOrderCreated(String payload) throws Exception {
        OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
        log.info("Received orders.created: orderId={} customerId={}", event.orderId(), event.customerId());

        String email = authServiceClient.findUser(event.customerId())
                .map(AuthServiceClient.UserInfo::email)
                .orElse("unknown");
        log.info("Resolved customer {} email={} for order {}", event.customerId(), email, event.orderId());

        sendNotification.queue(
                event.customerId(),
                NotificationChannel.EMAIL,
                TemplateInitializer.ORDER_CREATED,
                Map.of("orderId", String.valueOf(event.orderId())),
                null);
    }
}
