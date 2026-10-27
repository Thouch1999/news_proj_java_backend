package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.Comment;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.enumerate.CommentStatus;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.enumerate.RateLimitAction;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.request.CommentRequest;
import com.api.v1.newssystem.model.response.CommentDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.CommentRepository;
import com.api.v1.newssystem.repository.NewsRepository;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.service.ActivityLogService;
import com.api.v1.newssystem.service.RateLimitService;
import com.api.v1.newssystem.service.SystemSettingService;
import com.api.v1.newssystem.util.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The comment protection: links, duplicates, new accounts, blocked words (English and Khmer), the honeypot and the rate limits. */
@ExtendWith(MockitoExtension.class)
class CommentProtectionTest {
    private static final long NEWS_ID = 10L;
    private static final long ME = 1L;

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private NewsRepository newsRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private SystemSettingService systemSettingService;

    private final MutableClock clock = new MutableClock();
    private CommentServiceImpl service;
    private AppUser me;

    @BeforeEach
    void setUp() {
        service = new CommentServiceImpl(commentRepository, newsRepository, appUserRepository, currentUserService, eventPublisher,
                activityLogService, rateLimitService, systemSettingService, new AppProperty(), clock);
        me = user(ME, Roles.READER);
        me.setCreatedAt(LocalDateTime.of(2026, 5, 1, 8, 0)); // an old account
        lenient().when(currentUserService.getCurrentUser()).thenAnswer(call -> me);
        lenient().when(appUserRepository.getReferenceById(ME)).thenAnswer(call -> me);
        lenient().when(commentRepository.save(any(Comment.class))).thenAnswer(call -> {
            Comment comment = call.getArgument(0);
            if (comment.getId() == null) {
                comment.setId(99L);
            }
            return comment;
        });
        News news = new News();
        news.setId(NEWS_ID);
        news.setTitle("Title");
        news.setSlug("title");
        news.setStatus(NewsStatus.PUBLISHED);
        Category category = new Category();
        category.setActive(true);
        news.setCategory(category);
        news.setCommentsEnabled(true);
        news.setAuthor(user(50L, Roles.AUTHOR));
        lenient().when(newsRepository.findById(NEWS_ID)).thenReturn(Optional.of(news));
    }

    private AppUser user(long id, Roles role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        user.setUsername("user" + id);
        return user;
    }

    private CommentRequest request(String content) {
        CommentRequest request = new CommentRequest();
        request.setContent(content);
        return request;
    }

