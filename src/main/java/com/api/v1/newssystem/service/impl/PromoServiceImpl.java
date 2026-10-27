package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.CacheConfig;
import com.api.v1.newssystem.model.entity.Sponsor;
import com.api.v1.newssystem.model.entity.SponsorCreative;
import com.api.v1.newssystem.model.enumerate.PopupFrequency;
import com.api.v1.newssystem.model.enumerate.PopupTrigger;
import com.api.v1.newssystem.model.enumerate.SponsorLanguage;
import com.api.v1.newssystem.model.enumerate.SponsorMediaType;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import com.api.v1.newssystem.model.response.PopupRulesDto;
import com.api.v1.newssystem.model.response.PromoDto;
import com.api.v1.newssystem.model.response.PromoSidebarDto;
import com.api.v1.newssystem.model.response.SidebarSettingsDto;
import com.api.v1.newssystem.model.enumerate.SidebarLayout;
import com.api.v1.newssystem.repository.SponsorCategoryRepository;
import com.api.v1.newssystem.repository.SponsorCreativeRepository;
import com.api.v1.newssystem.repository.SponsorDailyStatRepository;
import com.api.v1.newssystem.repository.SponsorPlacementChoiceRepository;
import com.api.v1.newssystem.repository.SponsorRepository;
import com.api.v1.newssystem.security.PromoRateLimiter;
import com.api.v1.newssystem.service.PromoService;
import com.api.v1.newssystem.service.SystemSettingService;
import com.api.v1.newssystem.util.BotDetector;
import com.api.v1.newssystem.util.PopupRules;
import com.api.v1.newssystem.util.SettingValueValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The list of sponsors that may be shown in a placement is cached for about a minute (CacheConfig.PROMO_CACHE, also cleared
 * by every change in SponsorServiceImpl); the dates, the language, the category and the random pick are checked per request.
 */
@Service
@RequiredArgsConstructor
public class PromoServiceImpl implements PromoService {
    private static final String LABEL_EN = "Sponsored";
    private static final String LABEL_KM = "ឧបត្ថម្ភ";

    /**
     * What the cache keeps of a sponsor with media for one placement: plain values, nothing lazy. The popup rules are the
     * sponsor's own values; the site defaults are read when the promo is delivered, so a changed setting is not held back
     * by the cache.
     */
    record Candidate(Long id, String title, String description, String ctaText, String imageUrl, String altText,
                     int weight, SponsorLanguage language, Instant startAt, Instant endAt, Set<String> categorySlugs,
                     SponsorMediaType mediaType, String videoUrl, String youtubeId,
                     boolean popupUseDefaults, PopupTrigger popupTrigger, Integer popupDelaySeconds, Integer popupScrollPercent,
                     PopupFrequency popupFrequency, Integer popupFrequencyDays, String logoUrl) {
    }

    /** The most sponsors the sidebar slideshow takes part with. */
    static final int SIDEBAR_MAX = 24;

    private final SponsorRepository sponsorRepository;
    private final SponsorCreativeRepository creativeRepository;
    private final SponsorCategoryRepository categoryRepository;
    private final SponsorPlacementChoiceRepository placementRepository;
    private final SponsorDailyStatRepository statRepository;
    private final SystemSettingService systemSettingService;
    private final CacheManager cacheManager;
    private final PromoRateLimiter rateLimiter;
    private final AppProperty appProperty;

    // ---- choosing ----

    @Override
    public Optional<PromoDto> pick(String placementName, String categorySlug, String lang) {
        SponsorPlacement placement = placementOf(placementName);
        // the popup switch also turns off the phone bar, which is the popup on a phone
        if (!systemSettingService.isSponsorsEnabled()
                || (placement.chosen() == SponsorPlacement.ARTICLE_POPUP && !systemSettingService.isSponsorPopupEnabled())) {
            return Optional.empty();
        }
        String language = languageOf(lang);
        List<Candidate> eligible = candidates(placement).stream()
                .filter(candidate -> isEligible(candidate, Instant.now(), language, categorySlug))
                .toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        Candidate chosen = weightedPick(eligible, ThreadLocalRandom.current().nextInt(totalWeight(eligible)));
        return Optional.of(toDto(chosen, placement, language));
    }

