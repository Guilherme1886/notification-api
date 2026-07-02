package com.example.notification;

import com.example.notification.infra.entity.NotificationEntity;
import com.example.notification.infra.repository.JpaNotificationRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the full Saga consumer flow against a real Kafka + PostgreSQL: publishes
 * events to each topic and asserts the matching EMAIL notification is persisted
 * and DELIVERED. auth-service is not running, so the client falls back gracefully.
 */
@SpringBootTest(properties = {
        "kafka.enabled=true",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Testcontainers
class KafkaSagaConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    JpaNotificationRepository notifRepo;

    private KafkaTemplate<String, String> producer() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        props.put("key.serializer", StringSerializer.class);
        props.put("value.serializer", StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Test
    @DisplayName("orders.created -> notificação order_created EMAIL entregue")
    void orderCreatedProducesNotification() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = """
                {"orderId":"%s","customerId":"%s","totalAmount":150.00}
                """.formatted(orderId, customerId);

        producer().send("orders.created", json);

        NotificationEntity n = awaitNotification(customerId, "order_created");
        assertThat(n.toDomain().channel().name()).isEqualTo("EMAIL");
        assertThat(n.toDomain().variables()).containsEntry("orderId", orderId.toString());
    }

    @Test
    @DisplayName("payments.approved -> notificação payment_approved EMAIL entregue")
    void paymentApprovedProducesNotification() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = """
                {"orderId":"%s","customerId":"%s","status":"APPROVED","amount":150.00,"reason":null}
                """.formatted(orderId, customerId);

        producer().send("payments.approved", json);

        NotificationEntity n = awaitNotification(customerId, "payment_approved");
        assertThat(n.toDomain().variables()).containsEntry("amount", "150.00");
        assertThat(n.toDomain().variables()).containsEntry("orderId", orderId.toString());
    }

    @Test
    @DisplayName("payments.failed -> notificação payment_failed EMAIL entregue")
    void paymentFailedProducesNotification() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = """
                {"orderId":"%s","customerId":"%s","status":"FAILED","amount":150.00,"reason":"Cartão sem limite"}
                """.formatted(orderId, customerId);

        producer().send("payments.failed", json);

        NotificationEntity n = awaitNotification(customerId, "payment_failed");
        assertThat(n.toDomain().variables()).containsEntry("reason", "Cartão sem limite");
        assertThat(n.toDomain().variables()).containsEntry("orderId", orderId.toString());
    }

    private NotificationEntity awaitNotification(UUID recipientId, String templateId) {
        return Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> findFor(recipientId, templateId), Optional::isPresent)
                .orElseThrow();
    }

    private Optional<NotificationEntity> findFor(UUID recipientId, String templateId) {
        return notifRepo.findAll().stream()
                .filter(e -> e.toDomain().recipientId().equals(recipientId))
                .filter(e -> e.toDomain().templateId().equals(templateId))
                .findFirst();
    }
}
