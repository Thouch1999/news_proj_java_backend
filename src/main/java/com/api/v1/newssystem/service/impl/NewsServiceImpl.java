package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.entity.NewsImage;
import com.api.v1.newssystem.model.entity.NewsStatusHistory;
import com.api.v1.newssystem.model.entity.NewsTag;
import com.api.v1.newssystem.model.entity.Tag;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.request.NewsImageRequest;
import com.api.v1.newssystem.model.request.NewsRequest;
import com.api.v1.newssystem.model.response.CategoryDto;
import com.api.v1.newssystem.model.response.NewsDto;
import com.api.v1.newssystem.model.response.NewsImageDto;
import com.api.v1.newssystem.model.response.NewsStatusHistoryDto;
import com.api.v1.newssystem.model.response.NewsSummaryDto;
import com.api.v1.newssystem.model.response.TagDto;
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
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.service.ActivityLogService;
import com.api.v1.newssystem.service.FileStorageService;
import com.api.v1.newssystem.util.ActivityDetails;
import com.api.v1.newssystem.util.ApiResponseUtil;
import com.api.v1.newssystem.service.NewsService;
import com.api.v1.newssystem.service.PreviewService;
import com.api.v1.newssystem.service.SystemSettingService;
import com.api.v1.newssystem.util.SlugUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import com.api.v1.newssystem.model.enumerate.NotificationType;
import com.api.v1.newssystem.service.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsServiceImpl implements NewsService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SLUG_LENGTH = 240;
    // scheduled publishing: the time must be 5 minutes to 1 year after now
    private static final Duration SCHEDULE_MIN_LEAD = Duration.ofMinutes(5);
    private static final Duration SCHEDULE_MAX_LEAD = Duration.ofDays(365);
    // GET /api/v1/news/mine is a fixed path, and so are /manage and /review: no article may use these slugs
    private static final Set<String> RESERVED_SLUGS = Set.of("mine", "manage", "review", "latest", "most-viewed", "category-sections", "scheduled");
    // an author may edit only in these statuses (an editor in any but ARCHIVED, an admin in any status)
    private static final Set<NewsStatus> AUTHOR_EDITABLE = EnumSet.of(NewsStatus.DRAFT, NewsStatus.REJECTED);

    // the editorial workflow: the actions and, for each, the roles and statuses it is allowed from (see transition)
    private enum Action {
        SUBMIT(NewsStatus.PENDING, ActivityAction.NEWS_SUBMITTED),
        SCHEDULE(NewsStatus.SCHEDULED, ActivityAction.NEWS_SCHEDULED),
        RESCHEDULE(NewsStatus.SCHEDULED, ActivityAction.NEWS_SCHEDULE_CHANGED),
        UNSCHEDULE(NewsStatus.PENDING, ActivityAction.NEWS_SCHEDULE_CANCELLED),
        PUBLISH(NewsStatus.PUBLISHED, ActivityAction.NEWS_PUBLISHED),
        REJECT(NewsStatus.REJECTED, ActivityAction.NEWS_REJECTED),
        ARCHIVE(NewsStatus.ARCHIVED, ActivityAction.NEWS_ARCHIVED),
        RESTORE(NewsStatus.PUBLISHED, ActivityAction.NEWS_RESTORED);

        private final NewsStatus target;
        private final ActivityAction activity;

        Action(NewsStatus target, ActivityAction activity) {
            this.target = target;
            this.activity = activity;
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
    private final ApplicationEventPublisher eventPublisher;
    private final PreviewService previewService;
    private final ActivityLogService activityLogService;
    private final Clock clock;

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
        activityLogService.record(ActivityAction.NEWS_CREATED, ActivityTargetType.NEWS, saved.getId(), saved.getTitle(),
                ActivityDetails.of("category", category.getName()));

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
        // what the article was, for the activity log
        String titleBefore = news.getTitle();
        String summaryBefore = news.getSummary();
        String contentBefore = news.getContent();
        String categoryBefore = news.getCategory().getName();
        boolean commentsBefore = news.isCommentsEnabled();
        List<String> tagsBefore = tags == null ? null : newsTagRepository.findAllByNewsId(news.getId()).stream()
                .map(link -> link.getTag().getName()).sorted().toList();

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
        previewService.evictNews(saved.getSlug());

        ActivityDetails.Changes changes = new ActivityDetails.Changes()
                .field("title", titleBefore, saved.getTitle())
                .field("summary", summaryBefore, saved.getSummary())
                .field("category", categoryBefore, saved.getCategory().getName())
                .field("commentsEnabled", commentsBefore, saved.isCommentsEnabled())
                .note("content", !Objects.equals(contentBefore, saved.getContent()));
        if (tags != null) {
            changes.field("tags", tagsBefore, tags.stream().map(Tag::getName).sorted().toList());
        }
        if (!changes.isEmpty()) {
            activityLogService.record(ActivityAction.NEWS_UPDATED, ActivityTargetType.NEWS, saved.getId(), saved.getTitle(), changes.toDetails());
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
    public Page<NewsSummaryDto> listManaged(NewsStatus status, Long authorId, Long categoryId, String sort, int page, int size) {
        // an author is always limited to their own articles, whatever authorId they ask for
        Long scope = canSeeAll() ? authorId : currentUserService.getCurrentUser().getId();
        return newsRepository.search(scope, categoryId, status, pageRequest(page, size, sort)).map(this::toSummary);
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
        previewService.evictNews(news.getSlug());
        // the title is kept in the entry, so it still says which article it was
        activityLogService.record(ActivityAction.NEWS_DELETED, ActivityTargetType.NEWS, id, news.getTitle(),
                ActivityDetails.of("status", news.getStatus()));

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
        previewService.evictNews(saved.getSlug());
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
        previewService.evictNews(saved.getSlug());
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
    public NewsDto schedule(Long id, Instant scheduledAt) {
        return transition(id, Action.SCHEDULE, null, scheduledAt);
    }

    @Override
    @Transactional
    public NewsDto reschedule(Long id, Instant scheduledAt) {
        return transition(id, Action.RESCHEDULE, null, scheduledAt);
    }

    @Override
    @Transactional
    public NewsDto cancelSchedule(Long id) {
        return transition(id, Action.UNSCHEDULE, null);
    }

    @Override
    @Transactional
    public List<NewsSummaryDto> listUpcoming() {
        requireRole(Action.SCHEDULE);
        return newsRepository.findTop10ByStatusOrderByScheduledAtAscIdAsc(NewsStatus.SCHEDULED).stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional
    public List<Long> findDueScheduledIds() {
        return newsRepository.findDueScheduledIds(clock.instant());
    }

    // the job: no logged-in user; the system publishes in the name of the person who scheduled the article
    @Override
    @Transactional
    public boolean publishScheduled(Long id) {
        Instant now = clock.instant();
        News news = newsRepository.findById(id).orElse(null);
        if (news == null || news.getStatus() != NewsStatus.SCHEDULED || news.getScheduledAt() == null || news.getScheduledAt().isAfter(now)) {
            return false;
        }
        AppUser scheduledBy = news.getScheduledBy();
        String scheduler = scheduledBy == null ? null : authorName(scheduledBy);
        AppUser author = news.getAuthor();
        Instant scheduledAt = news.getScheduledAt();
        String title = news.getTitle();
        String slug = news.getSlug();
        Long changedBy = scheduledBy != null ? scheduledBy.getId() : author.getId();

        // one winner: another run or another instance that published it first leaves 0 rows here
        if (newsRepository.publishIfDue(id, LocalDateTime.now(clock), now) == 0) {
            return false;
        }

        News published = newsRepository.getReferenceById(id); // the update cleared the persistence context
        NewsStatusHistory history = new NewsStatusHistory();
        history.setNews(published);
        history.setFromStatus(NewsStatus.SCHEDULED);
        history.setToStatus(NewsStatus.PUBLISHED);
        history.setChangedBy(appUserRepository.getReferenceById(changedBy));
        history.setNote("Published automatically (scheduled)");
        newsStatusHistoryRepository.save(history);
        activityLogService.recordFor(null, ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, id, title,
                ActivityDetails.of("from", NewsStatus.SCHEDULED, "to", NewsStatus.PUBLISHED, "scheduledAt", scheduledAt.toString(),
                        "note", scheduler == null ? "scheduled" : "scheduled by " + scheduler));

        // the notification is written after the commit (NotificationEventListener); the preview cache is cleared after it too
        eventPublisher.publishEvent(NotificationEvent.articlePublishedAutomatically(published, scheduledBy));
        afterCommit(() -> previewService.evictNews(slug));
        return true;
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
        boolean before = news.isCommentsEnabled();
        news.setCommentsEnabled(enabled);
        News saved = newsRepository.save(news);
        if (before != enabled) {
            activityLogService.record(ActivityAction.COMMENTS_TOGGLED, ActivityTargetType.NEWS, saved.getId(), saved.getTitle(),
                    ActivityDetails.of("commentsEnabled", ActivityDetails.change(before, enabled)));
        }
        return toDto(saved);
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
            case SCHEDULE, RESCHEDULE, UNSCHEDULE, PUBLISH, REJECT, ARCHIVE -> EnumSet.of(Roles.EDITOR, Roles.ADMIN);
            case RESTORE -> EnumSet.of(Roles.ADMIN);
        };
    }

    // the statuses an action may start from, for the given role
    private static Set<NewsStatus> allowedFrom(Action action, Roles role) {
        return switch (action) {
            case SUBMIT -> EnumSet.of(NewsStatus.DRAFT, NewsStatus.REJECTED);
            // only an admin may schedule without review
            case SCHEDULE -> role == Roles.ADMIN
                    ? EnumSet.of(NewsStatus.PENDING, NewsStatus.DRAFT)
                    : EnumSet.of(NewsStatus.PENDING);
            case RESCHEDULE, UNSCHEDULE -> EnumSet.of(NewsStatus.SCHEDULED);
            // only an admin may publish without review; a SCHEDULED article may be published now
            case PUBLISH -> role == Roles.ADMIN
                    ? EnumSet.of(NewsStatus.PENDING, NewsStatus.SCHEDULED, NewsStatus.DRAFT)
                    : EnumSet.of(NewsStatus.PENDING, NewsStatus.SCHEDULED);
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

    private NewsDto transition(Long id, Action action, String reason) {
        return transition(id, action, reason, null);
    }

    // the one place where an article changes status: checks role and current status, applies the change and
    // writes the history row, also for admin actions. 403 = wrong role, 409 = not allowed from the current status,
    // 400 = a missing reason or a time outside the window. (The job that publishes a due article is publishScheduled.)
    private NewsDto transition(Long id, Action action, String reason, Instant scheduledAt) {
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
        Instant scheduledBefore = news.getScheduledAt();
        if (action == Action.SCHEDULE || action == Action.RESCHEDULE) {
            checkScheduleTime(scheduledAt);
            if (action == Action.RESCHEDULE && scheduledAt.equals(scheduledBefore)) {
                return toDto(news); // the same time: nothing changes, nothing is written
            }
        }

        switch (action) {
            case SUBMIT -> news.setRejectionReason(null);
            case REJECT -> news.setRejectionReason(note);
            case SCHEDULE, RESCHEDULE -> {
                news.setScheduledAt(scheduledAt);
                news.setScheduledBy(appUserRepository.getReferenceById(actor.getId()));
            }
            case UNSCHEDULE -> clearSchedule(news);
            case PUBLISH -> {
                news.setPublishedAt(LocalDateTime.now(clock));
                clearSchedule(news); // a "publish now" of a SCHEDULED article ends the schedule
            }
            case RESTORE -> {
                if (news.getPublishedAt() == null) {
                    news.setPublishedAt(LocalDateTime.now(clock));
                }
            }
            case ARCHIVE -> { }
        }
        news.setStatus(action.target);
        News saved = newsRepository.save(news);
        previewService.evictNews(saved.getSlug()); // published, archived, restored ...: the link preview changes

        NewsStatusHistory history = new NewsStatusHistory();
        history.setNews(saved);
        history.setFromStatus(from);
        history.setToStatus(action.target);
        history.setChangedBy(appUserRepository.getReferenceById(actor.getId()));
        history.setNote(historyNote(action, from, note, scheduledAt));
        newsStatusHistoryRepository.save(history);
        activityLogService.record(action.activity, ActivityTargetType.NEWS, saved.getId(), saved.getTitle(),
                activityDetails(action, from, note, scheduledAt, scheduledBefore));

        publishNotification(action, actor, saved, note, scheduledAt);
        return toDto(saved);
    }

    // an article is scheduled for 5 minutes to 1 year from now (UTC); a problem is a 400 VALIDATION_FAILED with scheduledAt in details
    private void checkScheduleTime(Instant scheduledAt) {
        String problem = null;
        if (scheduledAt == null) {
            problem = "is required";
        } else {
            Instant now = clock.instant();
            if (scheduledAt.isBefore(now.plus(SCHEDULE_MIN_LEAD))) {
                problem = "must be at least 5 minutes from now";
            } else if (scheduledAt.isAfter(now.plus(SCHEDULE_MAX_LEAD))) {
                problem = "must be at most 1 year from now";
            }
        }
        if (problem != null) {
            throw new CustomException(
                    ApiResponseUtil.error(HttpStatus.BAD_REQUEST, MessageConstant.VALIDATION_FAILED.getCode(),
                            MessageConstant.VALIDATION_FAILED.getDefaultMessage(), Map.of("scheduledAt", problem), null),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static void clearSchedule(News news) {
        news.setScheduledAt(null);
        news.setScheduledBy(null);
    }

    private Map<String, Object> activityDetails(Action action, NewsStatus from, String reason, Instant scheduledAt, Instant scheduledBefore) {
        return switch (action) {
            case SCHEDULE -> ActivityDetails.of("from", from, "to", action.target, "scheduledAt", scheduledAt.toString());
            case RESCHEDULE -> ActivityDetails.of("from", from, "to", action.target, "scheduledAt", scheduledAt.toString(),
                    "scheduledAtBefore", scheduledBefore == null ? null : scheduledBefore.toString());
            default -> ActivityDetails.of("from", from, "to", action.target, "reason", reason);
        };
    }

    // written after this transaction commits (NotificationEventListener); a restore and a cancelled schedule notify nobody
    private void publishNotification(Action action, AppUser actor, News news, String reason, Instant scheduledAt) {
        switch (action) {
            case SUBMIT -> eventPublisher.publishEvent(NotificationEvent.articleSubmitted(actor, news));
            case SCHEDULE, RESCHEDULE -> eventPublisher.publishEvent(NotificationEvent.articleScheduled(actor, news, scheduledAt));
            case PUBLISH -> eventPublisher.publishEvent(
                    NotificationEvent.articleDecision(NotificationType.ARTICLE_PUBLISHED, actor, news, null));
            case REJECT -> eventPublisher.publishEvent(
                    NotificationEvent.articleDecision(NotificationType.ARTICLE_REJECTED, actor, news, reason));
            case ARCHIVE -> eventPublisher.publishEvent(
                    NotificationEvent.articleDecision(NotificationType.ARTICLE_ARCHIVED, actor, news, null));
            case UNSCHEDULE, RESTORE -> { }
        }
    }

    private String historyNote(Action action, NewsStatus from, String reason, Instant scheduledAt) {
        return switch (action) {
            case SUBMIT -> "Submitted for review";
            case SCHEDULE -> "Scheduled for " + scheduledAt;
            case RESCHEDULE -> "Schedule changed";
            case UNSCHEDULE -> "Schedule cancelled";
            case PUBLISH -> switch (from) {
                case DRAFT -> "Published directly by an admin";
                case SCHEDULED -> "Published now";
                default -> "Approved and published";
            };
            case REJECT -> reason;
            case ARCHIVE -> "Archived";
            case RESTORE -> "Restored by an admin";
        };
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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

    // sort: none = newest change first; "scheduledAt" = the nearest scheduled time first; nothing else is accepted
    private PageRequest pageRequest(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) {
            return pageRequest(page, size);
        }
        if (!"scheduledAt".equals(sort)) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
        return PageRequest.of(
                Math.max(page, 1) - 1,
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("scheduledAt").ascending().and(Sort.by("id").ascending()));
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

    private String scheduledByName(News news) {
        return news.getScheduledAt() == null ? null : authorName(news.getScheduledBy());
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
                news.getScheduledAt(),
                scheduledByName(news),
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
                news.getScheduledAt(),
                scheduledByName(news),
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
