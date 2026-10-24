package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.model.enumerate.NotificationType;
import com.api.v1.newssystem.service.NotificationEvent;
import com.api.v1.newssystem.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class NotificationEventListenerTest {
    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final NotificationEventListener listener = new NotificationEventListener(notificationService);
    private final NotificationEvent event =
            new NotificationEvent(NotificationType.NEW_COMMENT, 1L, 2L, 10L, 99L, Map.of("newsTitle", "Title"));

    @Test
    void passesTheEventOn() {
        listener.onEvent(event);

        verify(notificationService).record(event);
    }

    @Test
    void aFailingNotification_isLogged_andNeverReachesTheCaller() {
        doThrow(new IllegalStateException("database down")).when(notificationService).record(any());

        assertThatCode(() -> listener.onEvent(event)).doesNotThrowAnyException();
    }
}
