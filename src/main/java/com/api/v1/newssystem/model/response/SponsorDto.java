package com.api.v1.newssystem.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** A sponsor as the admin sees it. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SponsorDto {
    private Long id;
    private String name;
    private String title;
    private String description;
    private String ctaText;
    private String targetUrl;
    private boolean active;
    // active, scheduled, ended or inactive (see SponsorServiceImpl.statusOf)
    private String status;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant endAt;
    private int weight;
    private String language;
    // true: the site defaults decide; false: the own rules below (null when not set)
    private boolean popupUseDefaults;
    private String popupTrigger;
    private Integer popupDelaySeconds;
    private Integer popupScrollPercent;
    private String popupFrequency;
    private Integer popupFrequencyDays;
    // the places the admin chose (ARTICLE_SIDEBAR, ARTICLE_POPUP); the popup is also the phone bar
    private List<String> placements;
    // empty: every category
    private List<Long> categoryIds;
    private List<CreativeDto> creatives;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;
    private String createdByName;
    // the square logo (256 and 128 px), or null
    private String logoUrl;
    private String logoSmallUrl;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreativeDto {
        private String placement;
        private String imageUrl;
        private String altText;
        // true for the bottom bar picture that the image service cut from the popup picture
        private boolean generated;
        // the size the placement displays at
        private int width;
        private int height;
        // IMAGE, VIDEO_FILE or YOUTUBE; for a video imageUrl is the poster
        private String mediaType;
        // the stored video file (VIDEO_FILE only)
        private String videoUrl;
        // the 11-character video ID (YOUTUBE only)
        private String youtubeId;
    }
}
