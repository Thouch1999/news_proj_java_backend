package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.NewsStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsStatusHistoryRepository extends JpaRepository<NewsStatusHistory, Long> {
    List<NewsStatusHistory> findAllByNewsIdOrderByChangedAtAsc(Long newsId);
    void deleteAllByNewsId(Long newsId);
}
