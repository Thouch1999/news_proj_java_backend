package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.Category;
import com.api.v1.bearerauth.model.entity.News;
import com.api.v1.bearerauth.model.entity.NewsStatusHistory;
import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.request.NewsRequest;
import com.api.v1.bearerauth.model.response.NewsDto;
import com.api.v1.bearerauth.model.response.NewsStatusHistoryDto;
import com.api.v1.bearerauth.model.response.NewsSummaryDto;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.repository.BookmarkRepository;
import com.api.v1.bearerauth.repository.CategoryRepository;
import com.api.v1.bearerauth.repository.CommentRepository;
import com.api.v1.bearerauth.repository.NewsImageRepository;
import com.api.v1.bearerauth.repository.NewsRepository;
import com.api.v1.bearerauth.repository.NewsStatusHistoryRepository;
import com.api.v1.bearerauth.repository.NewsTagRepository;
import com.api.v1.bearerauth.repository.NewsViewRepository;
import com.api.v1.bearerauth.repository.TagRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.FileStorageService;
import com.api.v1.bearerauth.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The editorial workflow: every valid transition, the admin-only ones, and the invalid ones
 * (wrong role = 403, wrong current status = 409, missing reason = 400, not own article = 404).
 */
@ExtendWith(MockitoExtension.class)
class NewsWorkflowServiceTest {
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

    private NewsServiceImpl service;
    private AppUser owner;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        service = new NewsServiceImpl(newsRepository, newsTagRepository, newsImageRepository, newsStatusHistoryRepository,
                commentRepository, bookmarkRepository, newsViewRepository, categoryRepository, tagRepository,
                appUserRepository, currentUserService, fileStorageService, new AppProperty(), systemSettingService);
        lenient().when(systemSettingService.isCommentsEnabledDefault()).thenReturn(true);
        owner = user(OWNER_ID, Roles.AUTHOR);
        loginAs(owner);
        lenient().when(newsRepository.save(any(News.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(appUserRepository.getReferenceById(anyLong())).thenAnswer(call -> actor);
    }

    private AppUser user(long id, Roles role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
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

    // an article of the owner (author 1), found by id for an editor/admin and by id + author for the owner
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

    private NewsStatusHistory savedHistory() {
        ArgumentCaptor<NewsStatusHistory> history = ArgumentCaptor.forClass(NewsStatusHistory.class);
        verify(newsStatusHistoryRepository).save(history.capture());
        return history.getValue();
    }

    private void assertHistory(NewsStatus from, NewsStatus to, AppUser by) {
        NewsStatusHistory row = savedHistory();
        assertThat(row.getFromStatus()).isEqualTo(from);
        assertThat(row.getToStatus()).isEqualTo(to);
        assertThat(row.getChangedBy()).isSameAs(by);
        assertThat(row.getNews().getId()).isEqualTo(NEWS_ID);
        assertThat(row.getNote()).isNotBlank();
    }

    private void assertFails(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(status));
    }

    private void assertNothingChanged(News news, NewsStatus status) {
        assertThat(news.getStatus()).isEqualTo(status);
        verify(newsRepository, never()).save(any());
        verify(newsStatusHistoryRepository, never()).save(any());
    }

    private Set<NewsStatus> allBut(NewsStatus... allowed) {
        Set<NewsStatus> rest = EnumSet.allOf(NewsStatus.class);
        for (NewsStatus status : allowed) {
            rest.remove(status);
        }
        return rest;
    }

    // ---------------- valid transitions ----------------

    @Test
    void submit_ownDraftAndOwnRejected_becomePending_andTheReasonIsCleared() {
        for (NewsStatus from : List.of(NewsStatus.DRAFT, NewsStatus.REJECTED)) {
            org.mockito.Mockito.clearInvocations(newsStatusHistoryRepository);
            News news = article(from);
            news.setRejectionReason(from == NewsStatus.REJECTED ? "Add sources" : null);

            NewsDto dto = service.submit(NEWS_ID);

            assertThat(dto.getStatus()).isEqualTo(NewsStatus.PENDING);
            assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);
            assertThat(news.getRejectionReason()).isNull();
            assertThat(dto.getRejectionReason()).isNull();
            assertHistory(from, NewsStatus.PENDING, owner);
        }
    }

    @Test
    void publish_editorAndAdmin_movePendingToPublished_andSetPublishedAt() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            org.mockito.Mockito.clearInvocations(newsStatusHistoryRepository);
            loginAs(role);
            News news = article(NewsStatus.PENDING);
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            NewsDto dto = service.publish(NEWS_ID);

            assertThat(dto.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
            assertThat(news.getPublishedAt()).isAfter(before);
            assertThat(dto.getPublishedAt()).isEqualTo(news.getPublishedAt());
            assertHistory(NewsStatus.PENDING, NewsStatus.PUBLISHED, actor);
        }
    }

