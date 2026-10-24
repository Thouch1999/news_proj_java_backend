package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.entity.NewsTag;
import com.api.v1.newssystem.model.entity.Tag;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.response.BookmarkDto;
import com.api.v1.newssystem.service.BookmarkService;
import com.api.v1.newssystem.service.PublicNewsService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bookmarks against the configured MySQL, in a transaction that is rolled back (rows carry a unique token).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class BookmarkQueryTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private BookmarkService bookmarkService;
    @Autowired
    private PublicNewsService publicNewsService;
    @Autowired
    private BookmarkRepository bookmarkRepository;

    private String token;
    private AppUser reader;
    private AppUser otherReader;
    private Category category;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString().substring(0, 8);
        reader = user("reader");
        otherReader = user("other");
        category = category("cat", true);
        loginAs(reader);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private AppUser user(String name) {
        AppUser user = new AppUser();
        user.setEmail(name + "-" + token + "@news.local");
        user.setPassword("x");
        user.setRole(Roles.READER);
        user.setUsername(name + " " + token);
        em.persist(user);
        return user;
    }

    private Category category(String part, boolean active) {
        Category category = new Category();
        category.setName(part + " " + token);
        category.setSlug(part + "-" + token);
        category.setActive(active);
        em.persist(category);
        return category;
    }

    private News news(String part, NewsStatus status, Category in) {
        News news = new News();
        news.setTitle("Title " + part);
        news.setSlug(part + "-" + token);
        news.setContent("<p>x</p>");
        news.setStatus(status);
        news.setAuthor(reader);
        news.setCategory(in);
        news.setPublishedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        em.persist(news);
        return news;
    }

    private News published(String part) {
        return news(part, NewsStatus.PUBLISHED, category);
    }

    private void loginAs(AppUser user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void fresh() {
        em.flush();
        em.clear();
    }

    private void bookmarkedAt(News news, AppUser user, LocalDateTime at) {
        em.flush();
        em.createNativeQuery("update bookmarks set created_at = ? where news_id = ? and user_id = ?")
                .setParameter(1, at).setParameter(2, news.getId()).setParameter(3, user.getId()).executeUpdate();
    }

    // ---------------- add and remove ----------------

    @Test
    void add_createsOneBookmark_andAddingAgainDoesNotCreateAnother() {
        News news = published("a");
        fresh();

        assertThat(bookmarkService.add(news.getId())).isTrue();
        assertThat(bookmarkService.add(news.getId())).isFalse();
        assertThat(bookmarkService.add(news.getId())).isFalse();
        fresh();

        assertThat(bookmarkRepository.countByUserIdAndNewsId(reader.getId(), news.getId())).isEqualTo(1);
    }

    @Test
    void theDatabaseItselfRefusesADuplicate() {
        News news = published("dup");
        fresh();
        bookmarkService.add(news.getId());
        em.flush();

        com.api.v1.newssystem.model.entity.Bookmark duplicate = new com.api.v1.newssystem.model.entity.Bookmark();
        duplicate.setUser(em.getReference(AppUser.class, reader.getId()));
        duplicate.setNews(em.getReference(News.class, news.getId()));

        assertThatThrownBy(() -> bookmarkRepository.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void add_onlyPublishedArticles_inAnActiveCategory() {
        Category hidden = category("hidden", false);
        List<News> notAllowed = List.of(
                news("draft", NewsStatus.DRAFT, category),
                news("pending", NewsStatus.PENDING, category),
                news("rejected", NewsStatus.REJECTED, category),
                news("archived", NewsStatus.ARCHIVED, category),
                news("in-hidden", NewsStatus.PUBLISHED, hidden));
        fresh();

        for (News news : notAllowed) {
            assertThatThrownBy(() -> bookmarkService.add(news.getId()))
                    .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus().value()).isEqualTo(404));
        }
        assertThatThrownBy(() -> bookmarkService.add(-5L))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus().value()).isEqualTo(404));
        assertThat(bookmarkService.list(1, 10).getTotalElements()).isZero();
    }

    @Test
    void remove_deletesOnlyMyBookmark_andRepeatingItIsFine() {
        News news = published("a");
        fresh();
        bookmarkService.add(news.getId());
        loginAs(otherReader);
        bookmarkService.add(news.getId());
        fresh();

        loginAs(reader);
        bookmarkService.remove(news.getId());
        bookmarkService.remove(news.getId()); // nothing to remove: no error
        fresh();

        assertThat(bookmarkRepository.existsByUserIdAndNewsId(reader.getId(), news.getId())).isFalse();
        assertThat(bookmarkRepository.existsByUserIdAndNewsId(otherReader.getId(), news.getId())).isTrue();
    }

    @Test
    void remove_worksEvenAfterTheArticleWasArchived() {
        News news = published("a");
        fresh();
        bookmarkService.add(news.getId());
        em.createQuery("update News n set n.status = :status where n.id = :id").setParameter("status", NewsStatus.ARCHIVED).setParameter("id", news.getId()).executeUpdate();
        fresh();

        bookmarkService.remove(news.getId());
        fresh();

        assertThat(bookmarkRepository.existsByUserIdAndNewsId(reader.getId(), news.getId())).isFalse();
    }

    // ---------------- my list ----------------

    @Test
    void list_isMineOnly_newestBookmarkFirst_withPaging() {
        News a = published("a");
        News b = published("b");
        News c = published("c");
        News onlyOther = published("other");
        fresh();
        bookmarkService.add(a.getId());
        bookmarkService.add(b.getId());
        bookmarkService.add(c.getId());
        loginAs(otherReader);
        bookmarkService.add(onlyOther.getId());
        loginAs(reader);
        bookmarkedAt(a, reader, LocalDateTime.of(2026, 2, 1, 8, 0));
        bookmarkedAt(b, reader, LocalDateTime.of(2026, 2, 3, 8, 0));
        bookmarkedAt(c, reader, LocalDateTime.of(2026, 2, 2, 8, 0));
        fresh();

        var first = bookmarkService.list(1, 2);

        assertThat(first.getContent()).extracting(item -> item.getNews().getSlug()).containsExactly("b-" + token, "c-" + token);
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getContent().get(0).getBookmarkedAt()).isEqualTo(LocalDateTime.of(2026, 2, 3, 8, 0));
        assertThat(bookmarkService.list(2, 2).getContent()).extracting(item -> item.getNews().getSlug()).containsExactly("a-" + token);
    }

    @Test
    void list_cardsHaveCategoryAuthorAndTags() {
        News news = published("a");
        Tag tag = new Tag();
        tag.setName("Tag " + token);
        tag.setSlug("tag-" + token);
        em.persist(tag);
        NewsTag link = new NewsTag();
        link.setNews(news);
        link.setTag(tag);
        em.persist(link);
        fresh();
        bookmarkService.add(news.getId());
        fresh();

        BookmarkDto item = bookmarkService.list(1, 10).getContent().get(0);

        assertThat(item.getNews().getCategorySlug()).isEqualTo("cat-" + token);
        assertThat(item.getNews().getAuthorName()).isEqualTo("reader " + token);
        assertThat(item.getNews().getTags()).extracting(t -> t.getSlug()).containsExactly("tag-" + token);
    }

    @Test
    void list_hidesArticlesThatAreNoLongerPublished_butKeepsTheirBookmark() {
        News news = published("a");
        fresh();
        bookmarkService.add(news.getId());
        fresh();
        assertThat(bookmarkService.list(1, 10).getContent()).hasSize(1);

        em.createQuery("update News n set n.status = :status where n.id = :id").setParameter("status", NewsStatus.ARCHIVED).setParameter("id", news.getId()).executeUpdate();
        fresh();
        assertThat(bookmarkService.list(1, 10).getContent()).isEmpty();
        assertThat(bookmarkService.list(1, 10).getTotalElements()).isZero();
        assertThat(bookmarkRepository.existsByUserIdAndNewsId(reader.getId(), news.getId())).isTrue(); // still saved

        em.createQuery("update News n set n.status = :status where n.id = :id").setParameter("status", NewsStatus.PUBLISHED).setParameter("id", news.getId()).executeUpdate();
        fresh();
        assertThat(bookmarkService.list(1, 10).getContent()).hasSize(1); // restored: it is back
    }

    @Test
    void list_runsAFixedNumberOfStatements_whateverTheNumberOfBookmarks() {
        Category second = category("second", true);
        for (int i = 0; i < 10; i++) {
            News news = news("n" + i, NewsStatus.PUBLISHED, i % 2 == 0 ? category : second);
            fresh();
            bookmarkService.add(news.getId());
        }
        fresh();
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(bookmarkService.list(1, 50).getContent()).hasSize(10);

        // the page (with articles, authors, categories) + count + tags + comment counts
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    // ---------------- the article page ----------------

    @Test
    void detail_saysWhetherTheLoggedInUserSavedTheArticle_andSaysNothingToAVisitor() {
        News news = published("a");
        fresh();

        assertThat(publicNewsService.getBySlug("a-" + token, "198.51.100.90").getBookmarked()).isFalse();

        bookmarkService.add(news.getId());
        fresh();
        assertThat(publicNewsService.getBySlug("a-" + token, "198.51.100.90").getBookmarked()).isTrue();

        loginAs(otherReader); // another user did not save it
        fresh();
        assertThat(publicNewsService.getBySlug("a-" + token, "198.51.100.91").getBookmarked()).isFalse();

        SecurityContextHolder.clearContext(); // a visitor
        fresh();
        assertThat(publicNewsService.getBySlug("a-" + token, "198.51.100.92").getBookmarked()).isNull();
    }

    @Test
    void deletingAnArticle_removesItsBookmarks() {
        News news = published("a");
        fresh();
        bookmarkService.add(news.getId());
        fresh();

        bookmarkRepository.deleteAllByNewsId(news.getId());
        fresh();

        assertThat(bookmarkRepository.existsByUserIdAndNewsId(reader.getId(), news.getId())).isFalse();
    }
}
