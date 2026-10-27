package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.common.response.PaginationResponse;
import com.api.v1.newssystem.common.response.SuccessResponse;
import com.api.v1.newssystem.model.request.SponsorRequest;
import com.api.v1.newssystem.model.response.SponsorDto;
import com.api.v1.newssystem.model.response.SponsorOptionsDto;
import com.api.v1.newssystem.model.response.SponsorStatsDto;
import com.api.v1.newssystem.model.response.SponsorSummaryDto;
import com.api.v1.newssystem.model.response.YoutubeCheckDto;
import com.api.v1.newssystem.service.SponsorService;
import com.api.v1.newssystem.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

// The management of the sponsors: ADMIN only (SecurityConfig, /api/v1/admin/**). The public side is PromoController.
@RestController
@RequestMapping("/api/v1/admin/promos")
@RequiredArgsConstructor
public class SponsorController {
    private final SponsorService sponsorService;

    // status: active, scheduled, ended or inactive; placement: ARTICLE_SIDEBAR or ARTICLE_POPUP; search in the name and the title
    @GetMapping
    public ResponseEntity<PaginationResponse<List<SponsorDto>>> list(@RequestParam(required = false) String status,
                                                                     @RequestParam(required = false) String placement,
                                                                     @RequestParam(required = false) String search,
                                                                     @RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponseUtil.pagination(HttpStatus.OK, sponsorService.list(status, search, placement, page, size)));
    }

    // ---- the form (fixed paths, before /{id}) ----

    // the current site defaults of the popup timing and the limits (popup, image, video) the form shows as hints
    @GetMapping("/options")
    public ResponseEntity<SuccessResponse<SponsorOptionsDto>> options() {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.options()));
    }

    // the video ID and thumbnail of a YouTube link, or a 400 with the problem (nothing is saved, YouTube is not called)
    @GetMapping("/youtube/check")
    public ResponseEntity<SuccessResponse<YoutubeCheckDto>> checkYoutube(@RequestParam String url) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.checkYoutube(url)));
    }

    // ---- statistics (fixed paths, before /{id}) ----

    @GetMapping("/stats/summary")
    public ResponseEntity<SuccessResponse<SponsorSummaryDto>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.summary(from, to)));
    }

    // The daily rows of one sponsor (sponsorId) or of all, as CSV (UTF-8 with a BOM).
    @GetMapping("/stats/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long sponsorId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"promo-stats.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(sponsorService.exportCsv(sponsorId, from, to));
    }

    // ---- one sponsor ----

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<SponsorDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.get(id)));
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<SponsorDto>> create(@Valid @RequestBody SponsorRequest request) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.CREATED, sponsorService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<SponsorDto>> update(@PathVariable Long id, @Valid @RequestBody SponsorRequest request) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.update(id, request)));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<SuccessResponse<SponsorDto>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.setActive(id, true)));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<SuccessResponse<SponsorDto>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.setActive(id, false)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse<Void>> delete(@PathVariable Long id) {
        sponsorService.delete(id);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    // multipart `file` (PNG, JPG or WebP, 2 MB at most) and an optional `altText`; replaces the image of the placement
    @PostMapping(value = "/{id}/creatives/{placement}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<SponsorDto>> uploadCreative(@PathVariable Long id,
                                                                      @PathVariable String placement,
                                                                      @RequestParam("file") MultipartFile file,
                                                                      @RequestParam(required = false) String altText) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.uploadCreative(id, placement, file, altText)));
    }

    // multipart `file` (MP4 with H.264 or WebM, 15 MB, 30 seconds) and `poster` (an image, required), optional `altText`
    @PostMapping(value = "/{id}/creatives/{placement}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<SponsorDto>> uploadVideo(@PathVariable Long id,
                                                                   @PathVariable String placement,
                                                                   @RequestParam("file") MultipartFile file,
                                                                   @RequestParam(value = "poster", required = false) MultipartFile poster,
                                                                   @RequestParam(required = false) String altText) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.uploadVideo(id, placement, file, poster, altText)));
    }

    // multipart `url` (a YouTube link; only its video ID is stored), optional `poster` image and `altText`; the popup only
    @PostMapping(value = "/{id}/creatives/{placement}/youtube", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<SponsorDto>> uploadYoutube(@PathVariable Long id,
                                                                     @PathVariable String placement,
                                                                     @RequestParam("url") String url,
                                                                     @RequestParam(value = "poster", required = false) MultipartFile poster,
                                                                     @RequestParam(required = false) String altText) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.uploadYoutube(id, placement, url, poster, altText)));
    }

    // multipart `file`: replaces only the poster of a video creative
    @PostMapping(value = "/{id}/creatives/{placement}/poster", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<SponsorDto>> uploadPoster(@PathVariable Long id,
                                                                    @PathVariable String placement,
                                                                    @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.uploadPoster(id, placement, file)));
    }

    // multipart `file`: the square logo (PNG, JPG or WebP, 2 MB); the 256 and 128 px variants are made by the image service
    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<SponsorDto>> uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.uploadLogo(id, file)));
    }

    @DeleteMapping("/{id}/logo")
    public ResponseEntity<SuccessResponse<SponsorDto>> deleteLogo(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.deleteLogo(id)));
    }

    @DeleteMapping("/{id}/creatives/{placement}")
    public ResponseEntity<SuccessResponse<SponsorDto>> deleteCreative(@PathVariable Long id, @PathVariable String placement) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.deleteCreative(id, placement)));
    }

    // from / to: UTC days (yyyy-MM-dd, both included), default the last 30 days
    @GetMapping("/{id}/stats")
    public ResponseEntity<SuccessResponse<SponsorStatsDto>> stats(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, sponsorService.stats(id, from, to)));
    }
}
