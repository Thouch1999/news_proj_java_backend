package com.api.v1.newssystem.model.entity;

import com.api.v1.newssystem.model.enumerate.SponsorMediaType;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The media of a sponsor for one placement (at most one per sponsor and placement): an image, a video file or a YouTube
 * video. imageUrl is always set: the picture, or the poster of a video.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@NullMarked
@Table(name = "sponsor_creatives", options = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = @UniqueConstraint(name = "uk_sponsor_creatives_placement", columnNames = {"sponsor_id", "placement"}))
public class SponsorCreative {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sponsor_id", nullable = false)
    private Sponsor sponsor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(30)")
    private SponsorPlacement placement;

    // the path of the stored file (/uploads/<uuid>.png); for a video its poster (an uploaded one, or the YouTube thumbnail)
    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 200)
    private String altText = "";

    // true for the bottom bar image that the image service cut from the popup image (an uploaded bar image is not generated)
    @Column(name = "is_generated", nullable = false, columnDefinition = "BIT(1) NOT NULL DEFAULT 0")
    private boolean generated = false;

    // VARCHAR, not a MySQL ENUM: a type can be added later
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'IMAGE'")
    private SponsorMediaType mediaType = SponsorMediaType.IMAGE;

    // VIDEO_FILE: the path of the stored video (/uploads/<uuid>.mp4 or .webm)
    @Column(length = 500)
    @Nullable
    private String videoUrl;

    // YOUTUBE: only the 11-character video ID, never the link the admin pasted
    @Column(columnDefinition = "VARCHAR(11)")
    @Nullable
    private String youtubeId;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
