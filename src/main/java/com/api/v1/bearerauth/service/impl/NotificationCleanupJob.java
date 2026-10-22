package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Deletes the read notifications older than app.notification.retention-days (default 90), on app.notification.cleanup-cron.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupJob {
    private final NotificationService notificationService;

    @Scheduled(cron = "${app.notification.cleanup-cron:0 30 3 * * *}")
    public void cleanup() {
        try {
            int deleted = notificationService.deleteOldRead();
            log.info("Notification cleanup: {} read notification(s) deleted", deleted);
        } catch (Exception e) {
            log.error("Notification cleanup failed", e);
        }
    }
}
