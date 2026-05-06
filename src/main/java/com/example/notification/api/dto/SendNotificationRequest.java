package com.example.notification.api.dto;

import com.example.notification.domain.model.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;
import java.util.UUID;

public record SendNotificationRequest(
        @NotNull UUID recipientId,
        @NotNull NotificationChannel channel,
        @NotBlank String templateId,
        Map<String, String> variables,
        @Positive Integer maxAttempts
) {}
