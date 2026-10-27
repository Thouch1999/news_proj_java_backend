package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.service.NewsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The job asks the service for the due articles and publishes each one on its own; a failure never stops the others. */
@ExtendWith(MockitoExtension.class)
class ScheduledPublishJobTest {
    @Mock
    private NewsService newsService;

    @Test
    void publishesEveryDueArticle() {
        when(newsService.findDueScheduledIds()).thenReturn(List.of(1L, 2L, 3L));
        when(newsService.publishScheduled(1L)).thenReturn(true);
        when(newsService.publishScheduled(2L)).thenReturn(true);
        when(newsService.publishScheduled(3L)).thenReturn(true);

        assertThat(new ScheduledPublishJob(newsService).publishDue()).isEqualTo(3);
    }

    @Test
    void oneFailure_doesNotStopTheOthers() {
        when(newsService.findDueScheduledIds()).thenReturn(List.of(1L, 2L, 3L));
        when(newsService.publishScheduled(1L)).thenReturn(true);
        when(newsService.publishScheduled(2L)).thenThrow(new IllegalStateException("database error"));
        when(newsService.publishScheduled(3L)).thenReturn(true);

        assertThat(new ScheduledPublishJob(newsService).publishDue()).isEqualTo(2);

        verify(newsService).publishScheduled(3L);
    }

    @Test
    void anArticleAnotherRunPublishedFirst_isNotCounted() {
        when(newsService.findDueScheduledIds()).thenReturn(List.of(1L));
        when(newsService.publishScheduled(1L)).thenReturn(false);

        assertThat(new ScheduledPublishJob(newsService).publishDue()).isZero();
    }

    @Test
    void aFailureToReadTheDueArticles_isLoggedAndNothingIsThrown() {
        when(newsService.findDueScheduledIds()).thenThrow(new IllegalStateException("database down"));

        assertThat(new ScheduledPublishJob(newsService).publishDue()).isZero();

        verify(newsService, never()).publishScheduled(org.mockito.ArgumentMatchers.anyLong());
    }
}
