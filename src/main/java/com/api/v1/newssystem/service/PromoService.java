package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.response.PromoDto;
import com.api.v1.newssystem.model.response.PromoSidebarDto;

import java.util.Optional;

/** The public side of the sponsors: choosing one to show, and counting its impressions and clicks. No login. */
public interface PromoService {
    /**
     * One sponsor for a placement, chosen by weighted random among those that are active, inside their dates, have an image
     * for the placement and match the language and the category. Empty when none is eligible, or when the setting
     * sponsors_enabled is off (or sponsor_popup_enabled for the popup). `categorySlug` and `lang` (en or km) may be null:
     * a sponsor with a category target needs a matching slug, and a sponsor of one language needs that language.
     */
    Optional<PromoDto> pick(String placement, String categorySlug, String lang);

    /**
     * The article sidebar: the display settings and up to 24 DIFFERENT eligible sponsors in weighted-random order (a weighted
     * shuffle without replacement), each with the image of the layout (the sidebar image, or the square logo for LOGOS_4, which
     * skips the sponsors without a logo). Empty when none is eligible or sponsors_enabled is off.
     */
    Optional<PromoSidebarDto> pickSidebar(String categorySlug, String lang);

    /**
     * Counts one impression today (UTC). A known bot (User-Agent) and an address over the limit are ignored without an
     * error. An unknown sponsor or a sponsor without an image for the placement is a 404.
     */
    void recordImpression(Long sponsorId, String placement, String ip, String userAgent);

    /**
     * Counts one video play (the video started) or one video completion (it reached its end) today (UTC): the same bot and
     * limit rules as the impressions. The sponsor must have chosen the placement and its creative there must be a video
     * (404 SPONSOR_VIDEO_NOT_FOUND otherwise); the phone bar format is 400 SPONSOR_MEDIA_NOT_ALLOWED (it is always an image).
     */
    void recordVideoPlay(Long sponsorId, String placement, String ip, String userAgent);

    void recordVideoComplete(Long sponsorId, String placement, String ip, String userAgent);

    /**
     * Counts one click (the same bot and limit rules) and returns the address to redirect to: always the one saved with the
     * sponsor, never one from the request.
     */
    String recordClick(Long sponsorId, String placement, String ip, String userAgent);
}
