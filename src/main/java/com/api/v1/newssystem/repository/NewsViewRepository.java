package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.NewsView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface NewsViewRepository extends JpaRepository<NewsView, Long> {
    long countByNewsId(Long newsId);

    // the 24-hour rule: has this user / this address already viewed the article since `since`?
    boolean existsByNewsIdAndUserIdAndViewedAtAfter(Long newsId, Long userId, LocalDateTime since);

    boolean existsByNewsIdAndIpAndViewedAtAfter(Long newsId, String ip, LocalDateTime since);

    // a bulk delete: an article can have very many views
    @Modifying
    @Query("delete from NewsView v where v.news.id = :newsId")
    void deleteAllByNewsId(@Param("newsId") Long newsId);
}
