package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.service.NotificationEvent;
import com.api.v1.newssystem.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes the notification after the transaction of the action committed, so a failure here can never break or roll
 * back the action: the error is only logged. fallbackExecution: an action without a transaction notifies at once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEvent(NotificationEvent event) {
        try {
            notificationService.record(event);
        } catch (Exception e) {
            log.error("Could not create the {} notification (news {}, comment {})",
                    event.type(), event.newsId(), event.commentId(), e);
        }
    }
}