    @Override
    public Optional<PromoSidebarDto> pickSidebar(String categorySlug, String lang) {
        if (!systemSettingService.isSponsorsEnabled()) {
            return Optional.empty();
        }
        SidebarSettingsDto settings = systemSettingService.sidebarSettings();
        boolean logos = SidebarLayout.LOGOS_4.name().equals(settings.getLayout());
        String language = languageOf(lang);
        Instant now = Instant.now();
        // LOGOS_4 shows the square logo: a sponsor without one is skipped in that layout
        List<Candidate> eligible = candidates(SponsorPlacement.ARTICLE_SIDEBAR).stream()
                .filter(candidate -> isEligible(candidate, now, language, categorySlug) && (!logos || candidate.logoUrl() != null))
                .toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        List<PromoDto> items = weightedShuffle(eligible, ThreadLocalRandom.current()).stream()
                .limit(SIDEBAR_MAX)
                .map(candidate -> toSidebarDto(candidate, logos, language))
                .toList();
        return Optional.of(new PromoSidebarDto(settings.getLayout(), settings.getPerSlide(), settings.isRotate(), settings.getIntervalSeconds(), items));
    }

    /**
     * The candidates in a random order where a higher weight is likelier to come first, each once (a weighted shuffle without
     * replacement, Efraimidis-Spirakis: every candidate gets the key u^(1/weight), u in (0, 1], and the highest keys come first).
     */
    static List<Candidate> weightedShuffle(List<Candidate> candidates, java.util.random.RandomGenerator random) {
        record Keyed(Candidate candidate, double key) {
        }
        return candidates.stream()
                .map(candidate -> new Keyed(candidate, Math.pow(1.0 - random.nextDouble(), 1.0 / Math.max(1, candidate.weight()))))
                .sorted(java.util.Comparator.comparingDouble(Keyed::key).reversed())
                .map(Keyed::candidate)
                .toList();
    }

    // a sponsor of the slideshow: the logo (a picture) for LOGOS_4, else the sidebar media
    private PromoDto toSidebarDto(Candidate candidate, boolean logos, String language) {
        SponsorPlacement placement = SponsorPlacement.ARTICLE_SIDEBAR;
        return new PromoDto(candidate.id(), placement.name(), candidate.title(), candidate.description(), candidate.ctaText(),
                "km".equals(language) ? LABEL_KM : LABEL_EN, logos ? candidate.logoUrl() : candidate.imageUrl(),
                logos ? 256 : placement.getWidth(), logos ? 256 : placement.getHeight(), candidate.title(),
                "/api/v1/promos/" + candidate.id() + "/click?placement=" + placement.name(),
                logos ? SponsorMediaType.IMAGE.name() : candidate.mediaType().name(), logos ? null : candidate.videoUrl(),
                logos ? null : candidate.youtubeId(), null);
    }

    static int totalWeight(List<Candidate> candidates) {
        return candidates.stream().mapToInt(Candidate::weight).sum();
    }

