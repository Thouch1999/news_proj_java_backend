package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.Category;
import com.api.v1.bearerauth.model.entity.Comment;
import com.api.v1.bearerauth.model.entity.News;
import com.api.v1.bearerauth.model.entity.NewsStatusHistory;
import com.api.v1.bearerauth.model.entity.NewsView;
import com.api.v1.bearerauth.model.enumerate.CommentStatus;
import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.api.v1.bearerauth.model.enumerate.ReportInterval;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.response.ActivityItemDto;
import com.api.v1.bearerauth.model.response.AuthorDashboardDto;
import com.api.v1.bearerauth.model.response.CategoryNewsCountDto;
import com.api.v1.bearerauth.model.response.DashboardSummaryDto;
import com.api.v1.bearerauth.model.response.PublishedOverTimeDto;
import com.api.v1.bearerauth.model.response.TimeSeriesPointDto;
import com.api.v1.bearerauth.model.response.TopNewsDto;
import com.api.v1.bearerauth.model.response.TrendsDto;
import com.api.v1.bearerauth.service.ReportService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The report aggregate queries against the configured MySQL, in a transaction that is rolled back. The site already
 * has data, so the whole-site numbers are checked as "before and after" differences, and the lists and series use
 * unique tokens or a period (the year 2001) in which nothing else exists.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ReportQueryTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private ReportService reportService;

    private String token;
    private AppUser author;
    private AppUser otherAuthor;
    private AppUser reader;
    private Category sports;
    private Category empty;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString().substring(0, 8);
        author = user("author", Roles.AUTHOR);
        otherAuthor = user("other", Roles.AUTHOR);
        reader = user("reader", Roles.READER);
        sports = category("sports", true);
        empty = category("empty", false);
        login(Roles.ADMIN);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private void login(Roles role) {
        AppUser user = role == Roles.AUTHOR ? author : new AppUser();
        if (role != Roles.AUTHOR) {
            user.setId(-1L);
            user.setRole(role);
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
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

    private Category category(String part, boolean active) {
        Category category = new Category();
        category.setName(part + " " + token);
        category.setSlug(part + "-" + token);
        category.setActive(active);
        em.persist(category);
        return category;
    }

    private News news(String part, NewsStatus status, AppUser by, Category in, LocalDateTime publishedAt, long views) {
        News news = new News();
        news.setTitle("Title " + part + " " + token);
        news.setSlug(part + "-" + token);
        news.setContent("<p>x</p>");
        news.setStatus(status);
        news.setAuthor(by);
        news.setCategory(in);
        news.setPublishedAt(publishedAt);
        news.setViewCount(views);
        em.persist(news);
        return news;
    }

    private News published(String part, long views) {
        return news(part, NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), views);
    }

    private void view(News news, LocalDateTime at) {
        NewsView view = new NewsView();
        view.setNews(news);
        view.setIp("203.0.113." + (Math.abs(at.hashCode()) % 200));
        em.persist(view);
        em.flush();
        em.createNativeQuery("update news_views set viewed_at = ? where id = ?").setParameter(1, at).setParameter(2, view.getId()).executeUpdate();
    }

    private void comment(News news, CommentStatus status, LocalDateTime at) {
        Comment comment = new Comment();
        comment.setNews(news);
        comment.setUser(reader);
        comment.setContent("c");
        comment.setStatus(status);
        em.persist(comment);
        em.flush();
        em.createNativeQuery("update comments set created_at = ? where id = ?").setParameter(1, at).setParameter(2, comment.getId()).executeUpdate();
    }

    private void fresh() {
        em.flush();
        em.clear();
    }

    // ---------------- summary ----------------

    @Test
    void summary_countsUsersNewsViewsAndComments_asDifferencesFromBefore() {
        fresh();
        DashboardSummaryDto before = reportService.summary();

        user("extra author 1", Roles.AUTHOR);
        user("extra author 2", Roles.AUTHOR);
        user("extra reader", Roles.READER);
        News one = news("a", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 100);
        news("b", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 50);
        news("c", NewsStatus.DRAFT, author, sports, null, 7);
        news("d", NewsStatus.PENDING, otherAuthor, sports, null, 0);
        news("e", NewsStatus.REJECTED, otherAuthor, sports, null, 0);
        news("f", NewsStatus.ARCHIVED, otherAuthor, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 3);
        comment(one, CommentStatus.VISIBLE, LocalDateTime.of(2001, 1, 2, 8, 0));
        comment(one, CommentStatus.VISIBLE, LocalDateTime.of(2001, 1, 2, 9, 0));
        comment(one, CommentStatus.HIDDEN, LocalDateTime.of(2001, 1, 2, 10, 0));
        fresh();

        DashboardSummaryDto after = reportService.summary();

        assertThat(after.getTotalUsers() - before.getTotalUsers()).isEqualTo(3);
        assertThat(after.getUsersByRole().get(Roles.AUTHOR) - before.getUsersByRole().get(Roles.AUTHOR)).isEqualTo(2);
        assertThat(after.getUsersByRole().get(Roles.READER) - before.getUsersByRole().get(Roles.READER)).isEqualTo(1);
        assertThat(after.getTotalNews() - before.getTotalNews()).isEqualTo(6);
        assertThat(after.getNewsByStatus().get(NewsStatus.PUBLISHED) - before.getNewsByStatus().get(NewsStatus.PUBLISHED)).isEqualTo(2);
        assertThat(after.getNewsByStatus().get(NewsStatus.DRAFT) - before.getNewsByStatus().get(NewsStatus.DRAFT)).isEqualTo(1);
        assertThat(after.getNewsByStatus().get(NewsStatus.PENDING) - before.getNewsByStatus().get(NewsStatus.PENDING)).isEqualTo(1);
        assertThat(after.getNewsByStatus().get(NewsStatus.REJECTED) - before.getNewsByStatus().get(NewsStatus.REJECTED)).isEqualTo(1);
        assertThat(after.getNewsByStatus().get(NewsStatus.ARCHIVED) - before.getNewsByStatus().get(NewsStatus.ARCHIVED)).isEqualTo(1);
        assertThat(after.getTotalViews() - before.getTotalViews()).isEqualTo(160);
        assertThat(after.getTotalComments() - before.getTotalComments()).isEqualTo(3);
        assertThat(after.getHiddenComments() - before.getHiddenComments()).isEqualTo(1);
    }

    @Test
    void summary_runsAFixedNumberOfStatements_noRowsAreLoaded() {
        for (int i = 0; i < 15; i++) {
            news("n" + i, NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), i);
        }
        fresh();
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        reportService.summary();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(6); // users, news by status, views, comments, hidden comments, unverified users
        assertThat(statistics.getEntityLoadCount()).isZero(); // nothing was loaded as an entity
    }

    // ---------------- per category ----------------

    @Test
    void newsPerCategory_listsEveryCategory_alsoOneWithoutNews_withPublishedCounts() {
        news("a", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 0);
        news("b", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 0);
        news("c", NewsStatus.DRAFT, author, sports, null, 0);
        news("d", NewsStatus.ARCHIVED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 0);
        fresh();

        List<CategoryNewsCountDto> ours = reportService.newsPerCategory().stream().filter(c -> c.getSlug().endsWith(token)).toList();

        assertThat(ours).hasSize(2);
        CategoryNewsCountDto sportsCount = ours.get(0); // the one with news comes first
        assertThat(sportsCount.getSlug()).isEqualTo("sports-" + token);
        assertThat(sportsCount.getNewsCount()).isEqualTo(4);
        assertThat(sportsCount.getPublishedCount()).isEqualTo(2);
        assertThat(sportsCount.isActive()).isTrue();
        CategoryNewsCountDto emptyCount = ours.get(1);
        assertThat(emptyCount.getSlug()).isEqualTo("empty-" + token);
        assertThat(emptyCount.getNewsCount()).isZero();
        assertThat(emptyCount.getPublishedCount()).isZero();
        assertThat(emptyCount.isActive()).isFalse();
        assertThat(emptyCount.getCategoryId()).isEqualTo(empty.getId());
    }

    // ---------------- most viewed ----------------

    @Test
    void mostViewed_allTime_ranksByTheCounter_publishedOnly_withNames() {
        News low = published("low", 9_000_001);
        News high = published("high", 9_000_003);
        News mid = published("mid", 9_000_002);
        news("draft", NewsStatus.DRAFT, author, sports, null, 9_999_999);
        news("archived", NewsStatus.ARCHIVED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 9_999_998);
        fresh();

        List<TopNewsDto> top = reportService.mostViewed(50, null, null).stream().filter(t -> t.getSlug().endsWith(token)).toList();

        assertThat(top).extracting(TopNewsDto::getId).containsExactly(high.getId(), mid.getId(), low.getId());
        assertThat(top.get(0).getCount()).isEqualTo(9_000_003);
        assertThat(top.get(0).getCategoryName()).isEqualTo("sports " + token);
        assertThat(top.get(0).getAuthorName()).isEqualTo("author " + token);
    }

    @Test
    void mostViewed_limit_cutsTheListInTheDatabase() {
        for (int i = 1; i <= 5; i++) {
            published("n" + i, 9_100_000 + i);
        }
        fresh();

        assertThat(reportService.mostViewed(3, null, null)).hasSize(3);
        assertThat(reportService.mostViewed(1, null, null)).hasSize(1);
    }

    @Test
    void mostViewed_inAPeriod_countsTheViewsRecordedInIt_bothDaysIncluded() {
        News a = published("a", 0);
        News b = published("b", 0);
        view(a, LocalDateTime.of(2001, 3, 1, 0, 0, 0));    // first day
        view(a, LocalDateTime.of(2001, 3, 10, 23, 59, 59)); // last day
        view(a, LocalDateTime.of(2001, 3, 5, 12, 0));
        view(b, LocalDateTime.of(2001, 3, 6, 12, 0));
        view(b, LocalDateTime.of(2001, 2, 28, 23, 59, 59)); // before: not counted
        view(b, LocalDateTime.of(2001, 3, 11, 0, 0, 0));    // after: not counted
        fresh();

        List<TopNewsDto> top = reportService.mostViewed(50, LocalDate.of(2001, 3, 1), LocalDate.of(2001, 3, 10));

        assertThat(top).extracting(TopNewsDto::getId).containsExactly(a.getId(), b.getId());
        assertThat(top).extracting(TopNewsDto::getCount).containsExactly(3L, 1L);
        // with an open end
        assertThat(reportService.mostViewed(50, LocalDate.of(2001, 3, 11), null)).extracting(TopNewsDto::getCount).contains(1L);
        assertThat(reportService.mostViewed(50, null, LocalDate.of(2001, 2, 28)).stream().filter(t -> t.getSlug().endsWith(token)).toList())
                .extracting(TopNewsDto::getCount).containsExactly(1L);
    }

    @Test
    void mostViewed_inAPeriod_leavesOutArticlesThatAreNotPublished() {
        News draft = news("draft", NewsStatus.DRAFT, author, sports, null, 0);
        view(draft, LocalDateTime.of(2001, 3, 5, 12, 0));
        fresh();

        assertThat(reportService.mostViewed(50, LocalDate.of(2001, 3, 1), LocalDate.of(2001, 3, 10))
                .stream().filter(t -> t.getSlug().endsWith(token))).isEmpty();
    }

    // ---------------- most commented ----------------

    @Test
    void mostCommented_countsVisibleComments_allTime_andInAPeriod() {
        News a = published("a", 0);
        News b = published("b", 0);
        News none = published("none", 0);
        comment(a, CommentStatus.VISIBLE, LocalDateTime.of(2001, 3, 1, 8, 0));
        comment(a, CommentStatus.VISIBLE, LocalDateTime.of(2001, 3, 2, 8, 0));
        comment(a, CommentStatus.HIDDEN, LocalDateTime.of(2001, 3, 3, 8, 0)); // a hidden comment does not count
        comment(b, CommentStatus.VISIBLE, LocalDateTime.of(2001, 4, 1, 8, 0));
        fresh();

        List<TopNewsDto> allTime = reportService.mostCommented(50, null, null).stream().filter(t -> t.getSlug().endsWith(token)).toList();
        assertThat(allTime).extracting(TopNewsDto::getId).containsExactly(a.getId(), b.getId()); // none has no comments: not listed
        assertThat(allTime).extracting(TopNewsDto::getCount).containsExactly(2L, 1L);
        assertThat(allTime).extracting(TopNewsDto::getId).doesNotContain(none.getId());

        List<TopNewsDto> inMarch = reportService.mostCommented(50, LocalDate.of(2001, 3, 1), LocalDate.of(2001, 3, 31));
        assertThat(inMarch).extracting(TopNewsDto::getId).containsExactly(a.getId());
        assertThat(inMarch.get(0).getCount()).isEqualTo(2);
    }

    // ---------------- published over time ----------------

    @Test
    void publishedPerDay_countsByPublishDate_zeroFilled_whateverTheStatusIsNow() {
        news("p1", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 5, 2, 8, 0), 0);
        news("p2", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 5, 2, 23, 59, 59), 0);
        news("p3", NewsStatus.ARCHIVED, author, sports, LocalDateTime.of(2001, 5, 4, 0, 0, 0), 0); // archived since: still counts
        news("p4", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 5, 6, 0, 0, 0), 0); // after the range
        news("p5", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 4, 30, 23, 59, 59), 0); // before the range
        news("draft", NewsStatus.DRAFT, author, sports, null, 0); // never published
        fresh();

        PublishedOverTimeDto report = reportService.publishedOverTime(ReportInterval.DAY, LocalDate.of(2001, 5, 1), LocalDate.of(2001, 5, 5));

        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getPeriod)
                .containsExactly("2001-05-01", "2001-05-02", "2001-05-03", "2001-05-04", "2001-05-05");
        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getCount).containsExactly(0L, 2L, 0L, 1L, 0L);
        assertThat(report.getTotal()).isEqualTo(3);
    }

    @Test
    void publishedPerDay_cutsTheDaysOnTheApplicationsClock_notAtUtcMidnight() {
        // The application runs on the JVM's clock and the database stores UTC: an article published at 00:30 or 06:30
        // local time must be counted on THAT day (an SQL day() on the stored value would give the day before).
        news("early", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 5, 4, 0, 30), 0);
        news("morning", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 5, 4, 6, 30), 0);
        news("late", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 5, 4, 23, 30), 0);
        news("next-day", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 5, 5, 0, 5), 0);
        fresh();

        PublishedOverTimeDto report = reportService.publishedOverTime(ReportInterval.DAY, LocalDate.of(2001, 5, 3), LocalDate.of(2001, 5, 5));

        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getPeriod).containsExactly("2001-05-03", "2001-05-04", "2001-05-05");
        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getCount).containsExactly(0L, 3L, 1L);

        // the same for months: the first minute of a month belongs to that month
        news("month-start", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 8, 1, 0, 1), 0);
        news("month-end", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 7, 31, 23, 59), 0);
        fresh();
        PublishedOverTimeDto months = reportService.publishedOverTime(ReportInterval.MONTH, LocalDate.of(2001, 7, 1), LocalDate.of(2001, 8, 31));
        assertThat(months.getPoints()).extracting(TimeSeriesPointDto::getCount).containsExactly(1L, 1L);
    }

    @Test
    void publishedPerMonth_countsByMonth_acrossAYearEnd() {
        news("m1", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 11, 15, 8, 0), 0);
        news("m2", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 12, 1, 0, 0), 0);
        news("m3", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 12, 31, 23, 59), 0);
        news("m4", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2002, 2, 10, 8, 0), 0);
        fresh();

        PublishedOverTimeDto report = reportService.publishedOverTime(ReportInterval.MONTH, LocalDate.of(2001, 11, 1), LocalDate.of(2002, 2, 28));

        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getPeriod).containsExactly("2001-11", "2001-12", "2002-01", "2002-02");
        assertThat(report.getPoints()).extracting(TimeSeriesPointDto::getCount).containsExactly(1L, 2L, 0L, 1L);
        assertThat(report.getTotal()).isEqualTo(4);
    }

    @Test
    void publishedOverTime_runsOneStatement_whateverTheNumberOfArticles() {
        for (int i = 1; i <= 20; i++) {
            news("n" + i, NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 7, 1 + (i % 28), 8, 0), 0);
        }
        fresh();
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        reportService.publishedOverTime(ReportInterval.DAY, LocalDate.of(2001, 7, 1), LocalDate.of(2001, 7, 31));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }

    // ---------------- author dashboard ----------------

    @Test
    void authorDashboard_countsOnlyMyArticles_andTheViewsOfMyPublishedOnes() {
        news("a", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 100);
        news("b", NewsStatus.PUBLISHED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 40);
        news("c", NewsStatus.DRAFT, author, sports, null, 5);          // views of a draft do not count
        news("d", NewsStatus.REJECTED, author, sports, null, 0);
        news("e", NewsStatus.ARCHIVED, author, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 9); // nor does an archived one
        news("other", NewsStatus.PUBLISHED, otherAuthor, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 1000); // not mine
        fresh();
        login(Roles.AUTHOR);

        AuthorDashboardDto dashboard = reportService.authorDashboard();

        assertThat(dashboard.getTotalNews()).isEqualTo(5);
        assertThat(dashboard.getNewsByStatus().get(NewsStatus.PUBLISHED)).isEqualTo(2);
        assertThat(dashboard.getNewsByStatus().get(NewsStatus.DRAFT)).isEqualTo(1);
        assertThat(dashboard.getNewsByStatus().get(NewsStatus.REJECTED)).isEqualTo(1);
        assertThat(dashboard.getNewsByStatus().get(NewsStatus.ARCHIVED)).isEqualTo(1);
        assertThat(dashboard.getNewsByStatus().get(NewsStatus.PENDING)).isZero();
        assertThat(dashboard.getTotalViews()).isEqualTo(140);
    }

    @Test
    void authorDashboard_ofAnAuthorWithoutArticles_isAllZeros() {
        login(Roles.AUTHOR);

        AuthorDashboardDto dashboard = reportService.authorDashboard();

        assertThat(dashboard.getTotalNews()).isZero();
        assertThat(dashboard.getTotalViews()).isZero();
        assertThat(dashboard.getNewsByStatus().values()).containsOnly(0L);
    }

    // ---------------- author scope, views series, trends, activity, thumbnail ----------------

    private void statusChange(News news, NewsStatus from, NewsStatus to, AppUser by, LocalDateTime at) {
        NewsStatusHistory history = new NewsStatusHistory();
        history.setNews(news);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangedBy(by);
        em.persist(history);
        em.flush();
        em.createNativeQuery("update news_status_history set changed_at = ? where id = ?").setParameter(1, at).setParameter(2, history.getId()).executeUpdate();
    }

    @Test
    void anAuthorsTopListsAndSeries_coverOnlyTheirOwnArticles() {
        News mine = published("mine", 5);
        News theirs = news("theirs", NewsStatus.PUBLISHED, otherAuthor, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 900);
        view(mine, LocalDateTime.of(2001, 3, 2, 8, 0));
        view(theirs, LocalDateTime.of(2001, 3, 2, 9, 0));
        view(theirs, LocalDateTime.of(2001, 3, 3, 9, 0));
        comment(mine, CommentStatus.VISIBLE, LocalDateTime.of(2001, 3, 2, 8, 0));
        comment(theirs, CommentStatus.VISIBLE, LocalDateTime.of(2001, 3, 2, 8, 0));
        fresh();
        login(Roles.AUTHOR);

        assertThat(reportService.mostViewed(50, null, null)).extracting(TopNewsDto::getId).containsExactly(mine.getId());
        assertThat(reportService.mostViewed(50, LocalDate.of(2001, 3, 1), LocalDate.of(2001, 3, 31))).extracting(TopNewsDto::getId).containsExactly(mine.getId());
        assertThat(reportService.mostCommented(50, null, null)).extracting(TopNewsDto::getId).containsExactly(mine.getId());
        assertThat(reportService.viewsOverTime(ReportInterval.DAY, LocalDate.of(2001, 3, 1), LocalDate.of(2001, 3, 4)).getTotal()).isEqualTo(1);
        assertThat(reportService.publishedOverTime(ReportInterval.DAY, LocalDate.of(2001, 1, 1), LocalDate.of(2001, 1, 1)).getTotal()).isEqualTo(1);
    }

    @Test
    void viewsOverTime_countsTheRecordedViewsPerDay_zeroFilled_andPerMonth() {
        News a = published("a", 0);
        view(a, LocalDate.of(2001, 8, 2).atTime(8, 0));
        view(a, LocalDate.of(2001, 8, 2).atTime(9, 0));
        view(a, LocalDate.of(2001, 8, 4).atTime(9, 0));
        fresh();

        PublishedOverTimeDto days = reportService.viewsOverTime(ReportInterval.DAY, LocalDate.of(2001, 8, 1), LocalDate.of(2001, 8, 5));
        assertThat(days.getPoints()).extracting(TimeSeriesPointDto::getCount).containsExactly(0L, 2L, 0L, 1L, 0L);

        PublishedOverTimeDto months = reportService.viewsOverTime(ReportInterval.MONTH, LocalDate.of(2001, 7, 1), LocalDate.of(2001, 8, 31));
        assertThat(months.getPoints()).extracting(TimeSeriesPointDto::getCount).containsExactly(0L, 3L);
    }

    @Test
    void trends_countThePeriodAndThePeriodBefore_forTheSiteAndForAnAuthor() {
        News mine = published("mine", 0);
        News theirs = news("theirs", NewsStatus.PUBLISHED, otherAuthor, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 0);
        view(mine, LocalDateTime.of(2001, 9, 12, 8, 0));  // in the period 11-20
        view(theirs, LocalDateTime.of(2001, 9, 13, 8, 0));
        view(mine, LocalDateTime.of(2001, 9, 2, 8, 0));   // in the previous period 1-10
        comment(mine, CommentStatus.VISIBLE, LocalDateTime.of(2001, 9, 15, 8, 0));
        fresh();

        TrendsDto site = reportService.trends(LocalDate.of(2001, 9, 11), LocalDate.of(2001, 9, 20));
        assertThat(site.getPreviousFrom()).isEqualTo(LocalDate.of(2001, 9, 1));
        assertThat(site.getPreviousTo()).isEqualTo(LocalDate.of(2001, 9, 10));
        assertThat(site.getCurrent().getViews()).isEqualTo(2);
        assertThat(site.getPrevious().getViews()).isEqualTo(1);
        assertThat(site.getCurrent().getComments()).isEqualTo(1);

        login(Roles.AUTHOR);
        TrendsDto own = reportService.trends(LocalDate.of(2001, 9, 11), LocalDate.of(2001, 9, 20));
        assertThat(own.getCurrent().getViews()).isEqualTo(1);
        assertThat(own.getCurrent().getNewUsers()).isZero();
    }

    @Test
    void recentActivity_listsStatusChangesAndComments_newestFirst_andAnAuthorSeesOnlyTheirArticles() {
        News mine = published("mine", 0);
        News theirs = news("theirs", NewsStatus.PUBLISHED, otherAuthor, sports, LocalDateTime.of(2001, 1, 1, 8, 0), 0);
        statusChange(mine, null, NewsStatus.DRAFT, author, LocalDateTime.of(2090, 1, 1, 8, 0));
        statusChange(mine, NewsStatus.PENDING, NewsStatus.PUBLISHED, otherAuthor, LocalDateTime.of(2090, 1, 3, 8, 0));
        statusChange(theirs, NewsStatus.DRAFT, NewsStatus.PENDING, otherAuthor, LocalDateTime.of(2090, 1, 4, 8, 0));
        comment(mine, CommentStatus.VISIBLE, LocalDateTime.of(2090, 1, 2, 8, 0));
        fresh();

        List<ActivityItemDto> site = reportService.recentActivity(4);
        assertThat(site).extracting(ActivityItemDto::getType)
                .containsExactly(ActivityItemDto.STATUS_CHANGE, ActivityItemDto.STATUS_CHANGE, ActivityItemDto.COMMENT, ActivityItemDto.STATUS_CHANGE);
        assertThat(site.get(0).getNewsSlug()).isEqualTo("theirs-" + token);
        assertThat(site.get(0).getToStatus()).isEqualTo(NewsStatus.PENDING);
        assertThat(site.get(2).getSnippet()).isEqualTo("c");
        assertThat(site.get(2).getActorName()).isEqualTo("reader " + token);

        login(Roles.AUTHOR);
        List<ActivityItemDto> own = reportService.recentActivity(10).stream().filter(i -> i.getNewsSlug().endsWith(token)).toList();
        assertThat(own).extracting(ActivityItemDto::getNewsSlug).containsOnly("mine-" + token);
        assertThat(own).hasSize(3);
    }

    @Test
    void topLists_carryTheThumbnail_andTheSummaryCountsUnverifiedUsers() {
        News a = published("a", 3);
        a.setThumbnail("/uploads/a.png");
        fresh();
        Long before = reportService.summary().getUnverifiedUsers();
        user("late", Roles.READER); // emailVerified defaults to false
        fresh();

        assertThat(reportService.mostViewed(50, null, null).stream().filter(t -> t.getSlug().endsWith(token)).toList())
                .extracting(TopNewsDto::getThumbnail).containsExactly("/uploads/a.png");
        assertThat(reportService.summary().getUnverifiedUsers()).isGreaterThan(before);
    }
}
