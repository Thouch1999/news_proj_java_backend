package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.SponsorCreative;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SponsorCreativeRepository extends JpaRepository<SponsorCreative, Long> {
    List<SponsorCreative> findAllBySponsorId(Long sponsorId);

    List<SponsorCreative> findAllBySponsorIdIn(Collection<Long> sponsorIds);

    Optional<SponsorCreative> findBySponsorIdAndPlacement(Long sponsorId, SponsorPlacement placement);

    boolean existsBySponsorIdAndPlacement(Long sponsorId, SponsorPlacement placement);

    List<SponsorCreative> findAllBySponsorIdInAndPlacement(Collection<Long> sponsorIds, SponsorPlacement placement);

    // the images of one placement of the active sponsors that CHOSE that placement (the dates, language and category are
    // checked per request)
    @Query("""
            select c from SponsorCreative c join fetch c.sponsor s
            where c.placement = :placement and s.active = true
              and exists (select 1 from SponsorPlacementChoice p where p.sponsor = s and p.placement = :placement)
            """)
    List<SponsorCreative> findForActiveSponsors(@Param("placement") SponsorPlacement placement);

    void deleteAllBySponsorId(Long sponsorId);
}
