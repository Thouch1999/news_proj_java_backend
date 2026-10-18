package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.News;
import com.api.v1.bearerauth.model.enumerate.CommentStatus;
import com.api.v1.bearerauth.model.response.ActivityItemDto;
import com.api.v1.bearerauth.model.response.CategoryNewsCountDto;
import com.api.v1.bearerauth.model.response.TopNewsDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only aggregate queries for the reports. Every number is computed by the database (count / sum / group by);
 * no article, comment or view is loaded into memory, and a top list is cut to N rows by the database.
 * It spans several tables, so it is a plain Repository and not tied to one entity.
 */
@org.springframework.stereotype.Repository
public interface ReportRepository extends Repository<News, Long> {

    // ---- dashboard summary ----

    // [role, number of users]; a role without users has no row
    @Query("select u.role, count(u) from AppUser u group by u.role")
    List<Object[]> countUsersByRole();

    // [status, number of articles]; a status without articles has no row
    @Query("select n.status, count(n) from News n group by n.status")
    List<Object[]> countNewsByStatus();

    @Query("select coalesce(sum(n.viewCount), 0) from News n")
    long sumViews();

    @Query("select count(c) from Comment c")
    long countComments();

    @Query("select count(c) from Comment c where c.status = :status")
    long countCommentsByStatus(@Param("status") CommentStatus status);

    // ---- per category ----

    // every category (also one without news), the one with the most articles first
    @Query("""
            select new com.api.v1.bearerauth.model.response.CategoryNewsCountDto(
                   c.id, c.name, c.slug, c.active, count(n),
                   coalesce(sum(case when n.status = com.api.v1.bearerauth.model.enumerate.NewsStatus.PUBLISHED then 1 else 0 end), 0))
            from Category c left join News n on n.category = c
            group by c.id, c.name, c.slug, c.active
            order by count(n) desc, c.name asc
            """)
    List<CategoryNewsCountDto> countNewsPerCategory();

    // ---- top lists (PUBLISHED articles only; the size of the list is the size of `pageable`) ----

    // all-time: the view counter of the article
    @Query("""
            select new com.api.v1.bearerauth.model.response.TopNewsDto(
                   n.id, n.title, n.slug, c.name, a.fullName, a.username, n.publishedAt, n.thumbnail, n.viewCount)
            from News n join n.category c join n.author a
            where n.status = com.api.v1.bearerauth.model.enumerate.NewsStatus.PUBLISHED
              and (:authorId is null or a.id = :authorId)
            order by n.viewCount desc, n.publishedAt desc, n.id desc
            """)
    List<TopNewsDto> findMostViewedAllTime(@Param("authorId") Long authorId, Pageable pageable);