    /** The candidate on which `roll` (0 up to the total weight, exclusive) falls when the weights are laid end to end. */
    static Candidate weightedPick(List<Candidate> candidates, int roll) {
        int left = roll;
        for (Candidate candidate : candidates) {
            left -= candidate.weight();
            if (left < 0) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    /** Inside the dates, the right language, the right category. `language` is en, km or null. */
    static boolean isEligible(Candidate candidate, Instant now, String language, String categorySlug) {
        if (candidate.startAt() != null && now.isBefore(candidate.startAt())) {
            return false;
        }
        if (candidate.endAt() != null && !now.isBefore(candidate.endAt())) {
            return false;
        }
        if (candidate.language() != SponsorLanguage.ALL
                && (language == null || !candidate.language().name().equalsIgnoreCase(language))) {
            return false;
        }
        // no category target: every page; otherwise the page must be of one of the categories
        return candidate.categorySlugs().isEmpty()
                || (categorySlug != null && candidate.categorySlugs().contains(categorySlug));
    }

    private PromoDto toDto(Candidate candidate, SponsorPlacement placement, String language) {
        // the timing belongs to the popup (a phone shows it as the bar); the sidebar has none
        PopupRulesDto popup = placement.chosen() == SponsorPlacement.ARTICLE_POPUP
                ? PopupRules.effective(candidate.popupUseDefaults(), candidate.popupTrigger(), candidate.popupDelaySeconds(),
                candidate.popupScrollPercent(), candidate.popupFrequency(), candidate.popupFrequencyDays(), systemSettingService.popupDefaults())
                : null;
        return new PromoDto(candidate.id(), placement.name(), candidate.title(), candidate.description(), candidate.ctaText(),
                "km".equals(language) ? LABEL_KM : LABEL_EN, candidate.imageUrl(), placement.getWidth(), placement.getHeight(),
                candidate.altText(), "/api/v1/promos/" + candidate.id() + "/click?placement=" + placement.name(),
                candidate.mediaType().name(), candidate.videoUrl(), candidate.youtubeId(), popup);
    }

    // ---- counting ----

    @Override
    @Transactional
    public void recordImpression(Long sponsorId, String placementName, String ip, String userAgent) {
        SponsorPlacement placement = placementOf(placementName);
        // a bot or a flood is ignored quietly: the answer is the same, so a script learns nothing
        boolean counted = !BotDetector.isBot(userAgent)
                && rateLimiter.tryAcquire("impression", ip, appProperty.getPromo().getImpressionsPerIpPerMinute());
        // only a placement the sponsor chose is counted (the bottom bar is the popup)
        if (!placementRepository.existsBySponsorIdAndPlacement(sponsorId, placement.chosen())) {
            throw new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SPONSOR_NOT_FOUND.getDefaultMessage());
        }
        if (counted) {
            statRepository.addImpression(sponsorId, placement.counted().name(), placement.device().name(), today());
        }
    }

    @Override
    @Transactional
    public void recordVideoPlay(Long sponsorId, String placementName, String ip, String userAgent) {
        recordVideo(sponsorId, placementName, ip, userAgent, "video-play", statRepository::addVideoPlay);
    }

    @Override
    @Transactional
    public void recordVideoComplete(Long sponsorId, String placementName, String ip, String userAgent) {
        recordVideo(sponsorId, placementName, ip, userAgent, "video-complete", statRepository::addVideoComplete);
    }

    /** A statement of SponsorDailyStatRepository that adds one to a counter: (sponsor, placement, device, day). */
    private interface Counter {
        int add(Long sponsorId, String placement, String device, LocalDate day);
    }

    // The same rules as an impression (bots and floods are ignored quietly), for a placement that shows a video.
    private void recordVideo(Long sponsorId, String placementName, String ip, String userAgent, String kind, Counter counter) {
        SponsorPlacement placement = placementOf(placementName);
        if (placement == SponsorPlacement.MOBILE_BOTTOM_BAR) {
            // the phone bar is always a picture: it has no video to play
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_MEDIA_NOT_ALLOWED.getDefaultMessage());
        }
        boolean counted = !BotDetector.isBot(userAgent)
                && rateLimiter.tryAcquire(kind, ip, appProperty.getPromo().getImpressionsPerIpPerMinute());
        if (!placementRepository.existsBySponsorIdAndPlacement(sponsorId, placement.chosen())) {
            throw new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SPONSOR_NOT_FOUND.getDefaultMessage());
        }
        // only a placement whose media really is a video can have plays
        boolean video = creativeRepository.findBySponsorIdAndPlacement(sponsorId, placement)
                .map(creative -> creative.getMediaType().isVideo()).orElse(false);
        if (!video) {
            throw new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SPONSOR_VIDEO_NOT_FOUND.getDefaultMessage());
        }
        if (counted) {
            counter.add(sponsorId, placement.counted().name(), placement.device().name(), today());
        }
    }

    @Override
    @Transactional
    public String recordClick(Long sponsorId, String placementName, String ip, String userAgent) {
        SponsorPlacement placement = placementOf(placementName);
        Sponsor sponsor = sponsorRepository.findById(sponsorId)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SPONSOR_NOT_FOUND.getDefaultMessage()));
        // the stored address is checked again: only an https address is ever a redirect target
        if (!SettingValueValidator.isHttpsUrl(sponsor.getTargetUrl())) {
            throw new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SPONSOR_NOT_FOUND.getDefaultMessage());
        }
        if (!BotDetector.isBot(userAgent)
                && rateLimiter.tryAcquire("click", ip, appProperty.getPromo().getClicksPerIpPerMinute())) {
            statRepository.addClick(sponsorId, placement.counted().name(), placement.device().name(), today());
        }
        return sponsor.getTargetUrl();
    }

    // ---- helpers ----

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static SponsorPlacement placementOf(String name) {
        return SponsorPlacement.parse(name)
                .orElseThrow(() -> new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_PLACEMENT_INVALID.getDefaultMessage()));
    }

    private static String languageOf(String lang) {
        String value = lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT);
        return value.equals("en") || value.equals("km") ? value : null;
    }

    @SuppressWarnings("unchecked")
    private List<Candidate> candidates(SponsorPlacement placement) {
        Cache cache = cacheManager.getCache(CacheConfig.PROMO_CACHE);
        if (cache == null) {
            return load(placement);
        }
        return (List<Candidate>) cache.get(placement.name(), () -> load(placement));
    }

    private List<Candidate> load(SponsorPlacement placement) {
        // the sponsors that chose the placement and have its image; the bottom bar is the popup of a phone, with its own
        // image when it has one (an uploaded one or the square crop made from the popup image)
        List<SponsorCreative> creatives = creativeRepository.findForActiveSponsors(placement.chosen());
        if (creatives.isEmpty()) {
            return List.of();
        }
        Map<Long, SponsorCreative> barImages = new HashMap<>();
        if (placement == SponsorPlacement.MOBILE_BOTTOM_BAR) {
            creativeRepository.findAllBySponsorIdInAndPlacement(creatives.stream().map(c -> c.getSponsor().getId()).toList(), placement)
                    .forEach(bar -> barImages.put(bar.getSponsor().getId(), bar));
        }
        Map<Long, Set<String>> slugs = new HashMap<>();
        for (Object[] row : categoryRepository.findTargets(creatives.stream().map(c -> c.getSponsor().getId()).toList())) {
            slugs.computeIfAbsent((Long) row[0], id -> new HashSet<>()).add((String) row[2]);
        }
        List<Candidate> candidates = new ArrayList<>();
        for (SponsorCreative creative : creatives) {
            Sponsor sponsor = creative.getSponsor();
            SponsorCreative image = barImages.getOrDefault(sponsor.getId(), creative);
            // the bar uses the logo; without one the crop of the popup image (or the popup image itself)
            boolean logoForBar = placement == SponsorPlacement.MOBILE_BOTTOM_BAR && sponsor.getLogoUrl() != null;
            // the phone bar is always a picture: of its own, or the picture / poster of the popup (a video is never played there)
            boolean bar = placement == SponsorPlacement.MOBILE_BOTTOM_BAR;
            candidates.add(new Candidate(sponsor.getId(), sponsor.getTitle(), sponsor.getDescription(), sponsor.getCtaText(),
                    logoForBar ? sponsor.getLogoUrl() : image.getImageUrl(), logoForBar ? sponsor.getTitle() : image.getAltText(), sponsor.getWeight(), sponsor.getLanguage(),
                    sponsor.getStartAt(), sponsor.getEndAt(), Set.copyOf(slugs.getOrDefault(sponsor.getId(), Set.of())),
                    bar ? SponsorMediaType.IMAGE : image.getMediaType(), bar ? null : image.getVideoUrl(), bar ? null : image.getYoutubeId(),
                    sponsor.isPopupUseDefaults(), sponsor.getPopupTrigger(), sponsor.getPopupDelaySeconds(),
                    sponsor.getPopupScrollPercent(), sponsor.getPopupFrequency(), sponsor.getPopupFrequencyDays(), sponsor.getLogoUrl()));
        }
        return List.copyOf(candidates);
    }
}
