package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.CacheConfig;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.entity.NewsTag;
import com.api.v1.newssystem.model.entity.Tag;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.response.PreviewDto;
import com.api.v1.newssystem.repository.CategoryRepository;
import com.api.v1.newssystem.repository.NewsRepository;
import com.api.v1.newssystem.repository.NewsTagRepository;
import com.api.v1.newssystem.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The content of the link previews: the text, the Khmer locale, the image fallback, the cache and that nothing here
 * can count a view (this service has no view repository, and the only article query it uses is the read-only one).
 */
@ExtendWith(MockitoExtension.class)
class PreviewServiceImplTest {
    private static final String FRONTEND = "https://ponleunews.com";
    private static final String BACKEND = "https://api.ponleunews.com";

    @Mock
    private NewsRepository newsRepository;
    @Mock
    private NewsTagRepository newsTagRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SystemSettingService systemSettingService;

    private final Map<String, String> settings = new HashMap<>();
    private PreviewServiceImpl service;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        AppProperty property = new AppProperty();
        property.setFrontendUrl(FRONTEND + "/");  // a trailing slash is tolerated
        property.setPublicBaseUrl(BACKEND);
        cacheManager = new ConcurrentMapCacheManager(CacheConfig.PREVIEW_CACHE);

        settings.put("site_name_en", "Ponleu News");
        settings.put("site_name_km", "ព័ត៌មានពន្លឺ");
        settings.put("default_language", "en");
        settings.put("title_format", "{page} | {site}");
        settings.put("share_image_url", "");
        settings.put("logo_url", "");
        lenient().when(systemSettingService.getPublic()).thenReturn(settings);

