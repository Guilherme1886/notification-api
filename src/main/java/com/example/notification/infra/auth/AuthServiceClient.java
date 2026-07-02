package com.example.notification.infra.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * Talks to auth-service over its internal {@code GET /users/{userId}} endpoint,
 * authenticating with the shared {@code X-Internal-Api-Key} header.
 *
 * <p>Lookups are best-effort: in the Saga flow a missing user or an unavailable
 * auth-service must not block the notification, so failures resolve to
 * {@link Optional#empty()} and are logged rather than propagated.
 */
@Component
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    public record UserInfo(UUID userId, String email, String status) {
    }

    private final RestClient restClient;

    public AuthServiceClient(RestClient.Builder builder,
                             @Value("${auth.service.url}") String authServiceUrl,
                             @Value("${internal.api.key}") String apiKey) {
        this.restClient = builder
                .baseUrl(authServiceUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .build();
    }

    public Optional<UserInfo> findUser(UUID userId) {
        try {
            UserInfo user = restClient.get()
                    .uri("/users/{userId}", userId)
                    .retrieve()
                    .body(UserInfo.class);
            return Optional.ofNullable(user);
        } catch (RuntimeException e) {
            log.warn("Could not resolve user {} from auth-service: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
