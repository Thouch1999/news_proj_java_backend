package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.entity.ActivityLog;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.entity.NewsStatusHistory;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.enumerate.NotificationType;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.service.NewsService;
import com.api.v1.newssystem.service.NotificationEvent;
import com.api.v1.newssystem.service.PreviewService;
import com.api.v1.newssystem.service.PublicNewsService;
import com.api.v1.newssystem.service.impl.ScheduledPublishJob;
import com.api.v1.newssystem.util.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scheduled publishing against the configured MySQL, with a controllable clock: the job publishes only the due articles and
 * only once (two runs), catches up after "downtime", writes the history, the activity log (the system as actor) and the
 * notification event, and a SCHEDULED article is invisible everywhere public. Everything runs in a transaction that is
 * rolled back, and every row carries a unique token.
 */
@SpringBootTest
@Import(ScheduledPublishingQueryTest.ClockConfig.class)
@RecordApplicationEvents
@Transactional
class ScheduledPublishingQueryTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private EntityManager em;
    @Autowired
    private MutableClock clock;
    @Autowired
    private ScheduledPublishJob job;
    @Autowired
    private NewsService newsService;
    @Autowired
    private NewsRepository newsRepository;
    @Autowired
    private PublicNewsService publicNewsService;
    @Autowired
    private PreviewService previewService;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ApplicationEvents events;

    private String token;
    private AppUser author;
    private AppUser scheduler;
    private Category category;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString().substring(0, 8);
        author = user("author", Roles.AUTHOR);
        scheduler = user("editor", Roles.EDITOR);
        category = new Category();
        category.setName("Politics " + token);
        category.setSlug("politics-" + token);
        category.setActive(true);
        em.persist(category);
    }

    private AppUser user(String name, Roles role) {
        AppUser user = new AppUser();
        user.setEmail(name + "-sched-" + token + "@news.local");
        user.setPassword("x");
        user.setRole(role);
        user.setUsername(name + " " + token);
        em.persist(user);
        return user;
    }

    private News article(String part, NewsStatus status, Instant scheduledAt) {
        News news = new News();
        news.setTitle("Scheduled " + part + " " + token);
        news.setSlug(part + "-" + token);
        news.setContent("<p>Body " + part + token + "</p>");
        news.setStatus(status);
        news.setAuthor(author);
        news.setCategory(category);
        if (scheduledAt != null) {
            news.setScheduledAt(scheduledAt);
            news.setScheduledBy(scheduler);
        }
        em.persist(news);
        return news;
    }

    private News reload(News news) {
        em.flush();
        em.clear();
        return newsRepository.findById(news.getId()).orElseThrow();
    }

    private Instant in(Duration duration) {
        return clock.instant().plus(duration);
    }

    private List<NewsStatusHistory> history(News news) {
        return em.createQuery("select h from NewsStatusHistory h where h.news.id = :id", NewsStatusHistory.class)
                .setParameter("id", news.getId()).getResultList();
    }

    // ---------------- the job ----------------

    @Test
    void job_publishesOnlyTheDueArticles_andOnlyOnce() {
        News due1 = article("due-one", NewsStatus.SCHEDULED, in(Duration.ofMinutes(10)));
        News due2 = article("due-two", NewsStatus.SCHEDULED, in(Duration.ofMinutes(20)));
        News later = article("later", NewsStatus.SCHEDULED, in(Duration.ofHours(5)));
        News pending = article("pending", NewsStatus.PENDING, null);
        em.flush();

        assertThat(job.publishDue()).isZero(); // nothing is due yet

        clock.advance(Duration.ofMinutes(30));
        assertThat(job.publishDue()).isEqualTo(2);
        assertThat(job.publishDue()).isZero(); // a second run (another instance, the next minute) publishes nothing again

        assertThat(reload(due1).getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(reload(due2).getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(reload(later).getStatus()).isEqualTo(NewsStatus.SCHEDULED);
        assertThat(reload(pending).getStatus()).isEqualTo(NewsStatus.PENDING);
        assertThat(history(due1)).hasSize(1); // written once
        assertThat(events.stream(NotificationEvent.class).filter(e -> e.type() == NotificationType.ARTICLE_PUBLISHED)).hasSize(2);
    }

    @Test
    void conditionalUpdate_neverPublishesTwice_orBeforeTheTime() {
        News news = article("race", NewsStatus.SCHEDULED, in(Duration.ofMinutes(10)));
        em.flush();
        LocalDateTime when = LocalDateTime.now(clock);

        assertThat(newsRepository.publishIfDue(news.getId(), when, clock.instant())).isZero(); // not due
        clock.advance(Duration.ofMinutes(11));
        assertThat(newsRepository.publishIfDue(news.getId(), when, clock.instant())).isEqualTo(1);
        assertThat(newsRepository.publishIfDue(news.getId(), when, clock.instant())).isZero(); // the second caller loses
    }

    @Test
    void job_catchesUpAfterDowntime_andPublishedAtIsTheActualTime() {
        Instant scheduledAt = in(Duration.ofHours(1));
        News news = article("downtime", NewsStatus.SCHEDULED, scheduledAt);
        em.flush();

        clock.advance(Duration.ofDays(3)); // the application was off, the job did not run

        assertThat(job.publishDue()).isEqualTo(1); // what the run right after the start does

        News published = reload(news);
        assertThat(published.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isEqualTo(LocalDateTime.now(clock)); // now, not the scheduled time
        assertThat(published.getPublishedAt()).isAfter(LocalDateTime.ofInstant(scheduledAt, clock.getZone()));
    }

    @Test
    void job_writesHistoryActivityLogAndNotification() {
        News news = article("audit", NewsStatus.SCHEDULED, in(Duration.ofMinutes(10)));
        em.flush();
        clock.advance(Duration.ofMinutes(15));

        assertThat(job.publishDue()).isEqualTo(1);
        em.flush();

        List<NewsStatusHistory> rows = history(news);
        assertThat(rows).hasSize(1);
        NewsStatusHistory row = rows.get(0);
        assertThat(row.getFromStatus()).isEqualTo(NewsStatus.SCHEDULED);
        assertThat(row.getToStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(row.getChangedBy().getId()).isEqualTo(scheduler.getId()); // changed_by = scheduled_by
        assertThat(row.getNote()).isEqualTo("Published automatically (scheduled)");

        ActivityLog entry = em.createQuery("select a from ActivityLog a where a.targetId = :id and a.action = :action", ActivityLog.class)
                .setParameter("id", news.getId()).setParameter("action", ActivityAction.NEWS_PUBLISHED).getSingleResult();
        assertThat(entry.getActor()).isNull(); // the system
        assertThat(entry.getDetails()).contains("scheduled by").contains("editor " + token);

        NotificationEvent event = events.stream(NotificationEvent.class).findFirst().orElseThrow();
        assertThat(event.type()).isEqualTo(NotificationType.ARTICLE_PUBLISHED);
        assertThat(event.recipientId()).isEqualTo(author.getId());
        assertThat(event.actorId()).isNull();
    }

    // ---------------- SCHEDULED is never public ----------------

    @Test
    void scheduled_isInvisibleEverywherePublic_untilItIsPublished() {
        News news = article("hidden", NewsStatus.SCHEDULED, in(Duration.ofMinutes(10)));
        em.flush();
        em.clear();
        String slug = news.getSlug();

        assertThat(publicNewsService.list(null, null, null, null, null, 1, 50).getContent())
                .noneMatch(item -> slug.equals(item.getSlug()));
        assertThat(publicNewsService.list("Body hidden" + token, null, null, null, null, 1, 50).getContent()).isEmpty(); // search
        assertThat(publicNewsService.list(null, category.getSlug(), null, null, null, 1, 50).getContent()).isEmpty();
        assertThatThrownBy(() -> publicNewsService.getBySlug(slug, "198.51.100.77"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus().value()).isEqualTo(404));
        assertThat(publicNewsService.latest(50)).noneMatch(item -> slug.equals(item.getSlug()));
        assertThat(publicNewsService.mostViewed(50)).noneMatch(item -> slug.equals(item.getSlug()));
        assertThat(publicNewsService.categorySections(10)).flatExtracting(section -> section.getNews())
                .noneMatch(item -> slug.equals(item.getSlug()));
        assertThat(previewService.news(slug, "en")).isEmpty(); // the link preview page
        assertThat(newsRepository.findPublishedBySlug(slug)).isEmpty();
        assertThat(newsRepository.findPublishedById(news.getId())).isEmpty(); // bookmarks

        clock.advance(Duration.ofMinutes(11));
        assertThat(job.publishDue()).isEqualTo(1);
        em.flush();
        em.clear();

        assertThat(publicNewsService.list(null, category.getSlug(), null, null, null, 1, 50).getContent())
                .extracting(item -> item.getSlug()).containsExactly(slug);
    }

    // ---------------- counts and lists ----------------

    // the count behind the dashboard (newsByStatus is zero filled from it)
    private long scheduledCount() {
        return reportRepository.countNewsByStatus().stream()
                .filter(row -> row[0] == NewsStatus.SCHEDULED).mapToLong(row -> (Long) row[1]).sum();
    }

    @Test
    void statusCounts_includeScheduled_andTheUpcomingListIsOrderedByTime() {
        long before = scheduledCount();
        News far = article("far", NewsStatus.SCHEDULED, in(Duration.ofDays(5)));
        News near = article("near", NewsStatus.SCHEDULED, in(Duration.ofHours(1)));
        em.flush();

        assertThat(scheduledCount()).isEqualTo(before + 2);

        List<Long> upcoming = newsRepository.findTop10ByStatusOrderByScheduledAtAscIdAsc(NewsStatus.SCHEDULED).stream()
                .map(News::getId).toList();
        assertThat(upcoming).containsSubsequence(near.getId(), far.getId());
        assertThat(upcoming).hasSizeLessThanOrEqualTo(10);
    }
}