        service = new PreviewServiceImpl(newsRepository, newsTagRepository, categoryRepository, systemSettingService, property, cacheManager);
    }

    private News article(String title, String summary, String thumbnail) {
        Category category = new Category();
        category.setName("Sports");
        category.setSlug("sports");
        News news = new News();
        news.setId(7L);
        news.setTitle(title);
        news.setSlug("my-article");
        news.setSummary(summary);
        news.setContent("<p>Body text</p>");
        news.setThumbnail(thumbnail);
        news.setStatus(NewsStatus.PUBLISHED);
        news.setCategory(category);
        news.setPublishedAt(LocalDateTime.of(2026, 3, 1, 9, 30));
        news.setUpdatedAt(LocalDateTime.of(2026, 3, 2, 10, 0));
        when(newsRepository.findPublishedBySlug("my-article")).thenReturn(Optional.of(news));
        return news;
    }

    private PreviewDto previewOf(News news) {
        return service.news(news.getSlug(), null).orElseThrow();
    }

    // ---------------- the tags of an article ----------------

    @Test
    void article_hasTheOpenGraphValues() {
        News news = article("Cup final tonight", "<p>The <b>big</b> match &amp; more</p>", "/uploads/a.png");
        Tag tag = new Tag();
        tag.setName("Football");
        NewsTag link = new NewsTag();
        link.setTag(tag);
        when(newsTagRepository.findAllWithTagByNewsIdIn(List.of(7L))).thenReturn(List.of(link));

        PreviewDto preview = previewOf(news);

        assertThat(preview.getOgType()).isEqualTo("article");
        assertThat(preview.getTitle()).isEqualTo("Cup final tonight");
        assertThat(preview.getPageTitle()).isEqualTo("Cup final tonight | Ponleu News");
        assertThat(preview.getSiteName()).isEqualTo("Ponleu News");
        assertThat(preview.getDescription()).isEqualTo("The big match & more"); // tags stripped, entity decoded
        assertThat(preview.getUrl()).isEqualTo(FRONTEND + "/news/my-article");   // the FRONTEND address, one slash
        assertThat(preview.getLocale()).isEqualTo("en_US");
        assertThat(preview.getImageUrl()).isEqualTo(BACKEND + "/uploads/a.png");
        assertThat(preview.getImageAlt()).isEqualTo("Cup final tonight");
        assertThat(preview.getSection()).isEqualTo("Sports");
        assertThat(preview.getTags()).containsExactly("Football");
        assertThat(preview.getPublishedTime()).startsWith("2026-03-01T09:30:00");
        assertThat(preview.getModifiedTime()).startsWith("2026-03-02T10:00:00");
    }

    @Test
    void description_isPlainText_andAtMost200Characters() {
        News news = article("T", "<p>" + "word ".repeat(100) + "</p><script>alert(1)</script>", null);

        String description = previewOf(news).getDescription();

        assertThat(description.codePointCount(0, description.length())).isLessThanOrEqualTo(200);
        assertThat(description).endsWith("…").doesNotContain("<").doesNotContain(">");
    }

    @Test
    void description_withoutASummary_comesFromTheContent() {
        News news = article("T", "   ", null);

        assertThat(previewOf(news).getDescription()).isEqualTo("Body text");
    }

    @Test
    void khmerDescription_isCutAtTheLimit_withoutBreakingACharacter() {
        News news = article("ចំណងជើង", "ព័ត៌មាន".repeat(80), null);

        String description = previewOf(news).getDescription();

        assertThat(description.codePointCount(0, description.length())).isLessThanOrEqualTo(200);
        assertThat(description).startsWith("ព័ត៌មាន");
    }

    // ---------------- language ----------------

    @Test
    void khmerTitle_isKhmerLocale() {
        assertThat(previewOf(article("ព័ត៌មានថ្មី", "x", null)).getLocale()).isEqualTo("km_KH");
    }

    @Test
    void englishTitle_isEnglishLocale_unlessTheLanguageIsKhmer() {
        article("Plain title", "x", null);

        assertThat(service.news("my-article", "en").orElseThrow().getLocale()).isEqualTo("en_US");
        PreviewDto km = service.news("my-article", "km").orElseThrow();
        assertThat(km.getLocale()).isEqualTo("km_KH");
        assertThat(km.getSiteName()).isEqualTo("ព័ត៌មានពន្លឺ"); // the Khmer site name
    }

    @Test
    void unknownLanguage_meansTheDefaultLanguageOfTheSettings() {
        article("Plain title", "x", null);
        settings.put("default_language", "km");

        assertThat(service.news("my-article", "fr").orElseThrow().getLanguage()).isEqualTo("km");
    }

    @Test
    void khmerSiteNameMissing_fallsBackToTheEnglishOne() {
        article("Plain title", "x", null);
        settings.put("site_name_km", "");

        assertThat(service.news("my-article", "km").orElseThrow().getSiteName()).isEqualTo("Ponleu News");
    }

    // ---------------- the image: thumbnail, then share image, then logo ----------------

    @Test
    void image_isTheThumbnail_whenThereIsOne() {
        settings.put("share_image_url", "/uploads/share.png");
        settings.put("logo_url", "/uploads/logo.png");

        assertThat(previewOf(article("T", "x", "/uploads/thumb.png")).getImageUrl()).isEqualTo(BACKEND + "/uploads/thumb.png");
    }

    @Test
    void image_withoutAThumbnail_isTheShareImage() {
        settings.put("share_image_url", "/uploads/share.png");
        settings.put("logo_url", "/uploads/logo.png");

        assertThat(previewOf(article("T", "x", null)).getImageUrl()).isEqualTo(BACKEND + "/uploads/share.png");
        assertThat(service.news("my-article", "km").orElseThrow().getImageUrl()).isEqualTo(BACKEND + "/uploads/share.png");
    }

    @Test
    void image_withoutAThumbnailAndShareImage_isTheLogo() {
        settings.put("logo_url", "/uploads/logo.png");

        assertThat(previewOf(article("T", "x", "  ")).getImageUrl()).isEqualTo(BACKEND + "/uploads/logo.png");
    }

    @Test
    void image_whenThereIsNone_isNull() {
        assertThat(previewOf(article("T", "x", null)).getImageUrl()).isNull();
    }

    @Test
    void imageAddress_isAbsolute_andHttpsIsPreferred() {
        assertThat(service.absoluteUrl("/uploads/a.png")).isEqualTo(BACKEND + "/uploads/a.png");
        assertThat(service.absoluteUrl("uploads/a.png")).isEqualTo(BACKEND + "/uploads/a.png");
        assertThat(service.absoluteUrl("https://cdn.example.com/a.png")).isEqualTo("https://cdn.example.com/a.png");
        assertThat(service.absoluteUrl("http://cdn.example.com/a.png")).isEqualTo("https://cdn.example.com/a.png");
        assertThat(service.absoluteUrl("//cdn.example.com/a.png")).isEqualTo("https://cdn.example.com/a.png");
        assertThat(service.absoluteUrl("http://localhost:8080/a.png")).isEqualTo("http://localhost:8080/a.png");
        assertThat(service.absoluteUrl(" ")).isNull();
        assertThat(service.absoluteUrl(null)).isNull();
    }

    @Test
    void uploadedImage_withoutABaseUrl_cannotBeAbsolute_soTheNextImageIsUsed() {
        AppProperty property = new AppProperty();
        property.setFrontendUrl(FRONTEND);
        service = new PreviewServiceImpl(newsRepository, newsTagRepository, categoryRepository, systemSettingService, property, cacheManager);
        settings.put("share_image_url", "https://cdn.example.com/share.png");

        assertThat(previewOf(article("T", "x", "/uploads/thumb.png")).getImageUrl()).isEqualTo("https://cdn.example.com/share.png");
    }

    // ---------------- not published ----------------

    @Test
    void anArticleThatIsNotPublished_hasNoPreview_andIsNotCached() {
        when(newsRepository.findPublishedBySlug("draft")).thenReturn(Optional.empty());

        assertThat(service.news("draft", null)).isEmpty();
        assertThat(service.news("draft", null)).isEmpty();

        verify(newsRepository, times(2)).findPublishedBySlug("draft"); // nothing kept
        assertThat(cacheManager.getCache(CacheConfig.PREVIEW_CACHE).get("news:draft:en")).isNull();
    }

    @Test
    void aBlankOrHugeSlug_hasNoPreview_withoutAQuery() {
        assertThat(service.news(" ", null)).isEmpty();
        assertThat(service.news("a".repeat(301), null)).isEmpty();

        verifyNoMoreInteractions(newsRepository);
    }

    // ---------------- the cache ----------------

    @Test
    void aFoundPreview_isCachedPerSlugAndLanguage() {
        article("Plain title", "x", null);

        service.news("my-article", "en");
        service.news("my-article", "en");
        service.news("my-article", "km");

        verify(newsRepository, times(2)).findPublishedBySlug("my-article"); // en once, km once
    }

    @Test
    void evictNews_forgetsBothLanguages() {
        article("Plain title", "x", null);
        service.news("my-article", "en");
        service.news("my-article", "km");

        service.evictNews("my-article");
        service.news("my-article", "en");
        service.news("my-article", "km");

        verify(newsRepository, times(4)).findPublishedBySlug("my-article");
    }

    // ---------------- a preview never counts as a view ----------------

    @Test
    void readingAPreview_onlyReadsTheArticle() {
        News news = article("T", "x", null);

        previewOf(news);

        verify(newsRepository).findPublishedBySlug("my-article");
        verify(newsRepository, never()).incrementViewCount(any());
        verify(newsRepository, never()).save(any());
        verifyNoMoreInteractions(newsRepository);
    }

    // ---------------- home and category ----------------

    @Test
    void home_isAWebsitePreview_withTheSiteNameAndDescription() {
        settings.put("meta_description_en", "All the news");

        PreviewDto home = service.home(null);

        assertThat(home.getOgType()).isEqualTo("website");
        assertThat(home.getTitle()).isEqualTo("Ponleu News");
        assertThat(home.getDescription()).isEqualTo("All the news");
        assertThat(home.getUrl()).isEqualTo(FRONTEND + "/");
        assertThat(home.getTags()).isEmpty();
        assertThat(home.getPublishedTime()).isNull();
    }

    @Test
    void category_isAWebsitePreview_ofAnActiveCategory() {
        Category category = new Category();
        category.setName("Sports");
        category.setSlug("sports");
        category.setDescription("Matches & results");
        when(categoryRepository.findBySlugAndActiveTrue("sports")).thenReturn(Optional.of(category));
        when(categoryRepository.findBySlugAndActiveTrue("gone")).thenReturn(Optional.empty());

        PreviewDto preview = service.category("sports", null).orElseThrow();

        assertThat(preview.getOgType()).isEqualTo("website");
        assertThat(preview.getTitle()).isEqualTo("Sports");
        assertThat(preview.getDescription()).isEqualTo("Matches & results");
        assertThat(preview.getUrl()).isEqualTo(FRONTEND + "/category/sports");
        assertThat(service.category("gone", null)).isEmpty();
    }
}
