package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.NewsStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsStatusHistoryRepository extends JpaRepository<NewsStatusHistory, Long> {
    List<NewsStatusHistory> findAllByNewsIdOrderByChangedAtAsc(Long newsId);
    void deleteAllByNewsId(Long newsId);
}
