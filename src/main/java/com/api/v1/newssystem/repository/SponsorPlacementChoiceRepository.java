package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.SponsorPlacementChoice;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SponsorPlacementChoiceRepository extends JpaRepository<SponsorPlacementChoice, Long> {

    // [sponsorId, placement] of the chosen placements of some sponsors
    @Query("select p.sponsor.id, p.placement from SponsorPlacementChoice p where p.sponsor.id in :sponsorIds")
    List<Object[]> findChosen(@Param("sponsorIds") Collection<Long> sponsorIds);

    List<SponsorPlacementChoice> findAllBySponsorId(Long sponsorId);

    boolean existsBySponsorIdAndPlacement(Long sponsorId, SponsorPlacement placement);

    void deleteAllBySponsorId(Long sponsorId);
}
