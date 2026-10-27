package com.api.v1.newssystem.model.entity;

import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;

/**
 * A placement the admin chose for a sponsor (ARTICLE_SIDEBAR and/or ARTICLE_POPUP). Unchecking a placement deletes its row
 * here and keeps its image (SponsorCreative), so checking it again needs no new upload.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@NullMarked
@Table(name = "sponsor_placements", options = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = @UniqueConstraint(name = "uk_sponsor_placements", columnNames = {"sponsor_id", "placement"}))
public class SponsorPlacementChoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sponsor_id", nullable = false)
    private Sponsor sponsor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private SponsorPlacement placement;
}
