package com.api.v1.newssystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What a page needs to show one sponsor: nothing about weights, dates, targeting or the sponsor's real address. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PromoDto {
    private Long id;
    private String placement;
    private String title;
    private String description;
    private String ctaText;
    // "Sponsored" (or the Khmer word), to show next to the promo
    private String label;
    // the picture, or the poster of a video
    private String imageUrl;
    // the size the image is displayed at
    private int width;
    private int height;
    private String altText;
    // the path to open on a click: it counts the click and redirects to the sponsor
    private String clickUrl;
    // IMAGE, VIDEO_FILE or YOUTUBE (the phone bar is always IMAGE)
    private String mediaType;
    // the video file to play (VIDEO_FILE only)
    private String videoUrl;
    // the YouTube video ID (YOUTUBE only): the page builds the youtube-nocookie.com embed address from it
    private String youtubeId;
    // the popup rules to follow (popup and bar only; null for the sidebar)
    private PopupRulesDto popup;
}