    // in a period: the views recorded in the period (a bound that is null is open)
    @Query("""
            select new com.api.v1.bearerauth.model.response.TopNewsDto(
                   n.id, n.title, n.slug, c.name, a.fullName, a.username, n.publishedAt, n.thumbnail, count(v))
            from NewsView v join v.news n join n.category c join n.author a
            where n.status = com.api.v1.bearerauth.model.enumerate.NewsStatus.PUBLISHED
              and (:from is null or v.viewedAt >= :from)
              and (:to is null or v.viewedAt < :to)
              and (:authorId is null or a.id = :authorId)
            group by n.id, n.title, n.slug, c.name, a.fullName, a.username, n.publishedAt, n.thumbnail
            order by count(v) desc, n.publishedAt desc, n.id desc
            """)
    List<TopNewsDto> findMostViewedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("authorId") Long authorId, Pageable pageable);

    // visible comments, all-time or in a period (a bound that is null is open); an article without comments is not listed
    @Query("""
            select new com.api.v1.bearerauth.model.response.TopNewsDto(
                   n.id, n.title, n.slug, c.name, a.fullName, a.username, n.publishedAt, n.thumbnail, count(m))
            from Comment m join m.news n join n.category c join n.author a
            where n.status = com.api.v1.bearerauth.model.enumerate.NewsStatus.PUBLISHED
              and m.status = com.api.v1.bearerauth.model.enumerate.CommentStatus.VISIBLE
              and (:from is null or m.createdAt >= :from)
              and (:to is null or m.createdAt < :to)
              and (:authorId is null or a.id = :authorId)
            group by n.id, n.title, n.slug, c.name, a.fullName, a.username, n.publishedAt, n.thumbnail
            order by count(m) desc, n.publishedAt desc, n.id desc
            """)
    List<TopNewsDto> findMostCommented(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("authorId") Long authorId, Pageable pageable);

    // ---- articles published over time (by the date they were published, whatever their status is now) ----

    // Why `offset`: the application writes a LocalDateTime in the JVM's time zone, while the connection stores it in
    // UTC (serverTimezone=UTC in the datasource URL). Entities and the [from, to) bounds are converted back and forth
    // consistently, but SQL day() / month() would cut the days at UTC midnight. convert_tz moves the stored value back
    // to the application's clock first, so a day is the same day the article page shows. `offset` is the JVM zone's
    // offset from UTC as "+07:00".

    // [year, month, day, number of articles] for publish dates in [from, to)
    @Query("""
            select year(cast(function('convert_tz', n.publishedAt, '+00:00', :offset) as LocalDateTime)),
                   month(cast(function('convert_tz', n.publishedAt, '+00:00', :offset) as LocalDateTime)),
                   day(cast(function('convert_tz', n.publishedAt, '+00:00', :offset) as LocalDateTime)),
                   count(n)
            from News n
            where n.publishedAt >= :from and n.publishedAt < :to
              and (:authorId is null or n.author.id = :authorId)
            group by year(cast(function('convert_tz', n.publishedAt, '+00:00', :offset) as LocalDateTime)),
                     month(cast(function('convert_tz', n.publishedAt, '+00:00', :offset) as LocalDateTime)),
                     day(cast(function('convert_tz', n.publishedAt, '+00:00', :offset) as LocalDateTime))
            """)
    List<Object[]> countPublishedPerDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("offset") String offset, @Param("authorId") Long authorId);

    // [year, month, number of articles] for publish dates in [from, to)
    @Query("""
            select year(cast(function('convert_tz', n.publishedAt, '+00:00', :offset) as LocalDateTime)),
                   month(cast(function('convert_tz', n.publishedAt, '+00:00', :offset) as LocalDateTime)),
                   count(n)
            from News n
            where n.publishedAt >= :from and n.publishedAt < :to
              and (:authorId is null or n.author.id = :authorId)
            group by year(cast(function('convert_tz', n.publishedAt, '+00:00', :offset) as LocalDateTime)),
                     month(cast(function('convert_tz', n.publishedAt, '+00:00', :offset) as LocalDateTime))
            """)
    List<Object[]> countPublishedPerMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("offset") String offset, @Param("authorId") Long authorId);

    // ---- views over time (the views recorded in news_views; same clock rule as the published series) ----

    // [year, month, day, number of views] for views in [from, to); authorId null = the whole site
    @Query("""
            select year(cast(function('convert_tz', v.viewedAt, '+00:00', :offset) as LocalDateTime)),
                   month(cast(function('convert_tz', v.viewedAt, '+00:00', :offset) as LocalDateTime)),
                   day(cast(function('convert_tz', v.viewedAt, '+00:00', :offset) as LocalDateTime)),
                   count(v)
            from NewsView v
            where v.viewedAt >= :from and v.viewedAt < :to
              and (:authorId is null or v.news.author.id = :authorId)
            group by year(cast(function('convert_tz', v.viewedAt, '+00:00', :offset) as LocalDateTime)),
                     month(cast(function('convert_tz', v.viewedAt, '+00:00', :offset) as LocalDateTime)),
                     day(cast(function('convert_tz', v.viewedAt, '+00:00', :offset) as LocalDateTime))
            """)
    List<Object[]> countViewsPerDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("offset") String offset, @Param("authorId") Long authorId);

    // [year, month, number of views] for views in [from, to)
    @Query("""
            select year(cast(function('convert_tz', v.viewedAt, '+00:00', :offset) as LocalDateTime)),
                   month(cast(function('convert_tz', v.viewedAt, '+00:00', :offset) as LocalDateTime)),
                   count(v)
            from NewsView v
            where v.viewedAt >= :from and v.viewedAt < :to
              and (:authorId is null or v.news.author.id = :authorId)
            group by year(cast(function('convert_tz', v.viewedAt, '+00:00', :offset) as LocalDateTime)),
                     month(cast(function('convert_tz', v.viewedAt, '+00:00', :offset) as LocalDateTime))
            """)
    List<Object[]> countViewsPerMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("offset") String offset, @Param("authorId") Long authorId);

    // ---- numbers of one period, for the trend against the previous period ----

    @Query("select count(u) from AppUser u where u.createdAt >= :from and u.createdAt < :to")
    long countUsersCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(n) from News n where n.publishedAt >= :from and n.publishedAt < :to and (:authorId is null or n.author.id = :authorId)")
    long countPublishedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("authorId") Long authorId);

    @Query("select count(v) from NewsView v where v.viewedAt >= :from and v.viewedAt < :to and (:authorId is null or v.news.author.id = :authorId)")
    long countViewsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("authorId") Long authorId);

    @Query("select count(c) from Comment c where c.createdAt >= :from and c.createdAt < :to and (:authorId is null or c.news.author.id = :authorId)")
    long countCommentsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("authorId") Long authorId);

    // ---- needs attention ----

    @Query("select count(u) from AppUser u where u.emailVerified = false")
    long countUnverifiedUsers();

    // ---- recent activity (the latest rows of each source; the service merges them) ----

    @Query("""
            select new com.api.v1.bearerauth.model.response.ActivityItemDto(
                   u.fullName, u.username, n.id, n.title, n.slug, h.fromStatus, h.toStatus, h.changedAt)
            from NewsStatusHistory h join h.news n join h.changedBy u
            where (:authorId is null or n.author.id = :authorId)
            order by h.changedAt desc, h.id desc
            """)
    List<ActivityItemDto> findRecentStatusChanges(@Param("authorId") Long authorId, Pageable pageable);

    @Query("""
            select new com.api.v1.bearerauth.model.response.ActivityItemDto(
                   u.fullName, u.username, n.id, n.title, n.slug, m.content, m.createdAt)
            from Comment m join m.news n join m.user u
            where (:authorId is null or n.author.id = :authorId)
            order by m.createdAt desc, m.id desc
            """)
    List<ActivityItemDto> findRecentComments(@Param("authorId") Long authorId, Pageable pageable);

    // ---- author dashboard ----

    @Query("select n.status, count(n) from News n where n.author.id = :authorId group by n.status")
    List<Object[]> countNewsByStatusOfAuthor(@Param("authorId") Long authorId);

    @Query("""
            select coalesce(sum(n.viewCount), 0) from News n
            where n.author.id = :authorId
              and n.status = com.api.v1.bearerauth.model.enumerate.NewsStatus.PUBLISHED
            """)
    long sumViewsOfPublishedByAuthor(@Param("authorId") Long authorId);

    @Query("select count(c) from Comment c where c.news.author.id = :authorId")
    long countCommentsOnArticlesOfAuthor(@Param("authorId") Long authorId);
}
