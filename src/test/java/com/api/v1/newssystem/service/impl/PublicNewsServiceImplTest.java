package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.entity.NewsTag;
import com.api.v1.newssystem.model.entity.NewsView;
import com.api.v1.newssystem.model.entity.Tag;
import com.api.v1.newssystem.model.enumerate.CommentStatus;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.response.PublicCategorySectionDto;
import com.api.v1.newssystem.model.response.PublicNewsDto;
import com.api.v1.newssystem.model.response.PublicNewsSummaryDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.BookmarkRepository;
import com.api.v1.newssystem.repository.CategoryRepository;
import com.api.v1.newssystem.repository.CommentRepository;
import com.api.v1.newssystem.repository.NewsImageRepository;
import com.api.v1.newssystem.repository.NewsRepository;
import com.api.v1.newssystem.repository.NewsTagRepository;
import com.api.v1.newssystem.repository.NewsViewRepository;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.service.SystemSettingService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicNewsServiceImplTest {
    private static final String IP = "203.0.113.7";

    @Mock
    private NewsRepository newsRepository;
    @Mock
    private NewsTagRepository newsTagRepository;
    @Mock
    private NewsImageRepository newsImageRepository;
    @Mock
    private NewsViewRepository newsViewRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private BookmarkRepository bookmarkRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private SystemSettingService systemSettingService;
    @Mock
    private com.api.v1.newssystem.service.RateLimitService rateLimitService;

    private PublicNewsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PublicNewsServiceImpl(newsRepository, newsTagRepository, newsImageRepository, newsViewRepository,
                commentRepository, bookmarkRepository, categoryRepository, appUserRepository, currentUserService, systemSettingService, rateLimitService);
        lenient().when(currentUserService.findCurrentUser()).thenReturn(Optional.empty()); // a visitor
    }

    private Category category(long id, String name, String slug) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug(slug);
        category.setActive(true);
        return category;
    }

    private News news(long id, Category category) {
        AppUser author = new AppUser();
        author.setId(50L);
        author.setFullName("Sok Dara");
        News news = new News();
        news.setId(id);
        news.setTitle("Title " + id);
        news.setSlug("title-" + id);
        news.setContent("<p>Content</p>");
        news.setStatus(NewsStatus.PUBLISHED);
        news.setAuthor(author);
        news.setCategory(category);
        news.setPublishedAt(LocalDateTime.of(2026, 3, (int) id, 9, 0));
        return news;
    }

    private NewsTag link(News news, long tagId, String name) {
        Tag tag = new Tag();
        tag.setId(tagId);
        tag.setName(name);
        tag.setSlug(name.toLowerCase());
        NewsTag link = new NewsTag();
        link.setNews(news);
        link.setTag(tag);
        return link;
    }

    private void assertFails(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(status));
    }

    // ---------------- list and search ----------------

    @Test
    void list_isNewestFirst_andThePageAndSizeAreKeptInRange() {
        when(newsRepository.searchPublished(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, null, null, 0, 1000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsRepository).searchPublished(any(), any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("publishedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_withoutSize_usesTheNewsPerPageOfTheSettings() {
        when(newsRepository.searchPublished(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        when(systemSettingService.getNewsPerPage()).thenReturn(20);

        service.list(null, null, null, null, null, 1, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsRepository).searchPublished(any(), any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void list_withASize_ignoresTheNewsPerPageOfTheSettings() {
        when(newsRepository.searchPublished(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, null, null, 1, 8);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsRepository).searchPublished(any(), any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(8);
        verify(systemSettingService, org.mockito.Mockito.never()).getNewsPerPage();
    }

    @Test
    void list_noFilters_passesNulls_andBlankFiltersCountAsNone() {
        when(newsRepository.searchPublished(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list("   ", "  ", "", null, null, 1, 10);

        verify(newsRepository).searchPublished(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void list_keyword_isLowerCasedAndWrappedForASubstringSearch() {
        when(newsRepository.searchPublished(any(), any(), anyString(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list("  Budget 2026 ", null, null, null, null, 1, 10);

        verify(newsRepository).searchPublished(isNull(), isNull(), eq("%budget 2026%"), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void list_keyword_withLikeWildcards_isMatchedLiterally() {
        when(newsRepository.searchPublished(any(), any(), anyString(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list("50%_off!", null, null, null, null, 1, 10);

        verify(newsRepository).searchPublished(isNull(), isNull(), eq("%50!%!_off!!%"), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void list_khmerKeyword_goesToTheQueryUnchanged() {
        when(newsRepository.searchPublished(any(), any(), anyString(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list("ព័ត៌មានកីឡា", null, null, null, null, 1, 10);

        verify(newsRepository).searchPublished(isNull(), isNull(), eq("%ព័ត៌មានកីឡា%"), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void list_keywordLongerThan100Characters_isABadRequest() {
        assertFails(() -> service.list("a".repeat(101), null, null, null, null, 1, 10), HttpStatus.BAD_REQUEST);

        verify(newsRepository, never()).searchPublished(any(), any(), any(), any(), any(), any());
    }

    @Test
    void list_categoryTagAndDateRange_areForwarded_withTheLastDayIncluded() {
        when(newsRepository.searchPublished(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, "sports", "football", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 10), 1, 10);

        verify(newsRepository).searchPublished(eq("sports"), eq("football"), isNull(),
                eq(LocalDateTime.of(2026, 1, 5, 0, 0)), eq(LocalDateTime.of(2026, 1, 11, 0, 0)), any(Pageable.class));
    }

    @Test
    void list_fromAfterTo_isABadRequest() {
        assertFails(() -> service.list(null, null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), 1, 10), HttpStatus.BAD_REQUEST);

        verify(newsRepository, never()).searchPublished(any(), any(), any(), any(), any(), any());
    }

    @Test
    void list_loadsTagsAndCommentCountsOnceForTheWholePage_notPerArticle() {
        Category sports = category(1, "Sports", "sports");
        News first = news(1, sports);
        News second = news(2, sports);
        News third = news(3, sports);
        first.setViewCount(7);
        when(newsRepository.searchPublished(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(third, second, first)));
        when(newsTagRepository.findAllWithTagByNewsIdIn(anyCollection())).thenReturn(List.of(link(first, 10, "Football"), link(first, 11, "Cup"), link(third, 10, "Football")));
        when(commentRepository.countByNewsIds(anyCollection(), eq(CommentStatus.VISIBLE))).thenReturn(List.<Object[]>of(new Object[]{1L, 4L}));

        Page<PublicNewsSummaryDto> page = service.list(null, null, null, null, null, 1, 10);

        assertThat(page.getContent()).extracting(PublicNewsSummaryDto::getId).containsExactly(3L, 2L, 1L);
        PublicNewsSummaryDto card = page.getContent().get(2);
        assertThat(card.getTags()).extracting(tag -> tag.getName()).containsExactly("Football", "Cup");
        assertThat(card.getCommentCount()).isEqualTo(4);
        assertThat(card.getViewCount()).isEqualTo(7);
        assertThat(card.getCategorySlug()).isEqualTo("sports");
        assertThat(card.getAuthorName()).isEqualTo("Sok Dara");
        assertThat(page.getContent().get(1).getTags()).isEmpty();
        assertThat(page.getContent().get(1).getCommentCount()).isZero();
        verify(newsTagRepository, times(1)).findAllWithTagByNewsIdIn(anyCollection());
        verify(commentRepository, times(1)).countByNewsIds(anyCollection(), any());
        verify(newsTagRepository, never()).findAllByNewsId(anyLong());
        verify(commentRepository, never()).countByNewsIdAndStatus(anyLong(), any());
    }

    @Test
    void list_emptyResult_doesNotQueryTagsOrComments() {
        when(newsRepository.searchPublished(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        assertThat(service.list(null, null, null, null, null, 1, 10).getContent()).isEmpty();

        verify(newsTagRepository, never()).findAllWithTagByNewsIdIn(any());
        verify(commentRepository, never()).countByNewsIds(any(), any());
    }

    // ---------------- latest, most viewed, home sections ----------------

    @Test
    void latest_isNewestFirst_andTheLimitIsKeptInRange() {
        when(newsRepository.findPublished(any(Pageable.class))).thenReturn(List.of());

        service.latest(500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsRepository).findPublished(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("publishedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void mostViewed_ordersByViewCountThenNewest_andALimitBelowOneBecomesOne() {
        when(newsRepository.findPublished(any(Pageable.class))).thenReturn(List.of());

        service.mostViewed(0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsRepository).findPublished(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
        assertThat(pageable.getValue().getSort().stream().map(Sort.Order::getProperty)).containsExactly("viewCount", "publishedAt", "id");
        assertThat(pageable.getValue().getSort().stream().allMatch(Sort.Order::isDescending)).isTrue();
    }

    @Test
    void categorySections_groupsByCategory_inCategoryNameOrder_skippingCategoriesWithoutNews() {
        Category sports = category(1, "Sports", "sports");
        Category world = category(2, "World", "world");
        Category empty = category(3, "Zzz", "zzz");
        News a = news(1, sports);
        News b = news(2, world);
        News c = news(3, sports);
        when(newsRepository.findNewestIdsPerCategory(2)).thenReturn(List.of(1L, 2L, 3L));
        when(newsRepository.findAllWithAuthorAndCategoryByIdIn(anyCollection())).thenReturn(List.of(a, b, c));
        when(categoryRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(sports, world, empty));

        List<PublicCategorySectionDto> sections = service.categorySections(2);

        assertThat(sections).extracting(section -> section.getCategory().getSlug()).containsExactly("sports", "world");
        assertThat(sections.get(0).getNews()).extracting(PublicNewsSummaryDto::getId).containsExactly(3L, 1L); // newest first
        assertThat(sections.get(1).getNews()).extracting(PublicNewsSummaryDto::getId).containsExactly(2L);
        verify(newsTagRepository, times(1)).findAllWithTagByNewsIdIn(anyCollection());
    }

    @Test
    void categorySections_withoutNews_returnsNothing_andPerCategoryIsKeptInRange() {
        when(newsRepository.findNewestIdsPerCategory(10)).thenReturn(List.of());

        assertThat(service.categorySections(99)).isEmpty();

        verify(newsRepository, never()).findAllWithAuthorAndCategoryByIdIn(any());
    }

    // ---------------- detail ----------------

    private News published(long id) {
        News news = news(id, category(1, "Sports", "sports"));
        news.setViewCount(10);
        when(newsRepository.findPublishedBySlug("title-" + id)).thenReturn(Optional.of(news));
        return news;
    }

    @Test
    void detail_unknownOrNotPublishedSlug_isNotFound_andNothingIsRecorded() {
        // the query only finds PUBLISHED articles of an active category: a draft, a rejected one, ... is "not found"
        assertFails(() -> service.getBySlug("draft-article", IP), HttpStatus.NOT_FOUND);

        verify(newsViewRepository, never()).save(any());
        verify(newsRepository, never()).incrementViewCount(anyLong());
    }

    @Test
    void detail_hasCategoryTagsAuthorImagesAndCounts() {
        News news = published(5);
        news.setSummary("Short summary");
        news.setThumbnail("/uploads/t.png");
        when(newsTagRepository.findAllWithTagByNewsIdIn(anyCollection())).thenReturn(List.of(link(news, 10, "Football")));
        com.api.v1.newssystem.model.entity.NewsImage image = new com.api.v1.newssystem.model.entity.NewsImage();
        image.setId(1L);
        image.setUrl("/uploads/a.png");
        image.setCaption("Cap");
        image.setSortOrder(0);
        when(newsImageRepository.findAllByNewsIdOrderBySortOrderAsc(5L)).thenReturn(List.of(image));
        when(commentRepository.countByNewsIdAndStatus(5L, CommentStatus.VISIBLE)).thenReturn(3L);

        PublicNewsDto dto = service.getBySlug("title-5", IP);

        assertThat(dto.getSlug()).isEqualTo("title-5");
        assertThat(dto.getSummary()).isEqualTo("Short summary");
        assertThat(dto.getContent()).isEqualTo("<p>Content</p>");
        assertThat(dto.getAuthorName()).isEqualTo("Sok Dara");
        assertThat(dto.getCategory().getSlug()).isEqualTo("sports");
        assertThat(dto.getTags()).extracting(tag -> tag.getSlug()).containsExactly("football");
        assertThat(dto.getImages()).extracting(i -> i.getUrl()).containsExactly("/uploads/a.png");
        assertThat(dto.getCommentCount()).isEqualTo(3);
        assertThat(dto.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 3, 5, 9, 0));
    }

    // ---------------- bookmarked flag ----------------

    @Test
    void detail_forAVisitor_hasNoBookmarkedFlag_andNothingIsLookedUp() {
        published(5);

        assertThat(service.getBySlug("title-5", IP).getBookmarked()).isNull();

        verify(bookmarkRepository, never()).existsByUserIdAndNewsId(anyLong(), anyLong());
    }

    @Test
    void detail_forALoggedInUser_saysWhetherTheyBookmarkedIt() {
        published(5);
        AppUser reader = new AppUser();
        reader.setId(77L);
        when(currentUserService.findCurrentUser()).thenReturn(Optional.of(reader));
        when(appUserRepository.getReferenceById(77L)).thenReturn(reader);
        when(bookmarkRepository.existsByUserIdAndNewsId(77L, 5L)).thenReturn(true, false);

        assertThat(service.getBySlug("title-5", IP).getBookmarked()).isTrue();
        assertThat(service.getBySlug("title-5", IP).getBookmarked()).isFalse();
    }

    @Test
    void toCards_buildsTheCardsOfLoadedArticles_withOneTagAndCommentQuery_keepingTheOrder() {
        Category sports = category(1, "Sports", "sports");
        News a = news(1, sports);
        News b = news(2, sports);

        List<PublicNewsSummaryDto> cards = service.toCards(List.of(b, a));

        assertThat(cards).extracting(PublicNewsSummaryDto::getId).containsExactly(2L, 1L);
        verify(newsTagRepository, times(1)).findAllWithTagByNewsIdIn(anyCollection());
        verify(commentRepository, times(1)).countByNewsIds(anyCollection(), any());
        assertThat(service.toCards(List.of())).isEmpty();
    }

    // ---------------- views: once per visitor address or user in 24 hours ----------------

    @Test
    void view_aVisitorsFirstOpen_isRecorded_andTheCountGoesUp() {
        published(5);
        when(newsViewRepository.existsByNewsIdAndIpAndViewedAtAfter(eq(5L), eq(IP), any(LocalDateTime.class))).thenReturn(false);

        PublicNewsDto dto = service.getBySlug("title-5", IP);

        ArgumentCaptor<NewsView> saved = ArgumentCaptor.forClass(NewsView.class);
        verify(newsViewRepository).save(saved.capture());
        assertThat(saved.getValue().getIp()).isEqualTo(IP);
        assertThat(saved.getValue().getUser()).isNull();
        assertThat(saved.getValue().getNews().getId()).isEqualTo(5L);
        verify(newsRepository).incrementViewCount(5L);
        assertThat(dto.getViewCount()).isEqualTo(11);
    }

    @Test
    void view_theSameAddressWithin24Hours_isNotCountedAgain() {
        published(5);
        when(newsViewRepository.existsByNewsIdAndIpAndViewedAtAfter(eq(5L), eq(IP), any(LocalDateTime.class))).thenReturn(true);

        PublicNewsDto dto = service.getBySlug("title-5", IP);

        verify(newsViewRepository, never()).save(any());
        verify(newsRepository, never()).incrementViewCount(anyLong());
        assertThat(dto.getViewCount()).isEqualTo(10);
    }

    @Test
    void view_theWindowIs24Hours() {
        published(5);
        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        LocalDateTime before = LocalDateTime.now();

        service.getBySlug("title-5", IP);

        verify(newsViewRepository).existsByNewsIdAndIpAndViewedAtAfter(eq(5L), eq(IP), since.capture());
        assertThat(since.getValue()).isBetween(before.minusHours(24).minusSeconds(1), LocalDateTime.now().minusHours(24).plusSeconds(1));
    }

    @Test
    void view_aLoggedInUser_isCountedOncePerUser_notPerAddress() {
        published(5);
        AppUser reader = new AppUser();
        reader.setId(77L);
        when(currentUserService.findCurrentUser()).thenReturn(Optional.of(reader));
        when(appUserRepository.getReferenceById(77L)).thenReturn(reader);
        when(newsViewRepository.existsByNewsIdAndUserIdAndViewedAtAfter(eq(5L), eq(77L), any(LocalDateTime.class))).thenReturn(false);

        assertThat(service.getBySlug("title-5", IP).getViewCount()).isEqualTo(11);

        ArgumentCaptor<NewsView> saved = ArgumentCaptor.forClass(NewsView.class);
        verify(newsViewRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(reader);
        verify(newsViewRepository, never()).existsByNewsIdAndIpAndViewedAtAfter(anyLong(), anyString(), any());

        // the same user again, from another address
        when(newsViewRepository.existsByNewsIdAndUserIdAndViewedAtAfter(eq(5L), eq(77L), any(LocalDateTime.class))).thenReturn(true);
        assertThat(service.getBySlug("title-5", "198.51.100.1").getViewCount()).isEqualTo(10);
        verify(newsViewRepository, times(1)).save(any());
    }

    @Test
    void view_aVisitorWithoutAnAddress_isNotCounted() {
        published(5);

        assertThat(service.getBySlug("title-5", null).getViewCount()).isEqualTo(10);
        assertThat(service.getBySlug("title-5", "  ").getViewCount()).isEqualTo(10);

        verify(newsViewRepository, never()).save(any());
        verify(newsRepository, never()).incrementViewCount(anyLong());
    }

    @Test
    void view_aTooLongAddress_isCutToTheColumnLength() {
        published(5);

        service.getBySlug("title-5", "x".repeat(80));

        ArgumentCaptor<NewsView> saved = ArgumentCaptor.forClass(NewsView.class);
        verify(newsViewRepository).save(saved.capture());
        assertThat(saved.getValue().getIp()).hasSize(45);
    }

    // ---------------- rate limit of the search ----------------

    @Test
    void list_withAKeyword_countsOneSearchForTheIp_butAPlainListDoesNot() {
        when(newsRepository.searchPublished(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, null, null, 1, 10);
        service.list("   ", null, null, null, null, 1, 10);
        org.mockito.Mockito.verify(rateLimitService, org.mockito.Mockito.never()).checkIp(any());

        service.list("ព័ត៌មាន", null, null, null, null, 1, 10);
        org.mockito.Mockito.verify(rateLimitService).checkIp(com.api.v1.newssystem.model.enumerate.RateLimitAction.SEARCH);
    }

    @Test
    void list_whenTheSearchIsRateLimited_noQueryIsRun() {
        org.mockito.Mockito.doThrow(new com.api.v1.newssystem.common.exception.CustomException(
                        com.api.v1.newssystem.common.constant.MessageConstant.RATE_LIMITED, 30))
                .when(rateLimitService).checkIp(com.api.v1.newssystem.model.enumerate.RateLimitAction.SEARCH);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.list("news", null, null, null, null, 1, 10))
                .isInstanceOf(com.api.v1.newssystem.common.exception.CustomException.class);

        org.mockito.Mockito.verify(newsRepository, org.mockito.Mockito.never()).searchPublished(any(), any(), any(), any(), any(), any());
    }
}
