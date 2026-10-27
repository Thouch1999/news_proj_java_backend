package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.CacheConfig;
import com.api.v1.newssystem.model.entity.Category;
import com.api.v1.newssystem.model.entity.Sponsor;
import com.api.v1.newssystem.model.entity.SponsorCategory;
import com.api.v1.newssystem.model.entity.SponsorCreative;
import com.api.v1.newssystem.model.entity.SponsorDailyStat;
import com.api.v1.newssystem.model.entity.SponsorPlacementChoice;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.model.enumerate.SponsorDevice;
import com.api.v1.newssystem.model.enumerate.PopupFrequency;
import com.api.v1.newssystem.model.enumerate.PopupTrigger;
import com.api.v1.newssystem.model.enumerate.SettingKey;
import com.api.v1.newssystem.model.enumerate.SponsorLanguage;
import com.api.v1.newssystem.model.enumerate.SponsorMediaType;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import com.api.v1.newssystem.model.request.SponsorRequest;
import com.api.v1.newssystem.model.response.SponsorDto;
import com.api.v1.newssystem.model.response.SponsorOptionsDto;
import com.api.v1.newssystem.model.response.SponsorStatsDto;
import com.api.v1.newssystem.model.response.SponsorSummaryDto;
import com.api.v1.newssystem.model.response.YoutubeCheckDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.CategoryRepository;
import com.api.v1.newssystem.repository.SponsorCategoryRepository;
import com.api.v1.newssystem.repository.SponsorCreativeRepository;
import com.api.v1.newssystem.repository.SponsorDailyStatRepository;
import com.api.v1.newssystem.repository.SponsorPlacementChoiceRepository;
import com.api.v1.newssystem.repository.SponsorRepository;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.service.ActivityLogService;
import com.api.v1.newssystem.service.FileStorageService;
import com.api.v1.newssystem.service.SponsorService;
import com.api.v1.newssystem.service.SystemSettingService;
import com.api.v1.newssystem.util.ActivityDetails;
import com.api.v1.newssystem.util.ApiResponseUtil;
import com.api.v1.newssystem.util.CsvUtil;
import com.api.v1.newssystem.util.SponsorValidator;
import com.api.v1.newssystem.util.VideoInspector;
import com.api.v1.newssystem.util.YouTubeUrl;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class SponsorServiceImpl implements SponsorService {
    static final long IMAGE_MAX_BYTES = 2L * 1024 * 1024;
    // the sponsor videos: MP4 (H.264) or WebM, small enough for a popup that must start at once
    static final long VIDEO_MAX_BYTES = 15L * 1024 * 1024;
    static final int VIDEO_MAX_SECONDS = 30;
    // an encoder rarely writes exactly 30.000 s for a "30 second" clip: half a second of slack, not a different limit
    private static final double VIDEO_SECONDS_SLACK = 0.5;
    private static final Set<String> VIDEO_TYPES = Set.of("mp4", "webm");
    // no GIF and no SVG (an SVG can carry scripts), the same as the site images
    private static final Set<String> IMAGE_TYPES = Set.of("png", "jpg", "webp");
    private static final Set<String> STATUSES = Set.of("active", "scheduled", "ended", "inactive");
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;
    private static final int MAX_PAGE_SIZE = 100;
    // the phone bar picture cut from the popup picture: twice its 120 px display size, for retina screens
    static final int BAR_CROP_SIZE = 240;
    // the logo: twice its display size for retina, and a small variant
    static final int LOGO_SIZE = 256;
    static final int LOGO_SMALL_SIZE = 128;

    private final SponsorRepository sponsorRepository;
    private final SponsorCreativeRepository creativeRepository;
    private final SponsorCategoryRepository sponsorCategoryRepository;
    private final SponsorPlacementChoiceRepository placementRepository;
    private final SponsorDailyStatRepository statRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final CacheManager cacheManager;
    private final ActivityLogService activityLogService;
    private final SystemSettingService systemSettingService;

    // ---- reading ----

    @Override
    @Transactional(readOnly = true)
    public Page<SponsorDto> list(String status, String search, String placement, int page, int size) {
        String wanted = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (!wanted.isEmpty() && !STATUSES.contains(wanted)) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_STATUS_INVALID.getDefaultMessage());
        }
        SponsorPlacement wantedPlacement = placement == null || placement.isBlank() ? null : placementOf(placement);
        if (wantedPlacement != null && !wantedPlacement.isChoosable()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_PLACEMENT_INVALID.getDefaultMessage());
        }
        PageRequest paging = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<Sponsor> found = sponsorRepository.search(likePattern(search), wanted, wantedPlacement, Instant.now(), paging);

        List<Long> ids = found.getContent().stream().map(Sponsor::getId).toList();
        Map<Long, List<SponsorCreative>> creatives = new HashMap<>();
        Map<Long, List<Long>> categories = new HashMap<>();
        Map<Long, List<SponsorPlacement>> chosen = new HashMap<>();
        if (!ids.isEmpty()) {
            placementRepository.findChosen(ids)
                    .forEach(row -> chosen.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((SponsorPlacement) row[1]));
            creativeRepository.findAllBySponsorIdIn(ids)
                    .forEach(creative -> creatives.computeIfAbsent(creative.getSponsor().getId(), id -> new ArrayList<>()).add(creative));
            sponsorCategoryRepository.findTargets(ids)
                    .forEach(row -> categories.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((Long) row[1]));
        }
        return found.map(sponsor -> toDto(sponsor, creatives.getOrDefault(sponsor.getId(), List.of()),
                categories.getOrDefault(sponsor.getId(), List.of()), chosen.getOrDefault(sponsor.getId(), List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public SponsorDto get(Long id) {
        return toDto(find(id));
    }

    // ---- writing ----

    @Override
    @Transactional
    public SponsorDto create(SponsorRequest request) {
        // a new sponsor has no image yet: it can only be created off (the admin form uploads the images, then activates it)
        List<Category> categories = checked(request, List.of(), Boolean.TRUE.equals(request.getActive()));

        Sponsor sponsor = new Sponsor();
        apply(sponsor, request);
        sponsor.setActive(Boolean.TRUE.equals(request.getActive()));
        sponsor.setCreatedBy(appUserRepository.getReferenceById(currentUserService.getCurrentUser().getId()));
        Sponsor saved = sponsorRepository.save(sponsor);
        saveCategories(saved, categories);
        List<SponsorPlacement> chosen = chosenOf(request);
        savePlacements(saved, chosen);

        activityLogService.record(ActivityAction.SPONSOR_CREATED, ActivityTargetType.SPONSOR, saved.getId(), saved.getName(),
                ActivityDetails.of("title", saved.getTitle(), "active", saved.isActive(), "weight", saved.getWeight(),
                        "language", saved.getLanguage().name(), "placements", chosen.toString(),
                        "popupUseDefaults", saved.isPopupUseDefaults()));
        afterCommit(this::clearCache);
        return toDto(saved);
    }

    @Override
    @Transactional
    public SponsorDto update(Long id, SponsorRequest request) {
        Sponsor sponsor = find(id);
        // an active sponsor must have the image of every chosen place; a sponsor that is off may still wait for it
        List<Category> categories = checked(request, storedPlacements(id), sponsor.isActive());
        List<Long> categoriesBefore = categoryIdsOf(id);
        List<SponsorPlacement> placementsBefore = placementsOf(id);

        String name = sponsor.getName(), title = sponsor.getTitle(), description = sponsor.getDescription();
        String ctaText = sponsor.getCtaText(), targetUrl = sponsor.getTargetUrl();
        Instant startAt = sponsor.getStartAt(), endAt = sponsor.getEndAt();
        int weight = sponsor.getWeight();
        SponsorLanguage language = sponsor.getLanguage();
        boolean popupUseDefaults = sponsor.isPopupUseDefaults();
        PopupTrigger popupTrigger = sponsor.getPopupTrigger();
        Integer popupDelay = sponsor.getPopupDelaySeconds(), popupScroll = sponsor.getPopupScrollPercent();
        PopupFrequency popupFrequency = sponsor.getPopupFrequency();
        Integer popupDays = sponsor.getPopupFrequencyDays();

        apply(sponsor, request);
        sponsorCategoryRepository.deleteAllBySponsorId(id);
        sponsorCategoryRepository.flush(); // the unique key (sponsor, category) must not see the old rows
        saveCategories(sponsor, categories);
        List<SponsorPlacement> placementsAfter = chosenOf(request);
        if (!placementsAfter.equals(placementsBefore)) {
            // unchecking a place keeps its image: only the choice is deleted
            placementRepository.deleteAllBySponsorId(id);
            placementRepository.flush();
            savePlacements(sponsor, placementsAfter);
        }
        Sponsor saved = sponsorRepository.save(sponsor);

        List<Long> categoriesAfter = categories.stream().map(Category::getId).sorted().toList();
        ActivityDetails.Changes changes = new ActivityDetails.Changes()
                .field("name", name, saved.getName())
                .field("title", title, saved.getTitle())
                .field("description", description, saved.getDescription())
                .field("ctaText", ctaText, saved.getCtaText())
                .field("targetUrl", targetUrl, saved.getTargetUrl())
                .field("startAt", startAt, saved.getStartAt())
                .field("endAt", endAt, saved.getEndAt())
                .field("weight", weight, saved.getWeight())
                .field("language", language.name(), saved.getLanguage().name())
                .field("placements", placementsBefore.toString(), placementsAfter.toString())
                .field("categories", categoriesBefore.toString(), categoriesAfter.toString())
                .field("popupUseDefaults", popupUseDefaults, saved.isPopupUseDefaults())
                .field("popupTrigger", nameOf(popupTrigger), nameOf(saved.getPopupTrigger()))
                .field("popupDelaySeconds", popupDelay, saved.getPopupDelaySeconds())
                .field("popupScrollPercent", popupScroll, saved.getPopupScrollPercent())
                .field("popupFrequency", nameOf(popupFrequency), nameOf(saved.getPopupFrequency()))
                .field("popupFrequencyDays", popupDays, saved.getPopupFrequencyDays());
        if (!changes.isEmpty()) {
            activityLogService.record(ActivityAction.SPONSOR_UPDATED, ActivityTargetType.SPONSOR, saved.getId(), saved.getName(),
                    changes.toDetails());
        }
        afterCommit(this::clearCache);
        return toDto(saved);
    }

    @Override
    @Transactional
    public SponsorDto setActive(Long id, boolean active) {
        Sponsor sponsor = find(id);
        boolean before = sponsor.isActive();
        if (active) {
            // switching on: at least one place, and the image of every chosen place
            Map<String, String> problems = new TreeMap<>();
            List<SponsorPlacement> chosen = placementsOf(id);
            if (chosen.isEmpty()) {
                problems.put("placements", "Choose at least one place.");
            }
            problems.putAll(imageProblems(chosen, storedPlacements(id)));
            if (!problems.isEmpty()) {
                throw validationFailed(problems);
            }
        }
        sponsor.setActive(active);
        Sponsor saved = sponsorRepository.save(sponsor);
        if (before != active) {
            activityLogService.record(active ? ActivityAction.SPONSOR_ACTIVATED : ActivityAction.SPONSOR_DEACTIVATED,
                    ActivityTargetType.SPONSOR, saved.getId(), saved.getName(), null);
            afterCommit(this::clearCache);
        }
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Sponsor sponsor = find(id);
        List<String> files = java.util.stream.Stream.concat(
                        creativeRepository.findAllBySponsorId(id).stream().flatMap(creative -> java.util.stream.Stream.of(creative.getImageUrl(), creative.getVideoUrl())),
                        java.util.stream.Stream.of(sponsor.getLogoUrl(), sponsor.getLogoSmallUrl()))
                .filter(java.util.Objects::nonNull).distinct().toList();
        // the statistics go with the sponsor: they have no meaning without it (see CLAUDE.md, Sponsors)
        statRepository.deleteAllBySponsorId(id);
        sponsorCategoryRepository.deleteAllBySponsorId(id);
        placementRepository.deleteAllBySponsorId(id);
        creativeRepository.deleteAllBySponsorId(id);
        sponsorRepository.delete(sponsor);
        sponsorRepository.flush();

        activityLogService.record(ActivityAction.SPONSOR_DELETED, ActivityTargetType.SPONSOR, id, sponsor.getName(),
                ActivityDetails.of("title", sponsor.getTitle(), "creatives", files.size()));
        afterCommit(this::clearCache);
        afterCommit(() -> files.forEach(fileStorageService::delete)); // the files only when the rows are really gone
    }

    @Override
    @Transactional
    public SponsorDto uploadCreative(Long id, String placementName, MultipartFile file, String altText) {
        Sponsor sponsor = find(id);
        SponsorPlacement placement = placementOf(placementName);
        requireChosenPlacement(placement);
        String alt = altOf(sponsor, altText);

        String url = fileStorageService.store(file, imageRules());
        Replaced replaced = replaceMedia(sponsor, placement, SponsorMediaType.IMAGE, url, null, null, alt);
        logMedia(sponsor, placement, SponsorMediaType.IMAGE, replaced, null);
        return toDto(sponsor);
    }

    @Override
    @Transactional
    public SponsorDto uploadVideo(Long id, String placementName, MultipartFile file, MultipartFile poster, String altText) {
        Sponsor sponsor = find(id);
        SponsorPlacement placement = placementOf(placementName);
        requireMedia(SponsorMediaType.VIDEO_FILE, placement);
        String alt = altOf(sponsor, altText);
        if (poster == null || poster.isEmpty()) {
            throw validationFailed(Map.of("poster", "Upload the poster image."));
        }
        checkVideo(file);

        String videoUrl = fileStorageService.store(file, new FileStorageService.Rules(VIDEO_TYPES, VIDEO_MAX_BYTES,
                MessageConstant.SPONSOR_VIDEO_TOO_LARGE, MessageConstant.SPONSOR_VIDEO_TYPE_NOT_ALLOWED));
        String posterUrl;
        try {
            posterUrl = fileStorageService.store(poster, imageRules());
        } catch (RuntimeException e) {
            fileStorageService.delete(videoUrl); // no video without its poster
            throw e;
        }
        Replaced replaced = replaceMedia(sponsor, placement, SponsorMediaType.VIDEO_FILE, posterUrl, videoUrl, null, alt);
        logMedia(sponsor, placement, SponsorMediaType.VIDEO_FILE, replaced, null);
        return toDto(sponsor);
    }

    @Override
    @Transactional
    public SponsorDto uploadYoutube(Long id, String placementName, String url, MultipartFile poster, String altText) {
        Sponsor sponsor = find(id);
        SponsorPlacement placement = placementOf(placementName);
        requireMedia(SponsorMediaType.YOUTUBE, placement);
        String alt = altOf(sponsor, altText);
        String videoId = youtubeId(url);

        // an uploaded poster, or the picture YouTube makes for the video (the address is derived from the ID, never pasted)
        String posterUrl = poster == null || poster.isEmpty() ? YouTubeUrl.thumbnailUrl(videoId) : fileStorageService.store(poster, imageRules());
        Replaced replaced = replaceMedia(sponsor, placement, SponsorMediaType.YOUTUBE, posterUrl, null, videoId, alt);
        logMedia(sponsor, placement, SponsorMediaType.YOUTUBE, replaced, videoId);
        return toDto(sponsor);
    }

    @Override
    @Transactional
    public SponsorDto uploadPoster(Long id, String placementName, MultipartFile poster) {
        Sponsor sponsor = find(id);
        SponsorPlacement placement = placementOf(placementName);
        SponsorCreative creative = creativeRepository.findBySponsorIdAndPlacement(id, placement)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SPONSOR_CREATIVE_NOT_FOUND.getDefaultMessage()));
        if (!creative.getMediaType().isVideo()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_MEDIA_NOT_ALLOWED.getDefaultMessage());
        }
        String posterUrl = fileStorageService.store(poster, imageRules());
        Replaced replaced = replaceMedia(sponsor, placement, creative.getMediaType(), posterUrl, creative.getVideoUrl(),
                creative.getYoutubeId(), creative.getAltText());
        logMedia(sponsor, placement, creative.getMediaType(), replaced, creative.getYoutubeId());
        return toDto(sponsor);
    }

    @Override
    public YoutubeCheckDto checkYoutube(String url) {
        String videoId = youtubeId(url);
        return new YoutubeCheckDto(videoId, YouTubeUrl.thumbnailUrl(videoId));
    }

    @Override
    public SponsorOptionsDto options() {
        return new SponsorOptionsDto(systemSettingService.popupDefaults(), systemSettingService.sidebarSettings(), new SponsorOptionsDto.Limits(
                SettingKey.POPUP_DELAY_MIN, SettingKey.POPUP_DELAY_MAX, SettingKey.SCROLL_PERCENT_MIN, SettingKey.SCROLL_PERCENT_MAX,
                SettingKey.FREQUENCY_DAYS_MIN, SettingKey.FREQUENCY_DAYS_MAX, IMAGE_MAX_BYTES, VIDEO_MAX_BYTES, VIDEO_MAX_SECONDS));
    }

    @Override
    @Transactional
    public SponsorDto deleteCreative(Long id, String placementName) {
        Sponsor sponsor = find(id);
        SponsorPlacement placement = placementOf(placementName);
        requireChosenPlacement(placement);
        SponsorCreative creative = creativeRepository.findBySponsorIdAndPlacement(id, placement)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SPONSOR_CREATIVE_NOT_FOUND.getDefaultMessage()));
        // the image of a chosen place stays (it can be replaced); the images of the others are only kept for later
        if (placement.isChoosable() && placementsOf(id).contains(placement)) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_IMAGE_IN_USE.getDefaultMessage());
        }
        creativeRepository.delete(creative);
        creativeRepository.flush();
        if (placement == SponsorPlacement.ARTICLE_POPUP) {
            // without the popup image there is nothing to cut the bar from: a generated crop goes with it
            creativeRepository.findBySponsorIdAndPlacement(id, SponsorPlacement.MOBILE_BOTTOM_BAR)
                    .filter(SponsorCreative::isGenerated)
                    .ifPresent(bar -> {
                        creativeRepository.delete(bar);
                        afterCommit(() -> fileStorageService.delete(bar.getImageUrl()));
                    });
        } else if (placement == SponsorPlacement.MOBILE_BOTTOM_BAR) {
            refreshGeneratedBar(sponsor); // back to the crop of the popup image
        }

        activityLogService.record(ActivityAction.SPONSOR_UPDATED, ActivityTargetType.SPONSOR, id, sponsor.getName(),
                ActivityDetails.of("creativeRemoved", placement.name(), "mediaType", creative.getMediaType().name()));
        afterCommit(this::clearCache);
        afterCommit(() -> fileStorageService.delete(creative.getImageUrl()));
        if (creative.getVideoUrl() != null) {
            afterCommit(() -> fileStorageService.delete(creative.getVideoUrl()));
        }
        return toDto(sponsor);
    }

    // the phone bar has no image of its own any more: it uses the logo (see uploadLogo) or the crop of the popup image
    private static void requireChosenPlacement(SponsorPlacement placement) {
        if (!placement.isChoosable()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_PLACEMENT_INVALID.getDefaultMessage());
        }
    }

    @Override
    @Transactional
    public SponsorDto uploadLogo(Long id, MultipartFile file) {
        Sponsor sponsor = find(id);
        String original = fileStorageService.store(file, imageRules());
        String big = fileStorageService.storeSquareCrop(original, LOGO_SIZE);
        String small = fileStorageService.storeSquareCrop(original, LOGO_SMALL_SIZE);
        if (big == null || small == null) {
            // a picture the image service cannot read (WebP): it is kept as uploaded, the page fits it into a square
            fileStorageService.delete(big);
            fileStorageService.delete(small);
            big = original;
            small = original;
        } else {
            fileStorageService.delete(original);
        }
        List<String> previous = logoFiles(sponsor);
        boolean replaced = sponsor.getLogoUrl() != null;
        sponsor.setLogoUrl(big);
        sponsor.setLogoSmallUrl(small);
        sponsorRepository.save(sponsor);
        deleteAfterCommit(previous, List.of(big, small));

        activityLogService.record(ActivityAction.SPONSOR_UPDATED, ActivityTargetType.SPONSOR, id, sponsor.getName(),
                ActivityDetails.of("logo", "uploaded", "replaced", replaced));
        afterCommit(this::clearCache);
        return toDto(sponsor);
    }

    @Override
    @Transactional
    public SponsorDto deleteLogo(Long id) {
        Sponsor sponsor = find(id);
        if (sponsor.getLogoUrl() == null) {
            throw new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SPONSOR_LOGO_NOT_FOUND.getDefaultMessage());
        }
        List<String> previous = logoFiles(sponsor);
        sponsor.setLogoUrl(null);
        sponsor.setLogoSmallUrl(null);
        sponsorRepository.save(sponsor);
        deleteAfterCommit(previous, List.of());

        activityLogService.record(ActivityAction.SPONSOR_UPDATED, ActivityTargetType.SPONSOR, id, sponsor.getName(),
                ActivityDetails.of("logoRemoved", true));
        afterCommit(this::clearCache);
        return toDto(sponsor);
    }

    private static List<String> logoFiles(Sponsor sponsor) {
        return java.util.stream.Stream.of(sponsor.getLogoUrl(), sponsor.getLogoSmallUrl()).filter(java.util.Objects::nonNull).distinct().toList();
    }

    // the files that are no longer used go after the commit (never a file that is still in use)
    private void deleteAfterCommit(List<String> files, List<String> keep) {
        files.stream().filter(file -> !keep.contains(file)).forEach(file -> afterCommit(() -> fileStorageService.delete(file)));
    }

    // ---- media helpers ----

    /** What a media upload replaced: the type that was there before (null for a first upload). */
    private record Replaced(SponsorMediaType before, boolean existed) {
    }

    private static FileStorageService.Rules imageRules() {
        return new FileStorageService.Rules(IMAGE_TYPES, IMAGE_MAX_BYTES,
                MessageConstant.SPONSOR_IMAGE_TOO_LARGE, MessageConstant.SPONSOR_IMAGE_TYPE_NOT_ALLOWED);
    }

    // the alt text of an upload (default: the title), checked as plain text
    private static String altOf(Sponsor sponsor, String altText) {
        String alt = altText == null || altText.isBlank() ? sponsor.getTitle() : altText.strip();
        String problem = SponsorValidator.altTextProblem(alt);
        if (problem != null) {
            throw validationFailed(Map.of("altText", problem));
        }
        return alt;
    }

    // the placement must accept this kind of media (the sidebar: image or video file; the popup: also YouTube; the bar: image)
    private static void requireMedia(SponsorMediaType type, SponsorPlacement placement) {
        if (!type.allowedIn(placement)) {
            throw validationFailed(Map.of("mediaType", type == SponsorMediaType.YOUTUBE
                    ? "A YouTube video can only be used in the popup." : "Only an image can be used in this place."));
        }
    }

    private static String youtubeId(String url) {
        return YouTubeUrl.videoId(url).orElseThrow(() -> validationFailed(Map.of("youtubeUrl",
                "must be a YouTube link: youtube.com/watch?v=..., youtu.be/..., youtube.com/shorts/... or youtube.com/embed/...")));
    }

    // The size, the real type and (when it can be read) the duration of an uploaded video, from its content. The stored file is
    // checked again by the file service (the container); this adds what it does not know: H.264 and the 30 seconds.
    private static void checkVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.FILE_EMPTY.getDefaultMessage());
        }
        if (file.getSize() > VIDEO_MAX_BYTES) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_VIDEO_TOO_LARGE.getDefaultMessage());
        }
        VideoInspector.Info info;
        try {
            info = VideoInspector.inspect(file.getBytes()).orElse(null);
        } catch (IOException e) {
            throw new CustomException(HttpStatus.INTERNAL_SERVER_ERROR, MessageConstant.FILE_STORAGE_ERROR.getDefaultMessage());
        }
        if (info == null || info.isNotH264()) {
            throw new CustomException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, MessageConstant.SPONSOR_VIDEO_TYPE_NOT_ALLOWED.getDefaultMessage());
        }
        if (info.seconds() != null && info.seconds() > VIDEO_MAX_SECONDS + VIDEO_SECONDS_SLACK) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_VIDEO_TOO_LONG.getDefaultMessage());
        }
    }

    // Saves or replaces the media of a placement. The files that are no longer used are deleted after the commit.
    private Replaced replaceMedia(Sponsor sponsor, SponsorPlacement placement, SponsorMediaType type, String imageUrl,
                                  String videoUrl, String youtubeId, String alt) {
        SponsorCreative creative = creativeRepository.findBySponsorIdAndPlacement(sponsor.getId(), placement).orElseGet(() -> {
            SponsorCreative created = new SponsorCreative();
            created.setSponsor(sponsor);
            created.setPlacement(placement);
            return created;
        });
        boolean existed = creative.getId() != null;
        SponsorMediaType before = existed ? creative.getMediaType() : null;
        List<String> previous = new ArrayList<>();
        if (existed) {
            previous.add(creative.getImageUrl());
            if (creative.getVideoUrl() != null) {
                previous.add(creative.getVideoUrl());
            }
        }
        creative.setMediaType(type);
        creative.setImageUrl(imageUrl);
        creative.setVideoUrl(videoUrl);
        creative.setYoutubeId(youtubeId);
        creative.setAltText(alt);
        creative.setGenerated(false); // an uploaded bar image replaces a generated crop
        creativeRepository.save(creative);
        if (placement == SponsorPlacement.ARTICLE_POPUP) {
            refreshGeneratedBar(sponsor);
        }
        previous.stream().filter(url -> !url.equals(imageUrl) && !url.equals(videoUrl))
                .forEach(url -> afterCommit(() -> fileStorageService.delete(url)));
        afterCommit(this::clearCache);
        return new Replaced(before, existed);
    }

    // The log entry of a media change: the placement and the type, never a file name; a YouTube video is its public ID.
    private void logMedia(Sponsor sponsor, SponsorPlacement placement, SponsorMediaType type, Replaced replaced, String youtubeId) {
        activityLogService.record(ActivityAction.SPONSOR_UPDATED, ActivityTargetType.SPONSOR, sponsor.getId(), sponsor.getName(),
                ActivityDetails.of("creative", placement.name(), "mediaType", type.name(), "replaced", replaced.existed(),
                        "mediaTypeBefore", replaced.before() != null && replaced.before() != type ? replaced.before().name() : null,
                        "youtubeId", youtubeId));
    }

    // ---- statistics ----

    // the counters of one group of rows: impressions, clicks, video plays, video completes
    private static final int IMPRESSIONS = 0, CLICKS = 1, PLAYS = 2, COMPLETES = 3;

    private static void add(long[] counts, SponsorDailyStat row) {
        counts[IMPRESSIONS] += row.getImpressions();
        counts[CLICKS] += row.getClicks();
        counts[PLAYS] += row.getVideoPlays();
        counts[COMPLETES] += row.getVideoCompletes();
    }

    @Override
    @Transactional(readOnly = true)
    public SponsorStatsDto stats(Long id, LocalDate from, LocalDate to) {
        Sponsor sponsor = find(id);
        LocalDate[] range = range(from, to);
        List<SponsorDailyStat> rows = statRepository.findRange(id, range[0], range[1]);

        Map<LocalDate, long[]> perDay = new TreeMap<>();
        for (LocalDate day = range[0]; !day.isAfter(range[1]); day = day.plusDays(1)) {
            perDay.put(day, new long[4]); // every day of the range, zeros included
        }
        Map<SponsorPlacement, long[]> perPlacement = new EnumMap<>(SponsorPlacement.class);
        Map<SponsorDevice, long[]> popupPerDevice = new EnumMap<>(SponsorDevice.class);
        long[] total = new long[4];
        List<SponsorStatsDto.Point> raw = new ArrayList<>();
        for (SponsorDailyStat row : rows) {
            add(perDay.get(row.getStatDate()), row);
            // the bar of a phone counts under the popup (an old row of the bar placement too)
            SponsorPlacement counted = row.getPlacement().counted();
            add(perPlacement.computeIfAbsent(counted, key -> new long[4]), row);
            if (counted == SponsorPlacement.ARTICLE_POPUP) {
                add(popupPerDevice.computeIfAbsent(row.getDevice(), key -> new long[4]), row);
            }
            add(total, row);
            raw.add(point(row.getStatDate().toString(), counted.name(), row.getDevice().name(), counts(row)));
        }
        List<SponsorStatsDto.Point> byDay = new ArrayList<>();
        perDay.forEach((day, counts) -> byDay.add(point(day.toString(), null, null, counts)));
        List<SponsorStatsDto.Point> byPlacement = new ArrayList<>();
        for (SponsorPlacement placement : SponsorPlacement.CHOOSABLE) { // both places, even without events
            byPlacement.add(point(null, placement.name(), null, perPlacement.getOrDefault(placement, new long[4])));
        }
        List<SponsorStatsDto.Point> byDevice = new ArrayList<>();
        for (SponsorDevice device : SponsorDevice.values()) { // the popup on a computer and the bar on a phone
            byDevice.add(point(null, SponsorPlacement.ARTICLE_POPUP.name(), device.name(), popupPerDevice.getOrDefault(device, new long[4])));
        }
        return new SponsorStatsDto(sponsor.getId(), sponsor.getName(), range[0], range[1], totals(total),
                byDay, byPlacement, byDevice, raw);
    }

    @Override
    @Transactional(readOnly = true)
    public SponsorSummaryDto summary(LocalDate from, LocalDate to) {
        LocalDate[] range = range(from, to);
        Map<Long, long[]> perSponsor = new HashMap<>();
        for (Object[] row : statRepository.totalsPerSponsor(range[0], range[1])) {
            perSponsor.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue(), ((Number) row[4]).longValue()});
        }
        List<SponsorSummaryDto.SponsorTotals> sponsors = new ArrayList<>();
        long[] total = new long[4];
        for (Sponsor sponsor : sponsorRepository.findAll()) {
            long[] counts = perSponsor.getOrDefault(sponsor.getId(), new long[4]);
            for (int i = 0; i < total.length; i++) {
                total[i] += counts[i];
            }
            sponsors.add(new SponsorSummaryDto.SponsorTotals(sponsor.getId(), sponsor.getName(), counts[IMPRESSIONS], counts[CLICKS],
                    rate(counts[IMPRESSIONS], counts[CLICKS]), counts[PLAYS], counts[COMPLETES], rate(counts[PLAYS], counts[COMPLETES])));
        }
        sponsors.sort(Comparator.comparingLong(SponsorSummaryDto.SponsorTotals::getImpressions).reversed()
                .thenComparing(SponsorSummaryDto.SponsorTotals::getSponsorName));
        return new SponsorSummaryDto(range[0], range[1], totals(total), sponsors);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCsv(Long sponsorId, LocalDate from, LocalDate to) {
        if (sponsorId != null) {
            find(sponsorId);
        }
        LocalDate[] range = range(from, to);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(CsvUtil.row(List.of("Date", "Sponsor", "Placement", "Device", "Impressions", "Clicks", "Click rate (%)",
                "Video plays", "Video completes", "Completion rate (%)")));
        for (SponsorDailyStat row : statRepository.findRange(sponsorId, range[0], range[1])) {
            csv.append(CsvUtil.row(List.of(row.getStatDate(), row.getSponsor().getName(), row.getPlacement().counted().name(),
                    row.getDevice().name(), row.getImpressions(), row.getClicks(), rate(row.getImpressions(), row.getClicks()),
                    row.getVideoPlays(), row.getVideoCompletes(), rate(row.getVideoPlays(), row.getVideoCompletes()))));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---- helpers ----

    // the request must be valid, and every category must exist; returns the categories (empty: every category)
    // `have` are the places that have an image; with `needImages` every chosen place must be one of them
    private List<Category> checked(SponsorRequest request, Collection<SponsorPlacement> have, boolean needImages) {
        Map<String, String> problems = SponsorValidator.problems(request);
        if (needImages && !problems.containsKey("placements")) {
            problems.putAll(imageProblems(chosenOf(request), have));
        }
        List<Category> categories = List.of();
        List<Long> ids = request.getCategoryIds() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(request.getCategoryIds()));
        if (!ids.isEmpty()) {
            categories = categoryRepository.findAllById(ids);
            if (categories.size() != ids.size()) {
                problems.put("categoryIds", "contains a category that does not exist");
            }
        }
        if (!problems.isEmpty()) {
            throw validationFailed(problems);
        }
        return categories;
    }

    // the chosen places without an image, as field errors (sidebarImage, popupImage)
    private static Map<String, String> imageProblems(Collection<SponsorPlacement> chosen, Collection<SponsorPlacement> have) {
        Map<String, String> problems = new TreeMap<>();
        if (chosen.contains(SponsorPlacement.ARTICLE_SIDEBAR) && !have.contains(SponsorPlacement.ARTICLE_SIDEBAR)) {
            problems.put("sidebarImage", "Upload the sidebar image.");
        }
        if (chosen.contains(SponsorPlacement.ARTICLE_POPUP) && !have.contains(SponsorPlacement.ARTICLE_POPUP)) {
            problems.put("popupImage", "Upload the popup image.");
        }
        return problems;
    }

    // the chosen places of a request, each once, in the order of the enum
    private static List<SponsorPlacement> chosenOf(SponsorRequest request) {
        return SponsorPlacement.CHOOSABLE.stream().filter(placement -> request.getPlacements() != null && request.getPlacements().contains(placement)).toList();
    }

    private void savePlacements(Sponsor sponsor, List<SponsorPlacement> chosen) {
        placementRepository.saveAll(chosen.stream().map(placement -> {
            SponsorPlacementChoice choice = new SponsorPlacementChoice();
            choice.setSponsor(sponsor);
            choice.setPlacement(placement);
            return choice;
        }).toList());
    }

    // the chosen places of a stored sponsor, in the order of the enum
    private List<SponsorPlacement> placementsOf(Long sponsorId) {
        List<SponsorPlacement> stored = placementRepository.findAllBySponsorId(sponsorId).stream().map(SponsorPlacementChoice::getPlacement).toList();
        return SponsorPlacement.CHOOSABLE.stream().filter(stored::contains).toList();
    }

    // the places that have a stored image
    private List<SponsorPlacement> storedPlacements(Long sponsorId) {
        return creativeRepository.findAllBySponsorId(sponsorId).stream().map(SponsorCreative::getPlacement).toList();
    }

    // The phone bar picture: when the sponsor has no bar image of its own, the image service cuts a square out of the center
    // of the popup image (a generated row); a popup image the service cannot read (WebP) leaves no row, and the page crops it.
    private void refreshGeneratedBar(Sponsor sponsor) {
        Long id = sponsor.getId();
        SponsorCreative popup = creativeRepository.findBySponsorIdAndPlacement(id, SponsorPlacement.ARTICLE_POPUP).orElse(null);
        SponsorCreative bar = creativeRepository.findBySponsorIdAndPlacement(id, SponsorPlacement.MOBILE_BOTTOM_BAR).orElse(null);
        if (bar != null && !bar.isGenerated()) {
            return; // the admin uploaded a bar image: it stays
        }
        String url = popup == null ? null : fileStorageService.storeSquareCrop(popup.getImageUrl(), BAR_CROP_SIZE);
        if (url == null) {
            if (bar != null) {
                creativeRepository.delete(bar);
                afterCommit(() -> fileStorageService.delete(bar.getImageUrl()));
            }
            return;
        }
        SponsorCreative target = bar != null ? bar : new SponsorCreative();
        String previous = bar == null ? null : bar.getImageUrl();
        target.setSponsor(sponsor);
        target.setPlacement(SponsorPlacement.MOBILE_BOTTOM_BAR);
        target.setImageUrl(url);
        target.setAltText(popup.getAltText());
        target.setGenerated(true);
        creativeRepository.save(target);
        if (previous != null) {
            afterCommit(() -> fileStorageService.delete(previous));
        }
    }

    private static CustomException validationFailed(Map<String, String> problems) {
        return new CustomException(
                ApiResponseUtil.error(HttpStatus.BAD_REQUEST, MessageConstant.VALIDATION_FAILED.getDefaultMessage(), problems, null),
                HttpStatus.BAD_REQUEST);
    }

    // copies the checked values of the request (trimmed) into the entity, with the defaults
    private static void apply(Sponsor sponsor, SponsorRequest request) {
        sponsor.setName(request.getName().strip());
        sponsor.setTitle(request.getTitle().strip());
        sponsor.setDescription(request.getDescription() == null ? "" : request.getDescription().strip());
        sponsor.setCtaText(request.getCtaText().strip());
        sponsor.setTargetUrl(request.getTargetUrl().strip());
        sponsor.setStartAt(request.getStartAt());
        sponsor.setEndAt(request.getEndAt());
        sponsor.setWeight(request.getWeight() == null ? SponsorValidator.WEIGHT_DEFAULT : request.getWeight());
        sponsor.setLanguage(request.getLanguage() == null ? SponsorLanguage.ALL : request.getLanguage());
        // the own popup rules are kept only when the sponsor uses them: with the site defaults the fields are null
        boolean own = SponsorValidator.usesOwnPopupRules(request);
        sponsor.setPopupUseDefaults(!own);
        sponsor.setPopupTrigger(own ? request.getPopupTrigger() : null);
        sponsor.setPopupDelaySeconds(own ? request.getPopupDelaySeconds() : null);
        sponsor.setPopupScrollPercent(own ? request.getPopupScrollPercent() : null);
        sponsor.setPopupFrequency(own ? request.getPopupFrequency() : null);
        sponsor.setPopupFrequencyDays(own ? request.getPopupFrequencyDays() : null);
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private void saveCategories(Sponsor sponsor, List<Category> categories) {
        sponsorCategoryRepository.saveAll(categories.stream().map(category -> {
            SponsorCategory target = new SponsorCategory();
            target.setSponsor(sponsor);
            target.setCategory(category);
            return target;
        }).toList());
    }

    private List<Long> categoryIdsOf(Long sponsorId) {
        return sponsorCategoryRepository.findTargets(List.of(sponsorId)).stream().map(row -> (Long) row[1]).sorted().toList();
    }

    private Sponsor find(Long id) {
        return sponsorRepository.findById(id)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SPONSOR_NOT_FOUND.getDefaultMessage()));
    }

    private static SponsorPlacement placementOf(String name) {
        return SponsorPlacement.parse(name)
                .orElseThrow(() -> new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_PLACEMENT_INVALID.getDefaultMessage()));
    }

    // active (on, inside its dates), scheduled (on, starts later), ended (on, over) or inactive (off)
    static String statusOf(Sponsor sponsor, Instant now) {
        if (!sponsor.isActive()) {
            return "inactive";
        }
        if (sponsor.getStartAt() != null && now.isBefore(sponsor.getStartAt())) {
            return "scheduled";
        }
        if (sponsor.getEndAt() != null && !now.isBefore(sponsor.getEndAt())) {
            return "ended";
        }
        return "active";
    }

    private SponsorDto toDto(Sponsor sponsor) {
        return toDto(sponsor, creativeRepository.findAllBySponsorId(sponsor.getId()), categoryIdsOf(sponsor.getId()), placementsOf(sponsor.getId()));
    }

    // written by hand: ModelMapper (strict) would not fill the status, the creatives and the nested lists
    private SponsorDto toDto(Sponsor sponsor, List<SponsorCreative> creatives, List<Long> categoryIds, List<SponsorPlacement> chosen) {
        List<SponsorDto.CreativeDto> images = creatives.stream()
                .sorted(Comparator.comparing(SponsorCreative::getPlacement))
                .map(creative -> new SponsorDto.CreativeDto(creative.getPlacement().name(), creative.getImageUrl(),
                        creative.getAltText(), creative.isGenerated(), creative.getPlacement().getWidth(), creative.getPlacement().getHeight(),
                        creative.getMediaType().name(), creative.getVideoUrl(), creative.getYoutubeId()))
                .toList();
        return new SponsorDto(sponsor.getId(), sponsor.getName(), sponsor.getTitle(), sponsor.getDescription(),
                sponsor.getCtaText(), sponsor.getTargetUrl(), sponsor.isActive(), statusOf(sponsor, Instant.now()),
                sponsor.getStartAt(), sponsor.getEndAt(), sponsor.getWeight(), sponsor.getLanguage().name(),
                sponsor.isPopupUseDefaults(), nameOf(sponsor.getPopupTrigger()), sponsor.getPopupDelaySeconds(),
                sponsor.getPopupScrollPercent(), nameOf(sponsor.getPopupFrequency()), sponsor.getPopupFrequencyDays(),
                SponsorPlacement.CHOOSABLE.stream().filter(chosen::contains).map(SponsorPlacement::name).toList(),
                categoryIds.stream().sorted().toList(), images, sponsor.getCreatedAt(), sponsor.getUpdatedAt(),
                sponsor.getCreatedBy() == null ? null : sponsor.getCreatedBy().getUsername(), sponsor.getLogoUrl(), sponsor.getLogoSmallUrl());
    }

    // [from, to]: both default to the last 30 days (UTC); from after to, or more than 366 days, is refused
    private static LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_STATS_RANGE_INVALID.getDefaultMessage());
        }
        return new LocalDate[]{start, end};
    }

    private static long[] counts(SponsorDailyStat row) {
        return new long[]{row.getImpressions(), row.getClicks(), row.getVideoPlays(), row.getVideoCompletes()};
    }

    private static SponsorStatsDto.Point point(String date, String placement, String device, long[] counts) {
        return new SponsorStatsDto.Point(date, placement, device, counts[IMPRESSIONS], counts[CLICKS],
                rate(counts[IMPRESSIONS], counts[CLICKS]), counts[PLAYS], counts[COMPLETES], rate(counts[PLAYS], counts[COMPLETES]));
    }

    private static SponsorStatsDto.Totals totals(long[] counts) {
        return new SponsorStatsDto.Totals(counts[IMPRESSIONS], counts[CLICKS], rate(counts[IMPRESSIONS], counts[CLICKS]),
                counts[PLAYS], counts[COMPLETES], rate(counts[PLAYS], counts[COMPLETES]));
    }

    // clicks per 100 impressions (or completes per 100 plays), two decimals; 0 without a base
    static double rate(long base, long events) {
        if (base <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(events * 100.0 / base).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // a ready LIKE pattern (lower case, % _ and ! escaped by "!"); no search matches everything
    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private void clearCache() {
        Cache cache = cacheManager.getCache(CacheConfig.PROMO_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    // After the commit: the cache is cleared only when the change is really saved, and an old file is deleted only then.
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
