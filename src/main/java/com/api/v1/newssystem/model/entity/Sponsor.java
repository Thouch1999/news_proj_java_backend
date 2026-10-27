package com.api.v1.newssystem.model.entity;

import com.api.v1.newssystem.model.enumerate.PopupFrequency;
import com.api.v1.newssystem.model.enumerate.PopupTrigger;
import com.api.v1.newssystem.model.enumerate.SponsorLanguage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** A sponsor (a company that pays for promotion). Its images are the SponsorCreative rows, one per placement. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@NullMarked
@Table(name = "sponsors", options = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        indexes = @Index(name = "idx_sponsors_active", columnList = "active"))
public class Sponsor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // the company
    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 150)
    private String title;

    // short plain text
    @Column(nullable = false, length = 300)
    private String description = "";

    @Column(nullable = false, length = 50)
    private String ctaText;

    // https only: the only address a click is ever redirected to
    @Column(nullable = false, length = 500)
    private String targetUrl;

    @Column(nullable = false)
    private boolean active = false;

    // UTC; null means no limit
    @Nullable
    private Instant startAt;

    @Nullable
    private Instant endAt;

    // 1 to 100: the share of the views against the other sponsors
    @Column(nullable = false)
    private int weight = 10;

    // VARCHAR, not a MySQL ENUM: a value can be added later without a schema change
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10)")
    private SponsorLanguage language = SponsorLanguage.ALL;

    // The popup timing. With popupUseDefaults (the default) the site settings decide and the fields below are null.
    @Column(nullable = false, columnDefinition = "BIT(1) NOT NULL DEFAULT 1")
    private boolean popupUseDefaults = true;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(20)")
    @Nullable
    private PopupTrigger popupTrigger;

    @Nullable
    private Integer popupDelaySeconds;

    @Nullable
    private Integer popupScrollPercent;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(20)")
    @Nullable
    private PopupFrequency popupFrequency;

    @Nullable
    private Integer popupFrequencyDays;

    // The square logo (256x256 and 128x128, made by the image service). The mobile bar and the LOGOS_4 sidebar use it.
    @Column(length = 500)
    @Nullable
    private String logoUrl;

    @Column(length = 500)
    @Nullable
    private String logoSmallUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @Nullable
    private AppUser createdBy;
}
