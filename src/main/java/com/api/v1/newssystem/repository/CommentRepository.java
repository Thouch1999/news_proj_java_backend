package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.Comment;
import com.api.v1.newssystem.model.enumerate.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    long countByNewsIdAndStatus(Long newsId, CommentStatus status);

    // the comments of an article in one status, page by page; the author comes in the same query (no query per comment)
    @EntityGraph(attributePaths = {"user", "news"})
    Page<Comment> findByNewsIdAndStatus(Long newsId, CommentStatus status, Pageable pageable);

    // moderation list; every filter is optional (null means "any")
    @EntityGraph(attributePaths = {"user", "news"})
    @Query("""
            select c from Comment c
            where (:status is null or c.status = :status)
              and (:newsId is null or c.news.id = :newsId)
              and (:userId is null or c.user.id = :userId)
            """)
    Page<Comment> search(@Param("status") CommentStatus status,
                         @Param("newsId") Long newsId,
                         @Param("userId") Long userId,
                         Pageable pageable);

    @EntityGraph(attributePaths = {"user", "news"})
    Optional<Comment> findWithUserAndNewsById(Long id);

    // [newsId, number of comments] for many articles in one query; an article without comments has no row
    @Query("select c.news.id, count(c) from Comment c where c.news.id in :newsIds and c.status = :status group by c.news.id")
    List<Object[]> countByNewsIds(@Param("newsIds") Collection<Long> newsIds, @Param("status") CommentStatus status);

    @Modifying
    @Query("delete from Comment c where c.news.id = :newsId")
    void deleteAllByNewsId(@Param("newsId") Long newsId);
}
