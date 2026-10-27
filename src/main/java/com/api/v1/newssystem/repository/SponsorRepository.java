package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.Sponsor;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SponsorRepository extends JpaRepository<Sponsor, Long> {

    // placement: only the sponsors that chose it (null: any). status: "" (any), "active" (on and inside its dates), "scheduled" (on, starts later), "ended" (on, over) or
    // "inactive" (off). `search` is a ready LIKE pattern: lower case, with % _ and ! escaped by "!".
    @Query("""
            select s from Sponsor s
            where (lower(s.name) like :search escape '!' or lower(s.title) like :search escape '!')
              and (:placement is null
                   or exists (select 1 from SponsorPlacementChoice p where p.sponsor = s and p.placement = :placement))
              and (:status = ''
                   or (:status = 'inactive' and s.active = false)
                   or (:status = 'scheduled' and s.active = true and s.startAt is not null and s.startAt > :now)
                   or (:status = 'ended' and s.active = true and s.endAt is not null and s.endAt <= :now)
                   or (:status = 'active' and s.active = true
                       and (s.startAt is null or s.startAt <= :now) and (s.endAt is null or s.endAt > :now)))
            order by s.createdAt desc, s.id desc
            """)
    Page<Sponsor> search(@Param("search") String search, @Param("status") String status, @Param("placement") SponsorPlacement placement,
                         @Param("now") Instant now, Pageable pageable);
}
