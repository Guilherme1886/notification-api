package com.example.notification;

import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationStatus;
import com.example.notification.domain.model.Template;
import com.example.notification.infra.repository.JpaNotificationRepository;
import com.example.notification.infra.repository.JpaTemplateRepository;
import com.example.notification.infra.sender.EmailSender;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true"
)
@ActiveProfiles("test")
@Testcontainers
@Import(NotificationFlowIntegrationTest.TestConfig.class)
class NotificationFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired JpaNotificationRepository notifRepo;
    @Autowired JpaTemplateRepository templateRepo;
    @Autowired EmailSender emailSender;

    private FlakyEmailSender flaky() {
        return (FlakyEmailSender) emailSender;
    }

    @BeforeEach
    void cleanDb() {
        notifRepo.deleteAll();
        templateRepo.deleteAll();
        flaky().reset();
    }

    @Test
    @DisplayName("1) fluxo completo de entrega — DELIVERED, attempts=1, deliveredAt preenchido")
    void fullDeliveryFlow() {
        createTemplate("payment_approved", "EMAIL", "Seu pagamento foi aprovado",
                "Olá {{nome}}, seu pagamento de {{valor}} foi aprovado.");

        UUID recipient = UUID.randomUUID();
        ResponseEntity<Map> created = rest.postForEntity("/notifications",
                Map.of(
                        "recipientId", recipient.toString(),
                        "channel", "EMAIL",
                        "templateId", "payment_approved",
                        "variables", Map.of("nome", "Guilherme", "valor", "R$ 300,00")
                ),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID id = UUID.fromString(created.getBody().get("id").toString());

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var stored = notifRepo.findById(id).orElseThrow().toDomain();
            assertThat(stored.status()).isEqualTo(NotificationStatus.DELIVERED);
        });

        ResponseEntity<Map> getResp = rest.getForEntity("/notifications/" + id, Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("status")).isEqualTo("DELIVERED");

        var stored = notifRepo.findById(id).orElseThrow().toDomain();
        assertThat(stored.attempts()).isEqualTo(1);
        assertThat(stored.deliveredAt()).isNotNull();
        assertThat(stored.lastError()).isNull();
        assertThat(flaky().callCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("2) template inexistente — 404 e nada salvo")
    void missingTemplate() {
        ResponseEntity<Map> resp = rest.postForEntity("/notifications",
                Map.of(
                        "recipientId", UUID.randomUUID().toString(),
                        "channel", "EMAIL",
                        "templateId", "template_que_nao_existe",
                        "variables", Map.of("nome", "Guilherme")
                ),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().get("error").toString())
                .contains("template_que_nao_existe");
        assertThat(notifRepo.count()).isZero();
        assertThat(flaky().callCount()).isZero();
    }

    @Test
    @DisplayName("3) dead letter após maxAttempts falhas — DEAD_LETTERED, attempts=maxAttempts")
    void deadLetterAfterFailures() {
        flaky().setAlwaysFail();
        createTemplate("payment_approved", "EMAIL", "Subj", "Body {{x}}");

        int maxAttempts = 3;
        ResponseEntity<Map> resp = rest.postForEntity("/notifications",
                Map.of(
                        "recipientId", UUID.randomUUID().toString(),
                        "channel", "EMAIL",
                        "templateId", "payment_approved",
                        "variables", Map.of("x", "1"),
                        "maxAttempts", maxAttempts
                ),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID id = UUID.fromString(resp.getBody().get("id").toString());

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var stored = notifRepo.findById(id).orElseThrow().toDomain();
            assertThat(stored.status()).isEqualTo(NotificationStatus.DEAD_LETTERED);
            assertThat(stored.attempts()).isEqualTo(maxAttempts);
        });

        var stored = notifRepo.findById(id).orElseThrow().toDomain();
        assertThat(stored.deliveredAt()).isNull();
        assertThat(stored.lastError()).contains("always fails");
        assertThat(flaky().callCount()).isEqualTo(maxAttempts);
    }

    @Test
    @DisplayName("4) retry com backoff — falha 2x, sucesso na 3ª — DELIVERED, attempts=3")
    void retryWithBackoffSucceedsOnThirdAttempt() {
        flaky().setFailFirst(2);
        createTemplate("payment_approved", "EMAIL", "Subj", "Body");

        ResponseEntity<Map> resp = rest.postForEntity("/notifications",
                Map.of(
                        "recipientId", UUID.randomUUID().toString(),
                        "channel", "EMAIL",
                        "templateId", "payment_approved",
                        "variables", Map.of(),
                        "maxAttempts", 5
                ),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID id = UUID.fromString(resp.getBody().get("id").toString());

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var stored = notifRepo.findById(id).orElseThrow().toDomain();
            assertThat(stored.status()).isEqualTo(NotificationStatus.DELIVERED);
            assertThat(stored.attempts()).isEqualTo(3);
        });

        var stored = notifRepo.findById(id).orElseThrow().toDomain();
        assertThat(stored.deliveredAt()).isNotNull();
        assertThat(flaky().callCount()).isEqualTo(3);
    }

    private void createTemplate(String id, String channel, String subject, String body) {
        ResponseEntity<Map> resp = rest.postForEntity("/templates",
                Map.of(
                        "id", id,
                        "name", id,
                        "channel", channel,
                        "subject", subject,
                        "body", body
                ),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public EmailSender emailSender() {
            return new FlakyEmailSender();
        }
    }

    static class FlakyEmailSender extends EmailSender {
        private final AtomicInteger calls = new AtomicInteger();
        private volatile int failFirst = 0;
        private volatile boolean alwaysFail = false;

        @Override
        public NotificationChannel channel() {
            return NotificationChannel.EMAIL;
        }

        @Override
        public void send(Notification notification, Template template) {
            int n = calls.incrementAndGet();
            if (alwaysFail) {
                throw new RuntimeException("always fails (attempt " + n + ")");
            }
            if (n <= failFirst) {
                throw new RuntimeException("transient failure (attempt " + n + ")");
            }
        }

        void reset() {
            calls.set(0);
            failFirst = 0;
            alwaysFail = false;
        }

        void setAlwaysFail() {
            alwaysFail = true;
        }

        void setFailFirst(int n) {
            failFirst = n;
        }

        int callCount() {
            return calls.get();
        }
    }
}
