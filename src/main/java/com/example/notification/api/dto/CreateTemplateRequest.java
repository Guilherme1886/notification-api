package com.example.notification.api.dto;

import com.example.notification.domain.model.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTemplateRequest(
        @NotBlank String id,
        @NotBlank String name,
        @NotNull NotificationChannel channel,
        String subject,
        @NotBlank String body
) {}
