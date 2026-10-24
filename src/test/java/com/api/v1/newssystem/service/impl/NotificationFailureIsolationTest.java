package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.enumerate.NotificationType;
import com.api.v1.newssystem.repository.CategoryRepository;
import com.api.v1.newssystem.service.NotificationEvent;
import com.api.v1.newssystem.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The real publisher, listener and transaction manager (committing, no rollback of the test): the notification is written
 * only after the commit, and a failure to write it never breaks the committed action. The notification service is a mock.
 */
@SpringBootTest
class NotificationFailureIsolationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private CategoryRepository categoryRepository;
    @MockitoBean
    private NotificationService notificationService;

    private NotificationEvent event() {
        return new NotificationEvent(NotificationType.ARTICLE_PUBLISHED, 1L, 2L, 10L, null, Map.of("newsTitle", "Title"));
    }

    private Category category(String token) {
        Category category = new Category();
        category.setName("Isolation " + token);
        category.setSlug("isolation-" + token);
        category.setActive(true);
        return categoryRepository.save(category);
    }

    @Test
    void aFailingNotification_doesNotBreakOrRollBackTheMainAction() {
        String token = UUID.randomUUID().toString().substring(0, 8);
        doThrow(new IllegalStateException("notification table is gone")).when(notificationService).record(any());
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        Long[] id = new Long[1];
        try {
            assertThatCode(() -> template.executeWithoutResult(status -> {
                id[0] = category(token).getId();
                eventPublisher.publishEvent(event());
            })).doesNotThrowAnyException();

            verify(notificationService).record(any()); // it was tried, after the commit
            assertThat(categoryRepository.findById(id[0])).isPresent(); // and the main action stayed committed
        } finally {
            if (id[0] != null) {
                categoryRepository.deleteById(id[0]);
            }
        }
    }

    @Test
    void nothingIsNotified_whenTheMainTransactionRollsBack() {
        String token = UUID.randomUUID().toString().substring(0, 8);
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThatCode(() -> template.executeWithoutResult(status -> {
            category(token);
            eventPublisher.publishEvent(event());
            status.setRollbackOnly();
        })).doesNotThrowAnyException();

        verify(notificationService, never()).record(any());
        assertThat(categoryRepository.findAll()).noneMatch(c -> ("Isolation " + token).equals(c.getName()));
    }
}