    @Test
    void reject_editorAndAdmin_movePendingToRejected_andSaveTheReason() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            org.mockito.Mockito.clearInvocations(newsStatusHistoryRepository);
            loginAs(role);
            News news = article(NewsStatus.PENDING);

            NewsDto dto = service.reject(NEWS_ID, "  Add sources  ");

            assertThat(dto.getStatus()).isEqualTo(NewsStatus.REJECTED);
            assertThat(news.getRejectionReason()).isEqualTo("Add sources");
            assertThat(dto.getRejectionReason()).isEqualTo("Add sources");
            assertHistory(NewsStatus.PENDING, NewsStatus.REJECTED, actor);
            assertThat(savedHistory().getNote()).isEqualTo("Add sources");
        }
    }

    @Test
    void archive_editorAndAdmin_movePublishedToArchived() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            org.mockito.Mockito.clearInvocations(newsStatusHistoryRepository);
            loginAs(role);
            News news = article(NewsStatus.PUBLISHED);

            assertThat(service.archive(NEWS_ID).getStatus()).isEqualTo(NewsStatus.ARCHIVED);

            assertThat(news.getStatus()).isEqualTo(NewsStatus.ARCHIVED);
            assertHistory(NewsStatus.PUBLISHED, NewsStatus.ARCHIVED, actor);
        }
    }

    @Test
    void rejectedArticle_canBeEditedAndResubmitted_untilPublished() {
        // REJECTED -> (author edits) -> PENDING -> PUBLISHED
        News news = article(NewsStatus.REJECTED);
        news.setRejectionReason("Add sources");
        NewsRequest edit = new NewsRequest();
        edit.setTitle("Better title");
        edit.setCategoryId(5L);
        service.update(NEWS_ID, edit);
        assertThat(news.getStatus()).isEqualTo(NewsStatus.REJECTED);

        service.submit(NEWS_ID);
        assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);

        loginAs(Roles.EDITOR);
        service.publish(NEWS_ID);
        assertThat(news.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
    }

    // ---------------- admin-only transitions ----------------

    @Test
    void publish_adminOnly_draftGoesStraightToPublished_withHistory() {
        AppUser admin = user(ADMIN_ID, Roles.ADMIN);
        loginAs(admin);
        News news = article(NewsStatus.DRAFT);

        NewsDto dto = service.publish(NEWS_ID);

        assertThat(dto.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(news.getPublishedAt()).isNotNull();
        assertHistory(NewsStatus.DRAFT, NewsStatus.PUBLISHED, admin);
        assertThat(savedHistory().getNote()).contains("directly");
    }

    @Test
    void restore_adminOnly_archivedGoesBackToPublished_keepingTheOriginalPublishedAt() {
        AppUser admin = user(ADMIN_ID, Roles.ADMIN);
        loginAs(admin);
        News news = article(NewsStatus.ARCHIVED);
        LocalDateTime first = LocalDateTime.of(2026, 1, 5, 9, 0);
        news.setPublishedAt(first);

        NewsDto dto = service.restore(NEWS_ID);

        assertThat(dto.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(news.getPublishedAt()).isEqualTo(first);
        assertHistory(NewsStatus.ARCHIVED, NewsStatus.PUBLISHED, admin);
    }

    @Test
    void restore_archivedWithoutPublishedAt_getsOne() {
        loginAs(Roles.ADMIN);
        News news = article(NewsStatus.ARCHIVED);

        service.restore(NEWS_ID);

        assertThat(news.getPublishedAt()).isNotNull();
    }

    // ---------------- invalid transitions: wrong role (403) ----------------

    @Test
    void editor_cannotPublishDraftDirectly() {
        loginAs(Roles.EDITOR);
        News news = article(NewsStatus.DRAFT);

        assertFails(() -> service.publish(NEWS_ID), HttpStatus.CONFLICT);

        assertNothingChanged(news, NewsStatus.DRAFT);
    }

    @Test
    void editor_cannotRestore_whateverTheStatus() {
        loginAs(Roles.EDITOR);
        News news = article(NewsStatus.ARCHIVED);

        assertFails(() -> service.restore(NEWS_ID), HttpStatus.FORBIDDEN);

        assertNothingChanged(news, NewsStatus.ARCHIVED);
    }

    @Test
    void editor_cannotSubmit() {
        loginAs(Roles.EDITOR);
        News news = article(NewsStatus.DRAFT);

        assertFails(() -> service.submit(NEWS_ID), HttpStatus.FORBIDDEN);

        assertNothingChanged(news, NewsStatus.DRAFT);
    }

    @Test
    void author_cannotPublishRejectArchiveOrRestore_evenOwnArticle() {
        for (NewsStatus status : NewsStatus.values()) {
            News news = article(status);

            assertFails(() -> service.publish(NEWS_ID), HttpStatus.FORBIDDEN);
            assertFails(() -> service.reject(NEWS_ID, "No"), HttpStatus.FORBIDDEN);
            assertFails(() -> service.archive(NEWS_ID), HttpStatus.FORBIDDEN);
            assertFails(() -> service.restore(NEWS_ID), HttpStatus.FORBIDDEN);
            assertNothingChanged(news, status);
        }
    }

    @Test
    void reader_cannotDoAnything() {
        loginAs(user(7L, Roles.READER));
        News news = article(NewsStatus.PENDING);

        assertFails(() -> service.submit(NEWS_ID), HttpStatus.FORBIDDEN);
        assertFails(() -> service.publish(NEWS_ID), HttpStatus.FORBIDDEN);
        assertFails(() -> service.reject(NEWS_ID, "No"), HttpStatus.FORBIDDEN);
        assertFails(() -> service.archive(NEWS_ID), HttpStatus.FORBIDDEN);
        assertFails(() -> service.restore(NEWS_ID), HttpStatus.FORBIDDEN);
        assertFails(() -> service.listReviewQueue(1, 10), HttpStatus.FORBIDDEN);
        assertNothingChanged(news, NewsStatus.PENDING);
    }

    // ---------------- invalid transitions: wrong status (409) ----------------

    @Test
    void submit_fromAnyOtherStatus_isAConflict() {
        for (NewsStatus status : allBut(NewsStatus.DRAFT, NewsStatus.REJECTED)) {
            News news = article(status);

            assertFails(() -> service.submit(NEWS_ID), HttpStatus.CONFLICT);

            assertNothingChanged(news, status);
        }
    }

    @Test
    void submit_anotherAuthorsArticle_isNotFound() {
        loginAs(user(99L, Roles.AUTHOR));
        News news = article(NewsStatus.DRAFT);

        assertFails(() -> service.submit(NEWS_ID), HttpStatus.NOT_FOUND);

        assertNothingChanged(news, NewsStatus.DRAFT);
    }

    @Test
    void publish_byEditor_fromAnyStatusButPending_isAConflict() {
        loginAs(Roles.EDITOR);
        for (NewsStatus status : allBut(NewsStatus.PENDING)) {
            News news = article(status);

            assertFails(() -> service.publish(NEWS_ID), HttpStatus.CONFLICT);

            assertNothingChanged(news, status);
        }
    }

    @Test
    void publish_byAdmin_fromRejectedPublishedOrArchived_isAConflict() {
        loginAs(Roles.ADMIN);
        for (NewsStatus status : allBut(NewsStatus.PENDING, NewsStatus.DRAFT)) {
            News news = article(status);

            assertFails(() -> service.publish(NEWS_ID), HttpStatus.CONFLICT);

            assertNothingChanged(news, status);
        }
    }

    @Test
    void reject_fromAnyStatusButPending_isAConflict() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            loginAs(role);
            for (NewsStatus status : allBut(NewsStatus.PENDING)) {
                News news = article(status);

                assertFails(() -> service.reject(NEWS_ID, "Reason"), HttpStatus.CONFLICT);

                assertNothingChanged(news, status);
            }
        }
    }

    @Test
    void reject_withoutAReason_isABadRequest() {
        loginAs(Roles.EDITOR);
        News news = article(NewsStatus.PENDING);

        assertFails(() -> service.reject(NEWS_ID, null), HttpStatus.BAD_REQUEST);
        assertFails(() -> service.reject(NEWS_ID, "   "), HttpStatus.BAD_REQUEST);

        assertNothingChanged(news, NewsStatus.PENDING);
    }

    @Test
    void archive_fromAnyStatusButPublished_isAConflict() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            loginAs(role);
            for (NewsStatus status : allBut(NewsStatus.PUBLISHED)) {
                News news = article(status);

                assertFails(() -> service.archive(NEWS_ID), HttpStatus.CONFLICT);

                assertNothingChanged(news, status);
            }
        }
    }

    @Test
    void restore_byAdmin_fromAnyStatusButArchived_isAConflict() {
        loginAs(Roles.ADMIN);
        for (NewsStatus status : allBut(NewsStatus.ARCHIVED)) {
            News news = article(status);

            assertFails(() -> service.restore(NEWS_ID), HttpStatus.CONFLICT);

            assertNothingChanged(news, status);
        }
    }

    @Test
    void invalidTransition_messageNamesTheStatuses() {
        loginAs(Roles.EDITOR);
        article(NewsStatus.DRAFT);

        assertThatThrownBy(() -> service.publish(NEWS_ID))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getMessage())
                        .startsWith(MessageConstant.NEWS_INVALID_TRANSITION.getDefaultMessage())
                        .contains("DRAFT")
                        .contains("PUBLISHED"));
    }

    @Test
    void unknownArticle_isNotFound() {
        loginAs(Roles.EDITOR);

        assertFails(() -> service.publish(77L), HttpStatus.NOT_FOUND);
        assertFails(() -> service.reject(77L, "Reason"), HttpStatus.NOT_FOUND);
        assertFails(() -> service.archive(77L), HttpStatus.NOT_FOUND);
        assertFails(() -> service.history(77L), HttpStatus.NOT_FOUND);
    }

    // ---------------- comments on/off ----------------

    @Test
    void setCommentsEnabled_editorAndAdmin_canSwitchItOnAndOff_inAnyStatus() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            loginAs(role);
            for (NewsStatus status : NewsStatus.values()) {
                News news = article(status);

                assertThat(service.setCommentsEnabled(NEWS_ID, false).isCommentsEnabled()).isFalse();
                assertThat(news.isCommentsEnabled()).isFalse();

                assertThat(service.setCommentsEnabled(NEWS_ID, true).isCommentsEnabled()).isTrue();
                assertThat(news.isCommentsEnabled()).isTrue();
                assertThat(news.getStatus()).isEqualTo(status); // the status is not touched
            }
        }
    }

    @Test
    void setCommentsEnabled_isForbiddenForAnAuthor_evenOnAnOwnArticle_andForAReader() {
        for (Roles role : List.of(Roles.AUTHOR, Roles.READER)) {
            loginAs(role == Roles.AUTHOR ? owner : user(7L, Roles.READER));
            News news = article(NewsStatus.PUBLISHED);

            assertFails(() -> service.setCommentsEnabled(NEWS_ID, false), HttpStatus.FORBIDDEN);

            assertThat(news.isCommentsEnabled()).isTrue();
        }
        verify(newsRepository, never()).save(any());
    }

    @Test
    void setCommentsEnabled_unknownArticle_isNotFound() {
        loginAs(Roles.EDITOR);

        assertFails(() -> service.setCommentsEnabled(77L, false), HttpStatus.NOT_FOUND);
    }

    // ---------------- review queue ----------------

    @Test
    void reviewQueue_listsPendingArticlesOldestFirst_forEditorAndAdmin() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            loginAs(role);
            News pending = article(NewsStatus.PENDING);
            when(newsRepository.findAllByStatus(eq(NewsStatus.PENDING), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(pending)));

            Page<NewsSummaryDto> queue = service.listReviewQueue(0, 1000);

            assertThat(queue.getContent()).hasSize(1);
            assertThat(queue.getContent().get(0).getStatus()).isEqualTo(NewsStatus.PENDING);
        }
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsRepository, org.mockito.Mockito.atLeastOnce()).findAllByStatus(eq(NewsStatus.PENDING), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("updatedAt").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void reviewQueue_isNotForAuthors() {
        assertFails(() -> service.listReviewQueue(1, 10), HttpStatus.FORBIDDEN);

        verify(newsRepository, never()).findAllByStatus(any(), any());
    }

    // ---------------- history ----------------

    private NewsStatusHistory row(long id, NewsStatus from, NewsStatus to, AppUser by, String note) {
        NewsStatusHistory row = new NewsStatusHistory();
        row.setId(id);
        row.setFromStatus(from);
        row.setToStatus(to);
        row.setChangedBy(by);
        row.setNote(note);
        row.setChangedAt(LocalDateTime.of(2026, 3, 1, 8, (int) id));
        return row;
    }

    @Test
    void history_authorSeesOwnArticle_editorAndAdminSeeAny() {
        AppUser editor = user(EDITOR_ID, Roles.EDITOR);
        editor.setFullName("Eve Editor");
        article(NewsStatus.REJECTED);
        when(newsStatusHistoryRepository.findAllByNewsIdOrderByChangedAtAsc(NEWS_ID)).thenReturn(List.of(
                row(1, null, NewsStatus.DRAFT, owner, "Article created"),
                row(2, NewsStatus.DRAFT, NewsStatus.PENDING, owner, "Submitted for review"),
                row(3, NewsStatus.PENDING, NewsStatus.REJECTED, editor, "Add sources")));

        List<NewsStatusHistoryDto> asAuthor = service.history(NEWS_ID);

        assertThat(asAuthor).extracting(NewsStatusHistoryDto::getToStatus)
                .containsExactly(NewsStatus.DRAFT, NewsStatus.PENDING, NewsStatus.REJECTED);
        assertThat(asAuthor.get(0).getFromStatus()).isNull();
        assertThat(asAuthor.get(2).getChangedById()).isEqualTo(EDITOR_ID);
        assertThat(asAuthor.get(2).getChangedByName()).isEqualTo("Eve Editor");
        assertThat(asAuthor.get(2).getNote()).isEqualTo("Add sources");

        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            loginAs(role);
            assertThat(service.history(NEWS_ID)).hasSize(3);
        }
    }

    @Test
    void history_anotherAuthorsArticle_isNotFound() {
        loginAs(user(99L, Roles.AUTHOR));
        article(NewsStatus.PUBLISHED);

        assertFails(() -> service.history(NEWS_ID), HttpStatus.NOT_FOUND);

        verify(newsStatusHistoryRepository, never()).findAllByNewsIdOrderByChangedAtAsc(any());
    }

    // ---------------- who may edit ----------------

    @Test
    void editor_canEditAnyArticleButAnArchivedOne() {
        loginAs(Roles.EDITOR);
        for (NewsStatus status : allBut(NewsStatus.ARCHIVED)) {
            News news = article(status);
            NewsRequest request = new NewsRequest();
            request.setTitle("Edited by editor");
            request.setCategoryId(5L);

            assertThat(service.update(NEWS_ID, request).getTitle()).isEqualTo("Edited by editor");
            assertThat(news.getStatus()).isEqualTo(status);
            assertThat(service.getManaged(NEWS_ID).isEditable()).isTrue();
        }

        News archived = article(NewsStatus.ARCHIVED);
        NewsRequest request = new NewsRequest();
        request.setTitle("Too late");
        request.setCategoryId(5L);
        assertFails(() -> service.update(NEWS_ID, request), HttpStatus.CONFLICT);
        assertThat(archived.getTitle()).isEqualTo("Title");
        assertThat(service.getManaged(NEWS_ID).isEditable()).isFalse();
    }

    @Test
    void admin_canEditAnArchivedArticle() {
        loginAs(Roles.ADMIN);
        News archived = article(NewsStatus.ARCHIVED);
        NewsRequest request = new NewsRequest();
        request.setTitle("Fixed by admin");
        request.setCategoryId(5L);

        service.update(NEWS_ID, request);

        assertThat(archived.getTitle()).isEqualTo("Fixed by admin");
        assertThat(archived.getStatus()).isEqualTo(NewsStatus.ARCHIVED);
    }

    @Test
    void author_cannotEditAPendingArticle_soTheEditorReviewsWhatWasSubmitted() {
        News news = article(NewsStatus.PENDING);
        NewsRequest request = new NewsRequest();
        request.setTitle("Sneaky change");
        request.setCategoryId(5L);

        assertFails(() -> service.update(NEWS_ID, request), HttpStatus.CONFLICT);

        assertThat(news.getTitle()).isEqualTo("Title");
    }

    @Test
    void editor_listManaged_seesEveryAuthor() {
        loginAs(Roles.EDITOR);
        when(newsRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listManaged(NewsStatus.PENDING, 5L, null, 1, 10);

        verify(newsRepository).search(eq(5L), eq(null), eq(NewsStatus.PENDING), any(Pageable.class));
    }
}
