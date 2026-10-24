package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Deletes the activity log entries older than app.activity-log.retention-days (default 365), on app.activity-log.cleanup-cron.
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLogCleanupJob {
    private final ActivityLogService activityLogService;

    @Scheduled(cron = "${app.activity-log.cleanup-cron:0 45 3 * * *}")
    public void cleanup() {
        try {
            int deleted = activityLogService.deleteExpired();
            log.info("Activity log cleanup: {} old entr(ies) deleted", deleted);
        } catch (Exception e) {
            log.error("Activity log cleanup failed", e);
        }
    }
}
