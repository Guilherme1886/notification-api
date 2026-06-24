package com.example.notification;

import com.example.notification.infra.repository.JpaNotificationRepository;
import com.example.notification.infra.repository.JpaTemplateRepository;
import com.example.notification.infra.security.ApiKeyAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ApiKeySecurityTest {

    private static final String VALID_KEY = "test-key";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired JpaNotificationRepository notifRepo;
    @Autowired JpaTemplateRepository templateRepo;

    @BeforeEach
    void cleanDb() {
        notifRepo.deleteAll();
        templateRepo.deleteAll();
    }

    @Test
    @DisplayName("POST /templates sem API Key -> 401")
    void templatesWithoutApiKeyIsUnauthorized() {
        ResponseEntity<String> resp = rest.exchange(
                "/templates", HttpMethod.POST,
                new HttpEntity<>(templateBody("t-no-key"), jsonHeaders(null)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(templateRepo.count()).isZero();
    }

    @Test
    @DisplayName("POST /templates com API Key errada -> 401")
    void templatesWithWrongApiKeyIsUnauthorized() {
        ResponseEntity<String> resp = rest.exchange(
                "/templates", HttpMethod.POST,
                new HttpEntity<>(templateBody("t-wrong-key"), jsonHeaders("chave-errada")),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(templateRepo.count()).isZero();
    }

    @Test
    @DisplayName("POST /templates com API Key correta -> 201")
    void templatesWithValidApiKeyIsCreated() {
        ResponseEntity<String> resp = rest.exchange(
                "/templates", HttpMethod.POST,
                new HttpEntity<>(templateBody("welcome"), jsonHeaders(VALID_KEY)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(templateRepo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /notifications com API Key correta -> 202")
    void notificationsWithValidApiKeyIsAccepted() {
        // Pré-condição: o template precisa existir (validado de forma síncrona pelo use case).
        ResponseEntity<String> templateResp = rest.exchange(
                "/templates", HttpMethod.POST,
                new HttpEntity<>(templateBody("welcome"), jsonHeaders(VALID_KEY)),
                String.class);
        assertThat(templateResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> notification = Map.of(
                "recipientId", UUID.randomUUID().toString(),
                "channel", "EMAIL",
                "templateId", "welcome",
                "variables", Map.of("nome", "Guilherme"));

        ResponseEntity<String> resp = rest.exchange(
                "/notifications", HttpMethod.POST,
                new HttpEntity<>(notification, jsonHeaders(VALID_KEY)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    private Map<String, Object> templateBody(String id) {
        return Map.of(
                "id", id,
                "name", id,
                "channel", "EMAIL",
                "subject", "Bem-vindo",
                "body", "Olá {{nome}}");
    }

    private HttpHeaders jsonHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null) {
            headers.set(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
        }
        return headers;
    }
}
