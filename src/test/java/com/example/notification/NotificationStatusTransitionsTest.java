package com.example.notification;

import com.example.notification.domain.model.NotificationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationStatusTransitionsTest {

    @Test
    void allowed_transitions() {
        assertThat(NotificationStatus.PENDING.canTransitionTo(NotificationStatus.PROCESSING)).isTrue();
        assertThat(NotificationStatus.PROCESSING.canTransitionTo(NotificationStatus.DELIVERED)).isTrue();
        assertThat(NotificationStatus.PROCESSING.canTransitionTo(NotificationStatus.FAILED)).isTrue();
        assertThat(NotificationStatus.FAILED.canTransitionTo(NotificationStatus.PENDING)).isTrue();
        assertThat(NotificationStatus.FAILED.canTransitionTo(NotificationStatus.DEAD_LETTERED)).isTrue();
    }

    @Test
    void terminal_states_have_no_exit() {
        assertThat(NotificationStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(NotificationStatus.DEAD_LETTERED.isTerminal()).isTrue();
        assertThat(NotificationStatus.PENDING.isTerminal()).isFalse();
    }

    @Test
    void rejects_illegal_transitions() {
        assertThat(NotificationStatus.PENDING.canTransitionTo(NotificationStatus.DELIVERED)).isFalse();
        assertThat(NotificationStatus.DELIVERED.canTransitionTo(NotificationStatus.FAILED)).isFalse();
    }
}
