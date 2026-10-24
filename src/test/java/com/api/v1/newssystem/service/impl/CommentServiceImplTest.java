package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.enumerate.NotificationType;
import com.api.v1.newssystem.service.NotificationEvent;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.Comment;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.enumerate.CommentStatus;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.request.CommentRequest;
import com.api.v1.newssystem.model.response.CommentDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.CommentRepository;
import com.api.v1.newssystem.repository.NewsRepository;
import com.api.v1.newssystem.security.CurrentUserService;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {
    private static final long NEWS_ID = 10L;
    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final long AUTHOR = 50L;

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private NewsRepository newsRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private CommentServiceImpl service;
    private AppUser me;

    @Mock
    private com.api.v1.newssystem.service.ActivityLogService activityLogService;

    @BeforeEach
    void setUp() {
        service = new CommentServiceImpl(commentRepository, newsRepository, appUserRepository, currentUserService, eventPublisher, activityLogService);
        loginAs(Roles.READER);
        lenient().when(commentRepository.save(any(Comment.class))).thenAnswer(call -> {
            Comment comment = call.getArgument(0);
            if (comment.getId() == null) {
                comment.setId(99L);
            }
            return comment;
        });
        lenient().when(appUserRepository.getReferenceById(ME)).thenAnswer(call -> me);
    }

    private AppUser user(long id, Roles role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setUsername("user" + id);
        return user;
    }

    private void loginAs(Roles role) {
        me = user(ME, role);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(me);
    }

    private News article(NewsStatus status, boolean commentsEnabled, boolean categoryActive) {
        Category category = new Category();
        category.setId(5L);
        category.setActive(categoryActive);
        News news = new News();
        news.setId(NEWS_ID);
        news.setTitle("Title");
        news.setSlug("title");
        news.setStatus(status);
        news.setCategory(category);
        news.setCommentsEnabled(commentsEnabled);
        news.setAuthor(user(AUTHOR, Roles.AUTHOR));
        lenient().when(newsRepository.findById(NEWS_ID)).thenReturn(Optional.of(news));
        return news;
    }

    private News published() {
        return article(NewsStatus.PUBLISHED, true, true);
    }

    private Comment comment(long id, AppUser author, CommentStatus status) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setNews(published());
        comment.setUser(author);
        comment.setContent("Hello");
        comment.setStatus(status);
        comment.setCreatedAt(LocalDateTime.of(2026, 3, 1, 8, 0));
        lenient().when(commentRepository.findWithUserAndNewsById(id)).thenReturn(Optional.of(comment));
        return comment;
    }

    private CommentRequest request(String content) {
        CommentRequest request = new CommentRequest();
        request.setContent(content);
        return request;
    }

    private void assertFails(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(status));
    }

    // ---------------- public list ----------------

    @Test
    void listVisible_returnsOnlyVisibleComments_newestFirst_inRange() {
        published();
        Comment first = comment(1, user(OTHER, Roles.READER), CommentStatus.VISIBLE);
        when(commentRepository.findByNewsIdAndStatus(eq(NEWS_ID), eq(CommentStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first)));

        Page<CommentDto> page = service.listVisible(NEWS_ID, 0, 1000);

        assertThat(page.getContent()).hasSize(1);
        CommentDto dto = page.getContent().get(0);
        assertThat(dto.getContent()).isEqualTo("Hello");
        assertThat(dto.getUserName()).isEqualTo("user2");
        assertThat(dto.getNewsId()).isEqualTo(NEWS_ID);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findByNewsIdAndStatus(eq(NEWS_ID), eq(CommentStatus.VISIBLE), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listVisible_usesTheFullNameWhenThereIsOne() {
        published();
        AppUser named = user(OTHER, Roles.READER);
        named.setFullName("សុខ ដារា");
        Comment byNamed = comment(1, named, CommentStatus.VISIBLE);
        when(commentRepository.findByNewsIdAndStatus(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(byNamed)));

        assertThat(service.listVisible(NEWS_ID, 1, 10).getContent().get(0).getUserName()).isEqualTo("សុខ ដារា");
    }

    @Test
    void listVisible_ofAnArticleThatIsNotPublished_orInAHiddenCategory_orUnknown_isNotFound() {
        for (NewsStatus status : List.of(NewsStatus.DRAFT, NewsStatus.PENDING, NewsStatus.REJECTED, NewsStatus.ARCHIVED)) {
            article(status, true, true);
            assertFails(() -> service.listVisible(NEWS_ID, 1, 10), HttpStatus.NOT_FOUND);
        }
        article(NewsStatus.PUBLISHED, true, false);
        assertFails(() -> service.listVisible(NEWS_ID, 1, 10), HttpStatus.NOT_FOUND);
        assertFails(() -> service.listVisible(404L, 1, 10), HttpStatus.NOT_FOUND);

        verify(commentRepository, never()).findByNewsIdAndStatus(any(), any(), any());
    }

    @Test
    void listVisible_stillWorksWhenCommentsAreDisabled_soExistingCommentsStayReadable() {
        article(NewsStatus.PUBLISHED, false, true);
        when(commentRepository.findByNewsIdAndStatus(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        assertThat(service.listVisible(NEWS_ID, 1, 10).getContent()).isEmpty();
    }

    // ---------------- create ----------------

    @Test
    void create_savesAVisibleCommentByTheLoggedInUser_withTrimmedText() {
        News news = published();

        CommentDto dto = service.create(NEWS_ID, request("  សួស្តី  hello  "));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("សួស្តី  hello");
        assertThat(saved.getValue().getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(saved.getValue().getUser()).isSameAs(me);
        assertThat(saved.getValue().getNews()).isSameAs(news);
        assertThat(dto.getId()).isEqualTo(99L);
        assertThat(dto.getUserId()).isEqualTo(ME);
        assertThat(dto.getStatus()).isEqualTo(CommentStatus.VISIBLE);
    }

    @Test
    void create_worksForEveryLoggedInRole() {
        for (Roles role : Roles.values()) {
            loginAs(role);
            published();

            assertThat(service.create(NEWS_ID, request("Hi")).getUserId()).isEqualTo(ME);
        }
    }

    @Test
    void create_onAnArticleThatIsNotPublished_isNotFound() {
        for (NewsStatus status : List.of(NewsStatus.DRAFT, NewsStatus.PENDING, NewsStatus.REJECTED, NewsStatus.ARCHIVED)) {
            article(status, true, true);

            assertFails(() -> service.create(NEWS_ID, request("Hi")), HttpStatus.NOT_FOUND);
        }
        article(NewsStatus.PUBLISHED, true, false);
        assertFails(() -> service.create(NEWS_ID, request("Hi")), HttpStatus.NOT_FOUND);
        assertFails(() -> service.create(404L, request("Hi")), HttpStatus.NOT_FOUND);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void create_whenCommentsAreDisabled_isAConflict() {
        article(NewsStatus.PUBLISHED, false, true);

        assertFails(() -> service.create(NEWS_ID, request("Hi")), HttpStatus.CONFLICT);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void create_withBlankOrTooLongText_isABadRequest() {
        assertFails(() -> service.create(NEWS_ID, request(null)), HttpStatus.BAD_REQUEST);
        assertFails(() -> service.create(NEWS_ID, request("   ")), HttpStatus.BAD_REQUEST);
        assertFails(() -> service.create(NEWS_ID, request("a".repeat(2001))), HttpStatus.BAD_REQUEST);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void create_text_atTheLimit_isAccepted() {
        published();

        assertThat(service.create(NEWS_ID, request("a".repeat(2000))).getContent()).hasSize(2000);
    }

    // ---------------- delete ----------------

    @Test
    void delete_ownComment_works_forEveryRole_evenWhenHidden() {
        for (Roles role : Roles.values()) {
            loginAs(role);
            Comment own = comment(5, me, CommentStatus.HIDDEN);

            service.delete(5L);

            verify(commentRepository).delete(own);
            org.mockito.Mockito.clearInvocations(commentRepository);
        }
    }

    @Test
    void delete_anotherUsersComment_isForbidden_forAReaderAndAnAuthor() {
        for (Roles role : List.of(Roles.READER, Roles.AUTHOR)) {
            loginAs(role);
            comment(5, user(OTHER, Roles.READER), CommentStatus.VISIBLE);

            assertFails(() -> service.delete(5L), HttpStatus.FORBIDDEN);
        }
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void delete_anyComment_worksForAnEditorAndAnAdmin() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            loginAs(role);
            Comment others = comment(5, user(OTHER, Roles.READER), CommentStatus.HIDDEN);

            service.delete(5L);

            verify(commentRepository).delete(others);
            org.mockito.Mockito.clearInvocations(commentRepository);
        }
    }

    @Test
    void delete_unknownComment_isNotFound() {
        assertFails(() -> service.delete(404L), HttpStatus.NOT_FOUND);

        verify(commentRepository, never()).delete(any());
    }

    // ---------------- moderation list ----------------

    @Test
    void listAll_forEditorAndAdmin_passesTheFilters_newestFirst() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            loginAs(role);
            Comment hidden = comment(5, user(OTHER, Roles.READER), CommentStatus.HIDDEN);
            when(commentRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(hidden)));

            Page<CommentDto> page = service.listAll(CommentStatus.HIDDEN, NEWS_ID, OTHER, 1, 10);

            assertThat(page.getContent()).extracting(CommentDto::getStatus).containsExactly(CommentStatus.HIDDEN);
            assertThat(page.getContent().get(0).getNewsTitle()).isEqualTo("Title");
        }
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository, org.mockito.Mockito.atLeastOnce()).search(eq(CommentStatus.HIDDEN), eq(NEWS_ID), eq(OTHER), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listAll_withoutFilters_passesNulls() {
        loginAs(Roles.EDITOR);
        when(commentRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.listAll(null, null, null, 0, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).search(isNull(), isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void listAll_isForbidden_forReaderAndAuthor() {
        for (Roles role : List.of(Roles.READER, Roles.AUTHOR)) {
            loginAs(role);

            assertFails(() -> service.listAll(null, null, null, 1, 10), HttpStatus.FORBIDDEN);
        }
        verify(commentRepository, never()).search(any(), any(), any(), any());
    }

    // ---------------- hide / unhide ----------------

    @Test
    void hide_andUnhide_switchTheStatus_forEditorAndAdmin() {
        for (Roles role : List.of(Roles.EDITOR, Roles.ADMIN)) {
            loginAs(role);
            Comment comment = comment(5, user(OTHER, Roles.READER), CommentStatus.VISIBLE);

            assertThat(service.hide(5L).getStatus()).isEqualTo(CommentStatus.HIDDEN);
            assertThat(comment.getStatus()).isEqualTo(CommentStatus.HIDDEN);

            assertThat(service.unhide(5L).getStatus()).isEqualTo(CommentStatus.VISIBLE);
            assertThat(comment.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        }
    }

    @Test
    void hide_notifiesTheCommentsAuthor_butUnhideAndAnAlreadyHiddenCommentDoNot() {
        loginAs(Roles.EDITOR);
        comment(5, user(OTHER, Roles.READER), CommentStatus.VISIBLE);

        service.hide(5L);

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(NotificationType.COMMENT_HIDDEN);
        assertThat(event.getValue().recipientId()).isEqualTo(OTHER);
        assertThat(event.getValue().actorId()).isEqualTo(ME);
        assertThat(event.getValue().commentId()).isEqualTo(5L);

        org.mockito.Mockito.clearInvocations(eventPublisher);
        service.unhide(5L);
        service.hide(5L);
        service.hide(5L); // already hidden
        verify(eventPublisher, org.mockito.Mockito.times(1)).publishEvent(any(Object.class));
    }

    @Test
    void create_notifiesTheArticlesAuthor_withTheCommenterAsActor() {
        published();

        service.create(NEWS_ID, request("hello"));

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(NotificationType.NEW_COMMENT);
        assertThat(event.getValue().recipientId()).isEqualTo(AUTHOR);
        assertThat(event.getValue().actorId()).isEqualTo(ME);
        assertThat(event.getValue().newsId()).isEqualTo(NEWS_ID);
        assertThat(event.getValue().commentId()).isEqualTo(99L);
    }

    @Test
    void hide_anAlreadyHiddenComment_changesNothing() {
        loginAs(Roles.EDITOR);
        comment(5, user(OTHER, Roles.READER), CommentStatus.HIDDEN);

        assertThat(service.hide(5L).getStatus()).isEqualTo(CommentStatus.HIDDEN);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void hideAndUnhide_areForbidden_forReaderAndAuthor_evenForTheirOwnComment() {
        for (Roles role : List.of(Roles.READER, Roles.AUTHOR)) {
            loginAs(role);
            Comment own = comment(5, me, CommentStatus.VISIBLE);

            assertFails(() -> service.hide(5L), HttpStatus.FORBIDDEN);
            assertFails(() -> service.unhide(5L), HttpStatus.FORBIDDEN);

            assertThat(own.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        }
    }

    @Test
    void hide_unknownComment_isNotFound() {
        loginAs(Roles.ADMIN);

        assertFails(() -> service.hide(404L), HttpStatus.NOT_FOUND);
        assertFails(() -> service.unhide(404L), HttpStatus.NOT_FOUND);
    }
}
