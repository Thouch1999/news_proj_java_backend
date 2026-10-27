package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.SponsorDailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SponsorDailyStatRepository extends JpaRepository<SponsorDailyStat, Long> {

    // One atomic statement per event: the row of the day is inserted on the first event, and every later one adds 1 in
    // the database itself (no read-then-write, so concurrent events are never lost). Needs a transaction (the service has it).
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into sponsor_daily_stats (sponsor_id, placement, device, stat_date, impressions, clicks)
            values (:sponsorId, :placement, :device, :day, 1, 0)
            on duplicate key update impressions = impressions + 1
            """)
    int addImpression(@Param("sponsorId") Long sponsorId, @Param("placement") String placement, @Param("device") String device,
                      @Param("day") LocalDate day);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into sponsor_daily_stats (sponsor_id, placement, device, stat_date, impressions, clicks)
            values (:sponsorId, :placement, :device, :day, 0, 1)
            on duplicate key update clicks = clicks + 1
            """)
    int addClick(@Param("sponsorId") Long sponsorId, @Param("placement") String placement, @Param("device") String device,
                 @Param("day") LocalDate day);

    // the same for the video events: the page counts a play when the video starts and a completion when it ends
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into sponsor_daily_stats (sponsor_id, placement, device, stat_date, impressions, clicks, video_plays, video_completes)
            values (:sponsorId, :placement, :device, :day, 0, 0, 1, 0)
            on duplicate key update video_plays = video_plays + 1
            """)
    int addVideoPlay(@Param("sponsorId") Long sponsorId, @Param("placement") String placement, @Param("device") String device,
                     @Param("day") LocalDate day);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into sponsor_daily_stats (sponsor_id, placement, device, stat_date, impressions, clicks, video_plays, video_completes)
            values (:sponsorId, :placement, :device, :day, 0, 0, 0, 1)
            on duplicate key update video_completes = video_completes + 1
            """)
    int addVideoComplete(@Param("sponsorId") Long sponsorId, @Param("placement") String placement, @Param("device") String device,
                         @Param("day") LocalDate day);

    // the rows of a range (both days included), of one sponsor or of every sponsor (sponsorId null), oldest first
    @Query("""
            select d from SponsorDailyStat d join fetch d.sponsor s
            where (:sponsorId is null or s.id = :sponsorId) and d.statDate between :from and :to
            order by d.statDate, s.id, d.placement, d.device
            """)
    List<SponsorDailyStat> findRange(@Param("sponsorId") Long sponsorId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    // [sponsorId, impressions, clicks, videoPlays, videoCompletes] per sponsor for a range
    @Query("""
            select d.sponsor.id, sum(d.impressions), sum(d.clicks), sum(d.videoPlays), sum(d.videoCompletes) from SponsorDailyStat d
            where d.statDate between :from and :to group by d.sponsor.id
            """)
    List<Object[]> totalsPerSponsor(@Param("from") LocalDate from, @Param("to") LocalDate to);

    void deleteAllBySponsorId(Long sponsorId);
}
