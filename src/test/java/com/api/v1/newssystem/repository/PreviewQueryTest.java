package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.entity.NewsTag;
import com.api.v1.newssystem.model.entity.Tag;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.response.PreviewDto;
import com.api.v1.newssystem.service.PreviewService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The link previews against the configured MySQL: only a PUBLISHED article of an active category has one, and opening
 * it changes neither the view count nor the view log. Rolled back; every row has a unique token.
 */
@SpringBootTest(properties = {"app.frontend-url=https://ponleunews.com", "app.public-base-url=https://api.ponleunews.com"})
@Transactional
class PreviewQueryTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private PreviewService previewService;
    @Autowired
    private NewsViewRepository newsViewRepository;

    private String token;
    private AppUser author;
    private Category category;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString().substring(0, 8);
        author = new AppUser();
        author.setEmail("preview-test-" + token + "@news.local");
        author.setPassword("x");
        author.setRole(Roles.AUTHOR);
        author.setUsername("Author " + token);
        em.persist(author);
        category = new Category();
        category.setName("Preview " + token);
        category.setSlug("preview-" + token);
        em.persist(category);
    }

    private News article(String slugPart, NewsStatus status, String title) {
        News news = new News();
        news.setTitle(title);
        news.setSlug(slugPart + "-" + token);
        news.setSummary("Summary of " + slugPart);
        news.setContent("<p>Body</p>");
        news.setStatus(status);
        news.setAuthor(author);
        news.setCategory(category);
        news.setPublishedAt(status == NewsStatus.PUBLISHED ? LocalDateTime.now() : null);
        news.setViewCount(41);
        em.persist(news);
        return news;
    }

    @Test
    void publishedArticle_hasAPreview_withItsTagsAndCategory() {
        News news = article("live", NewsStatus.PUBLISHED, "កីឡា \"Cup\" <final>");
        Tag tag = new Tag();
        tag.setName("Tag " + token);
        tag.setSlug("tag-" + token);
        em.persist(tag);
        NewsTag link = new NewsTag();
        link.setNews(news);
        link.setTag(tag);
        em.persist(link);
        em.flush();
        em.clear();

        PreviewDto preview = previewService.news("live-" + token, "en").orElseThrow();

        assertThat(preview.getTitle()).isEqualTo("កីឡា \"Cup\" <final>"); // as stored; the template escapes it
        assertThat(preview.getLocale()).isEqualTo("km_KH");
        assertThat(preview.getSection()).isEqualTo("Preview " + token);
        assertThat(preview.getTags()).containsExactly("Tag " + token);
        assertThat(preview.getUrl()).isEqualTo("https://ponleunews.com/news/live-" + token);
    }

    @Test
    void draftPendingRejectedAndArchived_haveNoPreview() {
        for (NewsStatus status : new NewsStatus[]{NewsStatus.DRAFT, NewsStatus.PENDING, NewsStatus.REJECTED, NewsStatus.ARCHIVED}) {
            article(status.name().toLowerCase(), status, "Secret " + status);
        }
        em.flush();
        em.clear();

        for (String part : new String[]{"draft", "pending", "rejected", "archived", "unknown"}) {
            assertThat(previewService.news(part + "-" + token, "en")).as(part).isEmpty();
        }
    }

    @Test
    void openingAPreview_doesNotChangeTheViewCount_orTheViewLog() {
        News news = article("counted", NewsStatus.PUBLISHED, "Counted");
        em.flush();
        em.clear();
        long logBefore = newsViewRepository.count();

        previewService.news("counted-" + token, "en");
        previewService.news("counted-" + token, "km");
        em.flush();
        em.clear();

        assertThat(em.find(News.class, news.getId()).getViewCount()).isEqualTo(41);
        assertThat(newsViewRepository.count()).isEqualTo(logBefore);
    }
}
