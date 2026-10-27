package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.request.SponsorRequest;
import com.api.v1.newssystem.model.response.SponsorDto;
import com.api.v1.newssystem.model.response.SponsorOptionsDto;
import com.api.v1.newssystem.model.response.SponsorStatsDto;
import com.api.v1.newssystem.model.response.SponsorSummaryDto;
import com.api.v1.newssystem.model.response.YoutubeCheckDto;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/** The management of the sponsors. ADMIN only (enforced in SecurityConfig). Every change is in the activity log. */
public interface SponsorService {
    /**
     * `status`: active, scheduled, ended, inactive (null or empty: any); `search` looks in the name and the title;
     * `placement`: ARTICLE_SIDEBAR or ARTICLE_POPUP, only the sponsors that chose it (null or empty: any).
     */
    Page<SponsorDto> list(String status, String search, String placement, int page, int size);

    SponsorDto get(Long id);

    /**
     * Creates a sponsor (off unless the request says active, which needs the images that a new sponsor cannot have yet).
     * `placements` (at least one of ARTICLE_SIDEBAR, ARTICLE_POPUP) says where it is shown. Wrong values: 400
     * VALIDATION_FAILED with the field errors (placements, sidebarImage, popupImage, ...).
     */
    SponsorDto create(SponsorRequest request);

    /**
     * Edits everything except the on/off state and the images, the chosen places included: unchecking a place keeps its image,
     * checking it again needs no new upload. For an active sponsor every chosen place must have its image.
     */
    SponsorDto update(Long id, SponsorRequest request);

    SponsorDto setActive(Long id, boolean active);

    /** Deletes the sponsor with its images (files included), its category targets and its statistics. */
    void delete(Long id);

    /** Saves the image of one placement (replacing the old one and deleting its file). `altText` null or blank: the title. */
    SponsorDto uploadCreative(Long id, String placement, MultipartFile file, String altText);

    /**
     * Saves a video file (MP4 with H.264 or WebM, 15 MB, 30 seconds when the duration can be read) with its required poster
     * image as the media of a placement (the sidebar or the popup). The real type is read from the content.
     */
    SponsorDto uploadVideo(Long id, String placement, MultipartFile file, MultipartFile poster, String altText);

    /**
     * Saves a YouTube video (the popup only) from a link: only the 11-character video ID is stored. The poster is the uploaded
     * image, or the YouTube thumbnail when `poster` is null or empty.
     */
    SponsorDto uploadYoutube(Long id, String placement, String url, MultipartFile poster, String altText);

    /** Replaces only the poster of a video (file or YouTube) creative. */
    SponsorDto uploadPoster(Long id, String placement, MultipartFile poster);

    /** The video ID and thumbnail of a valid YouTube link; a link that is not accepted is a 400 VALIDATION_FAILED (youtubeUrl). */
    YoutubeCheckDto checkYoutube(String url);

    /** The current site defaults of the popup timing and the limits of the form (popup, image and video). */
    SponsorOptionsDto options();

    SponsorDto deleteCreative(Long id, String placement);

    /**
     * Saves the square logo (PNG, JPG or WebP, 2 MB): the image service makes the 256x256 and 128x128 variants (a centered
     * square); a WebP it cannot decode is kept as uploaded. The old files are deleted after the commit.
     */
    SponsorDto uploadLogo(Long id, MultipartFile file);

    SponsorDto deleteLogo(Long id);

    /** Impressions, clicks and click rate of one sponsor; `from` and `to` (UTC days, both included) default to the last 30 days. */
    SponsorStatsDto stats(Long id, LocalDate from, LocalDate to);

    /** The totals of every sponsor for the range. */
    SponsorSummaryDto summary(LocalDate from, LocalDate to);

    /** The daily rows as CSV (UTF-8 with a BOM) for one sponsor, or for all when `sponsorId` is null. */
    byte[] exportCsv(Long sponsorId, LocalDate from, LocalDate to);
}
