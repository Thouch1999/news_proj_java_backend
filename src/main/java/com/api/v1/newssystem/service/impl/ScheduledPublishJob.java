package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes the SCHEDULED articles whose time has come: every 60 seconds after the last run, and once right after the start
 * (the articles that became due while the application was off). Each article is published in its own transaction by
 * NewsService.publishScheduled (a conditional update, so it is never published twice, also with two instances); a failure is
 * logged and the others go on.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPublishJob {
    private final NewsService newsService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        publishDue();
    }

    @Scheduled(initialDelay = 60, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void run() {
        publishDue();
    }

    /** One run; returns how many articles this run published. */
    public int publishDue() {
        List<Long> due;
        try {
            due = newsService.findDueScheduledIds();
        } catch (Exception e) {
            log.error("Scheduled publishing: could not read the due articles", e);
            return 0;
        }
        int published = 0;
        for (Long id : due) {
            try {
                if (newsService.publishScheduled(id)) {
                    published++;
                }
            } catch (Exception e) {
                log.error("Scheduled publishing: news {} was not published", id, e);
            }
        }
        if (published > 0) {
            log.info("Scheduled publishing: {} article(s) published", published);
        }
        return published;
    }
}
