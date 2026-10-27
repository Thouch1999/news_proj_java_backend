package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.entity.NewsStatusHistory;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.enumerate.NotificationType;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.response.NewsDto;
import com.api.v1.newssystem.model.response.NewsSummaryDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.BookmarkRepository;
import com.api.v1.newssystem.repository.CategoryRepository;
import com.api.v1.newssystem.repository.CommentRepository;
import com.api.v1.newssystem.repository.NewsImageRepository;
import com.api.v1.newssystem.repository.NewsRepository;
import com.api.v1.newssystem.repository.NewsStatusHistoryRepository;
import com.api.v1.newssystem.repository.NewsTagRepository;
import com.api.v1.newssystem.repository.NewsViewRepository;
import com.api.v1.newssystem.repository.TagRepository;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.service.ActivityLogService;
import com.api.v1.newssystem.service.FileStorageService;
import com.api.v1.newssystem.service.NotificationEvent;
import com.api.v1.newssystem.service.PreviewService;
import com.api.v1.newssystem.service.SystemSettingService;
import com.api.v1.newssystem.util.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scheduled publishing in the workflow: schedule, change, cancel and "publish now" with their roles and statuses (403 wrong
 * role, 409 wrong status, 400 time outside 5 minutes to 1 year), the history notes, the activity log, the notifications, and
 * publishScheduled (what the job calls) with a controllable clock.
 */
@ExtendWith(MockitoExtension.class)
class NewsScheduleServiceTest {
    private static final long OWNER_ID = 1L;
    private static final long EDITOR_ID = 2L;
    private static final long ADMIN_ID = 3L;
    private static final long NEWS_ID = 10L;

    @Mock
    private NewsRepository newsRepository;
    @Mock
    private NewsTagRepository newsTagRepository;
    @Mock
    private NewsImageRepository newsImageRepository;
    @Mock
    private NewsStatusHistoryRepository newsStatusHistoryRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private BookmarkRepository bookmarkRepository;
    @Mock
    private NewsViewRepository newsViewRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private SystemSettingService systemSettingService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PreviewService previewService;
    @Mock
    private ActivityLogService activityLogService;

