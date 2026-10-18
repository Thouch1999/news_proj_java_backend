package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.NewsImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NewsImageRepository extends JpaRepository<NewsImage, Long> {
    List<NewsImage> findAllByNewsIdOrderBySortOrderAsc(Long newsId);
    Optional<NewsImage> findByIdAndNewsId(Long id, Long newsId);
    long countByNewsId(Long newsId);
    void deleteAllByNewsId(Long newsId);
}
