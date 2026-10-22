package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.config.CacheConfig;
import com.api.v1.bearerauth.model.entity.Category;
import com.api.v1.bearerauth.model.entity.News;
import com.api.v1.bearerauth.model.response.PreviewDto;
import com.api.v1.bearerauth.repository.CategoryRepository;
import com.api.v1.bearerauth.repository.NewsRepository;
import com.api.v1.bearerauth.repository.NewsTagRepository;
import com.api.v1.bearerauth.service.PreviewService;
import com.api.v1.bearerauth.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Reading a preview only reads: it never records a view (that is done by PublicNewsService.getBySlug alone).
 * A found preview is cached per page and language for ten minutes (CacheConfig.PREVIEW_CACHE); a miss is not cached,
 * so a link to an unpublished article never keeps an entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreviewServiceImpl implements PreviewService {
    static final int DESCRIPTION_MAX = 200;
    private static final int MAX_SLUG_LENGTH = 300;
    private static final List<String> LANGUAGES = List.of("en", "km");
    private static final String UPLOADS = "/uploads/";

    private final NewsRepository newsRepository;
    private final NewsTagRepository newsTagRepository;
    private final CategoryRepository categoryRepository;
    private final SystemSettingService systemSettingService;
    private final AppProperty appProperty;
    private final CacheManager cacheManager;

    // ---- pages ----

    @Override
    @Transactional(readOnly = true)
    public Optional<PreviewDto> news(String slug, String lang) {
        if (slug == null || slug.isBlank() || slug.length() > MAX_SLUG_LENGTH) {
            return Optional.empty();
        }
        Map<String, String> settings = systemSettingService.getPublic();
        String language = language(lang, settings);
        return cached(newsKey(slug, language), () -> newsRepository.findPublishedBySlug(slug)
                .map(news -> articlePreview(news, language, settings)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PreviewDto> category(String slug, String lang) {
        if (slug == null || slug.isBlank() || slug.length() > MAX_SLUG_LENGTH) {
            return Optional.empty();
        }
        Map<String, String> settings = systemSettingService.getPublic();
        String language = language(lang, settings);
        return cached("category:" + slug + ":" + language, () -> categoryRepository.findBySlugAndActiveTrue(slug)
                .map(category -> categoryPreview(category, language, settings)));
    }

    @Override
    @Transactional(readOnly = true)
    public PreviewDto home(String lang) {
        Map<String, String> settings = systemSettingService.getPublic();
        String language = language(lang, settings);
        return cached("home:" + language, () -> Optional.of(homePreview(language, settings))).orElseThrow();
    }

    @Override
    public void evictNews(String slug) {
        if (slug == null) {
            return;
        }
        // after the commit: before it, a reader could load the old article into the cache again
        afterCommit(() -> {
            Cache cache = cacheManager.getCache(CacheConfig.PREVIEW_CACHE);
            if (cache != null) {
                LANGUAGES.forEach(language -> cache.evict(newsKey(slug, language)));
            }
        });
    }

    // ---- building ----

    private PreviewDto articlePreview(News news, String language, Map<String, String> settings) {
        String summary = plainText(news.getSummary());
        String description = truncate(summary.isEmpty() ? plainText(news.getContent()) : summary);
        List<String> tags = new ArrayList<>();
        newsTagRepository.findAllWithTagByNewsIdIn(List.of(news.getId())).forEach(link -> tags.add(link.getTag().getName()));

        Image image = image(settings, news.getThumbnail());
        return PreviewDto.builder()
                .language(language)
                .pageTitle(pageTitle(news.getTitle(), language, settings))
                .title(news.getTitle())
                .description(description)
                .ogType("article")
                .siteName(siteName(language, settings))
                .url(frontend("/news/" + segment(news.getSlug())))
                .locale(locale(news.getTitle(), language))
                .imageUrl(image.url()).imageWidth(image.width()).imageHeight(image.height())
                .imageAlt(news.getTitle())
                .publishedTime(isoTime(news.getPublishedAt()))
                .modifiedTime(isoTime(news.getUpdatedAt()))
                .section(news.getCategory().getName())
                .tags(List.copyOf(tags))
                .build();
    }

    private PreviewDto categoryPreview(Category category, String language, Map<String, String> settings) {
        String description = truncate(plainText(category.getDescription()));
        if (description.isEmpty()) {
            description = truncate(plainText(setting(settings, "meta_description_", language)));
        }
        Image image = image(settings, null);
        return PreviewDto.builder()
                .language(language)
                .pageTitle(pageTitle(category.getName(), language, settings))
                .title(category.getName())
                .description(description)
                .ogType("website")
                .siteName(siteName(language, settings))
                .url(frontend("/category/" + segment(category.getSlug())))
                .locale(locale(category.getName(), language))
                .imageUrl(image.url()).imageWidth(image.width()).imageHeight(image.height())
                .imageAlt(category.getName())
                .tags(List.of())
                .build();
    }

    private PreviewDto homePreview(String language, Map<String, String> settings) {
        String site = siteName(language, settings);
        String description = truncate(plainText(setting(settings, "meta_description_", language)));
        if (description.isEmpty()) {
            description = truncate(plainText(setting(settings, "tagline_", language)));
        }
        Image image = image(settings, null);
        return PreviewDto.builder()
                .language(language)
                .pageTitle(site)
                .title(site)
                .description(description)
                .ogType("website")
                .siteName(site)
                .url(frontend("/"))
                .locale(locale(site, language))
                .imageUrl(image.url()).imageWidth(image.width()).imageHeight(image.height())
                .imageAlt(site)
                .tags(List.of())
                .build();
    }

    // ---- language, names, titles ----

    // en or km; a missing or unknown value is the default language of the site
    private String language(String lang, Map<String, String> settings) {
        String wanted = lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
        if (LANGUAGES.contains(wanted)) {
            return wanted;
        }
        String fallback = settings.get("default_language");
        return fallback != null && LANGUAGES.contains(fallback) ? fallback : "en";
    }

    // the Khmer name when the language is km and one is set, else the English one
    private String siteName(String language, Map<String, String> settings) {
        String name = setting(settings, "site_name_", language);
        return name.isBlank() ? setting(settings, "site_name_", "en") : name;
    }

    private String pageTitle(String page, String language, Map<String, String> settings) {
        String format = settings.get("title_format");
        if (format == null || format.isBlank()) {
            return page;
        }
        return format.replace("{page}", page).replace("{site}", siteName(language, settings));
    }

    // Khmer letters in the title mean a Khmer page whatever the language of the link
    private String locale(String title, String language) {
        return containsKhmer(title) || "km".equals(language) ? "km_KH" : "en_US";
    }

    static boolean containsKhmer(String text) {
        return text != null && text.codePoints().anyMatch(c ->
                (c >= 0x1780 && c <= 0x17FF) || (c >= 0x19E0 && c <= 0x19FF)); // Khmer and Khmer Symbols
    }

    private String setting(Map<String, String> settings, String prefix, String language) {
        String value = settings.get(prefix + language);
        return value == null ? "" : value.trim();
    }

    // ---- text ----

    // Article text is stored as text but may hold HTML: tags go, entities are decoded, white space is one space.
    // The result is plain text; the template escapes it again when it is written.
    static String plainText(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("(?s)<[^>]*>", " ");
        text = HtmlUtils.htmlUnescape(text);
        return text.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
    }

    // at most DESCRIPTION_MAX characters (code points); cut at a space when there is one near the end
    // (Khmer has no spaces between words, so it is cut where the limit falls) and marked with an ellipsis
    static String truncate(String text) {
        if (text.codePointCount(0, text.length()) <= DESCRIPTION_MAX) {
            return text;
        }
        String cut = text.substring(0, text.offsetByCodePoints(0, DESCRIPTION_MAX - 1));
        int space = cut.lastIndexOf(' ');
        if (space >= DESCRIPTION_MAX * 3 / 5) {
            cut = cut.substring(0, space);
        }
        return cut.stripTrailing() + "…";
    }

    private String isoTime(LocalDateTime time) {
        return time == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time.atZone(ZoneId.systemDefault()));
    }

    // ---- urls and images ----

    private String frontend(String path) {
        return trimTrailingSlash(appProperty.getFrontendUrl()) + path;
    }

    private String segment(String slug) {
        return UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record Image(String url, Integer width, Integer height) {
        static final Image NONE = new Image(null, null, null);
    }

    // the first that exists: the article thumbnail, the share image of the settings, the site logo
    private Image image(Map<String, String> settings, String thumbnail) {
        for (String path : new String[]{thumbnail, settings.get("share_image_url"), settings.get("logo_url")}) {
            String url = absoluteUrl(path);
            if (url != null) {
                Integer[] size = localSize(path);
                return new Image(url, size == null ? null : size[0], size == null ? null : size[1]);
            }
        }
        return Image.NONE;
    }

    // An uploaded image is a path (/uploads/x.png) on this backend, so it gets app.public-base-url; with no base url it
    // cannot be made absolute and is skipped. A full address stays as it is, but http becomes https (except localhost).
    String absoluteUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String value = path.trim();
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (value.regionMatches(true, 0, "https://", 0, 8)) {
            return value;
        }
        if (value.regionMatches(true, 0, "http://", 0, 7)) {
            String host = value.substring(7).split("[/:?#]", 2)[0].toLowerCase(Locale.ROOT);
            boolean local = host.equals("localhost") || host.equals("127.0.0.1");
            return local ? value : "https://" + value.substring(7);
        }
        String base = trimTrailingSlash(appProperty.getPublicBaseUrl());
        if (base.isEmpty()) {
            return null;
        }
        return base + (value.startsWith("/") ? "" : "/") + value;
    }

    // width and height of an image in the upload folder, read from the file header; null when not known (an address
    // elsewhere, a type Java cannot read such as WebP, a missing file). Never leaves the upload folder.
    private Integer[] localSize(String path) {
        if (path == null || !path.trim().startsWith(UPLOADS)) {
            return null;
        }
        try {
            Path dir = Paths.get(appProperty.getUpload().getDir()).toAbsolutePath().normalize();
            Path file = dir.resolve(path.trim().substring(UPLOADS.length())).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                return null;
            }
            try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    return null;
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    return new Integer[]{reader.getWidth(0), reader.getHeight(0)};
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            log.debug("Image size not read for {}: {}", path, e.getMessage());
            return null;
        }
    }

    // ---- cache ----

    private static String newsKey(String slug, String language) {
        return "news:" + slug + ":" + language;
    }

    private Optional<PreviewDto> cached(String key, Supplier<Optional<PreviewDto>> loader) {
        Cache cache = cacheManager.getCache(CacheConfig.PREVIEW_CACHE);
        if (cache == null) {
            return loader.get();
        }
        PreviewDto hit = cache.get(key, PreviewDto.class);
        if (hit != null) {
            return Optional.of(hit);
        }
        Optional<PreviewDto> loaded = loader.get();
        loaded.ifPresent(page -> cache.put(key, page));
        return loaded;
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
}
