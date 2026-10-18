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
import com.api.v1.bearerauth.model.response.CategoryDto;
import com.api.v1.bearerauth.model.response.NewsDto;
import com.api.v1.bearerauth.model.response.NewsImageDto;
import com.api.v1.bearerauth.model.response.NewsStatusHistoryDto;
import com.api.v1.bearerauth.model.response.NewsSummaryDto;
import com.api.v1.bearerauth.model.response.TagDto;
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
import com.api.v1.bearerauth.service.NewsService;
import com.api.v1.bearerauth.service.SystemSettingService;
import com.api.v1.bearerauth.util.SlugUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsServiceImpl implements NewsService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SLUG_LENGTH = 240;
    // GET /api/v1/news/mine is a fixed path, and so are /manage and /review: no article may use these slugs
    private static final Set<String> RESERVED_SLUGS = Set.of("mine", "manage", "review", "latest", "most-viewed", "category-sections");
    // an author may edit only in these statuses (an editor in any but ARCHIVED, an admin in any status)
    private static final Set<NewsStatus> AUTHOR_EDITABLE = EnumSet.of(NewsStatus.DRAFT, NewsStatus.REJECTED);

    // the editorial workflow: the actions and, for each, the roles and statuses it is allowed from (see transition)
    private enum Action {
        SUBMIT(NewsStatus.PENDING),
        PUBLISH(NewsStatus.PUBLISHED),
        REJECT(NewsStatus.REJECTED),
        ARCHIVE(NewsStatus.ARCHIVED),
        RESTORE(NewsStatus.PUBLISHED);

        private final NewsStatus target;

        Action(NewsStatus target) {
            this.target = target;
        }
    }

    private final NewsRepository newsRepository;
    private final NewsTagRepository newsTagRepository;
    private final NewsImageRepository newsImageRepository;
    private final NewsStatusHistoryRepository newsStatusHistoryRepository;
    private final CommentRepository commentRepository;
    private final BookmarkRepository bookmarkRepository;
    private final NewsViewRepository newsViewRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final AppProperty appProperty;
    private final SystemSettingService systemSettingService;

    @Override
    @Transactional
    public NewsDto create(NewsRequest request) {
        AppUser author = appUserRepository.getReferenceById(currentUserService.getCurrentUser().getId());
        Category category = activeCategory(request.getCategoryId());
        List<Tag> tags = findTags(request.getTagIds());
        String title = request.getTitle().trim();

        News news = new News();
        news.setTitle(title);
        news.setSummary(blankToNull(request.getSummary()));
        news.setContent(request.getContent() == null ? "" : request.getContent());
        news.setStatus(NewsStatus.DRAFT);
        news.setAuthor(author);
        news.setCategory(category);
        // not said: the default of the system settings (comments_enabled_default)
        news.setCommentsEnabled(request.getCommentsEnabled() != null
                ? request.getCommentsEnabled()
                : systemSettingService.isCommentsEnabledDefault());

        // the slug comes from the title; a title without Latin letters (for example Khmer) gets news-<id>
        String base = slugBase(title);
        news.setSlug(base.isEmpty() ? UUID.randomUUID().toString() : SlugUtil.resolve(null, base, this::slugTaken));
        News saved = newsRepository.save(news);
        if (base.isEmpty()) {
            saved.setSlug(fallbackSlug(saved.getId()));
            saved = newsRepository.save(saved);
        }

        replaceTags(saved, tags);
        NewsStatusHistory history = new NewsStatusHistory();
        history.setNews(saved);
        history.setToStatus(NewsStatus.DRAFT);
        history.setChangedBy(author);
        history.setNote("Article created");
        newsStatusHistoryRepository.save(history);

        return toDto(saved);
    }

    @Override
    @Transactional
    public NewsDto update(Long id, NewsRequest request) {
        News news = findEditable(id);

        Category category = news.getCategory();
        if (!category.getId().equals(request.getCategoryId())) {
            category = activeCategory(request.getCategoryId()); // only a changed category must still be active
        }
        List<Tag> tags = request.getTagIds() == null ? null : findTags(request.getTagIds());

        // the slug and the status never change here; a REJECTED article keeps its rejection reason
        news.setTitle(request.getTitle().trim());
        news.setSummary(blankToNull(request.getSummary()));
        news.setContent(request.getContent() == null ? "" : request.getContent());
        news.setCategory(category);
        if (request.getCommentsEnabled() != null) {
            news.setCommentsEnabled(request.getCommentsEnabled());
        }
        News saved = newsRepository.save(news);
        if (tags != null) {
            replaceTags(saved, tags);
        }
        return toDto(saved);
    }

    @Override
    @Transactional
    public NewsDto getMine(Long id) {
        return toDto(findOwned(id));
    }

    @Override
    @Transactional
    public NewsDto getManaged(Long id) {
        return toDto(findManageable(id));
    }

    @Override
    @Transactional
    public Page<NewsSummaryDto> listMine(NewsStatus status, int page, int size) {
        Long authorId = currentUserService.getCurrentUser().getId();
        return newsRepository.search(authorId, null, status, pageRequest(page, size)).map(this::toSummary);
    }

    @Override
    @Transactional
    public Page<NewsSummaryDto> listManaged(NewsStatus status, Long authorId, Long categoryId, int page, int size) {
        // an author is always limited to their own articles, whatever authorId they ask for
        Long scope = canSeeAll() ? authorId : currentUserService.getCurrentUser().getId();
        return newsRepository.search(scope, categoryId, status, pageRequest(page, size)).map(this::toSummary);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // only an admin deletes any article; an editor, like an author, only their own DRAFT
        News news = isAdmin() ? newsRepository.findById(id).orElseThrow(this::newsNotFound) : findOwned(id);
        if (!isAdmin() && news.getStatus() != NewsStatus.DRAFT) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.NEWS_DELETE_ONLY_DRAFT.getDefaultMessage());
        }

        List<NewsImage> images = newsImageRepository.findAllByNewsIdOrderBySortOrderAsc(id);
        // everything that points to the article goes first, then the article itself
        commentRepository.deleteAllByNewsId(id);
        bookmarkRepository.deleteAllByNewsId(id);
        newsViewRepository.deleteAllByNewsId(id);
        newsTagRepository.deleteAllByNewsId(id);
        newsImageRepository.deleteAllByNewsId(id);
        newsStatusHistoryRepository.deleteAllByNewsId(id);
        newsRepository.delete(news);

        images.forEach(image -> fileStorageService.delete(image.getUrl()));
        fileStorageService.delete(news.getThumbnail());
    }

    @Override
    @Transactional
    public NewsImageDto addImage(Long newsId, MultipartFile file, String caption) {
        News news = findEditable(newsId);
        if (caption != null && caption.length() > 255) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
        long count = newsImageRepository.countByNewsId(news.getId());
        if (count >= appProperty.getUpload().getMaxImagesPerNews()) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.NEWS_IMAGE_LIMIT.getDefaultMessage());
        }

        NewsImage image = new NewsImage();
        image.setNews(news);
        image.setUrl(fileStorageService.store(file));
        image.setCaption(blankToNull(caption));
        image.setSortOrder((int) count);
        return toImageDto(newsImageRepository.save(image));
    }

    @Override
    @Transactional
    public NewsImageDto updateImage(Long newsId, Long imageId, NewsImageRequest request) {
        News news = findEditable(newsId);
        NewsImage image = findImage(news.getId(), imageId);
        if (request.getCaption() != null) {
            image.setCaption(blankToNull(request.getCaption()));
        }
        if (request.getSortOrder() != null) {
            image.setSortOrder(request.getSortOrder());
        }
        return toImageDto(newsImageRepository.save(image));
    }

    @Override
    @Transactional
    public void deleteImage(Long newsId, Long imageId) {
        News news = findEditable(newsId);
        NewsImage image = findImage(news.getId(), imageId);
        newsImageRepository.delete(image);
        fileStorageService.delete(image.getUrl());
    }

    @Override
    @Transactional
    public NewsDto setThumbnail(Long newsId, MultipartFile file) {
        News news = findEditable(newsId);
        String previous = news.getThumbnail();
        news.setThumbnail(fileStorageService.store(file));
        News saved = newsRepository.save(news);
        fileStorageService.delete(previous);
        return toDto(saved);
    }

    @Override
    @Transactional
    public NewsDto removeThumbnail(Long newsId) {
        News news = findEditable(newsId);
        String previous = news.getThumbnail();
        news.setThumbnail(null);
        News saved = newsRepository.save(news);
        fileStorageService.delete(previous);
        return toDto(saved);
    }

    // ---- editorial workflow: every transition rule is in this section ----

    @Override
    @Transactional
    public NewsDto submit(Long id) {
        return transition(id, Action.SUBMIT, null);
    }

    @Override
    @Transactional
    public NewsDto publish(Long id) {
        return transition(id, Action.PUBLISH, null);
    }

    @Override
    @Transactional
    public NewsDto reject(Long id, String reason) {
        return transition(id, Action.REJECT, reason);
    }

    @Override
    @Transactional
    public NewsDto archive(Long id) {
        return transition(id, Action.ARCHIVE, null);
    }

    @Override
    @Transactional
    public NewsDto restore(Long id) {
        return transition(id, Action.RESTORE, null);
    }

    @Override
    @Transactional
    public NewsDto setCommentsEnabled(Long id, boolean enabled) {
        if (!canSeeAll()) { // EDITOR or ADMIN
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.NEWS_ACTION_NOT_ALLOWED.getDefaultMessage());
        }
        News news = newsRepository.findById(id).orElseThrow(this::newsNotFound);
        news.setCommentsEnabled(enabled);
        return toDto(newsRepository.save(news));
    }

    @Override
    @Transactional
    public Page<NewsSummaryDto> listReviewQueue(int page, int size) {
        requireRole(Action.PUBLISH); // the queue is for the same roles that review: EDITOR and ADMIN
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 1) - 1,
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("updatedAt").ascending().and(Sort.by("id").ascending())); // oldest first
        return newsRepository.findAllByStatus(NewsStatus.PENDING, pageRequest).map(this::toSummary);
    }

    @Override
    @Transactional
    public List<NewsStatusHistoryDto> history(Long id) {
        News news = findManageable(id);
        return newsStatusHistoryRepository.findAllByNewsIdOrderByChangedAtAsc(news.getId()).stream()
                .map(row -> new NewsStatusHistoryDto(
                        row.getId(),
                        row.getFromStatus(),
                        row.getToStatus(),
                        authorId(row.getChangedBy()),
                        authorName(row.getChangedBy()),
                        row.getNote(),
                        row.getChangedAt()))
                .toList();
    }

    // who may perform an action
    private static Set<Roles> allowedRoles(Action action) {
        return switch (action) {
            case SUBMIT -> EnumSet.of(Roles.AUTHOR, Roles.ADMIN);
            case PUBLISH, REJECT, ARCHIVE -> EnumSet.of(Roles.EDITOR, Roles.ADMIN);
            case RESTORE -> EnumSet.of(Roles.ADMIN);
        };
    }

    // the statuses an action may start from, for the given role
    private static Set<NewsStatus> allowedFrom(Action action, Roles role) {
        return switch (action) {
            case SUBMIT -> EnumSet.of(NewsStatus.DRAFT, NewsStatus.REJECTED);
            // only an admin may publish without review
            case PUBLISH -> role == Roles.ADMIN
                    ? EnumSet.of(NewsStatus.PENDING, NewsStatus.DRAFT)
                    : EnumSet.of(NewsStatus.PENDING);
            case REJECT -> EnumSet.of(NewsStatus.PENDING);
            case ARCHIVE -> EnumSet.of(NewsStatus.PUBLISHED);
            case RESTORE -> EnumSet.of(NewsStatus.ARCHIVED);
        };
    }

    private AppUser requireRole(Action action) {
        AppUser actor = currentUserService.getCurrentUser();
        if (!allowedRoles(action).contains(actor.getRole())) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.NEWS_ACTION_NOT_ALLOWED.getDefaultMessage());
        }
        return actor;
    }

    // the one place where an article changes status: checks role and current status, applies the change and
    // writes the history row, also for admin actions. 403 = wrong role, 409 = not allowed from the current status.
    private NewsDto transition(Long id, Action action, String reason) {
        AppUser actor = requireRole(action);
        String note = null;
        if (action == Action.REJECT) {
            note = blankToNull(reason);
            if (note == null) {
                throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
            }
        }

        // an author submits an own article only; the review actions work on any article
        News news = action == Action.SUBMIT ? findOwned(id) : findManageable(id);
        NewsStatus from = news.getStatus();
        if (!allowedFrom(action, actor.getRole()).contains(from)) {
            throw new CustomException(HttpStatus.CONFLICT,
                    MessageConstant.NEWS_INVALID_TRANSITION.getDefaultMessage() + ": " + from + " -> " + action.target);
        }

        switch (action) {
            case SUBMIT -> news.setRejectionReason(null);
            case REJECT -> news.setRejectionReason(note);
            case PUBLISH -> news.setPublishedAt(LocalDateTime.now());
            case RESTORE -> {
                if (news.getPublishedAt() == null) {
                    news.setPublishedAt(LocalDateTime.now());
                }
            }
            case ARCHIVE -> { }
        }
        news.setStatus(action.target);
        News saved = newsRepository.save(news);

        NewsStatusHistory history = new NewsStatusHistory();
        history.setNews(saved);
        history.setFromStatus(from);
        history.setToStatus(action.target);
        history.setChangedBy(appUserRepository.getReferenceById(actor.getId()));
        history.setNote(historyNote(action, from, note));
        newsStatusHistoryRepository.save(history);

        return toDto(saved);
    }

    private String historyNote(Action action, NewsStatus from, String reason) {
        return switch (action) {
            case SUBMIT -> "Submitted for review";
            case PUBLISH -> from == NewsStatus.DRAFT ? "Published directly by an admin" : "Approved and published";
            case REJECT -> reason;
            case ARCHIVE -> "Archived";
            case RESTORE -> "Restored by an admin";
        };
    }

    // ---- who may see and change what ----

    private boolean isAdmin() {
        return currentUserService.getCurrentUser().getRole() == Roles.ADMIN;
    }

    // an admin and an editor see every article; an author only their own
    private boolean canSeeAll() {
        Roles role = currentUserService.getCurrentUser().getRole();
        return role == Roles.ADMIN || role == Roles.EDITOR;
    }

    // an own article only (another author's article is "not found", so its existence is not revealed)
    private News findOwned(Long id) {
        Long authorId = currentUserService.getCurrentUser().getId();
        return newsRepository.findByIdAndAuthorId(id, authorId).orElseThrow(this::newsNotFound);
    }

    // an admin or an editor finds any article, an author only their own
    private News findManageable(Long id) {
        if (canSeeAll()) {
            return newsRepository.findById(id).orElseThrow(this::newsNotFound);
        }
        return findOwned(id);
    }

    // an author may edit in DRAFT or REJECTED only; an editor in any status but ARCHIVED; an admin in any status
    private News findEditable(Long id) {
        News news = findManageable(id);
        if (!canEdit(news)) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.NEWS_NOT_EDITABLE.getDefaultMessage());
        }
        return news;
    }

    private boolean canEdit(News news) {
        Roles role = currentUserService.getCurrentUser().getRole();
        if (role == Roles.ADMIN) {
            return true;
        }
        if (role == Roles.EDITOR) {
            return news.getStatus() != NewsStatus.ARCHIVED;
        }
        return AUTHOR_EDITABLE.contains(news.getStatus());
    }

    private CustomException newsNotFound() {
        return new CustomException(HttpStatus.NOT_FOUND, MessageConstant.NEWS_NOT_FOUND.getDefaultMessage());
    }

    private NewsImage findImage(Long newsId, Long imageId) {
        return newsImageRepository.findByIdAndNewsId(imageId, newsId)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.NEWS_IMAGE_NOT_FOUND.getDefaultMessage()));
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(
                Math.max(page, 1) - 1,
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("updatedAt").descending().and(Sort.by("id").descending()));
    }

    // ---- categories, tags, slug ----

    private Category activeCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.CATEGORY_NOT_FOUND.getDefaultMessage()));
        if (!category.isActive()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.CATEGORY_INACTIVE.getDefaultMessage());
        }
        return category;
    }

    private List<Tag> findTags(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> distinct = new LinkedHashSet<>(ids);
        List<Tag> tags = tagRepository.findAllById(distinct);
        if (tags.size() != distinct.size()) {
            throw new CustomException(HttpStatus.NOT_FOUND, MessageConstant.TAG_NOT_FOUND.getDefaultMessage());
        }
        return tags;
    }

    // makes the stored links match `wanted`: removes the others and adds the missing ones
    private void replaceTags(News news, List<Tag> wanted) {
        List<NewsTag> existing = newsTagRepository.findAllByNewsId(news.getId());
        Set<Long> wantedIds = wanted.stream().map(Tag::getId).collect(Collectors.toSet());
        Set<Long> existingIds = existing.stream().map(link -> link.getTag().getId()).collect(Collectors.toSet());

        newsTagRepository.deleteAll(existing.stream().filter(link -> !wantedIds.contains(link.getTag().getId())).toList());
        List<NewsTag> added = wanted.stream()
                .filter(tag -> !existingIds.contains(tag.getId()))
                .map(tag -> {
                    NewsTag link = new NewsTag();
                    link.setNews(news);
                    link.setTag(tag);
                    return link;
                })
                .toList();
        newsTagRepository.saveAll(added);
    }

    private String slugBase(String title) {
        String slug = SlugUtil.slugify(title);
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
        }
        return slug;
    }

    private boolean slugTaken(String slug) {
        return RESERVED_SLUGS.contains(slug) || newsRepository.existsBySlug(slug);
    }

    private String fallbackSlug(Long id) {
        String candidate = "news-" + id;
        while (newsRepository.existsBySlug(candidate)) {
            candidate += "-1";
        }
        return candidate;
    }

    // ---- mapping ----

    private String authorName(AppUser author) {
        if (author == null) {
            return null;
        }
        return author.getFullName() != null && !author.getFullName().isBlank() ? author.getFullName() : author.getName();
    }

    private Long authorId(AppUser author) {
        return author == null ? null : author.getId();
    }

    private NewsDto toDto(News news) {
        Category category = news.getCategory();
        List<TagDto> tags = newsTagRepository.findAllByNewsId(news.getId()).stream()
                .map(link -> new TagDto(link.getTag().getId(), link.getTag().getName(), link.getTag().getSlug()))
                .toList();
        List<NewsImageDto> images = newsImageRepository.findAllByNewsIdOrderBySortOrderAsc(news.getId()).stream()
                .map(this::toImageDto)
                .toList();

        return new NewsDto(
                news.getId(),
                news.getTitle(),
                news.getSlug(),
                news.getSummary(),
                news.getContent(),
                news.getThumbnail(),
                news.getStatus(),
                authorId(news.getAuthor()),
                authorName(news.getAuthor()),
                new CategoryDto(category.getId(), category.getName(), category.getSlug(), category.getDescription(), category.isActive(), null),
                tags,
                images,
                news.isCommentsEnabled(),
                news.getRejectionReason(),
                canEdit(news),
                news.getViewCount(),
                news.getPublishedAt(),
                news.getCreatedAt(),
                news.getUpdatedAt());
    }

    private NewsSummaryDto toSummary(News news) {
        return new NewsSummaryDto(
                news.getId(),
                news.getTitle(),
                news.getSlug(),
                news.getSummary(),
                news.getThumbnail(),
                news.getStatus(),
                authorId(news.getAuthor()),
                authorName(news.getAuthor()),
                news.getCategory().getName(),
                news.getRejectionReason(),
                canEdit(news),
                news.getCreatedAt(),
                news.getUpdatedAt());
    }

    private NewsImageDto toImageDto(NewsImage image) {
        return new NewsImageDto(image.getId(), image.getUrl(), image.getCaption(), image.getSortOrder());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