    private void assertRejected(String content, MessageConstant expected) {
        assertThatThrownBy(() -> service.create(NEWS_ID, request(content)))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(expected.getStatus());
                    assertThat(e.getErrorResponse().getError()).isEqualTo(expected.getCode());
                });
        verify(commentRepository, never()).save(any());
    }

    // ---------------- links ----------------

    @Test
    void twoLinksAreAllowed_threeAreRejected() {
        assertThatCode(() -> service.create(NEWS_ID, request("see https://a.example/x and www.b.example"))).doesNotThrowAnyException();
        clearInvocations(commentRepository);

        assertRejected("https://a.example https://b.example http://c.example", MessageConstant.COMMENT_TOO_MANY_LINKS);
    }

    @Test
    void bareDomainsAndShortLinksCountAsLinks() {
        assertRejected("win at casino.com, shop.net/deal or bit.ly/abc", MessageConstant.COMMENT_TOO_MANY_LINKS);
    }

    @Test
    void aSentenceWithoutLinksIsNotAffected() {
        assertThatCode(() -> service.create(NEWS_ID, request("I read this at 5.30 pm. Good article, e.g. the second part.")))
                .doesNotThrowAnyException();
    }

    // ---------------- duplicate ----------------

    @Test
    void aCommentIdenticalToThePreviousOneIsRejected_ignoringCaseAndSpaces() {
        Comment previous = new Comment();
        previous.setContent("Great   article");
        when(commentRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(ME)).thenReturn(Optional.of(previous));

        assertRejected("great article ", MessageConstant.COMMENT_DUPLICATE);
    }

    @Test
    void aDifferentCommentIsNotADuplicate() {
        Comment previous = new Comment();
        previous.setContent("Great article");
        when(commentRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(ME)).thenReturn(Optional.of(previous));

        assertThatCode(() -> service.create(NEWS_ID, request("Great article, thanks"))).doesNotThrowAnyException();
    }

    // ---------------- new account ----------------

    @Test
    void anAccountYoungerThanTenMinutesCannotComment_andIsToldHowLongToWait() {
        me.setCreatedAt(LocalDateTime.now(clock).minusMinutes(9));

        assertThatThrownBy(() -> service.create(NEWS_ID, request("Hello")))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getErrorResponse().getError()).isEqualTo("ACCOUNT_TOO_NEW");
                    assertThat(e.getErrorResponse().getRetryAfterSeconds()).isEqualTo(60);
                });
        verify(commentRepository, never()).save(any());
    }

    @Test
    void anAccountOfTenMinutesCanComment() {
        me.setCreatedAt(LocalDateTime.now(clock).minusMinutes(10));

        assertThatCode(() -> service.create(NEWS_ID, request("Hello"))).doesNotThrowAnyException();
    }

    @Test
    void theAccountBecomesOldEnoughWhenTheClockMoves() {
        me.setCreatedAt(LocalDateTime.now(clock).minusMinutes(5));
        assertRejected("Hello", MessageConstant.ACCOUNT_TOO_NEW);

        clock.advance(java.time.Duration.ofMinutes(5));

        assertThatCode(() -> service.create(NEWS_ID, request("Hello"))).doesNotThrowAnyException();
    }

    // ---------------- blocked words ----------------

    @Test
    void aBlockedWordHoldsTheCommentForReview_withoutRefusingIt() {
        when(systemSettingService.getCommentBlockedWords()).thenReturn(List.of("free money", "casino"));

        CommentDto dto = service.create(NEWS_ID, request("Click here for FREE   Money now"));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(saved.getValue().getModerationNote()).isEqualTo("Contains a blocked word: free money");
        assertThat(dto.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(dto.getMessage()).isEqualTo("Your comment is waiting for review.");
        assertThat(dto.getModerationNote()).isNull(); // the commenter does not learn the word
        verify(eventPublisher, never()).publishEvent(any(Object.class)); // nobody is notified of a hidden comment
    }

    @Test
    void aCommentWithoutABlockedWordIsVisibleAndNotifies() {
        when(systemSettingService.getCommentBlockedWords()).thenReturn(List.of("casino"));

        CommentDto dto = service.create(NEWS_ID, request("A fine article"));

        assertThat(dto.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(dto.getMessage()).isNull();
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void aKhmerBlockedWordWorks_evenWithZeroWidthSpacesInTheComment() {
        // "ល្បែង" (gambling) is the blocked word; a comment in Khmer has zero-width spaces between its words
        when(systemSettingService.getCommentBlockedWords()).thenReturn(List.of("ល្បែង", "ឥតគិតថ្លៃ"));

        CommentDto dto = service.create(NEWS_ID, request("សូមចូលលេង​ល្បែង​នៅទីនេះ"));

        assertThat(dto.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(saved.capture());
        assertThat(saved.getValue().getModerationNote()).isEqualTo("Contains a blocked word: ល្បែង");
    }

    @Test
    void aKhmerPhraseWithAZeroWidthSpaceInTheCommentButNotInTheSettingStillMatches() {
        when(systemSettingService.getCommentBlockedWords()).thenReturn(List.of("ចាក់ឆ្នោតឥតគិតថ្លៃ"));

        CommentDto dto = service.create(NEWS_ID, request("ចាក់ឆ្នោត​ឥតគិតថ្លៃ ឥឡូវនេះ"));

        assertThat(dto.getStatus()).isEqualTo(CommentStatus.HIDDEN);
    }

    @Test
    void aKhmerCommentWithoutABlockedWordStaysVisible() {
        when(systemSettingService.getCommentBlockedWords()).thenReturn(List.of("ល្បែង"));

        assertThat(service.create(NEWS_ID, request("អត្ថបទល្អណាស់ អរគុណ")).getStatus()).isEqualTo(CommentStatus.VISIBLE);
    }

    @Test
    void moderatorsSeeTheModerationNote_thePublicDoesNot() {
        AppUser editor = user(2L, Roles.EDITOR);
        me = editor;
        Comment held = new Comment();
        held.setId(7L);
        held.setNews(newsRepository.findById(NEWS_ID).orElseThrow());
        held.setUser(user(3L, Roles.READER));
        held.setContent("casino");
        held.setStatus(CommentStatus.HIDDEN);
        held.setModerationNote("Contains a blocked word: casino");
        when(commentRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(held)));
        when(commentRepository.findByNewsIdAndStatus(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(held)));

        assertThat(service.listAll(null, null, null, 1, 10).getContent().get(0).getModerationNote())
                .isEqualTo("Contains a blocked word: casino");
        assertThat(service.listVisible(NEWS_ID, 1, 10).getContent().get(0).getModerationNote()).isNull();
    }

    // ---------------- honeypot ----------------

    @Test
    void aFilledHoneypotPretendsSuccess_butSavesAndNotifiesNothing() {
        CommentRequest bot = request("Buy cheap watches");
        bot.setWebsite("http://spam.example");

        CommentDto dto = service.create(NEWS_ID, bot);

        assertThat(dto.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(dto.getContent()).isEqualTo("Buy cheap watches");
        assertThat(dto.getId()).isNotNull();
        verify(commentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(rateLimitService, never()).checkUser(any(), anyLong());
    }

    @Test
    void aBlankHoneypotIsAnHonestRequest() {
        CommentRequest person = request("Hello");
        person.setWebsite("   ");

        service.create(NEWS_ID, person);

        verify(commentRepository).save(any(Comment.class));
    }

    // ---------------- rate limit ----------------

    @Test
    void theCommentIsCountedPerUser_perMinuteAndPerDay_beforeAnythingElse() {
        service.create(NEWS_ID, request("Hello"));

        InOrder order = inOrder(rateLimitService, commentRepository);
        order.verify(rateLimitService).checkUser(RateLimitAction.COMMENT_PER_MINUTE, ME);
        order.verify(rateLimitService).checkUser(RateLimitAction.COMMENT_PER_DAY, ME);
        order.verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void aRateLimitedUserSavesNothing() {
        doThrow(new CustomException(MessageConstant.RATE_LIMITED, 30))
                .when(rateLimitService).checkUser(RateLimitAction.COMMENT_PER_MINUTE, ME);

        assertRejected("Hello", MessageConstant.RATE_LIMITED);
    }
}
