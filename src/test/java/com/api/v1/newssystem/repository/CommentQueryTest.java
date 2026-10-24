package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.Comment;
import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.enumerate.CommentStatus;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.request.CommentRequest;
import com.api.v1.newssystem.model.response.CommentDto;
import com.api.v1.newssystem.service.CommentService;
import com.api.v1.newssystem.service.PublicNewsService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The comment queries and rules against the configured MySQL, in a transaction that is rolled back. Every row
 * carries a unique token, so existing data does not disturb the checks.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class CommentQueryTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private CommentService commentService;
    @Autowired
    private PublicNewsService publicNewsService;
    @Autowired
    private CommentRepository commentRepository;

    private String token;
    private AppUser reader;
    private AppUser otherReader;
    private AppUser editor;
    private News article;
    private News otherArticle;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString().substring(0, 8);
        reader = user("reader", Roles.READER);
        otherReader = user("other", Roles.READER);
        editor = user("editor", Roles.EDITOR);
        Category category = new Category();
        category.setName("Cat " + token);
        category.setSlug("cat-" + token);
        category.setActive(true);
        em.persist(category);
        article = news("a", category, NewsStatus.PUBLISHED);
        otherArticle = news("b", category, NewsStatus.PUBLISHED);
        loginAs(reader);
    }

    private AppUser user(String name, Roles role) {
        AppUser user = new AppUser();
        user.setEmail(name + "-" + token + "@news.local");
        user.setPassword("x");
        user.setRole(role);
        user.setUsername(name + " " + token);
        em.persist(user);
        return user;
    }

    private News news(String part, Category category, NewsStatus status) {
        News news = new News();
        news.setTitle("Title " + part);
        news.setSlug(part + "-" + token);
        news.setContent("<p>x</p>");
        news.setStatus(status);
        news.setAuthor(editor);
        news.setCategory(category);
        news.setPublishedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        em.persist(news);
        return news;
    }

    private void loginAs(AppUser user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private Comment stored(News news, AppUser user, CommentStatus status, String content, LocalDateTime createdAt) {
        Comment comment = new Comment();
        comment.setNews(news);
        comment.setUser(user);
        comment.setStatus(status);
        comment.setContent(content);
        em.persist(comment);
        em.flush();
        // created_at is set by the database layer; move it so the order is known
        em.createNativeQuery("update comments set created_at = ? where id = ?").setParameter(1, createdAt).setParameter(2, comment.getId()).executeUpdate();
        return comment;
    }

    private CommentRequest request(String content) {
        CommentRequest request = new CommentRequest();
        request.setContent(content);
        return request;
    }

    private void fresh() {
        em.flush();
        em.clear();
    }

    @org.junit.jupiter.api.AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    // ---------------- public list ----------------

    @Test
    void list_showsOnlyVisibleComments_ofThatArticle_newestFirst_withPaging() {
        stored(article, reader, CommentStatus.VISIBLE, "oldest", LocalDateTime.of(2026, 2, 1, 8, 0));
        stored(article, otherReader, CommentStatus.HIDDEN, "hidden", LocalDateTime.of(2026, 2, 2, 8, 0));
        stored(article, otherReader, CommentStatus.VISIBLE, "middle", LocalDateTime.of(2026, 2, 3, 8, 0));
        stored(article, reader, CommentStatus.VISIBLE, "newest", LocalDateTime.of(2026, 2, 4, 8, 0));
        stored(otherArticle, reader, CommentStatus.VISIBLE, "elsewhere", LocalDateTime.of(2026, 2, 5, 8, 0));
        fresh();

        var first = commentService.listVisible(article.getId(), 1, 2);
        assertThat(first.getContent()).extracting(CommentDto::getContent).containsExactly("newest", "middle");
        assertThat(first.getTotalElements()).isEqualTo(3); // the hidden one is not counted
        assertThat(commentService.listVisible(article.getId(), 2, 2).getContent()).extracting(CommentDto::getContent).containsExactly("oldest");
    }

    @Test
    void list_doesNotShowEmailsOrPasswords_justTheName() {
        stored(article, reader, CommentStatus.VISIBLE, "hi", LocalDateTime.of(2026, 2, 1, 8, 0));
        fresh();

        CommentDto dto = commentService.listVisible(article.getId(), 1, 10).getContent().get(0);

        assertThat(dto.getUserName()).isEqualTo("reader " + token);
        assertThat(dto.toString()).doesNotContain("@news.local").doesNotContain("password");
    }

    @Test
    void list_runsAFixedNumberOfStatements_whateverTheNumberOfComments() {
        for (int i = 0; i < 10; i++) {
            stored(article, i % 2 == 0 ? reader : otherReader, CommentStatus.VISIBLE, "c" + i, LocalDateTime.of(2026, 2, 1 + i, 8, 0));
        }
        fresh();
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(commentService.listVisible(article.getId(), 1, 10).getContent()).hasSize(10);

        // the article, its category (active check), the comments with their authors, the count
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    // ---------------- create ----------------

    @Test
    void create_thenItAppearsInTheListAndInThePublicCommentCount() {
        CommentDto created = commentService.create(article.getId(), request("  សួស្តី  "));
        fresh();

        assertThat(created.getContent()).isEqualTo("សួស្តី");
        assertThat(commentService.listVisible(article.getId(), 1, 10).getContent()).extracting(CommentDto::getContent).containsExactly("សួស្តី");
        assertThat(publicNewsService.getBySlug("a-" + token, "198.51.100.77").getCommentCount()).isEqualTo(1);
    }

    @Test
    void create_isRejectedForUnpublishedArticles_andForDisabledComments() {
        Category category = article.getCategory();
        News draft = news("draft", category, NewsStatus.DRAFT);
        article.setCommentsEnabled(false);
        fresh();

        assertThatThrownBy(() -> commentService.create(draft.getId(), request("Hi")))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus().value()).isEqualTo(404));
        assertThatThrownBy(() -> commentService.create(article.getId(), request("Hi")))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus().value()).isEqualTo(409));
    }

    // ---------------- moderation ----------------

    @Test
    void moderation_listsEverything_withFilters_newestFirst() {
        loginAs(editor);
        Comment a = stored(article, reader, CommentStatus.VISIBLE, "a", LocalDateTime.of(2026, 2, 1, 8, 0));
        Comment b = stored(article, otherReader, CommentStatus.HIDDEN, "b", LocalDateTime.of(2026, 2, 2, 8, 0));
        Comment c = stored(otherArticle, reader, CommentStatus.HIDDEN, "c", LocalDateTime.of(2026, 2, 3, 8, 0));
        fresh();

        assertThat(ids(commentService.listAll(null, article.getId(), null, 1, 10).getContent())).containsExactly(b.getId(), a.getId());
        assertThat(ids(commentService.listAll(CommentStatus.HIDDEN, null, reader.getId(), 1, 10).getContent())).containsExactly(c.getId());
        assertThat(ids(commentService.listAll(CommentStatus.HIDDEN, article.getId(), null, 1, 10).getContent())).containsExactly(b.getId());
        assertThat(ids(commentService.listAll(CommentStatus.VISIBLE, otherArticle.getId(), null, 1, 10).getContent())).isEmpty();
        assertThat(commentService.listAll(null, null, otherReader.getId(), 1, 10).getContent()).extracting(CommentDto::getNewsTitle).containsExactly("Title a");
    }

    @Test
    void moderation_listRunsAFixedNumberOfStatements() {
        loginAs(editor);
        for (int i = 0; i < 8; i++) {
            stored(i % 2 == 0 ? article : otherArticle, i % 2 == 0 ? reader : otherReader, CommentStatus.VISIBLE, "c" + i, LocalDateTime.of(2026, 2, 1 + i, 8, 0));
        }
        fresh();
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(commentService.listAll(null, null, null, 1, 50).getContent()).isNotEmpty();

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2); // the page (with user and news) + the count
    }

    @Test
    void hidingAComment_removesItFromThePublicList_andTheCount_unhidingBringsItBack() {
        Comment comment = stored(article, reader, CommentStatus.VISIBLE, "will be hidden", LocalDateTime.of(2026, 2, 1, 8, 0));
        loginAs(editor);
        fresh();

        commentService.hide(comment.getId());
        fresh();
        assertThat(commentService.listVisible(article.getId(), 1, 10).getContent()).isEmpty();
        assertThat(publicNewsService.getBySlug("a-" + token, "198.51.100.78").getCommentCount()).isZero();

        commentService.unhide(comment.getId());
        fresh();
        assertThat(commentService.listVisible(article.getId(), 1, 10).getContent()).hasSize(1);
    }

    // ---------------- delete ----------------

    @Test
    void delete_ownComment_works_butAnotherUsersDoesNot_andAnEditorMayDeleteAny() {
        Comment mine = stored(article, reader, CommentStatus.VISIBLE, "mine", LocalDateTime.of(2026, 2, 1, 8, 0));
        Comment theirs = stored(article, otherReader, CommentStatus.VISIBLE, "theirs", LocalDateTime.of(2026, 2, 2, 8, 0));
        fresh();

        assertThatThrownBy(() -> commentService.delete(theirs.getId()))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus().value()).isEqualTo(403));
        commentService.delete(mine.getId());
        fresh();
        assertThat(commentRepository.findById(mine.getId())).isEmpty();
        assertThat(commentRepository.findById(theirs.getId())).isPresent();

        loginAs(editor);
        commentService.delete(theirs.getId());
        fresh();
        assertThat(commentRepository.findById(theirs.getId())).isEmpty();
    }

    @Test
    void deletingAnArticle_stillRemovesItsComments() {
        // NewsServiceImpl.delete removes the comments first; the count shows the comment is gone with it
        Comment comment = stored(article, reader, CommentStatus.VISIBLE, "x", LocalDateTime.of(2026, 2, 1, 8, 0));
        fresh();

        commentRepository.deleteAllByNewsId(article.getId());
        fresh();

        assertThat(commentRepository.findById(comment.getId())).isEmpty();
    }

    private List<Long> ids(List<CommentDto> comments) {
        return comments.stream().map(CommentDto::getId).toList();
    }
}
