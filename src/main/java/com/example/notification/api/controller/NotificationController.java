package com.example.notification.api.controller;

import com.example.notification.api.dto.NotificationResponse;
import com.example.notification.api.dto.SendNotificationRequest;
import com.example.notification.application.usecase.GetNotificationUseCase;
import com.example.notification.application.usecase.RetryNotificationUseCase;
import com.example.notification.application.usecase.SendNotificationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final SendNotificationUseCase sendUseCase;
    private final RetryNotificationUseCase retryUseCase;
    private final GetNotificationUseCase getUseCase;

    public NotificationController(SendNotificationUseCase sendUseCase,
                                  RetryNotificationUseCase retryUseCase,
                                  GetNotificationUseCase getUseCase) {
        this.sendUseCase = sendUseCase;
        this.retryUseCase = retryUseCase;
        this.getUseCase = getUseCase;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody SendNotificationRequest request) {
        var notification = sendUseCase.queue(
                request.recipientId(),
                request.channel(),
                request.templateId(),
                request.variables(),
                request.maxAttempts()
        );
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(notification.id())
                .toUri();
        return ResponseEntity.accepted()
                .location(location)
                .body(NotificationResponse.from(notification));
    }

    @GetMapping("/{id}")
    public NotificationResponse get(@PathVariable UUID id) {
        return NotificationResponse.from(getUseCase.byId(id));
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationResponse retry(@PathVariable UUID id) {
        return NotificationResponse.from(retryUseCase.retry(id));
    }

    @GetMapping("/dead-letter")
    public List<NotificationResponse> deadLetter() {
        return getUseCase.deadLettered().stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
