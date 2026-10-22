package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.Category;
import com.api.v1.bearerauth.model.entity.News;
import com.api.v1.bearerauth.model.entity.NewsImage;
import com.api.v1.bearerauth.model.entity.NewsStatusHistory;
import com.api.v1.bearerauth.model.entity.NewsTag;
import com.api.v1.bearerauth.model.entity.Tag;
import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.request.NewsImageRequest;
import com.api.v1.bearerauth.model.request.NewsRequest;
import com.api.v1.bearerauth.model.response.NewsDto;
import com.api.v1.bearerauth.model.response.NewsImageDto;
import com.api.v1.bearerauth.model.response.NewsSummaryDto;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.repository.BookmarkRepository;
import com.api.v1.bearerauth.repository.CommentRepository;
import com.api.v1.bearerauth.repository.CategoryRepository;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceImplTest {
    private static final long ME = 1L;
    private static final long CATEGORY_ID = 5L;

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

    private final AppProperty appProperty = new AppProperty();
    private NewsServiceImpl service;
    private AppUser me;

    @Mock
    private com.api.v1.bearerauth.service.ActivityLogService activityLogService;

    @BeforeEach
    void setUp() {
        service = new NewsServiceImpl(newsRepository, newsTagRepository, newsImageRepository, newsStatusHistoryRepository,
                commentRepository, bookmarkRepository, newsViewRepository, categoryRepository, tagRepository, appUserRepository, currentUserService, fileStorageService, appProperty, systemSettingService, org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class), org.mockito.Mockito.mock(com.api.v1.bearerauth.service.PreviewService.class), activityLogService);
        lenient().when(systemSettingService.isCommentsEnabledDefault()).thenReturn(true);
        me = user(ME);
        me.setRole(Roles.AUTHOR);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(me);
    }

    private AppUser user(long id) {
        AppUser user = new AppUser();
        user.setId(id);
        return user;
    }

    private Category category(long id, boolean active) {
        Category category = new Category();
        category.setId(id);
        category.setName("Sports");
        category.setSlug("sports");
        category.setActive(active);
        return category;
    }

    private Tag tag(long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setSlug(name.toLowerCase());
        return tag;
    }

    private News article(long id, NewsStatus status) {
        News news = new News();
        news.setId(id);
        news.setTitle("Old title");
        news.setSlug("old-title");
        news.setContent("Old content");
        news.setStatus(status);
        news.setAuthor(me);
        news.setCategory(category(CATEGORY_ID, true));
        return news;
    }

    private NewsRequest request(String title) {
        NewsRequest request = new NewsRequest();
        request.setTitle(title);
        request.setCategoryId(CATEGORY_ID);
        return request;
    }

    private void ownArticle(News news) {
        when(newsRepository.findByIdAndAuthorId(news.getId(), ME)).thenReturn(Optional.of(news));
    }

    private void assertFails(Runnable call, MessageConstant expected, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(expected.getDefaultMessage());
                    assertThat(e.getHttpStatus()).isEqualTo(status);
                });
    }

    private void savingGivesAnId() {
        when(newsRepository.save(any(News.class))).thenAnswer(call -> {
            News news = call.getArgument(0);
            if (news.getId() == null) {
                news.setId(100L);
            }
            return news;
        });
    }

    private void createPrerequisites() {
        when(appUserRepository.getReferenceById(ME)).thenReturn(me);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category(CATEGORY_ID, true)));
        savingGivesAnId();
    }

    // ---------------- create ----------------

    @Test
    void create_savesADraftOwnedByTheCurrentAuthor_withSlugTagsAndHistory() {
        createPrerequisites();
        Tag politics = tag(7, "Politics");
        Tag economy = tag(8, "Economy");
        when(tagRepository.findAllById(anyCollection())).thenReturn(List.of(politics, economy));
        NewsRequest request = request("  My First Article ");
        request.setTagIds(List.of(7L, 8L));

        NewsDto created = service.create(request);

        ArgumentCaptor<News> saved = ArgumentCaptor.forClass(News.class);
        verify(newsRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(NewsStatus.DRAFT);
        assertThat(saved.getValue().getAuthor()).isSameAs(me);
        assertThat(saved.getValue().getTitle()).isEqualTo("My First Article");
        assertThat(saved.getValue().getSlug()).isEqualTo("my-first-article");
        assertThat(saved.getValue().getContent()).isEmpty();
        assertThat(saved.getValue().isCommentsEnabled()).isTrue();
        assertThat(created.getStatus()).isEqualTo(NewsStatus.DRAFT);
        assertThat(created.isEditable()).isTrue();

        ArgumentCaptor<List<NewsTag>> links = ArgumentCaptor.forClass(List.class);
        verify(newsTagRepository).saveAll(links.capture());
        assertThat(links.getValue()).extracting(link -> link.getTag().getId()).containsExactly(7L, 8L);

        ArgumentCaptor<NewsStatusHistory> history = ArgumentCaptor.forClass(NewsStatusHistory.class);
        verify(newsStatusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isNull();
        assertThat(history.getValue().getToStatus()).isEqualTo(NewsStatus.DRAFT);
        assertThat(history.getValue().getChangedBy()).isSameAs(me);
    }

    @Test
    void create_withoutCommentsChoice_usesTheCommentsEnabledDefaultOfTheSettings() {
        createPrerequisites();
        when(systemSettingService.isCommentsEnabledDefault()).thenReturn(false);

        service.create(request("Quiet article"));

        ArgumentCaptor<News> saved = ArgumentCaptor.forClass(News.class);
        verify(newsRepository).save(saved.capture());
        assertThat(saved.getValue().isCommentsEnabled()).isFalse();
    }

    @Test
    void create_withACommentsChoice_doesNotReadTheDefaultOfTheSettings() {
        createPrerequisites();
        NewsRequest request = request("Open article");
        request.setCommentsEnabled(true);

        service.create(request);

        ArgumentCaptor<News> saved = ArgumentCaptor.forClass(News.class);
        verify(newsRepository).save(saved.capture());
        assertThat(saved.getValue().isCommentsEnabled()).isTrue();
        verify(systemSettingService, org.mockito.Mockito.never()).isCommentsEnabledDefault();
    }

    @Test
    void create_makesTheSlugUnique_andNeverUsesTheReservedMineSlug() {
        createPrerequisites();
        when(newsRepository.existsBySlug("my-first-article")).thenReturn(true);

        assertThat(service.create(request("My First Article")).getSlug()).isEqualTo("my-first-article-2");
        assertThat(service.create(request("Mine")).getSlug()).isEqualTo("mine-2");
    }

    @Test
    void create_khmerTitleGetsNewsIdSlug() {
        createPrerequisites();

        NewsDto created = service.create(request("ព័ត៌មានថ្មី"));

        assertThat(created.getSlug()).isEqualTo("news-100");
        assertThat(created.getTitle()).isEqualTo("ព័ត៌មានថ្មី");
    }

    @Test
    void create_inactiveOrUnknownCategoryAndUnknownTag_areRejected() {
        when(appUserRepository.getReferenceById(ME)).thenReturn(me);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category(CATEGORY_ID, false)));
        assertFails(() -> service.create(request("A")), MessageConstant.CATEGORY_INACTIVE, HttpStatus.BAD_REQUEST);

        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());
        assertFails(() -> service.create(request("A")), MessageConstant.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);

        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category(CATEGORY_ID, true)));
        when(tagRepository.findAllById(anyCollection())).thenReturn(List.of(tag(7, "Politics")));
        NewsRequest request = request("A");
        request.setTagIds(List.of(7L, 99L));
        assertFails(() -> service.create(request), MessageConstant.TAG_NOT_FOUND, HttpStatus.NOT_FOUND);

        verify(newsRepository, never()).save(any());
    }

    // ---------------- update: ownership and editing rules ----------------

    @Test
    void update_ownDraftAndOwnRejected_areEditable_slugStatusAndReasonStay() {
        for (NewsStatus status : List.of(NewsStatus.DRAFT, NewsStatus.REJECTED)) {
            News news = article(10, status);
            news.setRejectionReason(status == NewsStatus.REJECTED ? "Too short" : null);
            ownArticle(news);
            when(newsRepository.save(news)).thenReturn(news);
            NewsRequest request = request("  New title ");
            request.setContent("New content");
            request.setSummary("  A summary ");
            request.setCommentsEnabled(false);

            NewsDto updated = service.update(10L, request);

            assertThat(updated.getTitle()).isEqualTo("New title");
            assertThat(updated.getContent()).isEqualTo("New content");
            assertThat(updated.getSummary()).isEqualTo("A summary");
            assertThat(updated.isCommentsEnabled()).isFalse();
            assertThat(updated.getSlug()).isEqualTo("old-title");
            assertThat(updated.getStatus()).isEqualTo(status);
            assertThat(updated.getRejectionReason()).isEqualTo(status == NewsStatus.REJECTED ? "Too short" : null);
        }
    }

    @Test
    void update_inEveryOtherStatus_isRejected() {
        for (NewsStatus status : List.of(NewsStatus.PENDING, NewsStatus.PUBLISHED, NewsStatus.ARCHIVED)) {
            News news = article(10, status);
            ownArticle(news);

            assertFails(() -> service.update(10L, request("Changed")), MessageConstant.NEWS_NOT_EDITABLE, HttpStatus.CONFLICT);

            assertThat(news.getTitle()).isEqualTo("Old title");
        }
        verify(newsRepository, never()).save(any());
    }

    @Test
    void update_anotherAuthorsArticle_isNotFound() {
        // the repository only finds articles of the given author, so article 20 (author 2) is not found for author 1
        assertFails(() -> service.update(20L, request("Hijacked")), MessageConstant.NEWS_NOT_FOUND, HttpStatus.NOT_FOUND);

        verify(newsRepository).findByIdAndAuthorId(20L, ME);
        verify(newsRepository, never()).save(any());
    }

    @Test
    void update_changingToAnInactiveCategoryIsRejected_butKeepingAnInactiveOneIsFine() {
        News news = article(10, NewsStatus.DRAFT);
        news.setCategory(category(CATEGORY_ID, false)); // deactivated after the article was written
        ownArticle(news);
        when(newsRepository.save(news)).thenReturn(news);

        assertThat(service.update(10L, request("Same category")).getTitle()).isEqualTo("Same category");

        NewsRequest move = request("Other category");
        move.setCategoryId(6L);
        when(categoryRepository.findById(6L)).thenReturn(Optional.of(category(6L, false)));
        assertFails(() -> service.update(10L, move), MessageConstant.CATEGORY_INACTIVE, HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_withoutTagIds_leavesTagsAlone_andANewListReplacesThem() {
        News news = article(10, NewsStatus.DRAFT);
        ownArticle(news);
        when(newsRepository.save(news)).thenReturn(news);

        service.update(10L, request("No tag change"));
        verify(newsTagRepository, never()).deleteAll(anyIterable());
        verify(newsTagRepository, never()).saveAll(anyIterable());

        Tag politics = tag(7, "Politics");
        Tag economy = tag(8, "Economy");
        Tag culture = tag(9, "Culture");
        NewsTag oldPolitics = link(news, politics);
        NewsTag oldEconomy = link(news, economy);
        when(newsTagRepository.findAllByNewsId(10L)).thenReturn(List.of(oldPolitics, oldEconomy));
        when(tagRepository.findAllById(anyCollection())).thenReturn(List.of(economy, culture));
        NewsRequest request = request("New tags");
        request.setTagIds(List.of(8L, 9L));

        service.update(10L, request);

        verify(newsTagRepository).deleteAll(List.of(oldPolitics));
        ArgumentCaptor<List<NewsTag>> added = ArgumentCaptor.forClass(List.class);
        verify(newsTagRepository).saveAll(added.capture());
        assertThat(added.getValue()).extracting(link -> link.getTag().getId()).containsExactly(9L);
    }

    private NewsTag link(News news, Tag tag) {
        NewsTag link = new NewsTag();
        link.setNews(news);
        link.setTag(tag);
        return link;
    }

    // ---------------- read ----------------

    @Test
    void getMine_anotherAuthorsArticle_isNotFound() {
        assertFails(() -> service.getMine(20L), MessageConstant.NEWS_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    void getMine_showsTheRejectionReason_andWhetherTheArticleIsEditable() {
        News rejected = article(10, NewsStatus.REJECTED);
        rejected.setRejectionReason("Please add sources");
        News published = article(11, NewsStatus.PUBLISHED);
        ownArticle(rejected);
        ownArticle(published);

        NewsDto first = service.getMine(10L);
        NewsDto second = service.getMine(11L);

        assertThat(first.getRejectionReason()).isEqualTo("Please add sources");
        assertThat(first.isEditable()).isTrue();
        assertThat(second.isEditable()).isFalse();
    }

    @Test
    void listMine_isScopedToTheCurrentAuthor_withStatusFilterAndClampedPaging() {
        News rejected = article(10, NewsStatus.REJECTED);
        rejected.setRejectionReason("Too short");
        when(newsRepository.search(eq(ME), isNull(), eq(NewsStatus.REJECTED), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(rejected)));

        Page<NewsSummaryDto> page = service.listMine(NewsStatus.REJECTED, 0, 1000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsRepository).search(eq(ME), isNull(), eq(NewsStatus.REJECTED), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getRejectionReason()).isEqualTo("Too short");
        assertThat(page.getContent().get(0).getCategoryName()).isEqualTo("Sports");
    }

    // ---------------- delete ----------------

    @Test
    void delete_ownDraft_removesChildrenThenTheArticle_andItsFiles() {
        News draft = article(10, NewsStatus.DRAFT);
        draft.setThumbnail("/uploads/thumb.png");
        NewsImage image = new NewsImage();
        image.setUrl("/uploads/a.png");
        ownArticle(draft);
        when(newsImageRepository.findAllByNewsIdOrderBySortOrderAsc(10L)).thenReturn(List.of(image));

        service.delete(10L);

        InOrder order = inOrder(newsTagRepository, newsImageRepository, newsStatusHistoryRepository, newsRepository);
        order.verify(newsTagRepository).deleteAllByNewsId(10L);
        order.verify(newsImageRepository).deleteAllByNewsId(10L);
        order.verify(newsStatusHistoryRepository).deleteAllByNewsId(10L);
        order.verify(newsRepository).delete(draft);
        verify(commentRepository).deleteAllByNewsId(10L);
        verify(bookmarkRepository).deleteAllByNewsId(10L);
        verify(newsViewRepository).deleteAllByNewsId(10L);
        verify(fileStorageService).delete("/uploads/a.png");
        verify(fileStorageService).delete("/uploads/thumb.png");
    }

    @Test
    void delete_anythingButADraft_isRejected() {
        for (NewsStatus status : List.of(NewsStatus.REJECTED, NewsStatus.PENDING, NewsStatus.PUBLISHED, NewsStatus.ARCHIVED)) {
            ownArticle(article(10, status));

            assertFails(() -> service.delete(10L), MessageConstant.NEWS_DELETE_ONLY_DRAFT, HttpStatus.CONFLICT);
        }
        verify(newsRepository, never()).delete(any());
    }

    @Test
    void delete_anotherAuthorsDraft_isNotFound() {
        assertFails(() -> service.delete(20L), MessageConstant.NEWS_NOT_FOUND, HttpStatus.NOT_FOUND);

        verify(newsRepository, never()).delete(any());
    }

    // ---------------- images and thumbnail ----------------

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    void addImage_toOwnDraftAndOwnRejected_storesTheFileAndAppendsTheImage() {
        when(newsImageRepository.countByNewsId(10L)).thenReturn(2L);
        when(fileStorageService.store(any())).thenReturn("/uploads/a.png");
        when(newsImageRepository.save(any(NewsImage.class))).thenAnswer(call -> call.getArgument(0));

        for (NewsStatus status : List.of(NewsStatus.DRAFT, NewsStatus.REJECTED)) {
            ownArticle(article(10, status));

            NewsImageDto added = service.addImage(10L, file(), "  A caption ");

            assertThat(added.getUrl()).isEqualTo("/uploads/a.png");
            assertThat(added.getCaption()).isEqualTo("A caption");
            assertThat(added.getSortOrder()).isEqualTo(2);
        }
    }

    @Test
    void addImage_rules_otherAuthorNotEditableLimitAndCaption() {
        // another author's article
        assertFails(() -> service.addImage(20L, file(), null), MessageConstant.NEWS_NOT_FOUND, HttpStatus.NOT_FOUND);

        // not editable
        ownArticle(article(11, NewsStatus.PENDING));
        assertFails(() -> service.addImage(11L, file(), null), MessageConstant.NEWS_NOT_EDITABLE, HttpStatus.CONFLICT);

        // image limit
        ownArticle(article(10, NewsStatus.DRAFT));
        when(newsImageRepository.countByNewsId(10L)).thenReturn((long) appProperty.getUpload().getMaxImagesPerNews());
        assertFails(() -> service.addImage(10L, file(), null), MessageConstant.NEWS_IMAGE_LIMIT, HttpStatus.CONFLICT);

        // caption too long
        assertFails(() -> service.addImage(10L, file(), "x".repeat(256)), MessageConstant.INVALID_PARAMETER, HttpStatus.BAD_REQUEST);

        verify(fileStorageService, never()).store(any());
    }

    @Test
    void updateImage_changesCaptionAndOrder_andAnImageOfAnotherArticleIsNotFound() {
        ownArticle(article(10, NewsStatus.DRAFT));
        NewsImage image = new NewsImage();
        image.setId(3L);
        image.setUrl("/uploads/a.png");
        when(newsImageRepository.findByIdAndNewsId(3L, 10L)).thenReturn(Optional.of(image));
        when(newsImageRepository.save(image)).thenReturn(image);
        NewsImageRequest request = new NewsImageRequest();
        request.setCaption("New caption");
        request.setSortOrder(5);

        NewsImageDto updated = service.updateImage(10L, 3L, request);

        assertThat(updated.getCaption()).isEqualTo("New caption");
        assertThat(updated.getSortOrder()).isEqualTo(5);
        assertFails(() -> service.updateImage(10L, 99L, request), MessageConstant.NEWS_IMAGE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteImage_removesTheRowAndTheFile_onlyWhileEditable() {
        ownArticle(article(10, NewsStatus.DRAFT));
        NewsImage image = new NewsImage();
        image.setId(3L);
        image.setUrl("/uploads/a.png");
        when(newsImageRepository.findByIdAndNewsId(3L, 10L)).thenReturn(Optional.of(image));

        service.deleteImage(10L, 3L);

        verify(newsImageRepository).delete(image);
        verify(fileStorageService).delete("/uploads/a.png");

        ownArticle(article(11, NewsStatus.PUBLISHED));
        assertFails(() -> service.deleteImage(11L, 3L), MessageConstant.NEWS_NOT_EDITABLE, HttpStatus.CONFLICT);
    }

    @Test
    void thumbnail_setReplacesTheOldFile_removeClearsIt_andNeedsAnEditableArticle() {
        News draft = article(10, NewsStatus.DRAFT);
        draft.setThumbnail("/uploads/old.png");
        ownArticle(draft);
        when(newsRepository.save(draft)).thenReturn(draft);
        when(fileStorageService.store(any())).thenReturn("/uploads/new.png");

        assertThat(service.setThumbnail(10L, file()).getThumbnail()).isEqualTo("/uploads/new.png");
        verify(fileStorageService).delete("/uploads/old.png");

        assertThat(service.removeThumbnail(10L).getThumbnail()).isNull();
        verify(fileStorageService).delete("/uploads/new.png");

        ownArticle(article(11, NewsStatus.PENDING));
        assertFails(() -> service.setThumbnail(11L, file()), MessageConstant.NEWS_NOT_EDITABLE, HttpStatus.CONFLICT);
    }

    // ---------------- admin: sees, edits and deletes everything ----------------

    private static final long ADMIN_ID = 9L;

    private AppUser loggedInAsAdmin() {
        AppUser admin = user(ADMIN_ID);
        admin.setRole(Roles.ADMIN);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(admin);
        return admin;
    }

    // an article written by author ME, found by id for the admin
    private News othersArticle(long id, NewsStatus status) {
        News news = article(id, status);
        news.setAuthor(me);
        when(newsRepository.findById(id)).thenReturn(Optional.of(news));
        return news;
    }

    @Test
    void admin_canViewAnyArticle_inAnyStatus_andSeesTheAuthor() {
        loggedInAsAdmin();
        for (NewsStatus status : NewsStatus.values()) {
            othersArticle(10, status).setRejectionReason(status == NewsStatus.REJECTED ? "Too short" : null);

            NewsDto dto = service.getManaged(10L);

            assertThat(dto.getStatus()).isEqualTo(status);
            assertThat(dto.getAuthorId()).isEqualTo(ME);
            assertThat(dto.isEditable()).isTrue();
            assertThat(dto.getRejectionReason()).isEqualTo(status == NewsStatus.REJECTED ? "Too short" : null);
        }
        verify(newsRepository, never()).findByIdAndAuthorId(any(), any());
    }

    @Test
    void admin_unknownArticle_isNotFound() {
        loggedInAsAdmin();

        assertFails(() -> service.getManaged(77L), MessageConstant.NEWS_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertFails(() -> service.update(77L, request("X")), MessageConstant.NEWS_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertFails(() -> service.delete(77L), MessageConstant.NEWS_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    void admin_canEditAnotherAuthorsArticle_inEveryStatus_slugAndStatusStay() {
        loggedInAsAdmin();
        for (NewsStatus status : NewsStatus.values()) {
            News news = othersArticle(10, status);
            when(newsRepository.save(news)).thenReturn(news);

            NewsDto updated = service.update(10L, request("Corrected by admin"));

            assertThat(updated.getTitle()).isEqualTo("Corrected by admin");
            assertThat(updated.getSlug()).isEqualTo("old-title");
            assertThat(updated.getStatus()).isEqualTo(status);
            assertThat(updated.getAuthorId()).isEqualTo(ME);
        }
    }

    @Test
    void admin_canManageTheImagesAndThumbnailOfAPublishedArticle() {
        loggedInAsAdmin();
        News published = othersArticle(10, NewsStatus.PUBLISHED);
        when(newsRepository.save(published)).thenReturn(published);
        when(fileStorageService.store(any())).thenReturn("/uploads/a.png");
        when(newsImageRepository.countByNewsId(10L)).thenReturn(0L);
        when(newsImageRepository.save(any(NewsImage.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.addImage(10L, file(), "Caption").getUrl()).isEqualTo("/uploads/a.png");
        assertThat(service.setThumbnail(10L, file()).getThumbnail()).isEqualTo("/uploads/a.png");
    }

    @Test
    void admin_canDeleteAnyArticle_withEverythingAttachedToIt() {
        loggedInAsAdmin();
        News published = othersArticle(10, NewsStatus.PUBLISHED);
        published.setThumbnail("/uploads/thumb.png");
        NewsImage image = new NewsImage();
        image.setUrl("/uploads/a.png");
        when(newsImageRepository.findAllByNewsIdOrderBySortOrderAsc(10L)).thenReturn(List.of(image));

        service.delete(10L);

        InOrder order = inOrder(commentRepository, bookmarkRepository, newsViewRepository, newsTagRepository,
                newsImageRepository, newsStatusHistoryRepository, newsRepository);
        order.verify(commentRepository).deleteAllByNewsId(10L);
        order.verify(bookmarkRepository).deleteAllByNewsId(10L);
        order.verify(newsViewRepository).deleteAllByNewsId(10L);
        order.verify(newsTagRepository).deleteAllByNewsId(10L);
        order.verify(newsImageRepository).deleteAllByNewsId(10L);
        order.verify(newsStatusHistoryRepository).deleteAllByNewsId(10L);
        order.verify(newsRepository).delete(published);
        verify(fileStorageService).delete("/uploads/a.png");
        verify(fileStorageService).delete("/uploads/thumb.png");
    }

    @Test
    void admin_canDeleteArticlesInEveryStatus() {
        loggedInAsAdmin();
        for (NewsStatus status : NewsStatus.values()) {
            News news = othersArticle(10, status);

            service.delete(10L);

            verify(newsRepository, org.mockito.Mockito.atLeastOnce()).delete(news);
        }
    }

    @Test
    void admin_listManaged_seesEveryAuthor_andFiltersByAuthorCategoryAndStatus() {
        loggedInAsAdmin();
        News other = article(10, NewsStatus.PUBLISHED);
        when(newsRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(other)));

        // no filter: every article of every author
        Page<NewsSummaryDto> all = service.listManaged(null, null, null, 1, 10);
        verify(newsRepository).search(isNull(), isNull(), isNull(), any(Pageable.class));
        assertThat(all.getContent().get(0).isEditable()).isTrue();
        assertThat(all.getContent().get(0).getAuthorId()).isEqualTo(ME);

        // with filters
        service.listManaged(NewsStatus.PUBLISHED, 5L, 3L, 1, 10);
        verify(newsRepository).search(eq(5L), eq(3L), eq(NewsStatus.PUBLISHED), any(Pageable.class));
    }

    @Test
    void author_listManaged_isAlwaysLimitedToOwnArticles_evenWhenAskingForAnotherAuthor() {
        when(newsRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listManaged(NewsStatus.DRAFT, 2L, 3L, 1, 10);

        verify(newsRepository).search(eq(ME), eq(3L), eq(NewsStatus.DRAFT), any(Pageable.class));
        verify(newsRepository, never()).search(eq(2L), any(), any(), any(Pageable.class));
    }

    @Test
    void author_getManaged_ownArticleWorks_butAnotherAuthorsIsNotFound() {
        ownArticle(article(10, NewsStatus.PUBLISHED));

        NewsDto own = service.getManaged(10L);

        assertThat(own.isEditable()).isFalse();
        assertFails(() -> service.getManaged(20L), MessageConstant.NEWS_NOT_FOUND, HttpStatus.NOT_FOUND);
        verify(newsRepository, never()).findById(any());
    }

    @Test
    void author_cannotEditOrDeleteAPublishedArticle() {
        // an author: not editable (409) and not deletable (409), even though it is their own
        ownArticle(article(10, NewsStatus.PUBLISHED));
        assertFails(() -> service.update(10L, request("Changed")), MessageConstant.NEWS_NOT_EDITABLE, HttpStatus.CONFLICT);
        assertFails(() -> service.delete(10L), MessageConstant.NEWS_DELETE_ONLY_DRAFT, HttpStatus.CONFLICT);
        verify(newsRepository, never()).delete(any());
    }

    @Test
    void admin_createdArticle_belongsToTheAdmin() {
        AppUser admin = loggedInAsAdmin();
        when(appUserRepository.getReferenceById(ADMIN_ID)).thenReturn(admin);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category(CATEGORY_ID, true)));
        savingGivesAnId();

        NewsDto created = service.create(request("Written by the admin"));

        ArgumentCaptor<News> saved = ArgumentCaptor.forClass(News.class);
        verify(newsRepository).save(saved.capture());
        assertThat(saved.getValue().getAuthor()).isSameAs(admin);
        assertThat(saved.getValue().getStatus()).isEqualTo(NewsStatus.DRAFT);
        assertThat(created.getAuthorId()).isEqualTo(ADMIN_ID);
    }

    @Test
    void create_neverUsesTheReservedManageSlug() {
        when(appUserRepository.getReferenceById(ME)).thenReturn(me);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category(CATEGORY_ID, true)));
        savingGivesAnId();

        assertThat(service.create(request("Manage")).getSlug()).isEqualTo("manage-2");
    }
}
