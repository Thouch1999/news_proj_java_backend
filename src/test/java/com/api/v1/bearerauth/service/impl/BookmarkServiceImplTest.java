package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.Bookmark;
import com.api.v1.bearerauth.model.entity.News;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.response.BookmarkDto;
import com.api.v1.bearerauth.model.response.PublicNewsSummaryDto;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.repository.BookmarkRepository;
import com.api.v1.bearerauth.repository.NewsRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.PublicNewsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceImplTest {
    private static final long ME = 1L;
    private static final long NEWS_ID = 10L;

    @Mock
    private BookmarkRepository bookmarkRepository;
    @Mock
    private NewsRepository newsRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PublicNewsService publicNewsService;

    private BookmarkServiceImpl service;
    private AppUser me;

    @BeforeEach
    void setUp() {
        service = new BookmarkServiceImpl(bookmarkRepository, newsRepository, appUserRepository, currentUserService, publicNewsService);
        loginAs(Roles.READER);
        lenient().when(appUserRepository.getReferenceById(ME)).thenAnswer(call -> me);
    }

    private void loginAs(Roles role) {
        me = new AppUser();
        me.setId(ME);
        me.setRole(role);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(me);
    }

    private News publishedNews(long id) {
        News news = new News();
        news.setId(id);
        news.setTitle("Title " + id);
        when(newsRepository.findPublishedById(id)).thenReturn(Optional.of(news));
        return news;
    }

    // ---------------- add ----------------

    @Test
    void add_savesABookmarkForTheLoggedInUser_andReportsItWasCreated() {
        News news = publishedNews(NEWS_ID);
        when(bookmarkRepository.existsByUserIdAndNewsId(ME, NEWS_ID)).thenReturn(false);

        assertThat(service.add(NEWS_ID)).isTrue();

        ArgumentCaptor<Bookmark> saved = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(me);
        assertThat(saved.getValue().getNews()).isSameAs(news);
    }

    @Test
    void add_worksForEveryRole() {
        for (Roles role : Roles.values()) {
            loginAs(role);
            publishedNews(NEWS_ID);
            when(bookmarkRepository.existsByUserIdAndNewsId(ME, NEWS_ID)).thenReturn(false);

            assertThat(service.add(NEWS_ID)).isTrue();
        }
    }

    @Test
    void add_twice_doesNotCreateADuplicate() {
        publishedNews(NEWS_ID);
        when(bookmarkRepository.existsByUserIdAndNewsId(ME, NEWS_ID)).thenReturn(true);

        assertThat(service.add(NEWS_ID)).isFalse();

        verify(bookmarkRepository, never()).saveAndFlush(any());
        verify(bookmarkRepository, never()).save(any());
    }

    @Test
    void add_whenTwoRequestsRace_theUniqueConstraintMeansAlreadySaved_notAnError() {
        publishedNews(NEWS_ID);
        when(bookmarkRepository.existsByUserIdAndNewsId(ME, NEWS_ID)).thenReturn(false);
        when(bookmarkRepository.saveAndFlush(any(Bookmark.class))).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThat(service.add(NEWS_ID)).isFalse();
    }

    @Test
    void add_anArticleThatIsNotPublishedOrUnknown_isNotFound() {
        // findPublishedById only finds PUBLISHED articles of an active category: a draft, a rejected one, an
        // archived one, one in a hidden category and an unknown id are all "not found"
        assertThatThrownBy(() -> service.add(NEWS_ID))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    // ---------------- remove ----------------

    @Test
    void remove_deletesTheBookmarkOfTheLoggedInUserOnly() {
        service.remove(NEWS_ID);

        verify(bookmarkRepository).deleteByUserIdAndNewsId(ME, NEWS_ID);
    }

    @Test
    void remove_doesNotNeedThePublishedArticle_soAnArchivedOneCanBeUnsaved() {
        service.remove(NEWS_ID);

        verify(newsRepository, never()).findPublishedById(anyLong());
        verify(newsRepository, never()).findById(anyLong());
    }

    // ---------------- list ----------------

    private Bookmark bookmark(long newsId, LocalDateTime at) {
        News news = new News();
        news.setId(newsId);
        Bookmark bookmark = new Bookmark();
        bookmark.setNews(news);
        bookmark.setCreatedAt(at);
        return bookmark;
    }

    private PublicNewsSummaryDto card(long id) {
        PublicNewsSummaryDto card = new PublicNewsSummaryDto();
        card.setId(id);
        return card;
    }

    @Test
    void list_returnsTheSavedArticlesAsCards_withTheDateTheyWereSaved_newestFirst() {
        Bookmark newer = bookmark(2, LocalDateTime.of(2026, 3, 2, 8, 0));
        Bookmark older = bookmark(1, LocalDateTime.of(2026, 3, 1, 8, 0));
        when(bookmarkRepository.findReadableByUserId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(newer, older)));
        when(publicNewsService.toCards(any())).thenReturn(List.of(card(2), card(1)));

        Page<BookmarkDto> page = service.list(1, 10);

        assertThat(page.getContent()).extracting(item -> item.getNews().getId()).containsExactly(2L, 1L);
        assertThat(page.getContent().get(0).getBookmarkedAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 8, 0));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookmarkRepository).findReadableByUserId(org.mockito.ArgumentMatchers.eq(ME), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        // the cards of the whole page come from one call (one tag query, one comment-count query)
        verify(publicNewsService).toCards(List.of(newer.getNews(), older.getNews()));
    }

    @Test
    void list_pageAndSizeAreKeptInRange() {
        when(bookmarkRepository.findReadableByUserId(any(), any(Pageable.class))).thenReturn(Page.empty());
        when(publicNewsService.toCards(any())).thenReturn(List.of());

        service.list(0, 1000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookmarkRepository).findReadableByUserId(any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void list_keepsTheTotalForPagination() {
        Pageable any = org.springframework.data.domain.PageRequest.of(0, 1);
        when(bookmarkRepository.findReadableByUserId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bookmark(1, LocalDateTime.now())), any, 7));
        when(publicNewsService.toCards(any())).thenReturn(List.of(card(1)));

        Page<BookmarkDto> page = service.list(1, 1);

        assertThat(page.getTotalElements()).isEqualTo(7);
        assertThat(page.getTotalPages()).isEqualTo(7);
    }
}