    private final MutableClock clock = new MutableClock(); // 2026-06-01T08:00:00Z
    private NewsServiceImpl service;
    private AppUser owner;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        service = new NewsServiceImpl(newsRepository, newsTagRepository, newsImageRepository, newsStatusHistoryRepository,
                commentRepository, bookmarkRepository, newsViewRepository, categoryRepository, tagRepository,
                appUserRepository, currentUserService, fileStorageService, new AppProperty(), systemSettingService,
                eventPublisher, previewService, activityLogService, clock);
        owner = user(OWNER_ID, Roles.AUTHOR);
        loginAs(owner);
        lenient().when(newsRepository.save(any(News.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(appUserRepository.getReferenceById(anyLong())).thenAnswer(call -> {
            long id = call.getArgument(0);
            AppUser found = new AppUser();
            found.setId(id);
            return id == actor.getId() ? actor : found;
        });
    }

    private AppUser user(long id, Roles role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setUsername("user" + id);
        return user;
    }

    private void loginAs(AppUser user) {
        actor = user;
        lenient().when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    private void loginAs(Roles role) {
        loginAs(switch (role) {
            case EDITOR -> user(EDITOR_ID, Roles.EDITOR);
            case ADMIN -> user(ADMIN_ID, Roles.ADMIN);
            default -> owner;
        });
    }

    private News article(NewsStatus status) {
        Category category = new Category();
        category.setId(5L);
        category.setName("Sports");
        category.setSlug("sports");
        category.setActive(true);

        News news = new News();
        news.setId(NEWS_ID);
        news.setTitle("Title");
        news.setSlug("title");
        news.setContent("Content");
        news.setStatus(status);
        news.setAuthor(owner);
        news.setCategory(category);
        lenient().when(newsRepository.findById(NEWS_ID)).thenReturn(Optional.of(news));
        lenient().when(newsRepository.findByIdAndAuthorId(NEWS_ID, OWNER_ID)).thenReturn(Optional.of(news));
        return news;
    }

    private Instant in(Duration duration) {
        return clock.instant().plus(duration);
    }

    private NewsStatusHistory savedHistory() {
        ArgumentCaptor<NewsStatusHistory> history = ArgumentCaptor.forClass(NewsStatusHistory.class);
        verify(newsStatusHistoryRepository).save(history.capture());
        return history.getValue();
    }

    private NotificationEvent publishedEvent() {
        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        return event.getValue();
    }

    private void assertFails(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(status));
    }

    private void assertNothingWritten(News news, NewsStatus status) {
        assertThat(news.getStatus()).isEqualTo(status);
        verify(newsRepository, never()).save(any());
        verify(newsStatusHistoryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ---------------- schedule ----------------

    @Test
    void schedule_editorOrAdmin_pendingBecomesScheduled_withHistoryLogAndNotification() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            org.mockito.Mockito.clearInvocations(newsStatusHistoryRepository, eventPublisher, activityLogService);
            loginAs(role);
            News news = article(NewsStatus.PENDING);
            Instant at = in(Duration.ofDays(2));

            NewsDto dto = service.schedule(NEWS_ID, at);

            assertThat(dto.getStatus()).isEqualTo(NewsStatus.SCHEDULED);
            assertThat(dto.getScheduledAt()).isEqualTo(at);
            assertThat(news.getScheduledAt()).isEqualTo(at);
            assertThat(news.getScheduledBy()).isSameAs(actor);
            NewsStatusHistory row = savedHistory();
            assertThat(row.getFromStatus()).isEqualTo(NewsStatus.PENDING);
            assertThat(row.getToStatus()).isEqualTo(NewsStatus.SCHEDULED);
            assertThat(row.getChangedBy()).isSameAs(actor);
            assertThat(row.getNote()).isEqualTo("Scheduled for " + at);
            verify(activityLogService).record(eq(ActivityAction.NEWS_SCHEDULED), eq(ActivityTargetType.NEWS), eq(NEWS_ID), eq("Title"),
                    any(Map.class));
            NotificationEvent event = publishedEvent();
            assertThat(event.type()).isEqualTo(NotificationType.ARTICLE_SCHEDULED);
            assertThat(event.recipientId()).isEqualTo(OWNER_ID);
            assertThat(event.actorId()).isEqualTo(actor.getId());
            assertThat(event.data()).containsEntry("scheduledAt", at.toString()).containsEntry("newsTitle", "Title");
        }
    }

    @Test
    void schedule_adminMayScheduleADraft() {
        loginAs(Roles.ADMIN);
        News news = article(NewsStatus.DRAFT);

        NewsDto dto = service.schedule(NEWS_ID, in(Duration.ofHours(1)));

        assertThat(dto.getStatus()).isEqualTo(NewsStatus.SCHEDULED);
        assertThat(savedHistory().getFromStatus()).isEqualTo(NewsStatus.DRAFT);
        assertThat(news.getScheduledBy()).isSameAs(actor);
    }

    @Test
    void schedule_authorIsForbidden() {
        loginAs(Roles.AUTHOR);
        News news = article(NewsStatus.PENDING);

        assertFails(() -> service.schedule(NEWS_ID, in(Duration.ofDays(1))), HttpStatus.FORBIDDEN);
        assertFails(() -> service.reschedule(NEWS_ID, in(Duration.ofDays(1))), HttpStatus.FORBIDDEN);
        assertFails(() -> service.cancelSchedule(NEWS_ID), HttpStatus.FORBIDDEN);
        assertFails(service::listUpcoming, HttpStatus.FORBIDDEN);
        assertNothingWritten(news, NewsStatus.PENDING);
    }

    @Test
    void schedule_editorCannotScheduleADraft_orAnyOtherStatus() {
        loginAs(Roles.EDITOR);
        for (NewsStatus from : List.of(NewsStatus.DRAFT, NewsStatus.REJECTED, NewsStatus.SCHEDULED, NewsStatus.PUBLISHED, NewsStatus.ARCHIVED)) {
            News news = article(from);
            assertFails(() -> service.schedule(NEWS_ID, in(Duration.ofDays(1))), HttpStatus.CONFLICT);
            assertNothingWritten(news, from);
        }
    }

    @Test
    void schedule_adminCannotScheduleRejectedPublishedOrArchived() {
        loginAs(Roles.ADMIN);
        for (NewsStatus from : List.of(NewsStatus.REJECTED, NewsStatus.SCHEDULED, NewsStatus.PUBLISHED, NewsStatus.ARCHIVED)) {
            News news = article(from);
            assertFails(() -> service.schedule(NEWS_ID, in(Duration.ofDays(1))), HttpStatus.CONFLICT);
            assertNothingWritten(news, from);
        }
    }

    @Test
    void schedule_timeOutsideTheWindow_is400WithScheduledAtInDetails() {
        loginAs(Roles.EDITOR);
        News news = article(NewsStatus.PENDING);

        for (Instant at : new Instant[]{
                null,
                clock.instant().minusSeconds(60),
                clock.instant(),
                in(Duration.ofMinutes(5)).minusSeconds(1),
                in(Duration.ofDays(365)).plusSeconds(1)}) {
            assertThatThrownBy(() -> service.schedule(NEWS_ID, at)).isInstanceOfSatisfying(CustomException.class, e -> {
                assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getErrorResponse().getDetails()).containsKey("scheduledAt");
                assertThat(e.getErrorResponse().getError()).isEqualTo("VALIDATION_FAILED");
            });
        }
        assertNothingWritten(news, NewsStatus.PENDING);
    }

    @Test
    void schedule_theEdgesOfTheWindowAreAllowed() {
        loginAs(Roles.EDITOR);
        article(NewsStatus.PENDING);
        assertThat(service.schedule(NEWS_ID, in(Duration.ofMinutes(5))).getStatus()).isEqualTo(NewsStatus.SCHEDULED);

        article(NewsStatus.PENDING);
        assertThat(service.schedule(NEWS_ID, in(Duration.ofDays(365))).getStatus()).isEqualTo(NewsStatus.SCHEDULED);
    }

    // ---------------- change and cancel ----------------

    @Test
    void reschedule_changesTheTime_whileScheduled() {
        loginAs(Roles.EDITOR);
        News news = article(NewsStatus.SCHEDULED);
        Instant before = in(Duration.ofDays(1));
        news.setScheduledAt(before);
        Instant after = in(Duration.ofDays(3));

        NewsDto dto = service.reschedule(NEWS_ID, after);

        assertThat(dto.getStatus()).isEqualTo(NewsStatus.SCHEDULED);
        assertThat(news.getScheduledAt()).isEqualTo(after);
        assertThat(news.getScheduledBy()).isSameAs(actor);
        NewsStatusHistory row = savedHistory();
        assertThat(row.getFromStatus()).isEqualTo(NewsStatus.SCHEDULED);
        assertThat(row.getToStatus()).isEqualTo(NewsStatus.SCHEDULED);
        assertThat(row.getNote()).isEqualTo("Schedule changed");
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(eq(ActivityAction.NEWS_SCHEDULE_CHANGED), eq(ActivityTargetType.NEWS), eq(NEWS_ID), eq("Title"),
                details.capture());
        assertThat(details.getValue()).containsEntry("scheduledAt", after.toString()).containsEntry("scheduledAtBefore", before.toString());
        assertThat(publishedEvent().type()).isEqualTo(NotificationType.ARTICLE_SCHEDULED);
    }

    @Test
    void reschedule_theSameTime_writesNothing() {
        loginAs(Roles.EDITOR);
        News news = article(NewsStatus.SCHEDULED);
        Instant at = in(Duration.ofDays(1));
        news.setScheduledAt(at);

        service.reschedule(NEWS_ID, at);

        assertNothingWritten(news, NewsStatus.SCHEDULED);
        verify(activityLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void reschedule_onlyWhileScheduled_andInsideTheWindow() {
        loginAs(Roles.EDITOR);
        News pending = article(NewsStatus.PENDING);
        assertFails(() -> service.reschedule(NEWS_ID, in(Duration.ofDays(1))), HttpStatus.CONFLICT);
        assertNothingWritten(pending, NewsStatus.PENDING);

        News scheduled = article(NewsStatus.SCHEDULED);
        scheduled.setScheduledAt(in(Duration.ofDays(1)));
        assertFails(() -> service.reschedule(NEWS_ID, in(Duration.ofMinutes(1))), HttpStatus.BAD_REQUEST);
        assertFails(() -> service.reschedule(NEWS_ID, in(Duration.ofDays(400))), HttpStatus.BAD_REQUEST);
        assertThat(scheduled.getScheduledAt()).isEqualTo(in(Duration.ofDays(1)));
    }

    @Test
    void cancelSchedule_goesBackToPending_andClearsTheSchedule() {
        loginAs(Roles.EDITOR);
        News news = article(NewsStatus.SCHEDULED);
        news.setScheduledAt(in(Duration.ofDays(1)));
        news.setScheduledBy(owner);

        NewsDto dto = service.cancelSchedule(NEWS_ID);

        assertThat(dto.getStatus()).isEqualTo(NewsStatus.PENDING);
        assertThat(dto.getScheduledAt()).isNull();
        assertThat(news.getScheduledAt()).isNull();
        assertThat(news.getScheduledBy()).isNull();
        NewsStatusHistory row = savedHistory();
        assertThat(row.getFromStatus()).isEqualTo(NewsStatus.SCHEDULED);
        assertThat(row.getToStatus()).isEqualTo(NewsStatus.PENDING);
        assertThat(row.getNote()).isEqualTo("Schedule cancelled");
        verify(activityLogService).record(eq(ActivityAction.NEWS_SCHEDULE_CANCELLED), eq(ActivityTargetType.NEWS), eq(NEWS_ID), eq("Title"),
                any(Map.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class)); // a cancelled schedule notifies nobody
    }

    @Test
    void cancelSchedule_onlyFromScheduled() {
        loginAs(Roles.ADMIN);
        for (NewsStatus from : List.of(NewsStatus.DRAFT, NewsStatus.PENDING, NewsStatus.PUBLISHED, NewsStatus.REJECTED, NewsStatus.ARCHIVED)) {
            News news = article(from);
            assertFails(() -> service.cancelSchedule(NEWS_ID), HttpStatus.CONFLICT);
            assertNothingWritten(news, from);
        }
    }

    // ---------------- publish now, editing ----------------

    @Test
    void publishNow_fromScheduled_editorAndAdmin_clearsTheSchedule() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            org.mockito.Mockito.clearInvocations(newsStatusHistoryRepository, eventPublisher);
            loginAs(role);
            News news = article(NewsStatus.SCHEDULED);
            news.setScheduledAt(in(Duration.ofDays(1)));
            news.setScheduledBy(owner);

            NewsDto dto = service.publish(NEWS_ID);

            assertThat(dto.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
            assertThat(news.getPublishedAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
            assertThat(news.getScheduledAt()).isNull();
            assertThat(news.getScheduledBy()).isNull();
            NewsStatusHistory row = savedHistory();
            assertThat(row.getFromStatus()).isEqualTo(NewsStatus.SCHEDULED);
            assertThat(row.getToStatus()).isEqualTo(NewsStatus.PUBLISHED);
            assertThat(row.getNote()).isEqualTo("Published now");
            assertThat(publishedEvent().type()).isEqualTo(NotificationType.ARTICLE_PUBLISHED);
        }
    }

    @Test
    void publishNow_authorIsForbidden() {
        News news = article(NewsStatus.SCHEDULED);
        assertFails(() -> service.publish(NEWS_ID), HttpStatus.FORBIDDEN);
        assertNothingWritten(news, NewsStatus.SCHEDULED);
    }

    @Test
    void editing_scheduledBehavesLikePending() {
        News news = article(NewsStatus.SCHEDULED);
        // the author sees the article but cannot edit it (as in PENDING)
        assertThat(service.getMine(NEWS_ID).isEditable()).isFalse();

        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            loginAs(role);
            assertThat(service.getManaged(NEWS_ID).isEditable()).isTrue();
        }
        assertThat(news.getStatus()).isEqualTo(NewsStatus.SCHEDULED);
    }

    // ---------------- lists ----------------

    @Test
    void upcoming_isTheNextTenScheduled_forEditorsAndAdmins() {
        loginAs(Roles.EDITOR);
        News news = article(NewsStatus.SCHEDULED);
        Instant at = in(Duration.ofDays(1));
        news.setScheduledAt(at);
        news.setScheduledBy(user(ADMIN_ID, Roles.ADMIN));
        when(newsRepository.findTop10ByStatusOrderByScheduledAtAscIdAsc(NewsStatus.SCHEDULED)).thenReturn(List.of(news));

        List<NewsSummaryDto> upcoming = service.listUpcoming();

        assertThat(upcoming).hasSize(1);
        assertThat(upcoming.get(0).getScheduledAt()).isEqualTo(at);
        assertThat(upcoming.get(0).getStatus()).isEqualTo(NewsStatus.SCHEDULED);
    }

    @Test
    void listManaged_sortByScheduledAt_andNothingElse() {
        loginAs(Roles.ADMIN);
        when(newsRepository.search(any(), any(), eq(NewsStatus.SCHEDULED), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listManaged(NewsStatus.SCHEDULED, null, null, "scheduledAt", 1, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsRepository).search(isNull(), isNull(), eq(NewsStatus.SCHEDULED), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("scheduledAt").getDirection()).isEqualTo(Sort.Direction.ASC);

        assertFails(() -> service.listManaged(null, null, null, "title", 1, 10), HttpStatus.BAD_REQUEST);
    }

    // ---------------- what the job calls ----------------

    private void dueArticle(Instant scheduledAt, AppUser scheduledBy) {
        News news = article(NewsStatus.SCHEDULED);
        news.setScheduledAt(scheduledAt);
        news.setScheduledBy(scheduledBy);
        lenient().when(newsRepository.getReferenceById(NEWS_ID)).thenReturn(news);
    }

    @Test
    void publishScheduled_dueArticle_isPublishedByTheSystem_withHistoryLogNotificationAndCache() {
        AppUser scheduler = user(EDITOR_ID, Roles.EDITOR);
        scheduler.setFullName("Edith Editor");
        Instant at = in(Duration.ofHours(1));
        dueArticle(at, scheduler);
        clock.advance(Duration.ofHours(2)); // the application was down: it is late
        when(newsRepository.publishIfDue(eq(NEWS_ID), any(LocalDateTime.class), eq(clock.instant()))).thenReturn(1);

        boolean published = service.publishScheduled(NEWS_ID);

        assertThat(published).isTrue();
        // published_at is the actual time of publishing (now), not the scheduled time
        verify(newsRepository).publishIfDue(NEWS_ID, LocalDateTime.ofInstant(clock.instant(), clock.getZone()), clock.instant());
        NewsStatusHistory row = savedHistory();
        assertThat(row.getFromStatus()).isEqualTo(NewsStatus.SCHEDULED);
        assertThat(row.getToStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(row.getChangedBy().getId()).isEqualTo(EDITOR_ID); // changed_by = scheduled_by
        assertThat(row.getNote()).isEqualTo("Published automatically (scheduled)");
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).recordFor(isNull(), eq(ActivityAction.NEWS_PUBLISHED), eq(ActivityTargetType.NEWS), eq(NEWS_ID),
                eq("Title"), details.capture()); // no actor: the system
        assertThat(details.getValue()).containsEntry("note", "scheduled by Edith Editor").containsEntry("scheduledAt", at.toString());
        NotificationEvent event = publishedEvent();
        assertThat(event.type()).isEqualTo(NotificationType.ARTICLE_PUBLISHED);
        assertThat(event.recipientId()).isEqualTo(OWNER_ID);
        assertThat(event.actorId()).isNull(); // so the author is told even when they scheduled it
        verify(previewService).evictNews("title");
    }

    @Test
    void publishScheduled_notDueYet_orNotScheduled_doesNothing() {
        dueArticle(in(Duration.ofMinutes(10)), user(EDITOR_ID, Roles.EDITOR));
        assertThat(service.publishScheduled(NEWS_ID)).isFalse();

        article(NewsStatus.PUBLISHED);
        assertThat(service.publishScheduled(NEWS_ID)).isFalse();

        verify(newsRepository, never()).publishIfDue(any(), any(), any());
        verify(newsStatusHistoryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void publishScheduled_lostTheRace_publishesNothingTwice() {
        dueArticle(clock.instant(), user(EDITOR_ID, Roles.EDITOR));
        clock.advance(Duration.ofMinutes(1));
        when(newsRepository.publishIfDue(eq(NEWS_ID), any(LocalDateTime.class), any(Instant.class))).thenReturn(0); // another instance won

        assertThat(service.publishScheduled(NEWS_ID)).isFalse();

        verify(newsStatusHistoryRepository, never()).save(any());
        verify(activityLogService, never()).recordFor(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(previewService, never()).evictNews(any());
    }

    @Test
    void errorsAreTheOnesOfMessageConstant() {
        // the 409 text of a forbidden transition is the usual one
        loginAs(Roles.EDITOR);
        article(NewsStatus.DRAFT);
        assertThatThrownBy(() -> service.schedule(NEWS_ID, in(Duration.ofDays(1))))
                .hasMessageContaining(MessageConstant.NEWS_INVALID_TRANSITION.getDefaultMessage());
    }
}
