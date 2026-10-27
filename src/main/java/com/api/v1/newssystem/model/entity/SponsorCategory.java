package com.api.v1.newssystem.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;

/** Optional targeting: a sponsor with rows here is shown only with articles of these categories; no rows means all. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@NullMarked
@Table(name = "sponsor_categories", options = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = @UniqueConstraint(name = "uk_sponsor_categories", columnNames = {"sponsor_id", "category_id"}))
public class SponsorCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sponsor_id", nullable = false)
    private Sponsor sponsor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
}
