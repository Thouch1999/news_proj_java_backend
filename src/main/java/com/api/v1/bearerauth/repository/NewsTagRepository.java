package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.NewsTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsTagRepository extends JpaRepository<NewsTag, Long> {
    List<NewsTag> findAllByNewsId(Long newsId);

    // the tags of many articles in one query (no query per article)
    @Query("select nt from NewsTag nt join fetch nt.tag where nt.news.id in :newsIds")
    List<NewsTag> findAllWithTagByNewsIdIn(@Param("newsIds") Collection<Long> newsIds);

    void deleteAllByTagId(Long tagId);
    void deleteAllByNewsId(Long newsId);
}
