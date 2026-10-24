package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    Optional<Bookmark> findByUserIdAndNewsId(Long userId, Long newsId);
    boolean existsByUserIdAndNewsId(Long userId, Long newsId);
    long countByUserIdAndNewsId(Long userId, Long newsId);

    // the saved news of a user that readers can open: PUBLISHED, in an active category. An article that was archived
    // keeps its bookmark (it shows again if it is restored). The articles come with author and category.
    @EntityGraph(attributePaths = {"news", "news.author", "news.category"})
    @Query("""
            select b from Bookmark b
            where b.user.id = :userId
              and b.news.status = com.api.v1.newssystem.model.enumerate.NewsStatus.PUBLISHED
              and b.news.category.active = true
            """)
    Page<Bookmark> findReadableByUserId(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("delete from Bookmark b where b.user.id = :userId and b.news.id = :newsId")
    void deleteByUserIdAndNewsId(@Param("userId") Long userId, @Param("newsId") Long newsId);

    @Modifying
    @Query("delete from Bookmark b where b.news.id = :newsId")
    void deleteAllByNewsId(@Param("newsId") Long newsId);
}
