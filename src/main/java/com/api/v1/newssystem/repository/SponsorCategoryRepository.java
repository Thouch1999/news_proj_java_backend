package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.SponsorCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SponsorCategoryRepository extends JpaRepository<SponsorCategory, Long> {

    // [sponsorId, categoryId, categorySlug] of the targeted categories of some sponsors
    @Query("select sc.sponsor.id, sc.category.id, sc.category.slug from SponsorCategory sc where sc.sponsor.id in :sponsorIds")
    List<Object[]> findTargets(@Param("sponsorIds") Collection<Long> sponsorIds);

    void deleteAllBySponsorId(Long sponsorId);
}
