package com.example.notification.domain.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum NotificationStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED,
    DEAD_LETTERED;

    private static final Map<NotificationStatus, Set<NotificationStatus>> TRANSITIONS = Map.of(
            PENDING,        EnumSet.of(PROCESSING),
            PROCESSING,     EnumSet.of(DELIVERED, FAILED),
            FAILED,         EnumSet.of(PENDING, DEAD_LETTERED),
            DELIVERED,      EnumSet.noneOf(NotificationStatus.class),
            DEAD_LETTERED,  EnumSet.noneOf(NotificationStatus.class)
    );

    public boolean canTransitionTo(NotificationStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
