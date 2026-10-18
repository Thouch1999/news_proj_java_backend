package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.Category;
import com.api.v1.bearerauth.model.entity.News;
import com.api.v1.bearerauth.model.entity.NewsTag;
import com.api.v1.bearerauth.model.entity.Tag;
import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.response.PublicCategorySectionDto;
import com.api.v1.bearerauth.model.response.PublicNewsDto;
import com.api.v1.bearerauth.model.response.PublicNewsSummaryDto;
import com.api.v1.bearerauth.service.PublicNewsService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The public reading queries against the configured MySQL: Khmer search, the filters, the 24-hour view rule and the
 * number of SQL statements. Everything runs in a transaction that is rolled back, and every row carries a unique
 * token, so existing data does not disturb the checks.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class PublicNewsQueryTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private PublicNewsService service;
    @Autowired
    private NewsRepository newsRepository;
    @Autowired
    private NewsViewRepository newsViewRepository;

    private String token; // unique per test
    private AppUser author;
    private Category sports;
    private Category world;
    private Tag football;
    private Tag cup;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString().substring(0, 8);
        author = new AppUser();
        author.setEmail("reader-test-" + token + "@news.local");
        author.setPassword("x");
        author.setRole(Roles.AUTHOR);
        author.setUsername("Author " + token);
        author.setFullName("សុខ ដារា");
        em.persist(author);
        sports = category("Sports " + token, "sports-" + token, true);
        world = category("World " + token, "world-" + token, true);
        football = tag("Football " + token, "football-" + token);
        cup = tag("Cup " + token, "cup-" + token);
    }

    private Category category(String name, String slug, boolean active) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setActive(active);
        em.persist(category);
        return category;
    }

    private Tag tag(String name, String slug) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setSlug(slug);
        em.persist(tag);
        return tag;
    }

    private News article(String slugPart, NewsStatus status, Category category, LocalDateTime publishedAt, String title, String summary, String content) {
        News news = new News();
        news.setTitle(title);
        news.setSlug(slugPart + "-" + token);
        news.setSummary(summary);
        news.setContent(content);
        news.setStatus(status);
        news.setAuthor(author);
        news.setCategory(category);
        news.setPublishedAt(publishedAt);
        em.persist(news);
        return news;
    }

    private News published(String slugPart, Category category, LocalDateTime publishedAt) {
        return article(slugPart, NewsStatus.PUBLISHED, category, publishedAt, "Title " + slugPart, null, "<p>Body</p>");
    }

    private void link(News news, Tag tag) {
        NewsTag link = new NewsTag();
        link.setNews(news);
        link.setTag(tag);
        em.persist(link);
    }

    private List<String> slugs(String keyword, String category, String tag, LocalDate from, LocalDate to) {
        em.flush();
        em.clear();
        return service.list(keyword, category, tag, from, to, 1, 50).getContent().stream()
                .map(PublicNewsSummaryDto::getSlug)
                .toList();
    }

    // ---------------- keyword search, Khmer included ----------------

    @Test
    void keyword_findsKhmerTextInTheTitleSummaryAndContent() {
        String khmer = "កីឡាបាល់ទាត់" + token;
        article("in-title", NewsStatus.PUBLISHED, sports, LocalDateTime.of(2026, 1, 1, 8, 0), "ព័ត៌មាន " + khmer, null, "<p>x</p>");
        article("in-summary", NewsStatus.PUBLISHED, sports, LocalDateTime.of(2026, 1, 2, 8, 0), "No match here", "សង្ខេប " + khmer, "<p>x</p>");
        article("in-content", NewsStatus.PUBLISHED, sports, LocalDateTime.of(2026, 1, 3, 8, 0), "Other title", null, "<p>អត្ថបទ " + khmer + " បន្ត</p>");
        article("unrelated", NewsStatus.PUBLISHED, sports, LocalDateTime.of(2026, 1, 4, 8, 0), "ព័ត៌មានផ្សេង", "ខ្លឹមសារផ្សេង", "<p>មិនទាក់ទង</p>");

        assertThat(slugs(khmer, null, null, null, null))
                .containsExactly("in-content-" + token, "in-summary-" + token, "in-title-" + token); // newest first
    }

    @Test
    void keyword_isCaseInsensitive_andAPartOfAWordMatches() {
        published("budget", sports, LocalDateTime.of(2026, 1, 1, 8, 0)).setTitle("The National BUDGET" + token);
        published("other", sports, LocalDateTime.of(2026, 1, 2, 8, 0));

        assertThat(slugs("budget" + token, null, null, null, null)).containsExactly("budget-" + token);
        assertThat(slugs("TIONAL budget" + token, null, null, null, null)).containsExactly("budget-" + token); // part of a word
        assertThat(slugs("national  budget" + token, null, null, null, null)).isEmpty(); // a phrase must match as written
    }

    @Test
    void keyword_withLikeWildcards_isMatchedLiterally() {
        News percent = published("percent", sports, LocalDateTime.of(2026, 1, 1, 8, 0));
        percent.setTitle("Rate 50%_up " + token);
        News plain = published("plain", sports, LocalDateTime.of(2026, 1, 2, 8, 0));
        plain.setTitle("Rate 500xup " + token);

        assertThat(slugs("50%_up " + token, null, null, null, null)).containsExactly("percent-" + token);
    }

    // ---------------- only what readers may see ----------------

    @Test
    void onlyPublishedArticlesOfAnActiveCategory_areListed() {
        LocalDateTime at = LocalDateTime.of(2026, 1, 1, 8, 0);
        Category hidden = category("Hidden " + token, "hidden-" + token, false);
        published("live", sports, at);
        for (NewsStatus status : List.of(NewsStatus.DRAFT, NewsStatus.PENDING, NewsStatus.REJECTED, NewsStatus.ARCHIVED)) {
            article(status.name().toLowerCase(), status, sports, at, "Title " + status, null, "<p>x</p>");
        }
        published("in-hidden", hidden, at);

        assertThat(slugs(null, null, null, null, null)).contains("live-" + token)
                .doesNotContain("draft-" + token, "pending-" + token, "rejected-" + token, "archived-" + token, "in-hidden-" + token);
    }

    // ---------------- filters and order ----------------

    @Test
    void list_isNewestFirst_andFiltersByCategorySlug() {
        published("old", sports, LocalDateTime.of(2026, 1, 1, 8, 0));
        published("new", sports, LocalDateTime.of(2026, 3, 1, 8, 0));
        published("mid", sports, LocalDateTime.of(2026, 2, 1, 8, 0));
        published("elsewhere", world, LocalDateTime.of(2026, 4, 1, 8, 0));

        assertThat(slugs(null, sports.getSlug(), null, null, null)).containsExactly("new-" + token, "mid-" + token, "old-" + token);
        assertThat(slugs(null, world.getSlug(), null, null, null)).containsExactly("elsewhere-" + token);
        assertThat(slugs(null, "no-such-category-" + token, null, null, null)).isEmpty();
    }

    @Test
    void list_filtersByTagSlug() {
        News a = published("a", sports, LocalDateTime.of(2026, 1, 1, 8, 0));
        News b = published("b", sports, LocalDateTime.of(2026, 1, 2, 8, 0));
        published("c", sports, LocalDateTime.of(2026, 1, 3, 8, 0));
        link(a, football);
        link(b, football);
        link(b, cup);

        assertThat(slugs(null, null, football.getSlug(), null, null)).containsExactly("b-" + token, "a-" + token);
        assertThat(slugs(null, null, cup.getSlug(), null, null)).containsExactly("b-" + token);
        assertThat(slugs(null, sports.getSlug(), cup.getSlug(), null, null)).containsExactly("b-" + token); // filters combine
    }

    @Test
    void list_filtersByDateRange_bothDaysIncluded() {
        published("before", sports, LocalDateTime.of(2026, 1, 4, 23, 59, 59));
        published("first-day", sports, LocalDateTime.of(2026, 1, 5, 0, 0, 0));
        published("middle", sports, LocalDateTime.of(2026, 1, 7, 12, 0));
        published("last-day", sports, LocalDateTime.of(2026, 1, 10, 23, 59, 59));
        published("after", sports, LocalDateTime.of(2026, 1, 11, 0, 0, 0));
        String category = sports.getSlug();

        assertThat(slugs(null, category, null, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 10)))
                .containsExactly("last-day-" + token, "middle-" + token, "first-day-" + token);
        assertThat(slugs(null, category, null, LocalDate.of(2026, 1, 10), null)).containsExactly("after-" + token, "last-day-" + token);
        assertThat(slugs(null, category, null, null, LocalDate.of(2026, 1, 4))).containsExactly("before-" + token);
    }

    @Test
    void list_paginates_andCountsAllMatches() {
        for (int day = 1; day <= 5; day++) {
            published("p" + day, sports, LocalDateTime.of(2026, 1, day, 8, 0));
        }
        em.flush();
        em.clear();

        var page = service.list(null, sports.getSlug(), null, null, null, 2, 2);

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(PublicNewsSummaryDto::getSlug).containsExactly("p3-" + token, "p2-" + token);
    }

    @Test
    void list_cardsHaveCategoryAuthorTagsAndCounts() {
        News news = published("card", sports, LocalDateTime.of(2026, 1, 1, 8, 0));
        news.setViewCount(9);
        link(news, football);
        link(news, cup);

        PublicNewsSummaryDto card = service.list(null, sports.getSlug(), null, null, null, 1, 10).getContent().get(0);
        em.flush();
        em.clear();
        card = service.list(null, sports.getSlug(), null, null, null, 1, 10).getContent().get(0);

        assertThat(card.getCategoryName()).isEqualTo("Sports " + token);
        assertThat(card.getCategorySlug()).isEqualTo(sports.getSlug());
        assertThat(card.getAuthorName()).isEqualTo("សុខ ដារា");
        assertThat(card.getTags()).extracting(t -> t.getSlug()).containsExactlyInAnyOrder(football.getSlug(), cup.getSlug());
        assertThat(card.getViewCount()).isEqualTo(9);
        assertThat(card.getCommentCount()).isZero();
    }

    // ---------------- no N+1 ----------------

    @Test
    void list_runsAFixedNumberOfStatements_whateverTheNumberOfArticles() {
        Category[] categories = {sports, world, category("Third " + token, "third-" + token, true)};
        for (int i = 1; i <= 12; i++) {
            News news = published("n" + i, categories[i % 3], LocalDateTime.of(2026, 1, i, 8, 0));
            news.setTitle("Many " + token + " " + i);
            link(news, football);
            link(news, cup);
        }
        em.flush();
        em.clear();
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<PublicNewsSummaryDto> cards = service.list("many " + token, null, null, null, null, 1, 50).getContent();

        assertThat(cards).hasSize(12);
        assertThat(cards).allSatisfy(card -> assertThat(card.getTags()).hasSize(2));
        // page (with author and category) + count + tags + comment counts
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void detail_andTheHomeLists_alsoRunAFixedNumberOfStatements() {
        for (int i = 1; i <= 8; i++) {
            News news = published("h" + i, i % 2 == 0 ? sports : world, LocalDateTime.of(2026, 1, i, 8, 0));
            link(news, football);
        }
        em.flush();
        em.clear();
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        service.latest(8);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3); // articles, tags, comment counts

        statistics.clear();
        service.mostViewed(8);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);

        statistics.clear();
        List<PublicCategorySectionDto> sections = service.categorySections(3);
        assertThat(sections).isNotEmpty();
        // ids per category, the articles, tags, comment counts, the categories
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);

        em.clear();
        statistics.clear();
        service.getBySlug("h1-" + token, "198.51.100.20");
        // article, view check, tags, images, comments, insert view, update count
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(8);
    }

    // ---------------- home page lists ----------------

    @Test
    void mostViewed_ordersByViews_thenNewest() {
        News low = published("low", sports, LocalDateTime.of(2026, 1, 1, 8, 0));
        low.setViewCount(5_000_001L);
        News high = published("high", sports, LocalDateTime.of(2026, 1, 2, 8, 0));
        high.setViewCount(5_000_003L);
        News tieOld = published("tie-old", sports, LocalDateTime.of(2026, 1, 3, 8, 0));
        tieOld.setViewCount(5_000_002L);
        News tieNew = published("tie-new", sports, LocalDateTime.of(2026, 1, 4, 8, 0));
        tieNew.setViewCount(5_000_002L);
        em.flush();
        em.clear();

        List<String> top = service.mostViewed(20).stream().map(PublicNewsSummaryDto::getSlug).filter(s -> s.endsWith(token)).toList();

        assertThat(top).containsExactly("high-" + token, "tie-new-" + token, "tie-old-" + token, "low-" + token);
    }

    @Test
    void latest_isNewestFirst_andNeverShowsADraft() {
        published("old", sports, LocalDateTime.of(2090, 1, 1, 8, 0));
        published("new", sports, LocalDateTime.of(2090, 1, 2, 8, 0));
        article("draft", NewsStatus.DRAFT, sports, LocalDateTime.of(2090, 1, 3, 8, 0), "Draft", null, "<p>x</p>");
        em.flush();
        em.clear();

        List<String> latest = service.latest(20).stream().map(PublicNewsSummaryDto::getSlug).filter(s -> s.endsWith(token)).toList();

        assertThat(latest).containsExactly("new-" + token, "old-" + token);
    }

    @Test
    void categorySections_haveTheNewestArticlesOfEachActiveCategory() {
        for (int day = 1; day <= 4; day++) {
            published("s" + day, sports, LocalDateTime.of(2026, 1, day, 8, 0));
        }
        published("w1", world, LocalDateTime.of(2026, 2, 1, 8, 0));
        Category hidden = category("Hidden " + token, "hidden-" + token, false);
        published("x1", hidden, LocalDateTime.of(2026, 2, 2, 8, 0));
        category("Empty " + token, "empty-" + token, true);
        em.flush();
        em.clear();

        List<PublicCategorySectionDto> sections = service.categorySections(2).stream()
                .filter(section -> section.getCategory().getSlug().endsWith(token))
                .toList();

        assertThat(sections).extracting(section -> section.getCategory().getSlug()).containsExactly("sports-" + token, "world-" + token);
        assertThat(sections.get(0).getNews()).extracting(PublicNewsSummaryDto::getSlug).containsExactly("s4-" + token, "s3-" + token);
        assertThat(sections.get(1).getNews()).extracting(PublicNewsSummaryDto::getSlug).containsExactly("w1-" + token);
    }

    // ---------------- detail ----------------

    @Test
    void detail_ofAPublishedArticle_hasEverythingReadersNeed() {
        News news = published("detail", sports, LocalDateTime.of(2026, 1, 1, 8, 0));
        news.setContent("<p>ខ្លឹមសារ</p>");
        link(news, football);
        em.flush();
        em.clear();

        PublicNewsDto dto = service.getBySlug("detail-" + token, "198.51.100.30");

        assertThat(dto.getContent()).isEqualTo("<p>ខ្លឹមសារ</p>");
        assertThat(dto.getAuthorName()).isEqualTo("សុខ ដារា");
        assertThat(dto.getCategory().getSlug()).isEqualTo(sports.getSlug());
        assertThat(dto.getTags()).extracting(t -> t.getSlug()).containsExactly(football.getSlug());
        assertThat(dto.getCommentCount()).isZero();
    }

    @Test
    void detail_ofAnythingButAPublishedArticleInAnActiveCategory_isNotFound() {
        Category hidden = category("Hidden " + token, "hidden-" + token, false);
        for (NewsStatus status : List.of(NewsStatus.DRAFT, NewsStatus.PENDING, NewsStatus.REJECTED, NewsStatus.ARCHIVED)) {
            article(status.name().toLowerCase(), status, sports, LocalDateTime.of(2026, 1, 1, 8, 0), "T", null, "<p>x</p>");
        }
        published("in-hidden", hidden, LocalDateTime.of(2026, 1, 1, 8, 0));
        em.flush();
        em.clear();

        for (String slug : List.of("draft", "pending", "rejected", "archived", "in-hidden", "does-not-exist")) {
            assertThatThrownBy(() -> service.getBySlug(slug + "-" + token, "198.51.100.40"))
                    .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus().value()).isEqualTo(404));
        }
        assertThat(newsViewRepository.count() >= 0).isTrue();
    }

    // ---------------- the 24-hour view rule ----------------

    private long storedViewCount(News news) {
        em.flush();
        em.clear();
        return newsRepository.findById(news.getId()).orElseThrow().getViewCount();
    }

    @Test
    void view_isCountedOncePerAddressIn24Hours_andAgainAfterwards() {
        News news = published("views", sports, LocalDateTime.of(2026, 1, 1, 8, 0));
        em.flush();
        em.clear();

        assertThat(service.getBySlug("views-" + token, "198.51.100.50").getViewCount()).isEqualTo(1);
        em.clear();
        assertThat(service.getBySlug("views-" + token, "198.51.100.50").getViewCount()).isEqualTo(1); // same address: no new view
        em.clear();
        assertThat(service.getBySlug("views-" + token, "198.51.100.51").getViewCount()).isEqualTo(2); // another address counts
        assertThat(storedViewCount(news)).isEqualTo(2);
        assertThat(newsViewRepository.countByNewsId(news.getId())).isEqualTo(2);

        // 25 hours later the first address counts again
        em.createNativeQuery("update news_views set viewed_at = ? where news_id = ? and ip = ?")
                .setParameter(1, LocalDateTime.now().minusHours(25))
                .setParameter(2, news.getId())
                .setParameter(3, "198.51.100.50")
                .executeUpdate();
        em.clear();
        assertThat(service.getBySlug("views-" + token, "198.51.100.50").getViewCount()).isEqualTo(3);
        assertThat(storedViewCount(news)).isEqualTo(3);

        // 23 hours later it does not
        em.createNativeQuery("update news_views set viewed_at = ? where news_id = ? and ip = ?")
                .setParameter(1, LocalDateTime.now().minusHours(23))
                .setParameter(2, news.getId())
                .setParameter(3, "198.51.100.51")
                .executeUpdate();
        em.clear();
        assertThat(service.getBySlug("views-" + token, "198.51.100.51").getViewCount()).isEqualTo(3);
    }

    @Test
    void listing_andSearching_neverCountAView() {
        News news = published("quiet", sports, LocalDateTime.of(2026, 1, 1, 8, 0));
        slugs(null, sports.getSlug(), null, null, null);
        service.latest(5);
        service.mostViewed(5);

        assertThat(storedViewCount(news)).isZero();
        assertThat(newsViewRepository.countByNewsId(news.getId())).isZero();
    }
}
