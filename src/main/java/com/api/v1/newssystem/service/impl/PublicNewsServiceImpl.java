package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.entity.NewsTag;
import com.api.v1.newssystem.model.entity.NewsView;
import com.api.v1.newssystem.model.entity.Tag;
import com.api.v1.newssystem.model.enumerate.CommentStatus;
import com.api.v1.newssystem.model.response.CategoryDto;
import com.api.v1.newssystem.model.response.NewsImageDto;
import com.api.v1.newssystem.model.response.PublicCategorySectionDto;
import com.api.v1.newssystem.model.response.PublicNewsDto;
import com.api.v1.newssystem.model.response.PublicNewsSummaryDto;
import com.api.v1.newssystem.model.response.TagDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.BookmarkRepository;
import com.api.v1.newssystem.repository.CategoryRepository;
import com.api.v1.newssystem.repository.CommentRepository;
import com.api.v1.newssystem.repository.NewsImageRepository;
import com.api.v1.newssystem.repository.NewsRepository;
import com.api.v1.newssystem.repository.NewsTagRepository;
import com.api.v1.newssystem.repository.NewsViewRepository;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.service.PublicNewsService;
import com.api.v1.newssystem.service.SystemSettingService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Every list here costs a fixed number of queries, however many articles it returns: the articles come with their
// author and category (one query), then the tags of all of them (one query) and their comment counts (one query).
@Service
@RequiredArgsConstructor
public class PublicNewsServiceImpl implements PublicNewsService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_LIMIT = 20;
    private static final int MAX_PER_CATEGORY = 10;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_IP_LENGTH = 45;
    // a reader counts once per article in this many hours (a logged-in user by account, a visitor by address)
    static final int VIEW_WINDOW_HOURS = 24;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    private static final Sort MOST_VIEWED = Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final NewsRepository newsRepository;
    private final NewsTagRepository newsTagRepository;
    private final NewsImageRepository newsImageRepository;
    private final NewsViewRepository newsViewRepository;
    private final CommentRepository commentRepository;
    private final BookmarkRepository bookmarkRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final SystemSettingService systemSettingService;

    @Override
    @Transactional
    public Page<PublicNewsSummaryDto> list(String keyword, String category, String tag, LocalDate from, LocalDate to, int page, Integer size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, clamp(size == null ? systemSettingService.getNewsPerPage() : size, MAX_PAGE_SIZE), NEWEST);
        Page<News> found = newsRepository.searchPublished(
                blankToNull(category),
                blankToNull(tag),
                likePattern(keyword),
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(), // `to` is included: up to the start of the next day
                pageable);
        List<PublicNewsSummaryDto> cards = toSummaries(found.getContent());
        return new PageImpl<>(cards, pageable, found.getTotalElements());
    }

    @Override
    @Transactional
    public PublicNewsDto getBySlug(String slug, String ip) {
        News news = newsRepository.findPublishedBySlug(slug)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.NEWS_NOT_FOUND.getDefaultMessage()));

        long views = news.getViewCount();
        if (recordView(news, ip)) {
            views++;
        }

        List<TagDto> tags = newsTagRepository.findAllWithTagByNewsIdIn(List.of(news.getId())).stream()
                .map(link -> toTagDto(link.getTag()))
                .toList();
        List<NewsImageDto> images = newsImageRepository.findAllByNewsIdOrderBySortOrderAsc(news.getId()).stream()
                .map(image -> new NewsImageDto(image.getId(), image.getUrl(), image.getCaption(), image.getSortOrder()))
                .toList();
        long comments = commentRepository.countByNewsIdAndStatus(news.getId(), CommentStatus.VISIBLE);
        Boolean bookmarked = currentUserService.findCurrentUser()
                .map(current -> bookmarkRepository.existsByUserIdAndNewsId(current.getId(), news.getId()))
                .orElse(null);

        return new PublicNewsDto(
                news.getId(),
                news.getTitle(),
                news.getSlug(),
                news.getSummary(),
                news.getContent(),
                news.getThumbnail(),
                authorName(news.getAuthor()),
                toCategoryDto(news.getCategory()),
                tags,
                images,
                news.isCommentsEnabled(),
                views,
                comments,
                news.getPublishedAt(),
                news.getUpdatedAt(),
                bookmarked);
    }

    @Override
    @Transactional
    public List<PublicNewsSummaryDto> toCards(List<News> news) {
        return toSummaries(news);
    }

    @Override
    @Transactional
    public List<PublicNewsSummaryDto> latest(int limit) {
        return toSummaries(newsRepository.findPublished(PageRequest.of(0, clamp(limit, MAX_LIMIT), NEWEST)));
    }

    @Override
    @Transactional
    public List<PublicNewsSummaryDto> mostViewed(int limit) {
        return toSummaries(newsRepository.findPublished(PageRequest.of(0, clamp(limit, MAX_LIMIT), MOST_VIEWED)));
    }

    @Override
    @Transactional
    public List<PublicCategorySectionDto> categorySections(int perCategory) {
        List<Long> ids = newsRepository.findNewestIdsPerCategory(clamp(perCategory, MAX_PER_CATEGORY));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<News> news = new ArrayList<>(newsRepository.findAllWithAuthorAndCategoryByIdIn(ids));
        news.sort(Comparator.comparing(News::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(News::getId, Comparator.reverseOrder()));

        Map<String, List<PublicNewsSummaryDto>> bySlug = toSummaries(news).stream()
                .collect(Collectors.groupingBy(PublicNewsSummaryDto::getCategorySlug, LinkedHashMap::new, Collectors.toList()));

        // sections in the order of the category names; a category without news has no section
        List<PublicCategorySectionDto> sections = new ArrayList<>();
        for (Category category : categoryRepository.findAllByActiveTrueOrderByNameAsc()) {
            List<PublicNewsSummaryDto> cards = bySlug.get(category.getSlug());
            if (cards != null) {
                sections.add(new PublicCategorySectionDto(toCategoryDto(category), cards));
            }
        }
        return sections;
    }

    // ---- views ----

    // true when this open counted as a new view. A logged-in user counts once per article in 24 hours; a visitor
    // once per address. A visitor whose address is unknown is not counted (there is nothing to tell visitors apart).
    private boolean recordView(News news, String ip) {
        Optional<AppUser> user = currentUserService.findCurrentUser();
        String address = ip == null || ip.isBlank() ? null : truncate(ip.trim(), MAX_IP_LENGTH);
        LocalDateTime since = LocalDateTime.now().minusHours(VIEW_WINDOW_HOURS);

        boolean alreadyCounted;
        if (user.isPresent()) {
            alreadyCounted = newsViewRepository.existsByNewsIdAndUserIdAndViewedAtAfter(news.getId(), user.get().getId(), since);
        } else if (address != null) {
            alreadyCounted = newsViewRepository.existsByNewsIdAndIpAndViewedAtAfter(news.getId(), address, since);
        } else {
            return false;
        }
        if (alreadyCounted) {
            return false;
        }

        NewsView view = new NewsView();
        view.setNews(news);
        user.ifPresent(current -> view.setUser(appUserRepository.getReferenceById(current.getId())));
        view.setIp(address);
        newsViewRepository.save(view);
        newsRepository.incrementViewCount(news.getId());
        return true;
    }

    // ---- mapping ----

    // the cards of many articles with a fixed number of queries; keeps the order of `news`
    private List<PublicNewsSummaryDto> toSummaries(List<News> news) {
        if (news.isEmpty()) {
            return List.of();
        }
        List<Long> ids = news.stream().map(News::getId).toList();

        Map<Long, List<TagDto>> tagsByNews = new HashMap<>();
        for (NewsTag link : newsTagRepository.findAllWithTagByNewsIdIn(ids)) {
            tagsByNews.computeIfAbsent(link.getNews().getId(), key -> new ArrayList<>()).add(toTagDto(link.getTag()));
        }
        Map<Long, Long> commentsByNews = new HashMap<>();
        for (Object[] row : commentRepository.countByNewsIds(ids, CommentStatus.VISIBLE)) {
            commentsByNews.put((Long) row[0], (Long) row[1]);
        }

        return news.stream()
                .map(item -> new PublicNewsSummaryDto(
                        item.getId(),
                        item.getTitle(),
                        item.getSlug(),
                        item.getSummary(),
                        item.getThumbnail(),
                        item.getCategory().getName(),
                        item.getCategory().getSlug(),
                        authorName(item.getAuthor()),
                        tagsByNews.getOrDefault(item.getId(), List.of()),
                        item.getViewCount(),
                        commentsByNews.getOrDefault(item.getId(), 0L),
                        item.getPublishedAt()))
                .toList();
    }

    private TagDto toTagDto(Tag tag) {
        return new TagDto(tag.getId(), tag.getName(), tag.getSlug());
    }

    private CategoryDto toCategoryDto(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getSlug(), category.getDescription(), category.isActive(), null);
    }

    private String authorName(AppUser author) {
        if (author == null) {
            return null;
        }
        return author.getFullName() != null && !author.getFullName().isBlank() ? author.getFullName() : author.getName();
    }

    // ---- input ----

    private int clamp(int value, int max) {
        return Math.min(Math.max(value, 1), max);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    // the LIKE pattern for a keyword: lower case, and % _ and the escape character itself are matched literally.
    // A plain substring match works for any script; Khmer has no spaces between words, so word search would not.
    private String likePattern(String keyword) {
        String trimmed = blankToNull(keyword);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
