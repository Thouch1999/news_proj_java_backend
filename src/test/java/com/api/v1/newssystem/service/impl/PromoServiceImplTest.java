package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.model.response.SidebarSettingsDto;
import com.api.v1.newssystem.model.response.PromoSidebarDto;
import com.api.v1.newssystem.model.response.PopupRulesDto;
import com.api.v1.newssystem.model.enumerate.PopupTrigger;
import com.api.v1.newssystem.model.enumerate.PopupFrequency;
import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.CacheConfig;
import com.api.v1.newssystem.model.entity.Sponsor;
import com.api.v1.newssystem.model.entity.SponsorCreative;
import com.api.v1.newssystem.model.enumerate.SponsorLanguage;
import com.api.v1.newssystem.model.enumerate.SponsorMediaType;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import com.api.v1.newssystem.model.response.PromoDto;
import com.api.v1.newssystem.repository.SponsorCategoryRepository;
import com.api.v1.newssystem.repository.SponsorCreativeRepository;
import com.api.v1.newssystem.repository.SponsorDailyStatRepository;
import com.api.v1.newssystem.repository.SponsorPlacementChoiceRepository;
import com.api.v1.newssystem.repository.SponsorRepository;
import com.api.v1.newssystem.security.PromoRateLimiter;
import com.api.v1.newssystem.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoServiceImplTest {
    private static final String BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final String SIDEBAR = SponsorPlacement.ARTICLE_SIDEBAR.name();

    @Mock
    private SponsorRepository sponsorRepository;
    @Mock
    private SponsorCreativeRepository creativeRepository;
    @Mock
    private SponsorCategoryRepository categoryRepository;
    @Mock
    private SponsorPlacementChoiceRepository placementRepository;
    @Mock
    private SponsorDailyStatRepository statRepository;
    @Mock
    private SystemSettingService systemSettingService;

    private final AppProperty properties = new AppProperty();
    private PromoServiceImpl service;
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(CacheConfig.PROMO_CACHE, CacheConfig.PROMO_RATE_CACHE);
        service = new PromoServiceImpl(sponsorRepository, creativeRepository, categoryRepository, placementRepository, statRepository,
                systemSettingService, cacheManager, new PromoRateLimiter(cacheManager), properties);
        lenient().when(systemSettingService.isSponsorsEnabled()).thenReturn(true);
        lenient().when(systemSettingService.isSponsorPopupEnabled()).thenReturn(true);
        lenient().when(categoryRepository.findTargets(any())).thenReturn(List.of());
    }

    // ---- helpers ----

    private Sponsor sponsor(long id, int weight) {
        Sponsor sponsor = new Sponsor();
        sponsor.setId(id);
        sponsor.setName("Company " + id);
        sponsor.setTitle("Title " + id);
        sponsor.setDescription("Description " + id);
        sponsor.setCtaText("Learn more");
        sponsor.setTargetUrl("https://sponsor" + id + ".example/landing");
        sponsor.setActive(true);
        sponsor.setWeight(weight);
        return sponsor;
    }

    private SponsorCreative creative(Sponsor sponsor, SponsorPlacement placement) {
        SponsorCreative creative = new SponsorCreative();
        creative.setSponsor(sponsor);
        creative.setPlacement(placement);
        creative.setImageUrl("/uploads/" + sponsor.getId() + ".png");
        creative.setAltText("alt " + sponsor.getId());
        return creative;
    }

    private void eligibleInRepository(SponsorPlacement placement, Sponsor... sponsors) {
        List<SponsorCreative> creatives = new ArrayList<>();
        for (Sponsor sponsor : sponsors) {
            creatives.add(creative(sponsor, placement));
        }
        when(creativeRepository.findForActiveSponsors(placement)).thenReturn(creatives);
    }

    private Set<Long> pickedIds(int times, String placement, String slug, String lang) {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < times; i++) {
            service.pick(placement, slug, lang).ifPresent(promo -> ids.add(promo.getId()));
        }
        return ids;
    }

    // ---- choosing ----

    @Test
    void pick_returnsOnlyEligibleSponsors() {
        Sponsor fine = sponsor(1, 10);
        Sponsor ended = sponsor(2, 100);
        ended.setEndAt(now.minus(Duration.ofHours(1)));
        Sponsor notStarted = sponsor(3, 100);
        notStarted.setStartAt(now.plus(Duration.ofHours(1)));
        Sponsor otherLanguage = sponsor(4, 100);
        otherLanguage.setLanguage(SponsorLanguage.KM);
        Sponsor otherCategory = sponsor(5, 100);
        Sponsor running = sponsor(6, 10);
        running.setStartAt(now.minus(Duration.ofDays(1)));
        running.setEndAt(now.plus(Duration.ofDays(1)));
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, fine, ended, notStarted, otherLanguage, otherCategory, running);
        when(categoryRepository.findTargets(any())).thenReturn(List.<Object[]>of(new Object[]{5L, 50L, "sports"}));

        assertThat(pickedIds(300, SIDEBAR, "politics", "en")).containsExactlyInAnyOrder(1L, 6L);
    }

    @Test
    void pick_matchesTheLanguageAndTheCategory() {
        Sponsor khmerSports = sponsor(1, 10);
        khmerSports.setLanguage(SponsorLanguage.KM);
        Sponsor englishOnly = sponsor(2, 10);
        englishOnly.setLanguage(SponsorLanguage.EN);
        Sponsor everyone = sponsor(3, 10);
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, khmerSports, englishOnly, everyone);
        when(categoryRepository.findTargets(any())).thenReturn(List.<Object[]>of(new Object[]{1L, 7L, "sports"}));

        assertThat(pickedIds(300, SIDEBAR, "sports", "km")).containsExactlyInAnyOrder(1L, 3L);
        assertThat(pickedIds(300, SIDEBAR, "sports", "en")).containsExactlyInAnyOrder(2L, 3L);
        // another category: the sports sponsor is out; no language known: only the sponsors of ALL languages
        assertThat(pickedIds(300, SIDEBAR, "health", "km")).containsExactly(3L);
        assertThat(pickedIds(300, SIDEBAR, null, null)).containsExactly(3L);
    }

    @Test
    void pick_answersWithOnlyWhatThePageNeeds() {
        Sponsor sponsor = sponsor(7, 10);
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, sponsor);

        PromoDto promo = service.pick(SIDEBAR, null, "km").orElseThrow();

        assertThat(promo.getId()).isEqualTo(7L);
        assertThat(promo.getTitle()).isEqualTo("Title 7");
        assertThat(promo.getCtaText()).isEqualTo("Learn more");
        assertThat(promo.getImageUrl()).isEqualTo("/uploads/7.png");
        assertThat(promo.getAltText()).isEqualTo("alt 7");
        assertThat(promo.getWidth()).isEqualTo(300);
        assertThat(promo.getHeight()).isEqualTo(250);
        assertThat(promo.getLabel()).isEqualTo("ឧបត្ថម្ភ");
        // the click goes through the backend; the real address of the sponsor is not in the answer
        assertThat(promo.getClickUrl()).isEqualTo("/api/v1/promos/7/click?placement=ARTICLE_SIDEBAR");
        assertThat(promo.toString()).doesNotContain("sponsor7.example");
        assertThat(service.pick(SIDEBAR, null, "en").orElseThrow().getLabel()).isEqualTo("Sponsored");
    }

    @Test
    void pick_isEmptyWhenNoSponsorIsEligible() {
        when(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(List.of());

        assertThat(service.pick(SIDEBAR, "sports", "en")).isEmpty();
    }

    @Test
    void pick_isEmptyWhenSponsorsAreOff_withoutReadingTheDatabase() {
        when(systemSettingService.isSponsorsEnabled()).thenReturn(false);

        assertThat(service.pick(SIDEBAR, null, "en")).isEmpty();
        assertThat(service.pick(SponsorPlacement.ARTICLE_POPUP.name(), null, "en")).isEmpty();
        verifyNoInteractions(creativeRepository);
    }

    @Test
    void pick_theSwitchOfThePopupDoesNotAffectTheOtherPlacements() {
        when(systemSettingService.isSponsorPopupEnabled()).thenReturn(false);
        Sponsor sponsor = sponsor(1, 10);
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, sponsor);

        assertThat(service.pick(SponsorPlacement.ARTICLE_POPUP.name(), null, "en")).isEmpty();
        assertThat(service.pick(SIDEBAR, null, "en")).isPresent();
        verify(creativeRepository, never()).findForActiveSponsors(SponsorPlacement.ARTICLE_POPUP);
    }

    @Test
    void pick_readsTheListOncePerPlacementWithinTheCache() {
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, sponsor(1, 10));

        service.pick(SIDEBAR, null, "en");
        service.pick(SIDEBAR, null, "en");
        service.pick(SIDEBAR, null, "km");

        verify(creativeRepository, times(1)).findForActiveSponsors(SponsorPlacement.ARTICLE_SIDEBAR);
    }

    @Test
    void pick_withAnUnknownPlacement_is400() {
        assertThatThrownBy(() -> service.pick("HOME_BANNER", null, "en"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo(MessageConstant.SPONSOR_PLACEMENT_INVALID.getDefaultMessage());
                });
    }

    // ---- the weighted random ----

    @Test
    void weightedPick_followsTheWeightsLaidEndToEnd() {
        PromoServiceImpl.Candidate first = candidate(1, 10);
        PromoServiceImpl.Candidate second = candidate(2, 30);
        List<PromoServiceImpl.Candidate> candidates = List.of(first, second);

        assertThat(PromoServiceImpl.totalWeight(candidates)).isEqualTo(40);
        for (int roll = 0; roll < 10; roll++) {
            assertThat(PromoServiceImpl.weightedPick(candidates, roll)).isSameAs(first);
        }
        for (int roll = 10; roll < 40; roll++) {
            assertThat(PromoServiceImpl.weightedPick(candidates, roll)).isSameAs(second);
        }
    }

    @Test
    void pick_aHeavierSponsorIsChosenMoreOften() {
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, sponsor(1, 10), sponsor(2, 90));
        int heavy = 0;
        int light = 0;
        for (int i = 0; i < 2000; i++) {
            long id = service.pick(SIDEBAR, null, "en").orElseThrow().getId();
            if (id == 2L) {
                heavy++;
            } else {
                light++;
            }
        }
        assertThat(light).isPositive();
        assertThat(heavy).isGreaterThan(light * 3);
    }

    private PromoServiceImpl.Candidate candidate(long id, int weight) {
        return new PromoServiceImpl.Candidate(id, "t", "d", "c", "/uploads/x.png", "a", weight, SponsorLanguage.ALL, null, null, Set.of(),
                SponsorMediaType.IMAGE, null, null, true, null, null, null, null, null, null);
    }

    // ---- impressions ----

    @Test
    void recordImpression_countsOneImpressionToday() {
        when(placementRepository.existsBySponsorIdAndPlacement(1L, SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(true);

        service.recordImpression(1L, SIDEBAR, "203.0.113.5", BROWSER);

        verify(statRepository).addImpression(1L, SIDEBAR, "DESKTOP", LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void recordImpression_ignoresBots() {
        lenient().when(placementRepository.existsBySponsorIdAndPlacement(anyLong(), any())).thenReturn(true);

        service.recordImpression(1L, SIDEBAR, "203.0.113.5", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");
        service.recordImpression(1L, SIDEBAR, "203.0.113.5", "curl/8.4.0");
        service.recordImpression(1L, SIDEBAR, "203.0.113.5", null);

        verify(statRepository, never()).addImpression(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordImpression_limitsOneAddress_butNotAnother() {
        lenient().when(placementRepository.existsBySponsorIdAndPlacement(anyLong(), any())).thenReturn(true);
        int limit = properties.getPromo().getImpressionsPerIpPerMinute();

        for (int i = 0; i < limit + 20; i++) {
            service.recordImpression(1L, SIDEBAR, "203.0.113.5", BROWSER);
        }
        verify(statRepository, times(limit)).addImpression(eq(1L), eq(SIDEBAR), eq("DESKTOP"), any());

        service.recordImpression(1L, SIDEBAR, "203.0.113.99", BROWSER);
        verify(statRepository, times(limit + 1)).addImpression(eq(1L), eq(SIDEBAR), eq("DESKTOP"), any());
    }

    @Test
    void recordImpression_forASponsorThatDidNotChooseThePopup_is404() {
        when(placementRepository.existsBySponsorIdAndPlacement(9L, SponsorPlacement.ARTICLE_POPUP)).thenReturn(false); // the bar is the popup

        assertThatThrownBy(() -> service.recordImpression(9L, "MOBILE_BOTTOM_BAR", "203.0.113.5", BROWSER))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(statRepository, never()).addImpression(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordImpression_withAnUnknownPlacement_is400() {
        assertThatThrownBy(() -> service.recordImpression(1L, "nope", "203.0.113.5", BROWSER))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- clicks ----

    @Test
    void recordClick_countsTheClick_andReturnsTheStoredAddress() {
        Sponsor sponsor = sponsor(1, 10);
        when(sponsorRepository.findById(1L)).thenReturn(Optional.of(sponsor));

        String target = service.recordClick(1L, SIDEBAR, "203.0.113.5", BROWSER);

        assertThat(target).isEqualTo("https://sponsor1.example/landing");
        verify(statRepository).addClick(1L, SIDEBAR, "DESKTOP", LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void recordClick_aBotIsRedirectedButNotCounted() {
        when(sponsorRepository.findById(1L)).thenReturn(Optional.of(sponsor(1, 10)));

        String target = service.recordClick(1L, SIDEBAR, "203.0.113.5", "curl/8.4.0");

        assertThat(target).isEqualTo("https://sponsor1.example/landing");
        verify(statRepository, never()).addClick(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordClick_limitsOneAddress() {
        when(sponsorRepository.findById(1L)).thenReturn(Optional.of(sponsor(1, 10)));
        int limit = properties.getPromo().getClicksPerIpPerMinute();

        for (int i = 0; i < limit + 5; i++) {
            assertThat(service.recordClick(1L, SIDEBAR, "203.0.113.5", BROWSER)).startsWith("https://");
        }

        verify(statRepository, times(limit)).addClick(eq(1L), eq(SIDEBAR), eq("DESKTOP"), any());
    }

    @Test
    void recordClick_neverRedirectsToANonHttpsAddress() {
        Sponsor sponsor = sponsor(1, 10);
        sponsor.setTargetUrl("javascript:alert(1)"); // cannot be saved through the API, but the redirect checks it again
        when(sponsorRepository.findById(1L)).thenReturn(Optional.of(sponsor));

        assertThatThrownBy(() -> service.recordClick(1L, SIDEBAR, "203.0.113.5", BROWSER))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(statRepository, never()).addClick(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordClick_forAnUnknownSponsor_is404() {
        when(sponsorRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordClick(5L, SIDEBAR, "203.0.113.5", BROWSER))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---- the phone bar is the popup of a phone ----

    @Test
    void pick_theBottomBar_usesItsOwnImage_orTheCropOrThePopupImage() {
        Sponsor withBar = sponsor(1, 10);
        Sponsor withoutBar = sponsor(2, 10);
        when(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_POPUP))
                .thenReturn(List.of(creative(withBar, SponsorPlacement.ARTICLE_POPUP), creative(withoutBar, SponsorPlacement.ARTICLE_POPUP)));
        SponsorCreative bar = creative(withBar, SponsorPlacement.MOBILE_BOTTOM_BAR);
        bar.setImageUrl("/uploads/bar-1.png");
        when(creativeRepository.findAllBySponsorIdInAndPlacement(any(), eq(SponsorPlacement.MOBILE_BOTTOM_BAR))).thenReturn(List.of(bar));

        Map<Long, PromoDto> seen = new HashMap<>();
        for (int i = 0; i < 300; i++) {
            PromoDto promo = service.pick("MOBILE_BOTTOM_BAR", null, "en").orElseThrow();
            seen.put(promo.getId(), promo);
        }

        assertThat(seen.get(1L).getImageUrl()).isEqualTo("/uploads/bar-1.png"); // the bar image (uploaded or generated)
        assertThat(seen.get(2L).getImageUrl()).isEqualTo("/uploads/2.png"); // none: the popup image (the page crops it)
        assertThat(seen.get(1L).getPlacement()).isEqualTo("MOBILE_BOTTOM_BAR");
        assertThat(seen.get(1L).getWidth()).isEqualTo(120);
        assertThat(seen.get(1L).getClickUrl()).isEqualTo("/api/v1/promos/1/click?placement=MOBILE_BOTTOM_BAR");
        // the sponsors are the ones that chose the popup: the bar placement itself is never looked up
        verify(creativeRepository, never()).findForActiveSponsors(SponsorPlacement.MOBILE_BOTTOM_BAR);
    }

    @Test
    void pick_theBottomBar_isOffWithThePopupSwitch() {
        when(systemSettingService.isSponsorPopupEnabled()).thenReturn(false);

        assertThat(service.pick("MOBILE_BOTTOM_BAR", null, "en")).isEmpty();
        verifyNoInteractions(creativeRepository);
    }

    @Test
    void pick_aPlacementThatIsNotChosen_isNeverReturned() {
        // the query of the repository returns only the sponsors that chose the placement: nobody chose the sidebar here
        when(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(List.of());
        when(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_POPUP))
                .thenReturn(List.of(creative(sponsor(1, 10), SponsorPlacement.ARTICLE_POPUP)));

        assertThat(service.pick(SIDEBAR, null, "en")).isEmpty();
        assertThat(service.pick("ARTICLE_POPUP", null, "en")).isPresent();
    }

    @Test
    void recordImpression_ofTheBar_countsUnderThePopup_asMobile() {
        when(placementRepository.existsBySponsorIdAndPlacement(1L, SponsorPlacement.ARTICLE_POPUP)).thenReturn(true);

        service.recordImpression(1L, "MOBILE_BOTTOM_BAR", "203.0.113.5", BROWSER);
        service.recordImpression(1L, "ARTICLE_POPUP", "203.0.113.6", BROWSER);

        verify(statRepository).addImpression(1L, "ARTICLE_POPUP", "MOBILE", LocalDate.now(ZoneOffset.UTC));
        verify(statRepository).addImpression(1L, "ARTICLE_POPUP", "DESKTOP", LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void recordImpression_forAPlacementTheSponsorDidNotChoose_is404() {
        when(placementRepository.existsBySponsorIdAndPlacement(1L, SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(false);

        assertThatThrownBy(() -> service.recordImpression(1L, SIDEBAR, "203.0.113.5", BROWSER))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(statRepository, never()).addImpression(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordClick_ofTheBar_countsUnderThePopup_asMobile() {
        when(sponsorRepository.findById(1L)).thenReturn(Optional.of(sponsor(1, 10)));

        service.recordClick(1L, "MOBILE_BOTTOM_BAR", "203.0.113.5", BROWSER);

        verify(statRepository).addClick(1L, "ARTICLE_POPUP", "MOBILE", LocalDate.now(ZoneOffset.UTC));
    }

    // ---- popup timing and media in the answer ----

    private static final String POPUP = SponsorPlacement.ARTICLE_POPUP.name();
    private static final String BAR = SponsorPlacement.MOBILE_BOTTOM_BAR.name();

    private PopupRulesDto siteDefaults() {
        return new PopupRulesDto("FIRST_OF_BOTH", 15, 50, "PER_DAY", 3);
    }

    private SponsorCreative mediaCreative(Sponsor sponsor, SponsorPlacement placement, SponsorMediaType type) {
        SponsorCreative creative = creative(sponsor, placement);
        creative.setMediaType(type);
        if (type == SponsorMediaType.VIDEO_FILE) {
            creative.setVideoUrl("/uploads/clip-" + sponsor.getId() + ".mp4");
        } else if (type == SponsorMediaType.YOUTUBE) {
            creative.setYoutubeId("dQw4w9WgXcQ");
        }
        return creative;
    }

    @Test
    void pick_thePopup_followsTheSiteDefaults_whenTheSponsorUsesThem() {
        when(systemSettingService.popupDefaults()).thenReturn(siteDefaults());
        Sponsor sponsor = sponsor(1, 10);
        sponsor.setPopupUseDefaults(true);
        sponsor.setPopupDelaySeconds(99); // a leftover value is not used
        sponsor.setPopupTrigger(PopupTrigger.SCROLL);
        eligibleInRepository(SponsorPlacement.ARTICLE_POPUP, sponsor);

        PromoDto promo = service.pick(POPUP, null, "en").orElseThrow();

        assertThat(promo.getPopup()).isEqualTo(siteDefaults());
    }

    @Test
    void pick_thePopup_followsTheSponsorsOwnRules_andFillsWhatItDoesNotSetFromTheDefaults() {
        when(systemSettingService.popupDefaults()).thenReturn(siteDefaults());
        Sponsor sponsor = sponsor(1, 10);
        sponsor.setPopupUseDefaults(false);
        sponsor.setPopupTrigger(PopupTrigger.DELAY);
        sponsor.setPopupDelaySeconds(30);
        sponsor.setPopupFrequency(PopupFrequency.EVERY_N_DAYS);
        sponsor.setPopupFrequencyDays(3);
        eligibleInRepository(SponsorPlacement.ARTICLE_POPUP, sponsor);

        PromoDto promo = service.pick(POPUP, null, "en").orElseThrow();

        assertThat(promo.getPopup()).isEqualTo(new PopupRulesDto("DELAY", 30, 50, "EVERY_N_DAYS", 3));
    }

    @Test
    void pick_aChangedSiteSetting_isNotHeldBackByTheCacheOfTheSponsors() {
        Sponsor sponsor = sponsor(1, 10);
        eligibleInRepository(SponsorPlacement.ARTICLE_POPUP, sponsor);
        when(systemSettingService.popupDefaults()).thenReturn(siteDefaults(), new PopupRulesDto("DELAY", 60, 50, "PER_VISIT", 3));

        assertThat(service.pick(POPUP, null, "en").orElseThrow().getPopup().getDelaySeconds()).isEqualTo(15);
        assertThat(service.pick(POPUP, null, "en").orElseThrow().getPopup().getDelaySeconds()).isEqualTo(60);
        verify(creativeRepository, times(1)).findForActiveSponsors(SponsorPlacement.ARTICLE_POPUP);
    }

    @Test
    void pick_theSidebarHasNoPopupRules() {
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, sponsor(1, 10));

        assertThat(service.pick(SIDEBAR, null, "en").orElseThrow().getPopup()).isNull();
        verify(systemSettingService, never()).popupDefaults();
    }

    @Test
    void pick_theMediaIsInTheAnswer_withTheVideoFileOrTheYouTubeId() {
        Sponsor file = sponsor(1, 10);
        Sponsor tube = sponsor(2, 10);
        Sponsor picture = sponsor(3, 10);
        when(systemSettingService.popupDefaults()).thenReturn(siteDefaults());
        when(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_POPUP)).thenReturn(List.of(
                mediaCreative(file, SponsorPlacement.ARTICLE_POPUP, SponsorMediaType.VIDEO_FILE),
                mediaCreative(tube, SponsorPlacement.ARTICLE_POPUP, SponsorMediaType.YOUTUBE),
                mediaCreative(picture, SponsorPlacement.ARTICLE_POPUP, SponsorMediaType.IMAGE)));
        Map<Long, PromoDto> byId = new HashMap<>();
        for (int i = 0; i < 300; i++) {
            PromoDto promo = service.pick(POPUP, null, "en").orElseThrow();
            byId.put(promo.getId(), promo);
        }

        assertThat(byId.get(1L).getMediaType()).isEqualTo("VIDEO_FILE");
        assertThat(byId.get(1L).getVideoUrl()).isEqualTo("/uploads/clip-1.mp4");
        assertThat(byId.get(1L).getYoutubeId()).isNull();
        assertThat(byId.get(1L).getImageUrl()).isEqualTo("/uploads/1.png"); // the poster
        assertThat(byId.get(2L).getMediaType()).isEqualTo("YOUTUBE");
        assertThat(byId.get(2L).getYoutubeId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(byId.get(2L).getVideoUrl()).isNull();
        assertThat(byId.get(3L).getMediaType()).isEqualTo("IMAGE");
        assertThat(byId.get(3L).getVideoUrl()).isNull();
        assertThat(byId.get(3L).getYoutubeId()).isNull();
    }

    @Test
    void pick_thePhoneBar_isAlwaysAPicture_evenWhenThePopupIsAVideo_andFollowsThePopupRules() {
        when(systemSettingService.popupDefaults()).thenReturn(siteDefaults());
        Sponsor sponsor = sponsor(1, 10);
        when(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_POPUP))
                .thenReturn(List.of(mediaCreative(sponsor, SponsorPlacement.ARTICLE_POPUP, SponsorMediaType.YOUTUBE)));

        PromoDto bar = service.pick(BAR, null, "en").orElseThrow();

        assertThat(bar.getPlacement()).isEqualTo(BAR);
        assertThat(bar.getMediaType()).isEqualTo("IMAGE");
        assertThat(bar.getYoutubeId()).isNull();
        assertThat(bar.getVideoUrl()).isNull();
        assertThat(bar.getImageUrl()).isEqualTo("/uploads/1.png"); // the poster of the popup when there is no bar image
        assertThat(bar.getPopup()).isEqualTo(siteDefaults());
    }

    // ---- video events ----

    private void videoIn(SponsorPlacement placement, SponsorMediaType type) {
        lenient().when(placementRepository.existsBySponsorIdAndPlacement(1L, placement)).thenReturn(true);
        lenient().when(creativeRepository.findBySponsorIdAndPlacement(1L, placement))
                .thenReturn(Optional.of(mediaCreative(sponsor(1, 10), placement, type)));
    }

    @Test
    void recordVideoPlay_andComplete_countOneEachToday_underThePlacementAndTheDevice() {
        videoIn(SponsorPlacement.ARTICLE_POPUP, SponsorMediaType.YOUTUBE);

        service.recordVideoPlay(1L, POPUP, "203.0.113.5", BROWSER);
        service.recordVideoComplete(1L, POPUP, "203.0.113.5", BROWSER);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        verify(statRepository).addVideoPlay(1L, POPUP, "DESKTOP", today);
        verify(statRepository).addVideoComplete(1L, POPUP, "DESKTOP", today);
        verify(statRepository, never()).addImpression(anyLong(), anyString(), anyString(), any());
        verify(statRepository, never()).addClick(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordVideoPlay_inTheSidebar_countsUnderTheSidebar() {
        videoIn(SponsorPlacement.ARTICLE_SIDEBAR, SponsorMediaType.VIDEO_FILE);

        service.recordVideoPlay(1L, SIDEBAR, "203.0.113.5", BROWSER);

        verify(statRepository).addVideoPlay(1L, SIDEBAR, "DESKTOP", LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void recordVideoEvents_ignoreBots_withTheSameAnswer() {
        videoIn(SponsorPlacement.ARTICLE_SIDEBAR, SponsorMediaType.VIDEO_FILE);

        for (String agent : new String[]{"Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", "curl/8.4.0", null}) {
            service.recordVideoPlay(1L, SIDEBAR, "203.0.113.5", agent);
            service.recordVideoComplete(1L, SIDEBAR, "203.0.113.5", agent);
        }

        verify(statRepository, never()).addVideoPlay(anyLong(), anyString(), anyString(), any());
        verify(statRepository, never()).addVideoComplete(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordVideoEvents_areLimitedPerAddress_withTheirOwnCountersPerKind() {
        // every sponsor id (the other address plays the same sponsor) has a video in the sidebar
        lenient().when(placementRepository.existsBySponsorIdAndPlacement(anyLong(), eq(SponsorPlacement.ARTICLE_SIDEBAR))).thenReturn(true);
        lenient().when(creativeRepository.findBySponsorIdAndPlacement(anyLong(), eq(SponsorPlacement.ARTICLE_SIDEBAR)))
                .thenReturn(Optional.of(mediaCreative(sponsor(1, 10), SponsorPlacement.ARTICLE_SIDEBAR, SponsorMediaType.VIDEO_FILE)));
        int limit = properties.getPromo().getImpressionsPerIpPerMinute();

        for (int i = 0; i < limit + 20; i++) {
            service.recordVideoPlay(1L, SIDEBAR, "203.0.113.5", BROWSER);
        }
        verify(statRepository, times(limit)).addVideoPlay(eq(1L), eq(SIDEBAR), eq("DESKTOP"), any());

        // another address, and the other kind of event from the same address, are not held back
        service.recordVideoPlay(1L, SIDEBAR, "203.0.113.99", BROWSER);
        service.recordVideoComplete(1L, SIDEBAR, "203.0.113.5", BROWSER);
        verify(statRepository, times(limit + 1)).addVideoPlay(eq(1L), eq(SIDEBAR), eq("DESKTOP"), any());
        verify(statRepository, times(1)).addVideoComplete(eq(1L), eq(SIDEBAR), eq("DESKTOP"), any());
    }

    @Test
    void recordVideoPlay_forAPlacementWithAnImage_orWithoutMedia_isA404_andNotCounted() {
        videoIn(SponsorPlacement.ARTICLE_SIDEBAR, SponsorMediaType.IMAGE);
        when(placementRepository.existsBySponsorIdAndPlacement(2L, SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(true);
        when(creativeRepository.findBySponsorIdAndPlacement(2L, SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordVideoPlay(1L, SIDEBAR, "203.0.113.5", BROWSER)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getMessage()).isEqualTo(MessageConstant.SPONSOR_VIDEO_NOT_FOUND.getDefaultMessage());
        });
        assertThatThrownBy(() -> service.recordVideoComplete(2L, SIDEBAR, "203.0.113.5", BROWSER)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(statRepository, never()).addVideoPlay(anyLong(), anyString(), anyString(), any());
        verify(statRepository, never()).addVideoComplete(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordVideoPlay_forAPlacementTheSponsorDidNotChoose_isA404() {
        when(placementRepository.existsBySponsorIdAndPlacement(9L, SponsorPlacement.ARTICLE_POPUP)).thenReturn(false);

        assertThatThrownBy(() -> service.recordVideoPlay(9L, POPUP, "203.0.113.5", BROWSER)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(statRepository, never()).addVideoPlay(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void recordVideoPlay_forThePhoneBar_orAnUnknownPlacement_is400() {
        for (String placement : new String[]{BAR, "nope"}) {
            assertThatThrownBy(() -> service.recordVideoPlay(1L, placement, "203.0.113.5", BROWSER)).as(placement)
                    .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
            assertThatThrownBy(() -> service.recordVideoComplete(1L, placement, "203.0.113.5", BROWSER)).as(placement)
                    .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(statRepository, never()).addVideoPlay(anyLong(), anyString(), anyString(), any());
    }

    // ---- the sidebar slideshow ----

    private static final String BAR_FORMAT = SponsorPlacement.MOBILE_BOTTOM_BAR.name();

    private void sidebarSettings(String layout, int perSlide) {
        when(systemSettingService.sidebarSettings()).thenReturn(new SidebarSettingsDto(layout, perSlide, true, 8));
    }

    private Sponsor sponsorWithLogo(long id, int weight, boolean logo) {
        Sponsor sponsor = sponsor(id, weight);
        if (logo) {
            sponsor.setLogoUrl("/uploads/logo-" + id + ".png");
            sponsor.setLogoSmallUrl("/uploads/logo-small-" + id + ".png");
        }
        return sponsor;
    }

    private List<Long> sidebarIds() {
        return service.pickSidebar(null, "en").orElseThrow().getItems().stream().map(PromoDto::getId).toList();
    }

    @Test
    void pickSidebar_answersTheSettingsAndTheSponsors_withTheSidebarImageForAListLayout() {
        sidebarSettings("GRID_2", 4);
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, sponsor(1, 10), sponsor(2, 10), sponsor(3, 10));

        PromoSidebarDto answer = service.pickSidebar(null, "km").orElseThrow();

        assertThat(answer.getLayout()).isEqualTo("GRID_2");
        assertThat(answer.getPerSlide()).isEqualTo(4);
        assertThat(answer.isRotate()).isTrue();
        assertThat(answer.getIntervalSeconds()).isEqualTo(8);
        assertThat(answer.getItems()).hasSize(3);
        PromoDto first = answer.getItems().get(0);
        assertThat(first.getImageUrl()).isEqualTo("/uploads/" + first.getId() + ".png"); // the sidebar image
        assertThat(first.getWidth()).isEqualTo(300);
        assertThat(first.getHeight()).isEqualTo(250);
        assertThat(first.getPlacement()).isEqualTo("ARTICLE_SIDEBAR");
        assertThat(first.getPopup()).isNull();
        assertThat(first.getClickUrl()).isEqualTo("/api/v1/promos/" + first.getId() + "/click?placement=ARTICLE_SIDEBAR");
        assertThat(first.getLabel()).isEqualTo("ឧបត្ថម្ភ");
        assertThat(first.toString()).doesNotContain("sponsor1.example");
    }

    @Test
    void pickSidebar_neverRepeatsASponsor_inOneAnswer() {
        sidebarSettings("LIST_1", 1);
        Sponsor[] sponsors = new Sponsor[10];
        for (int i = 0; i < sponsors.length; i++) {
            sponsors[i] = sponsor(i + 1, 1 + i * 10);
        }
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, sponsors);

        for (int run = 0; run < 200; run++) {
            List<Long> ids = sidebarIds();
            assertThat(ids).hasSize(10).doesNotHaveDuplicates();
        }
    }

    @Test
    void pickSidebar_takesAtMost24Sponsors_andFewerWhenFewerAreEligible() {
        sidebarSettings("LIST_1", 1);
        Sponsor[] many = new Sponsor[40];
        for (int i = 0; i < many.length; i++) {
            many[i] = sponsor(i + 1, 10);
        }
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, many);
        assertThat(sidebarIds()).hasSize(24).doesNotHaveDuplicates();

        // 3 eligible: 3 come back (the expired and the wrong-language ones are not eligible)
        Sponsor ended = sponsor(101, 10);
        ended.setEndAt(now.minus(Duration.ofHours(1)));
        Sponsor khmerOnly = sponsor(102, 10);
        khmerOnly.setLanguage(SponsorLanguage.KM);
        ConcurrentMapCacheManager fresh = new ConcurrentMapCacheManager(CacheConfig.PROMO_CACHE, CacheConfig.PROMO_RATE_CACHE);
        service = new PromoServiceImpl(sponsorRepository, creativeRepository, categoryRepository, placementRepository, statRepository,
                systemSettingService, fresh, new PromoRateLimiter(fresh), properties);
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, sponsor(1, 10), sponsor(2, 10), sponsor(3, 10), ended, khmerOnly);
        assertThat(sidebarIds()).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    void pickSidebar_logosLayout_skipsSponsorsWithoutALogo_andUsesTheLogo() {
        sidebarSettings("LOGOS_4", 8);
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, sponsorWithLogo(1, 10, true), sponsorWithLogo(2, 10, false), sponsorWithLogo(3, 10, true));

        PromoSidebarDto answer = service.pickSidebar(null, "en").orElseThrow();

        assertThat(answer.getItems()).extracting(PromoDto::getId).containsExactlyInAnyOrder(1L, 3L);
        for (PromoDto item : answer.getItems()) {
            assertThat(item.getImageUrl()).isEqualTo("/uploads/logo-" + item.getId() + ".png");
            assertThat(item.getWidth()).isEqualTo(256);
            assertThat(item.getHeight()).isEqualTo(256);
            assertThat(item.getMediaType()).isEqualTo("IMAGE");
            assertThat(item.getVideoUrl()).isNull();
        }
    }

    @Test
    void pickSidebar_logosLayout_isEmptyWhenNoSponsorHasALogo() {
        sidebarSettings("LOGOS_4", 4);
        eligibleInRepository(SponsorPlacement.ARTICLE_SIDEBAR, sponsorWithLogo(1, 10, false), sponsorWithLogo(2, 10, false));

        assertThat(service.pickSidebar(null, "en")).isEmpty();
    }

    @Test
    void pickSidebar_isEmptyWhenSponsorsAreOff_orNoneIsEligible() {
        when(systemSettingService.isSponsorsEnabled()).thenReturn(false);
        assertThat(service.pickSidebar(null, "en")).isEmpty();
        verifyNoInteractions(creativeRepository);

        when(systemSettingService.isSponsorsEnabled()).thenReturn(true);
        sidebarSettings("LIST_1", 1);
        when(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(List.of());
        assertThat(service.pickSidebar(null, "en")).isEmpty();
    }

    @Test
    void pickSidebar_aVideoSponsorKeepsItsVideo_inAListLayout() {
        sidebarSettings("LIST_1", 1);
        Sponsor sponsor = sponsor(1, 10);
        when(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_SIDEBAR))
                .thenReturn(List.of(mediaCreative(sponsor, SponsorPlacement.ARTICLE_SIDEBAR, SponsorMediaType.VIDEO_FILE)));

        PromoDto item = service.pickSidebar(null, "en").orElseThrow().getItems().get(0);

        assertThat(item.getMediaType()).isEqualTo("VIDEO_FILE");
        assertThat(item.getVideoUrl()).isEqualTo("/uploads/clip-1.mp4");
    }

    @Test
    void weightedShuffle_aHigherWeightIsMoreOftenFirst_withAFixedSeed() {
        List<PromoServiceImpl.Candidate> candidates = List.of(candidate(1, 1), candidate(2, 5), candidate(3, 50));
        java.util.Random random = new java.util.Random(20260101L);
        int[] first = new int[4];
        for (int run = 0; run < 5000; run++) {
            List<PromoServiceImpl.Candidate> order = PromoServiceImpl.weightedShuffle(candidates, random);
            assertThat(order).hasSize(3).doesNotHaveDuplicates();
            first[(int) (long) order.get(0).id()]++;
        }
        // the chance to be first is weight / total: about 1.8%, 8.9% and 89.3%
        assertThat(first[3]).isGreaterThan(first[2]).isGreaterThan(first[1]);
        assertThat(first[3]).isBetween(4200, 4700);
        assertThat(first[1]).isBetween(20, 200);
        // and the same seed gives the same order
        assertThat(PromoServiceImpl.weightedShuffle(candidates, new java.util.Random(7))).isEqualTo(PromoServiceImpl.weightedShuffle(candidates, new java.util.Random(7)));
    }

    @Test
    void weightedShuffle_keepsEveryoneOnce_whatEverTheWeights() {
        List<PromoServiceImpl.Candidate> candidates = new ArrayList<>();
        for (int id = 1; id <= 24; id++) {
            candidates.add(candidate(id, id % 2 == 0 ? 1 : 100));
        }
        for (int run = 0; run < 100; run++) {
            assertThat(PromoServiceImpl.weightedShuffle(candidates, new java.util.Random(run))).containsExactlyInAnyOrderElementsOf(candidates);
        }
    }

    @Test
    void pick_thePhoneBar_usesTheLogo_whenTheSponsorHasOne() {
        when(systemSettingService.popupDefaults()).thenReturn(siteDefaults());
        Sponsor sponsor = sponsorWithLogo(1, 10, true);
        eligibleInRepository(SponsorPlacement.ARTICLE_POPUP, sponsor);

        PromoDto bar = service.pick(BAR_FORMAT, null, "en").orElseThrow();

        assertThat(bar.getImageUrl()).isEqualTo("/uploads/logo-1.png");
        assertThat(bar.getMediaType()).isEqualTo("IMAGE");
        // the popup itself is unchanged: exactly one sponsor with its popup image
        assertThat(service.pick(POPUP, null, "en").orElseThrow().getImageUrl()).isEqualTo("/uploads/1.png");
    }
}
