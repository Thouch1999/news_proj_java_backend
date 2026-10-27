package com.api.v1.newssystem.model.entity;

import com.api.v1.newssystem.model.enumerate.SponsorDevice;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;

import java.time.LocalDate;

/**
 * The impressions, clicks and video plays / completions of a sponsor in one placement on one day (UTC). The counters are changed only by the atomic
 * statements of SponsorDailyStatRepository (insert on the first event, then "impressions = impressions + 1").
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@NullMarked
@Table(name = "sponsor_daily_stats", options = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = @UniqueConstraint(name = "uk_sponsor_daily_stats_device", columnNames = {"sponsor_id", "placement", "stat_date", "device"}),
        indexes = @Index(name = "idx_sponsor_daily_stats_date", columnList = "stat_date"))
public class SponsorDailyStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sponsor_id", nullable = false)
    private Sponsor sponsor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private SponsorPlacement placement;

    // DESKTOP or MOBILE (the popup on a computer, the bar on a phone); the sidebar is always DESKTOP
    @Enumerated(EnumType.STRING)
    @Column(name = "device", nullable = false, columnDefinition = "VARCHAR(10) NOT NULL DEFAULT 'DESKTOP'")
    private SponsorDevice device = SponsorDevice.DESKTOP;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(nullable = false)
    private long impressions;

    @Column(nullable = false)
    private long clicks;

    @Column(name = "video_plays", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private long videoPlays;

    @Column(name = "video_completes", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private long videoCompletes;
}
