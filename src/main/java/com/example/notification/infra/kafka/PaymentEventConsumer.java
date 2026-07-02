package com.example.notification.infra.kafka;

import com.example.notification.application.usecase.SendNotificationUseCase;
import com.example.notification.domain.event.PaymentResultEvent;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.infra.auth.AuthServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consumes payment outcomes and notifies the customer (Saga: payment approved →
 * order confirmed; payment failed → order cancelled). Each topic maps to its own
 * template.
 */
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final SendNotificationUseCase sendNotification;
    private final AuthServiceClient authServiceClient;

    public PaymentEventConsumer(ObjectMapper objectMapper,
                                SendNotificationUseCase sendNotification,
                                AuthServiceClient authServiceClient) {
        this.objectMapper = objectMapper;
        this.sendNotification = sendNotification;
        this.authServiceClient = authServiceClient;
    }

    @KafkaListener(topics = "payments.approved", groupId = "notification-api")
    public void onPaymentApproved(String payload) throws Exception {
        PaymentResultEvent event = parse(payload, "payments.approved");
        sendNotification.queue(
                event.customerId(),
                NotificationChannel.EMAIL,
                TemplateInitializer.PAYMENT_APPROVED,
                Map.of(
                        "orderId", String.valueOf(event.orderId()),
                        "amount", formatAmount(event.amount())),
                null);
    }

    @KafkaListener(topics = "payments.failed", groupId = "notification-api")
    public void onPaymentFailed(String payload) throws Exception {
        PaymentResultEvent event = parse(payload, "payments.failed");
        sendNotification.queue(
                event.customerId(),
                NotificationChannel.EMAIL,
                TemplateInitializer.PAYMENT_FAILED,
                Map.of(
                        "orderId", String.valueOf(event.orderId()),
                        "reason", event.reason() == null ? "não informado" : event.reason()),
                null);
    }

    private PaymentResultEvent parse(String payload, String topic) throws Exception {
        PaymentResultEvent event = objectMapper.readValue(payload, PaymentResultEvent.class);
        log.info("Received {}: orderId={} customerId={} status={}",
                topic, event.orderId(), event.customerId(), event.status());
        authServiceClient.findUser(event.customerId()).ifPresent(user ->
                log.info("Resolved customer {} email={}", event.customerId(), user.email()));
        return event;
    }

    private static String formatAmount(BigDecimal amount) {
        return amount == null ? "0,00" : amount.toPlainString();
    }
}
