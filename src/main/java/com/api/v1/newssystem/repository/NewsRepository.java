package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.News;
import com.api.v1.newssystem.model.enumerate.NewsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsRepository extends JpaRepository<News, Long> {
    Optional<News> findBySlug(String slug);
    boolean existsBySlug(String slug);
    long countByCategoryId(Long categoryId);

    // an article of this author only: another author's id gives an empty result
    Optional<News> findByIdAndAuthorId(Long id, Long authorId);

    // management list; every filter is optional (null means "any")
    @Query("""
            select n from News n
            where (:authorId is null or n.author.id = :authorId)
              and (:categoryId is null or n.category.id = :categoryId)
              and (:status is null or n.status = :status)
            """)
    Page<News> search(@Param("authorId") Long authorId,
                      @Param("categoryId") Long categoryId,
                      @Param("status") NewsStatus status,
                      Pageable pageable);

    // the editors' review queue: every article in this status
    Page<News> findAllByStatus(NewsStatus status, Pageable pageable);

    // ---- public reading: only PUBLISHED articles of an active category; author and category come in the same query ----

    // every filter is optional. `keyword` is a ready LIKE pattern: lower case, with % _ and ! escaped by "!"
    @EntityGraph(attributePaths = {"author", "category"})
    @Query("""
            select n from News n
            where n.status = com.api.v1.newssystem.model.enumerate.NewsStatus.PUBLISHED
              and n.category.active = true
              and (:category is null or n.category.slug = :category)
              and (:tag is null or exists (select 1 from NewsTag nt where nt.news = n and nt.tag.slug = :tag))
              and (:keyword is null
                   or lower(n.title) like :keyword escape '!'
                   or lower(n.summary) like :keyword escape '!'
                   or lower(n.content) like :keyword escape '!')
              and (:from is null or n.publishedAt >= :from)
              and (:to is null or n.publishedAt < :to)
            """)
    Page<News> searchPublished(@Param("category") String category,
                               @Param("tag") String tag,
                               @Param("keyword") String keyword,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               Pageable pageable);

    // the first articles in the order of `pageable` (no count query): latest, most viewed
    @EntityGraph(attributePaths = {"author", "category"})
    @Query("""
            select n from News n
            where n.status = com.api.v1.newssystem.model.enumerate.NewsStatus.PUBLISHED
              and n.category.active = true
            """)
    List<News> findPublished(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "category"})
    @Query("""
            select n from News n
            where n.slug = :slug
              and n.status = com.api.v1.newssystem.model.enumerate.NewsStatus.PUBLISHED
              and n.category.active = true
            """)
    Optional<News> findPublishedBySlug(@Param("slug") String slug);

    // a PUBLISHED article of an active category by id (bookmarking): anything else is empty
    @EntityGraph(attributePaths = {"category"})
    @Query("""
            select n from News n
            where n.id = :id
              and n.status = com.api.v1.newssystem.model.enumerate.NewsStatus.PUBLISHED
              and n.category.active = true
            """)
    Optional<News> findPublishedById(@Param("id") Long id);

    // ids of the newest `perCategory` published articles of every active category, in one query
    @Query(value = """
            select t.id from (
                select n.id as id,
                       row_number() over (partition by n.category_id order by n.published_at desc, n.id desc) as rn
                from news n
                join categories c on c.id = n.category_id
                where n.status = 'PUBLISHED' and c.active = true
            ) t
            where t.rn <= :perCategory
            """, nativeQuery = true)
    List<Long> findNewestIdsPerCategory(@Param("perCategory") int perCategory);

    @EntityGraph(attributePaths = {"author", "category"})
    @Query("select n from News n where n.id in :ids")
    List<News> findAllWithAuthorAndCategoryByIdIn(@Param("ids") Collection<Long> ids);

    // atomic, so two readers at the same time do not lose a view
    @Modifying
    @Query("update News n set n.viewCount = n.viewCount + 1 where n.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // one row per category that has news: [categoryId, number of news]
    @Query("select n.category.id, count(n) from News n group by n.category.id")
    List<Object[]> countNewsPerCategory();
}
