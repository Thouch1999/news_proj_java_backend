package com.api.v1.newssystem.service.impl;

import static org.mockito.ArgumentMatchers.anyInt;
import com.api.v1.newssystem.model.response.SidebarSettingsDto;
import com.api.v1.newssystem.util.VideoFixtures;
import com.api.v1.newssystem.model.response.YoutubeCheckDto;
import com.api.v1.newssystem.model.response.SponsorOptionsDto;
import com.api.v1.newssystem.model.response.PopupRulesDto;
import com.api.v1.newssystem.model.enumerate.SponsorMediaType;
import com.api.v1.newssystem.model.enumerate.PopupTrigger;
import com.api.v1.newssystem.model.enumerate.PopupFrequency;
import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.CacheConfig;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Sponsor;
import com.api.v1.newssystem.model.entity.SponsorCreative;
import com.api.v1.newssystem.model.entity.SponsorDailyStat;
import com.api.v1.newssystem.model.entity.SponsorPlacementChoice;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.model.enumerate.SponsorDevice;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import com.api.v1.newssystem.model.request.SponsorRequest;
import com.api.v1.newssystem.model.response.SponsorDto;
import com.api.v1.newssystem.model.response.SponsorStatsDto;
import com.api.v1.newssystem.model.response.SponsorSummaryDto;
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
import com.api.v1.newssystem.service.SystemSettingService;
import com.api.v1.newssystem.service.impl.LocalFileStorageServiceImpl;
import com.api.v1.newssystem.config.AppProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.data.domain.Page;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SponsorServiceImplTest {
    @Mock
    private SponsorRepository sponsorRepository;
    @Mock
    private SponsorCreativeRepository creativeRepository;
    @Mock
    private SponsorCategoryRepository sponsorCategoryRepository;
    @Mock
    private SponsorPlacementChoiceRepository placementRepository;
    @Mock
    private SponsorDailyStatRepository statRepository;
    @TempDir
    Path folder;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private SystemSettingService systemSettingService;

    private SponsorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SponsorServiceImpl(sponsorRepository, creativeRepository, sponsorCategoryRepository, placementRepository, statRepository,
                categoryRepository, appUserRepository, currentUserService, fileStorageService,
                new ConcurrentMapCacheManager(CacheConfig.PROMO_CACHE), activityLogService, systemSettingService);
        AppUser admin = new AppUser();
        admin.setId(1L);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(admin);
        lenient().when(appUserRepository.getReferenceById(anyLong())).thenReturn(admin);
        lenient().when(sponsorRepository.save(any())).thenAnswer(call -> {
            Sponsor saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(42L);
            }
            return saved;
        });
        lenient().when(creativeRepository.findAllBySponsorId(anyLong())).thenReturn(List.of());
        lenient().when(sponsorCategoryRepository.findTargets(any())).thenReturn(List.of());
    }

    private SponsorRequest request() {
        SponsorRequest request = new SponsorRequest();
        request.setName("  Acme Bank ");
        request.setTitle("Save more");
        request.setCtaText("Learn more");
        request.setTargetUrl("https://acme.example/offer");
        request.setPlacements(List.of(SponsorPlacement.ARTICLE_SIDEBAR));
        return request;
    }

    private Sponsor existing() {
        Sponsor sponsor = new Sponsor();
        sponsor.setId(5L);
        sponsor.setName("Acme Bank");
        sponsor.setTitle("Save more");
        sponsor.setCtaText("Learn more");
        sponsor.setTargetUrl("https://acme.example/offer");
        lenient().when(sponsorRepository.findById(5L)).thenReturn(Optional.of(sponsor));
        choose(SponsorPlacement.ARTICLE_SIDEBAR);
        return sponsor;
    }

    // the places the sponsor 5 chose
    private void choose(SponsorPlacement... places) {
        Sponsor sponsor = new Sponsor();
        sponsor.setId(5L);
        List<SponsorPlacementChoice> choices = new ArrayList<>();
        for (SponsorPlacement place : places) {
            SponsorPlacementChoice choice = new SponsorPlacementChoice();
            choice.setSponsor(sponsor);
            choice.setPlacement(place);
            choices.add(choice);
        }
        lenient().when(placementRepository.findAllBySponsorId(5L)).thenReturn(choices);
    }

    // the places of the sponsor 5 that have a stored image
    private List<SponsorCreative> imagesFor(SponsorPlacement... places) {
        List<SponsorCreative> images = new ArrayList<>();
        for (SponsorPlacement place : places) {
            SponsorCreative creative = new SponsorCreative();
            creative.setPlacement(place);
            creative.setImageUrl("/uploads/" + place.name() + ".png");
            images.add(creative);
        }
        lenient().when(creativeRepository.findAllBySponsorId(5L)).thenReturn(images);
        return images;
    }

    private SponsorDailyStat stat(Sponsor sponsor, String day, SponsorPlacement placement, long impressions, long clicks) {
        return stat(sponsor, day, placement, SponsorDevice.DESKTOP, impressions, clicks);
    }

    private SponsorDailyStat stat(Sponsor sponsor, String day, SponsorPlacement placement, SponsorDevice device, long impressions, long clicks) {
        SponsorDailyStat row = new SponsorDailyStat();
        row.setDevice(device);
        row.setSponsor(sponsor);
        row.setPlacement(placement);
        row.setStatDate(LocalDate.parse(day));
        row.setImpressions(impressions);
        row.setClicks(clicks);
        return row;
    }

    // ---- create / update ----

    @Test
    void create_startsInactive_withDefaults_andIsLogged() {
        SponsorDto created = service.create(request());

        assertThat(created.getName()).isEqualTo("Acme Bank");
        assertThat(created.isActive()).isFalse();
        assertThat(created.getStatus()).isEqualTo("inactive");
        assertThat(created.getWeight()).isEqualTo(10);
        assertThat(created.getLanguage()).isEqualTo("ALL");
        assertThat(created.getCategoryIds()).isEmpty();
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_CREATED), eq(ActivityTargetType.SPONSOR), eq(42L), eq("Acme Bank"), any());
    }

    @Test
    void create_withBadValues_is400_withTheFieldErrors_andSavesNothing() {
        SponsorRequest bad = request();
        bad.setTargetUrl("http://acme.example");
        bad.setWeight(500);
        bad.setStartAt(Instant.parse("2026-12-01T00:00:00Z"));
        bad.setEndAt(Instant.parse("2026-11-01T00:00:00Z"));

        assertThatThrownBy(() -> service.create(bad)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getErrorResponse().getDetails()).containsKeys("targetUrl", "weight", "endAt");
        });
        verify(sponsorRepository, never()).save(any());
        verify(activityLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void create_withAnUnknownCategory_is400() {
        SponsorRequest bad = request();
        bad.setCategoryIds(List.of(1L, 2L));
        when(categoryRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(bad)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorResponse().getDetails()).containsKey("categoryIds"));
    }

    @Test
    void update_logsOnlyWhatChanged_andNothingWhenNothingChanged() {
        Sponsor sponsor = existing();
        SponsorRequest same = request();
        same.setWeight(10);
        service.update(5L, same);
        verify(activityLogService, never()).record(any(), any(), any(), any(), any());

        SponsorRequest changed = request();
        changed.setWeight(40);
        service.update(5L, changed);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_UPDATED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), details.capture());
        assertThat(details.getValue().toString()).contains("weight").doesNotContain("title");
        assertThat(sponsor.getWeight()).isEqualTo(40);
    }

    // ---- activate / deactivate / delete ----

    @Test
    void setActive_logsOnlyARealChange() {
        existing();
        imagesFor(SponsorPlacement.ARTICLE_SIDEBAR); // the chosen place has its image

        service.setActive(5L, true);
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_ACTIVATED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), isNull());

        service.setActive(5L, true); // already on
        service.setActive(5L, false);
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_DEACTIVATED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), isNull());
    }

    @Test
    void delete_removesTheStatistics_theTargets_theImages_andIsLogged() {
        Sponsor sponsor = existing();
        SponsorCreative creative = new SponsorCreative();
        creative.setSponsor(sponsor);
        creative.setPlacement(SponsorPlacement.ARTICLE_SIDEBAR);
        creative.setImageUrl("/uploads/old.png");
        when(creativeRepository.findAllBySponsorId(5L)).thenReturn(List.of(creative));

        service.delete(5L);

        verify(statRepository).deleteAllBySponsorId(5L);
        verify(sponsorCategoryRepository).deleteAllBySponsorId(5L);
        verify(creativeRepository).deleteAllBySponsorId(5L);
        verify(sponsorRepository).delete(sponsor);
        verify(fileStorageService).delete("/uploads/old.png"); // no transaction in this test: runs at once
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_DELETED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), any());
    }

    @Test
    void anUnknownSponsor_is404() {
        when(sponsorRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(CustomException.class);
    }

    // ---- creatives ----

    @Test
    void uploadCreative_replacesTheImage_andDeletesTheOldFile() {
        Sponsor sponsor = existing();
        SponsorCreative old = new SponsorCreative();
        old.setId(3L);
        old.setSponsor(sponsor);
        old.setPlacement(SponsorPlacement.ARTICLE_SIDEBAR);
        old.setImageUrl("/uploads/old.png");
        when(creativeRepository.findBySponsorIdAndPlacement(5L, SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(Optional.of(old));
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/new.png");

        service.uploadCreative(5L, "article_sidebar", new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}), "  ");

        assertThat(old.getImageUrl()).isEqualTo("/uploads/new.png");
        assertThat(old.getAltText()).isEqualTo("Save more"); // blank alt text: the title
        verify(fileStorageService).delete("/uploads/old.png");
    }

    @Test
    void uploadCreative_storesWithTheImageRules_noGifNoSvg_twoMegabytes() {
        existing();
        when(creativeRepository.findBySponsorIdAndPlacement(any(), any())).thenReturn(Optional.empty());
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/new.png");

        service.uploadCreative(5L, "ARTICLE_POPUP", new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}), null);

        ArgumentCaptor<FileStorageService.Rules> rules = ArgumentCaptor.forClass(FileStorageService.Rules.class);
        verify(fileStorageService).store(any(), rules.capture());
        assertThat(rules.getValue().extensions()).containsExactlyInAnyOrder("png", "jpg", "webp");
        assertThat(rules.getValue().maxBytes()).isEqualTo(2L * 1024 * 1024);
    }

    @Test
    void uploadCreative_withAnUnknownPlacementOrAScriptInTheAltText_isRefused_andStoresNothing() {
        existing();
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.uploadCreative(5L, "HOME", file, null))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.uploadCreative(5L, "ARTICLE_SIDEBAR", file, "<script>"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(fileStorageService, never()).store(any(), any(FileStorageService.Rules.class));
    }

    // ---- statistics ----

    @Test
    void stats_arePerDay_perPlacement_andInTotal_withZerosForQuietDays() {
        Sponsor sponsor = existing();
        when(statRepository.findRange(5L, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-04"))).thenReturn(List.of(
                stat(sponsor, "2026-03-01", SponsorPlacement.ARTICLE_SIDEBAR, 100, 5),
                stat(sponsor, "2026-03-01", SponsorPlacement.ARTICLE_POPUP, SponsorDevice.MOBILE, 50, 1),
                stat(sponsor, "2026-03-03", SponsorPlacement.ARTICLE_SIDEBAR, 200, 20)));

        SponsorStatsDto stats = service.stats(5L, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-04"));

        assertThat(stats.getTotals().getImpressions()).isEqualTo(350);
        assertThat(stats.getTotals().getClicks()).isEqualTo(26);
        assertThat(stats.getTotals().getClickRate()).isEqualTo(7.43); // 26 / 350

        assertThat(stats.getByDay()).extracting(SponsorStatsDto.Point::getDate)
                .containsExactly("2026-03-01", "2026-03-02", "2026-03-03", "2026-03-04");
        assertThat(stats.getByDay()).extracting(SponsorStatsDto.Point::getImpressions).containsExactly(150L, 0L, 200L, 0L);
        assertThat(stats.getByDay()).extracting(SponsorStatsDto.Point::getClicks).containsExactly(6L, 0L, 20L, 0L);
        assertThat(stats.getByDay().get(0).getClickRate()).isEqualTo(4.0);
        assertThat(stats.getByDay().get(1).getClickRate()).isZero(); // no impressions: no division by zero

        assertThat(stats.getByPlacement()).extracting(SponsorStatsDto.Point::getPlacement).containsExactly("ARTICLE_SIDEBAR", "ARTICLE_POPUP");
        assertThat(stats.getByPlacement()).extracting(SponsorStatsDto.Point::getImpressions).containsExactly(300L, 50L);
        assertThat(stats.getByPlacement()).extracting(SponsorStatsDto.Point::getClicks).containsExactly(25L, 1L);
        // the popup split by device: nothing on a computer, the bar of a phone has the 50 views
        assertThat(stats.getByDevice()).extracting(SponsorStatsDto.Point::getDevice).containsExactly("DESKTOP", "MOBILE");
        assertThat(stats.getByDevice()).extracting(SponsorStatsDto.Point::getImpressions).containsExactly(0L, 50L);
        assertThat(stats.getByDevice()).extracting(SponsorStatsDto.Point::getClicks).containsExactly(0L, 1L);
        assertThat(stats.getRows()).hasSize(3);
    }

    @Test
    void stats_withABadRange_is400() {
        existing();

        assertThatThrownBy(() -> service.stats(5L, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-01")))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.stats(5L, LocalDate.parse("2024-01-01"), LocalDate.parse("2026-03-01")))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void summary_totalsEverySponsor_includingThoseWithoutEvents() {
        Sponsor first = existing();
        Sponsor second = new Sponsor();
        second.setId(6L);
        second.setName("Zeta");
        Sponsor quiet = new Sponsor();
        quiet.setId(7L);
        quiet.setName("Quiet");
        when(sponsorRepository.findAll()).thenReturn(List.of(first, second, quiet));
        when(statRepository.totalsPerSponsor(any(), any())).thenReturn(List.of(
                new Object[]{5L, 1000L, 50L, 0L, 0L}, new Object[]{6L, 400L, 4L, 0L, 0L}));

        SponsorSummaryDto summary = service.summary(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"));

        assertThat(summary.getTotals().getImpressions()).isEqualTo(1400);
        assertThat(summary.getTotals().getClicks()).isEqualTo(54);
        assertThat(summary.getSponsors()).extracting(SponsorSummaryDto.SponsorTotals::getSponsorName)
                .containsExactly("Acme Bank", "Zeta", "Quiet");
        assertThat(summary.getSponsors().get(0).getClickRate()).isEqualTo(5.0);
        assertThat(summary.getSponsors().get(2).getImpressions()).isZero();
    }

    @Test
    void exportCsv_hasABom_aHeader_andOneLinePerRow_withSafeCells() {
        Sponsor sponsor = existing();
        sponsor.setName("=HYPERLINK(\"x\")"); // a formula in the name must not run in a spreadsheet
        when(statRepository.findRange(eq(5L), any(), any())).thenReturn(List.of(
                stat(sponsor, "2026-03-01", SponsorPlacement.ARTICLE_SIDEBAR, 100, 5)));

        String csv = new String(service.exportCsv(5L, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-02")), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿Date,Sponsor,Placement,Device,Impressions,Clicks,Click rate (%),Video plays,Video completes,Completion rate (%)\r\n");
        assertThat(csv).contains("2026-03-01,\"'=HYPERLINK(\"\"x\"\")\",ARTICLE_SIDEBAR,DESKTOP,100,5,5.0,0,0,0.0\r\n");
    }

    // ---- status ----

    @Test
    void statusOf_followsTheSwitchAndTheDates() {
        Instant now = Instant.parse("2026-06-15T12:00:00Z");
        Sponsor sponsor = new Sponsor();
        assertThat(SponsorServiceImpl.statusOf(sponsor, now)).isEqualTo("inactive");

        sponsor.setActive(true);
        assertThat(SponsorServiceImpl.statusOf(sponsor, now)).isEqualTo("active");

        sponsor.setStartAt(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(SponsorServiceImpl.statusOf(sponsor, now)).isEqualTo("scheduled");

        sponsor.setStartAt(Instant.parse("2026-06-01T00:00:00Z"));
        sponsor.setEndAt(Instant.parse("2026-06-10T00:00:00Z"));
        assertThat(SponsorServiceImpl.statusOf(sponsor, now)).isEqualTo("ended");

        sponsor.setActive(false); // off wins over the dates
        assertThat(SponsorServiceImpl.statusOf(sponsor, now)).isEqualTo("inactive");
    }

    @Test
    void list_withAnUnknownStatus_is400() {
        assertThatThrownBy(() -> service.list("paused", null, null, 1, 20)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- the places the admin chooses ----

    @Test
    void create_withNoPlace_isRefused_withThePlacementsError() {
        SponsorRequest none = request();
        none.setPlacements(List.of());
        SponsorRequest missing = request();
        missing.setPlacements(null);
        SponsorRequest bar = request();
        bar.setPlacements(List.of(SponsorPlacement.MOBILE_BOTTOM_BAR)); // the bar is not a place to choose: it is the popup on a phone

        for (SponsorRequest bad : List.of(none, missing, bar)) {
            assertThatThrownBy(() -> service.create(bad)).isInstanceOfSatisfying(CustomException.class, e -> {
                assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getErrorResponse().getDetails()).containsKey("placements");
            });
        }
        verify(sponsorRepository, never()).save(any());
    }

    @Test
    void update_withNoPlace_isRefused() {
        existing();
        SponsorRequest none = request();
        none.setPlacements(List.of());

        assertThatThrownBy(() -> service.update(5L, none)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorResponse().getDetails()).containsKey("placements"));
    }

    @Test
    void create_storesTheChosenPlaces_andShowsThem() {
        SponsorRequest both = request();
        both.setPlacements(List.of(SponsorPlacement.ARTICLE_POPUP, SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP));

        service.create(both);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SponsorPlacementChoice>> saved = ArgumentCaptor.forClass(List.class);
        verify(placementRepository).saveAll(saved.capture());
        // each place once, in the order of the enum
        assertThat(saved.getValue()).extracting(SponsorPlacementChoice::getPlacement)
                .containsExactly(SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP);
    }

    @Test
    void create_activeWithoutTheImages_isRefused_withTheImageErrors() {
        SponsorRequest on = request();
        on.setActive(true);
        on.setPlacements(List.of(SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP));

        assertThatThrownBy(() -> service.create(on)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorResponse().getDetails()).containsKeys("sidebarImage", "popupImage")
                        .containsEntry("sidebarImage", "Upload the sidebar image.").containsEntry("popupImage", "Upload the popup image."));
        verify(sponsorRepository, never()).save(any());
    }

    @Test
    void update_anActiveSponsor_cannotChooseAPlaceWithoutItsImage() {
        Sponsor sponsor = existing();
        sponsor.setActive(true);
        imagesFor(SponsorPlacement.ARTICLE_SIDEBAR); // only the sidebar image is stored
        SponsorRequest both = request();
        both.setPlacements(List.of(SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP));

        assertThatThrownBy(() -> service.update(5L, both)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getErrorResponse().getDetails()).containsKey("popupImage").doesNotContainKey("sidebarImage");
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        });
        verify(placementRepository, never()).deleteAllBySponsorId(any());
    }

    @Test
    void update_aSponsorThatIsOff_mayChooseAPlaceBeforeItsImage() {
        existing();
        imagesFor();
        SponsorRequest both = request();
        both.setPlacements(List.of(SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP));

        service.update(5L, both);

        verify(placementRepository).deleteAllBySponsorId(5L);
    }

    @Test
    void activate_needsTheImageOfEveryChosenPlace_andAPlace() {
        existing();
        choose(SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP);
        imagesFor(SponsorPlacement.ARTICLE_POPUP);

        assertThatThrownBy(() -> service.setActive(5L, true)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorResponse().getDetails()).containsKey("sidebarImage").doesNotContainKey("popupImage"));

        imagesFor(SponsorPlacement.ARTICLE_POPUP, SponsorPlacement.ARTICLE_SIDEBAR);
        assertThat(service.setActive(5L, true).isActive()).isTrue();

        choose(); // an old sponsor without any chosen place
        assertThatThrownBy(() -> service.setActive(5L, true)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorResponse().getDetails()).containsKey("placements"));
    }

    @Test
    void uncheckingAPlace_keepsItsImage_andCheckingItAgainNeedsNoUpload() {
        Sponsor sponsor = existing();
        sponsor.setActive(true);
        choose(SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP);
        imagesFor(SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP);

        // uncheck the popup
        SponsorRequest sidebarOnly = request();
        sidebarOnly.setPlacements(List.of(SponsorPlacement.ARTICLE_SIDEBAR));
        SponsorDto unchecked = service.update(5L, sidebarOnly);

        verify(placementRepository).deleteAllBySponsorId(5L);
        verify(creativeRepository, never()).delete(any());
        verify(creativeRepository, never()).deleteAllBySponsorId(any());
        verify(fileStorageService, never()).delete(any());
        verify(fileStorageService, never()).store(any(), any(FileStorageService.Rules.class)); // and nothing was uploaded
        assertThat(unchecked.getCreatives()).extracting(SponsorDto.CreativeDto::getPlacement)
                .contains("ARTICLE_SIDEBAR", "ARTICLE_POPUP"); // the image is still stored

        // check it again: the image is still there, so there is nothing to upload
        choose(SponsorPlacement.ARTICLE_SIDEBAR);
        SponsorRequest both = request();
        both.setPlacements(List.of(SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP));
        service.update(5L, both);

        verify(placementRepository, times(2)).deleteAllBySponsorId(5L);
        verify(fileStorageService, never()).store(any(), any(FileStorageService.Rules.class));
    }

    @Test
    void update_logsThePlacementChange_beforeAndAfter() {
        existing();
        choose(SponsorPlacement.ARTICLE_SIDEBAR);
        SponsorRequest popup = request();
        popup.setPlacements(List.of(SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP));

        service.update(5L, popup);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_UPDATED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), details.capture());
        assertThat(details.getValue().toString()).contains("placements", "before=[ARTICLE_SIDEBAR]", "after=[ARTICLE_SIDEBAR, ARTICLE_POPUP]");
    }

    @Test
    void deleteCreative_ofAChosenPlace_isRefused_butAPlaceThatIsOffMayLoseItsImage() {
        existing();
        choose(SponsorPlacement.ARTICLE_SIDEBAR);
        SponsorCreative sidebar = new SponsorCreative();
        sidebar.setPlacement(SponsorPlacement.ARTICLE_SIDEBAR);
        sidebar.setImageUrl("/uploads/s.png");
        SponsorCreative popup = new SponsorCreative();
        popup.setPlacement(SponsorPlacement.ARTICLE_POPUP);
        popup.setImageUrl("/uploads/p.png");
        when(creativeRepository.findBySponsorIdAndPlacement(5L, SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(Optional.of(sidebar));
        when(creativeRepository.findBySponsorIdAndPlacement(5L, SponsorPlacement.ARTICLE_POPUP)).thenReturn(Optional.of(popup));
        when(creativeRepository.findBySponsorIdAndPlacement(5L, SponsorPlacement.MOBILE_BOTTOM_BAR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCreative(5L, "ARTICLE_SIDEBAR")).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(creativeRepository, never()).delete(sidebar);

        service.deleteCreative(5L, "ARTICLE_POPUP"); // not chosen
        verify(creativeRepository).delete(popup);
    }

    @Test
    void list_filtersByPlacement_andRefusesTheBar() {
        when(sponsorRepository.search(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.list(null, null, "article_sidebar", 1, 20);
        verify(sponsorRepository).search(eq("%"), eq(""), eq(SponsorPlacement.ARTICLE_SIDEBAR), any(), any());

        assertThatThrownBy(() -> service.list(null, null, "MOBILE_BOTTOM_BAR", 1, 20)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.list(null, null, "HOME", 1, 20)).isInstanceOf(CustomException.class);
    }

    // ---- the phone bar picture made by the image service ----

    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, x < width / 2 ? 0xFF0000 : 0x0000FF); // red left, blue right
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    // a service with the real file storage and a small in-memory store of the creatives of the sponsor 5
    private Map<SponsorPlacement, SponsorCreative> serviceWithRealImages() {
        AppProperty properties = new AppProperty();
        properties.getUpload().setDir(folder.toString());
        service = new SponsorServiceImpl(sponsorRepository, creativeRepository, sponsorCategoryRepository, placementRepository, statRepository,
                categoryRepository, appUserRepository, currentUserService, new LocalFileStorageServiceImpl(properties),
                new ConcurrentMapCacheManager(CacheConfig.PROMO_CACHE), activityLogService, systemSettingService);
        existing();
        Map<SponsorPlacement, SponsorCreative> stored = new HashMap<>();
        lenient().when(creativeRepository.findBySponsorIdAndPlacement(eq(5L), any())).thenAnswer(call -> Optional.ofNullable(stored.get(call.<SponsorPlacement>getArgument(1))));
        lenient().when(creativeRepository.save(any())).thenAnswer(call -> {
            SponsorCreative creative = call.getArgument(0);
            if (creative.getId() == null) {
                creative.setId((long) (stored.size() + 100)); // saved rows have an id
            }
            stored.put(creative.getPlacement(), creative);
            return creative;
        });
        lenient().when(creativeRepository.findAllBySponsorId(5L)).thenAnswer(call -> new ArrayList<>(stored.values()));
        return stored;
    }

    @Test
    void theBar_withoutItsOwnImage_getsASquareCenterCropOfThePopupImage() throws IOException {
        Map<SponsorPlacement, SponsorCreative> stored = serviceWithRealImages();

        service.uploadCreative(5L, "ARTICLE_POPUP", new MockMultipartFile("file", "p.png", "image/png", png(800, 400)), null);

        SponsorCreative bar = stored.get(SponsorPlacement.MOBILE_BOTTOM_BAR);
        assertThat(bar).isNotNull();
        assertThat(bar.isGenerated()).isTrue();
        assertThat(bar.getImageUrl()).isNotEqualTo(stored.get(SponsorPlacement.ARTICLE_POPUP).getImageUrl());
        BufferedImage crop = ImageIO.read(folder.resolve(bar.getImageUrl().substring("/uploads/".length())).toFile());
        assertThat(crop.getWidth()).isEqualTo(SponsorServiceImpl.BAR_CROP_SIZE);
        assertThat(crop.getHeight()).isEqualTo(SponsorServiceImpl.BAR_CROP_SIZE);
        // the center square of a 800 x 400 picture is 400 x 400 from x = 200: left half red, right half blue is cut in the middle
        assertThat(crop.getRGB(5, 120) & 0xFFFFFF).isEqualTo(0xFF0000);
        assertThat(crop.getRGB(crop.getWidth() - 5, 120) & 0xFFFFFF).isEqualTo(0x0000FF);
    }

    @Test
    void thePopupImage_alwaysRefreshesTheGeneratedCrop_theFallbackOfTheBarWithoutALogo() throws IOException {
        Map<SponsorPlacement, SponsorCreative> stored = serviceWithRealImages();
        service.uploadCreative(5L, "ARTICLE_POPUP", new MockMultipartFile("file", "p.png", "image/png", png(800, 400)), null);
        String first = stored.get(SponsorPlacement.MOBILE_BOTTOM_BAR).getImageUrl();

        service.uploadCreative(5L, "ARTICLE_POPUP", new MockMultipartFile("file", "p2.png", "image/png", png(1200, 800)), null);

        SponsorCreative bar = stored.get(SponsorPlacement.MOBILE_BOTTOM_BAR);
        assertThat(bar.isGenerated()).isTrue();
        assertThat(bar.getImageUrl()).isNotEqualTo(first);
        assertThat(folder.resolve(first.substring("/uploads/".length()))).doesNotExist(); // the replaced crop file is gone
    }

    @Test
    void aPopupImageThatCannotBeCropped_leavesNoBarImage_sothePageCropsThePopupImage() {
        Map<SponsorPlacement, SponsorCreative> stored = serviceWithRealImages();

        service.uploadCreative(5L, "ARTICLE_POPUP", new MockMultipartFile("file", "p.webp", "image/webp", WEBP), null);

        assertThat(stored).containsKey(SponsorPlacement.ARTICLE_POPUP).doesNotContainKey(SponsorPlacement.MOBILE_BOTTOM_BAR);
    }

    // ---- popup timing ----

    private SponsorRequest ownRules(PopupTrigger trigger, Integer delay, Integer scroll, PopupFrequency frequency, Integer days) {
        SponsorRequest own = request();
        own.setPopupUseDefaults(false);
        own.setPopupTrigger(trigger);
        own.setPopupDelaySeconds(delay);
        own.setPopupScrollPercent(scroll);
        own.setPopupFrequency(frequency);
        own.setPopupFrequencyDays(days);
        return own;
    }

    @Test
    void create_usesTheSiteDefaults_unlessTheRequestSaysOtherwise() {
        SponsorDto created = service.create(request());

        assertThat(created.isPopupUseDefaults()).isTrue();
        assertThat(created.getPopupTrigger()).isNull();
        assertThat(created.getPopupDelaySeconds()).isNull();
        assertThat(created.getPopupFrequency()).isNull();
    }

    @Test
    void update_withOwnRules_savesThem_andLogsTheChangeBeforeAndAfter() {
        Sponsor sponsor = existing();

        // an image sponsor with its own timing: after 30 seconds, once every 3 days
        service.update(5L, ownRules(PopupTrigger.DELAY, 30, null, PopupFrequency.EVERY_N_DAYS, 3));

        assertThat(sponsor.isPopupUseDefaults()).isFalse();
        assertThat(sponsor.getPopupTrigger()).isEqualTo(PopupTrigger.DELAY);
        assertThat(sponsor.getPopupDelaySeconds()).isEqualTo(30);
        assertThat(sponsor.getPopupScrollPercent()).isNull();
        assertThat(sponsor.getPopupFrequency()).isEqualTo(PopupFrequency.EVERY_N_DAYS);
        assertThat(sponsor.getPopupFrequencyDays()).isEqualTo(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_UPDATED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), details.capture());
        assertThat(details.getValue().toString()).contains("popupUseDefaults", "popupTrigger", "popupDelaySeconds", "popupFrequency",
                "popupFrequencyDays", "after=30", "after=EVERY_N_DAYS").doesNotContain("title");
    }

    @Test
    void update_backToTheDefaults_clearsTheOwnRules_andLogsIt() {
        Sponsor sponsor = existing();
        sponsor.setPopupUseDefaults(false);
        sponsor.setPopupTrigger(PopupTrigger.SCROLL);
        sponsor.setPopupScrollPercent(60);
        sponsor.setPopupFrequency(PopupFrequency.PER_VISIT);

        SponsorRequest back = request();
        back.setPopupUseDefaults(true);
        back.setPopupDelaySeconds(99); // ignored
        service.update(5L, back);

        assertThat(sponsor.isPopupUseDefaults()).isTrue();
        assertThat(sponsor.getPopupTrigger()).isNull();
        assertThat(sponsor.getPopupDelaySeconds()).isNull();
        assertThat(sponsor.getPopupScrollPercent()).isNull();
        assertThat(sponsor.getPopupFrequency()).isNull();
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_UPDATED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), any());
    }

    @Test
    void update_withTheSameRules_writesNoEntry() {
        Sponsor sponsor = existing();
        sponsor.setPopupUseDefaults(false);
        sponsor.setPopupTrigger(PopupTrigger.DELAY);
        sponsor.setPopupDelaySeconds(30);
        sponsor.setPopupFrequency(PopupFrequency.PER_DAY);

        SponsorRequest same = ownRules(PopupTrigger.DELAY, 30, null, PopupFrequency.PER_DAY, null);
        same.setWeight(10);
        service.update(5L, same);

        verify(activityLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void ownRules_outsideTheLimits_are400_withTheFieldErrors_andSaveNothing() {
        existing();
        SponsorRequest bad = ownRules(PopupTrigger.FIRST_OF_BOTH, 4, 95, PopupFrequency.EVERY_N_DAYS, 31);

        assertThatThrownBy(() -> service.update(5L, bad)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getErrorResponse().getDetails()).containsKeys("popupDelaySeconds", "popupScrollPercent", "popupFrequencyDays");
        });
        assertThatThrownBy(() -> service.create(ownRules(PopupTrigger.DELAY, 121, null, PopupFrequency.PER_DAY, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorResponse().getDetails()).containsKey("popupDelaySeconds"));
        verify(sponsorRepository, never()).save(any());
        verify(activityLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void options_giveTheSiteDefaultsAndTheLimitsOfTheForm() {
        when(systemSettingService.popupDefaults()).thenReturn(new PopupRulesDto("FIRST_OF_BOTH", 15, 50, "PER_DAY", 3));

        SponsorOptionsDto options = service.options();

        assertThat(options.getPopupDefaults().getDelaySeconds()).isEqualTo(15);
        assertThat(options.getLimits().getPopupDelayMin()).isEqualTo(5);
        assertThat(options.getLimits().getPopupDelayMax()).isEqualTo(120);
        assertThat(options.getLimits().getPopupScrollMin()).isEqualTo(20);
        assertThat(options.getLimits().getPopupScrollMax()).isEqualTo(90);
        assertThat(options.getLimits().getPopupFrequencyDaysMax()).isEqualTo(30);
        assertThat(options.getLimits().getVideoMaxBytes()).isEqualTo(15L * 1024 * 1024);
        assertThat(options.getLimits().getVideoMaxSeconds()).isEqualTo(30);
        assertThat(options.getLimits().getImageMaxBytes()).isEqualTo(2L * 1024 * 1024);
    }

    // ---- video files ----

    private MockMultipartFile video(byte[] content) {
        return new MockMultipartFile("file", "clip.mp4", "video/mp4", content);
    }

    private MockMultipartFile poster() {
        return new MockMultipartFile("poster", "poster.png", "image/png", new byte[]{1});
    }

    private void assertVideoRefused(Runnable upload, HttpStatus status, MessageConstant message) {
        assertThatThrownBy(upload::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(status);
            assertThat(e.getMessage()).isEqualTo(message.getDefaultMessage());
        });
        verify(fileStorageService, never()).store(any(), any(FileStorageService.Rules.class));
        verify(creativeRepository, never()).save(any());
    }

    @Test
    void uploadVideo_savesAVideoCreative_withItsPoster_underTheVideoRules() {
        existing();
        when(creativeRepository.findBySponsorIdAndPlacement(5L, SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(Optional.empty());
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/v.mp4", "/uploads/p.png");

        service.uploadVideo(5L, "article_sidebar", video(VideoFixtures.mp4("avc1", 20.0)), poster(), " ");

        ArgumentCaptor<SponsorCreative> saved = ArgumentCaptor.forClass(SponsorCreative.class);
        verify(creativeRepository).save(saved.capture());
        SponsorCreative creative = saved.getValue();
        assertThat(creative.getMediaType()).isEqualTo(SponsorMediaType.VIDEO_FILE);
        assertThat(creative.getVideoUrl()).isEqualTo("/uploads/v.mp4");
        assertThat(creative.getImageUrl()).isEqualTo("/uploads/p.png"); // the poster
        assertThat(creative.getYoutubeId()).isNull();
        assertThat(creative.getAltText()).isEqualTo("Save more");
        ArgumentCaptor<FileStorageService.Rules> rules = ArgumentCaptor.forClass(FileStorageService.Rules.class);
        verify(fileStorageService, times(2)).store(any(), rules.capture());
        assertThat(rules.getAllValues().get(0).extensions()).containsExactlyInAnyOrder("mp4", "webm");
        assertThat(rules.getAllValues().get(0).maxBytes()).isEqualTo(15L * 1024 * 1024);
        assertThat(rules.getAllValues().get(1).extensions()).containsExactlyInAnyOrder("png", "jpg", "webp"); // the image rules
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_UPDATED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), details.capture());
        assertThat(details.getValue().toString()).contains("VIDEO_FILE", "ARTICLE_SIDEBAR").doesNotContain("v.mp4", "p.png");
    }

    @Test
    void uploadVideo_needsAPoster() {
        existing();

        for (MockMultipartFile missing : new MockMultipartFile[]{null, new MockMultipartFile("poster", "p.png", "image/png", new byte[0])}) {
            assertThatThrownBy(() -> service.uploadVideo(5L, "ARTICLE_SIDEBAR", video(VideoFixtures.mp4("avc1", 5.0)), missing, null))
                    .isInstanceOfSatisfying(CustomException.class, e -> {
                        assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getErrorResponse().getDetails()).containsEntry("poster", "Upload the poster image.");
                    });
        }
        verify(fileStorageService, never()).store(any(), any(FileStorageService.Rules.class));
    }

    @Test
    void uploadVideo_aVideoOver15Megabytes_isRefused_withoutBeingRead() {
        existing();

        assertVideoRefused(() -> service.uploadVideo(5L, "ARTICLE_SIDEBAR", video(new byte[15 * 1024 * 1024 + 1]), poster(), null),
                HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_VIDEO_TOO_LARGE);
    }

    @Test
    void uploadVideo_theRealTypeIsReadFromTheContent_notFromTheNameOrTheContentType() {
        existing();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        byte[][] notVideos = {png, script, VideoFixtures.mp4("hvc1", 5.0) /* H.265 */, VideoFixtures.webm("matroska", 5.0),
                VideoFixtures.mp4("qt  ", "avc1", 5.0) /* QuickTime */, new byte[]{0, 1, 2}};
        for (byte[] content : notVideos) {
            assertVideoRefused(() -> service.uploadVideo(5L, "ARTICLE_SIDEBAR", video(content), poster(), null),
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, MessageConstant.SPONSOR_VIDEO_TYPE_NOT_ALLOWED);
        }
    }

    @Test
    void uploadVideo_longerThan30Seconds_isRefused_butAnUnreadableDurationOnlyHasTheSizeLimit() {
        existing();
        when(creativeRepository.findBySponsorIdAndPlacement(any(), any())).thenReturn(Optional.empty());
        assertVideoRefused(() -> service.uploadVideo(5L, "ARTICLE_SIDEBAR", video(VideoFixtures.mp4("avc1", 31.0)), poster(), null),
                HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_VIDEO_TOO_LONG);
        assertVideoRefused(() -> service.uploadVideo(5L, "ARTICLE_SIDEBAR", video(VideoFixtures.webm("webm", 120.0)), poster(), null),
                HttpStatus.BAD_REQUEST, MessageConstant.SPONSOR_VIDEO_TOO_LONG);

        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/a.mp4", "/uploads/a.png", "/uploads/b.webm", "/uploads/b.png");
        service.uploadVideo(5L, "ARTICLE_SIDEBAR", video(VideoFixtures.mp4("avc1", 30.0)), poster(), null);
        service.uploadVideo(5L, "ARTICLE_SIDEBAR", video(VideoFixtures.webm("webm", null)), poster(), null); // no duration to read
        verify(creativeRepository, times(2)).save(any());
    }

    @Test
    void uploadVideo_ifThePosterIsRefused_theStoredVideoIsDeleted() {
        existing();
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class)))
                .thenReturn("/uploads/v.mp4")
                .thenThrow(new CustomException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, MessageConstant.SPONSOR_IMAGE_TYPE_NOT_ALLOWED.getDefaultMessage()));

        assertThatThrownBy(() -> service.uploadVideo(5L, "ARTICLE_SIDEBAR", video(VideoFixtures.mp4("avc1", 5.0)), poster(), null))
                .isInstanceOf(CustomException.class);

        verify(fileStorageService).delete("/uploads/v.mp4");
        verify(creativeRepository, never()).save(any());
    }

    // ---- which media a placement accepts ----

    @Test
    void thePlacementsAcceptOnlyTheirMedia() {
        for (SponsorMediaType type : SponsorMediaType.values()) {
            assertThat(type.allowedIn(SponsorPlacement.ARTICLE_POPUP)).as("popup " + type).isTrue();
        }
        assertThat(SponsorMediaType.IMAGE.allowedIn(SponsorPlacement.ARTICLE_SIDEBAR)).isTrue();
        assertThat(SponsorMediaType.VIDEO_FILE.allowedIn(SponsorPlacement.ARTICLE_SIDEBAR)).isTrue();
        assertThat(SponsorMediaType.YOUTUBE.allowedIn(SponsorPlacement.ARTICLE_SIDEBAR)).isFalse();
        assertThat(SponsorMediaType.IMAGE.allowedIn(SponsorPlacement.MOBILE_BOTTOM_BAR)).isTrue();
        assertThat(SponsorMediaType.VIDEO_FILE.allowedIn(SponsorPlacement.MOBILE_BOTTOM_BAR)).isFalse();
        assertThat(SponsorMediaType.YOUTUBE.allowedIn(SponsorPlacement.MOBILE_BOTTOM_BAR)).isFalse();
    }

    @Test
    void aYouTubeVideo_isRefusedInTheSidebarAndTheBar_andAVideoFileInTheBar() {
        existing();
        MockMultipartFile clip = video(VideoFixtures.mp4("avc1", 5.0));
        String link = "https://youtu.be/dQw4w9WgXcQ";

        assertThatThrownBy(() -> service.uploadYoutube(5L, "ARTICLE_SIDEBAR", link, null, null)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorResponse().getDetails()).containsKey("mediaType"));
        assertThatThrownBy(() -> service.uploadYoutube(5L, "MOBILE_BOTTOM_BAR", link, null, null)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorResponse().getDetails()).containsKey("mediaType"));
        assertThatThrownBy(() -> service.uploadVideo(5L, "MOBILE_BOTTOM_BAR", clip, poster(), null)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorResponse().getDetails()).containsKey("mediaType"));
        verify(fileStorageService, never()).store(any(), any(FileStorageService.Rules.class));
        verify(creativeRepository, never()).save(any());
    }

    @Test
    void aVideoFile_isAcceptedInTheSidebarAndThePopup() {
        existing();
        when(creativeRepository.findBySponsorIdAndPlacement(any(), any())).thenReturn(Optional.empty());
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/a.mp4", "/uploads/a.png", "/uploads/b.mp4", "/uploads/b.png");

        service.uploadVideo(5L, "ARTICLE_SIDEBAR", video(VideoFixtures.mp4("avc1", 5.0)), poster(), null);
        service.uploadVideo(5L, "ARTICLE_POPUP", video(VideoFixtures.mp4("avc1", 5.0)), poster(), null);

        verify(creativeRepository, times(2)).save(any());
    }

    // ---- YouTube ----

    @Test
    void uploadYoutube_storesOnlyTheVideoId_andTheYouTubeThumbnailAsThePoster() {
        existing();
        when(creativeRepository.findBySponsorIdAndPlacement(any(), any())).thenReturn(Optional.empty());

        service.uploadYoutube(5L, "ARTICLE_POPUP", "  https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s&list=x  ", null, null);

        ArgumentCaptor<SponsorCreative> saved = ArgumentCaptor.forClass(SponsorCreative.class);
        verify(creativeRepository).save(saved.capture());
        SponsorCreative creative = saved.getValue();
        assertThat(creative.getMediaType()).isEqualTo(SponsorMediaType.YOUTUBE);
        assertThat(creative.getYoutubeId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(creative.getVideoUrl()).isNull();
        assertThat(creative.getImageUrl()).isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
        verify(fileStorageService, never()).store(any(), any(FileStorageService.Rules.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_UPDATED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), details.capture());
        assertThat(details.getValue().toString()).contains("YOUTUBE", "dQw4w9WgXcQ").doesNotContain("watch?v=");
    }

    @Test
    void uploadYoutube_withAnUploadedPoster_usesIt() {
        existing();
        when(creativeRepository.findBySponsorIdAndPlacement(any(), any())).thenReturn(Optional.empty());
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/own.png");

        service.uploadYoutube(5L, "ARTICLE_POPUP", "https://youtu.be/dQw4w9WgXcQ", poster(), "alt");

        ArgumentCaptor<SponsorCreative> saved = ArgumentCaptor.forClass(SponsorCreative.class);
        verify(creativeRepository).save(saved.capture());
        assertThat(saved.getValue().getImageUrl()).isEqualTo("/uploads/own.png");
        assertThat(saved.getValue().getAltText()).isEqualTo("alt");
    }

    @Test
    void uploadYoutube_refusesALinkThatIsNotYouTube_includingLookAlikes() {
        existing();
        for (String link : new String[]{"https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ", "https://evil.example/", "dQw4w9WgXcQ",
                "javascript:alert(1)", "https://www.youtube.com/watch?v=short", "https://vimeo.com/123456789", null, ""}) {
            assertThatThrownBy(() -> service.uploadYoutube(5L, "ARTICLE_POPUP", link, null, null)).as(link).isInstanceOfSatisfying(CustomException.class, e -> {
                assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getErrorResponse().getDetails()).containsKey("youtubeUrl");
            });
        }
        verify(creativeRepository, never()).save(any());
    }

    @Test
    void checkYoutube_givesTheIdAndTheThumbnail_orTheError() {
        YoutubeCheckDto ok = service.checkYoutube("https://www.youtube.com/shorts/dQw4w9WgXcQ");

        assertThat(ok.getVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(ok.getThumbnailUrl()).isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
        assertThatThrownBy(() -> service.checkYoutube("https://notyoutube.com/watch?v=dQw4w9WgXcQ")).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorResponse().getDetails()).containsKey("youtubeUrl"));
    }

    // ---- replacing and removing media ----

    @Test
    void anImage_replacesAVideo_andTheVideoFileAndThePosterAreDeleted() {
        existing();
        SponsorCreative old = new SponsorCreative();
        old.setId(3L);
        old.setPlacement(SponsorPlacement.ARTICLE_SIDEBAR);
        old.setMediaType(SponsorMediaType.VIDEO_FILE);
        old.setVideoUrl("/uploads/old.mp4");
        old.setImageUrl("/uploads/old-poster.png");
        when(creativeRepository.findBySponsorIdAndPlacement(5L, SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(Optional.of(old));
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/new.png");

        service.uploadCreative(5L, "ARTICLE_SIDEBAR", new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}), null);

        assertThat(old.getMediaType()).isEqualTo(SponsorMediaType.IMAGE);
        assertThat(old.getVideoUrl()).isNull();
        assertThat(old.getYoutubeId()).isNull();
        verify(fileStorageService).delete("/uploads/old.mp4");
        verify(fileStorageService).delete("/uploads/old-poster.png");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_UPDATED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), details.capture());
        assertThat(details.getValue().toString()).contains("mediaTypeBefore=VIDEO_FILE", "mediaType=IMAGE");
    }

    @Test
    void uploadPoster_replacesOnlyThePoster_ofAVideo_butNotOfAnImage() {
        existing();
        SponsorCreative video = new SponsorCreative();
        video.setId(3L);
        video.setPlacement(SponsorPlacement.ARTICLE_SIDEBAR);
        video.setMediaType(SponsorMediaType.VIDEO_FILE);
        video.setVideoUrl("/uploads/v.mp4");
        video.setImageUrl("/uploads/old-poster.png");
        when(creativeRepository.findBySponsorIdAndPlacement(5L, SponsorPlacement.ARTICLE_SIDEBAR)).thenReturn(Optional.of(video));
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/new-poster.png");

        service.uploadPoster(5L, "ARTICLE_SIDEBAR", poster());

        assertThat(video.getImageUrl()).isEqualTo("/uploads/new-poster.png");
        assertThat(video.getVideoUrl()).isEqualTo("/uploads/v.mp4");
        verify(fileStorageService).delete("/uploads/old-poster.png");
        verify(fileStorageService, never()).delete("/uploads/v.mp4");

        video.setMediaType(SponsorMediaType.IMAGE);
        assertThatThrownBy(() -> service.uploadPoster(5L, "ARTICLE_SIDEBAR", poster())).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void deleteCreative_ofAVideo_deletesTheVideoFileToo() {
        existing();
        choose(SponsorPlacement.ARTICLE_SIDEBAR);
        SponsorCreative popup = new SponsorCreative();
        popup.setPlacement(SponsorPlacement.ARTICLE_POPUP); // not chosen: it may go
        popup.setMediaType(SponsorMediaType.VIDEO_FILE);
        popup.setVideoUrl("/uploads/v.mp4");
        popup.setImageUrl("/uploads/p.png");
        when(creativeRepository.findBySponsorIdAndPlacement(5L, SponsorPlacement.ARTICLE_POPUP)).thenReturn(Optional.of(popup));
        when(creativeRepository.findBySponsorIdAndPlacement(5L, SponsorPlacement.MOBILE_BOTTOM_BAR)).thenReturn(Optional.empty());

        service.deleteCreative(5L, "ARTICLE_POPUP");

        verify(fileStorageService).delete("/uploads/v.mp4");
        verify(fileStorageService).delete("/uploads/p.png");
    }

    // ---- video statistics ----

    @Test
    void stats_countVideoPlaysAndCompletions_withTheCompletionRate() {
        Sponsor sponsor = existing();
        SponsorDailyStat popup = stat(sponsor, "2026-03-10", SponsorPlacement.ARTICLE_POPUP, 100, 5);
        popup.setVideoPlays(80);
        popup.setVideoCompletes(20);
        SponsorDailyStat sidebar = stat(sponsor, "2026-03-11", SponsorPlacement.ARTICLE_SIDEBAR, 50, 1);
        sidebar.setVideoPlays(20);
        sidebar.setVideoCompletes(15);
        when(statRepository.findRange(5L, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-12"))).thenReturn(List.of(popup, sidebar));

        SponsorStatsDto stats = service.stats(5L, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-12"));

        assertThat(stats.getTotals().getVideoPlays()).isEqualTo(100);
        assertThat(stats.getTotals().getVideoCompletes()).isEqualTo(35);
        assertThat(stats.getTotals().getCompletionRate()).isEqualTo(35.0);
        assertThat(stats.getByDay()).extracting(SponsorStatsDto.Point::getVideoPlays).containsExactly(80L, 20L, 0L);
        SponsorStatsDto.Point popupPoint = stats.getByPlacement().stream().filter(p -> "ARTICLE_POPUP".equals(p.getPlacement())).findFirst().orElseThrow();
        assertThat(popupPoint.getVideoPlays()).isEqualTo(80);
        assertThat(popupPoint.getCompletionRate()).isEqualTo(25.0);
        assertThat(stats.getByDevice().get(0).getVideoCompletes()).isEqualTo(20); // the popup on a computer
        assertThat(stats.getRows()).extracting(SponsorStatsDto.Point::getVideoPlays).containsExactly(80L, 20L);
        // a placement without plays: the rate is 0, not a division by zero
        assertThat(stats.getByDay().get(2).getCompletionRate()).isZero();
    }

    @Test
    void summary_andTheCsv_haveTheVideoColumns() {
        Sponsor sponsor = existing();
        when(sponsorRepository.findAll()).thenReturn(List.of(sponsor));
        when(statRepository.totalsPerSponsor(any(), any())).thenReturn(List.<Object[]>of(new Object[]{5L, 1000L, 50L, 200L, 50L}));

        SponsorSummaryDto summary = service.summary(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"));

        assertThat(summary.getTotals().getVideoPlays()).isEqualTo(200);
        assertThat(summary.getSponsors().get(0).getVideoCompletes()).isEqualTo(50);
        assertThat(summary.getSponsors().get(0).getCompletionRate()).isEqualTo(25.0);

        SponsorDailyStat row = stat(sponsor, "2026-03-10", SponsorPlacement.ARTICLE_POPUP, 100, 5);
        row.setVideoPlays(40);
        row.setVideoCompletes(10);
        when(statRepository.findRange(5L, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))).thenReturn(List.of(row));
        String csv = new String(service.exportCsv(5L, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10")), StandardCharsets.UTF_8);
        assertThat(csv).contains("Video plays,Video completes,Completion rate (%)").contains("100,5,5.0,40,10,25.0");
    }

    // ---- the logo ----

    @Test
    void uploadLogo_savesTheTwoVariants_andDeletesTheUploadAndTheOldLogo() {
        Sponsor sponsor = existing();
        sponsor.setLogoUrl("/uploads/old-256.png");
        sponsor.setLogoSmallUrl("/uploads/old-128.png");
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/original.png");
        when(fileStorageService.storeSquareCrop("/uploads/original.png", 256)).thenReturn("/uploads/new-256.png");
        when(fileStorageService.storeSquareCrop("/uploads/original.png", 128)).thenReturn("/uploads/new-128.png");

        SponsorDto dto = service.uploadLogo(5L, new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1}));

        assertThat(sponsor.getLogoUrl()).isEqualTo("/uploads/new-256.png");
        assertThat(sponsor.getLogoSmallUrl()).isEqualTo("/uploads/new-128.png");
        assertThat(dto.getLogoUrl()).isEqualTo("/uploads/new-256.png");
        assertThat(dto.getLogoSmallUrl()).isEqualTo("/uploads/new-128.png");
        verify(fileStorageService).delete("/uploads/original.png"); // only the variants are kept
        verify(fileStorageService).delete("/uploads/old-256.png");
        verify(fileStorageService).delete("/uploads/old-128.png");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(eq(ActivityAction.SPONSOR_UPDATED), eq(ActivityTargetType.SPONSOR), eq(5L), eq("Acme Bank"), details.capture());
        assertThat(details.getValue()).containsKey("logo").doesNotContainValue("/uploads/new-256.png");
    }

    @Test
    void uploadLogo_aPictureThatCannotBeCut_isKeptAsUploaded_forBothVariants() {
        Sponsor sponsor = existing();
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/original.webp");
        when(fileStorageService.storeSquareCrop(any(), anyInt())).thenReturn(null); // a WebP the JDK cannot decode

        service.uploadLogo(5L, new MockMultipartFile("file", "logo.webp", "image/webp", new byte[]{1}));

        assertThat(sponsor.getLogoUrl()).isEqualTo("/uploads/original.webp");
        assertThat(sponsor.getLogoSmallUrl()).isEqualTo("/uploads/original.webp");
        verify(fileStorageService, never()).delete("/uploads/original.webp");
    }

    @Test
    void uploadLogo_usesTheImageRules() {
        existing();
        when(fileStorageService.store(any(), any(FileStorageService.Rules.class))).thenReturn("/uploads/o.png");

        service.uploadLogo(5L, new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1}));

        ArgumentCaptor<FileStorageService.Rules> rules = ArgumentCaptor.forClass(FileStorageService.Rules.class);
        verify(fileStorageService).store(any(), rules.capture());
        assertThat(rules.getValue().extensions()).containsExactlyInAnyOrder("png", "jpg", "webp");
        assertThat(rules.getValue().maxBytes()).isEqualTo(2L * 1024 * 1024);
    }

    @Test
    void deleteLogo_removesBothFiles_andALogoThatIsNotThere_is404() {
        Sponsor sponsor = existing();
        assertThatThrownBy(() -> service.deleteLogo(5L)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        sponsor.setLogoUrl("/uploads/l-256.png");
        sponsor.setLogoSmallUrl("/uploads/l-128.png");
        SponsorDto dto = service.deleteLogo(5L);

        assertThat(dto.getLogoUrl()).isNull();
        assertThat(sponsor.getLogoSmallUrl()).isNull();
        verify(fileStorageService).delete("/uploads/l-256.png");
        verify(fileStorageService).delete("/uploads/l-128.png");
    }

    @Test
    void theBarPlacement_hasNoImageOfItsOwnAnyMore_theLogoReplacesIt() {
        existing();
        MockMultipartFile file = new MockMultipartFile("file", "b.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.uploadCreative(5L, "MOBILE_BOTTOM_BAR", file, null)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.deleteCreative(5L, "MOBILE_BOTTOM_BAR")).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(fileStorageService, never()).store(any(), any(FileStorageService.Rules.class));
    }

    @Test
    void delete_alsoDeletesTheLogoFiles() {
        Sponsor sponsor = existing();
        sponsor.setLogoUrl("/uploads/l-256.png");
        sponsor.setLogoSmallUrl("/uploads/l-128.png");

        service.delete(5L);

        verify(fileStorageService).delete("/uploads/l-256.png");
        verify(fileStorageService).delete("/uploads/l-128.png");
    }

    @Test
    void options_carryTheSidebarSettings() {
        when(systemSettingService.sidebarSettings()).thenReturn(new SidebarSettingsDto("LOGOS_4", 8, true, 8));

        assertThat(service.options().getSidebar().getLayout()).isEqualTo("LOGOS_4");
    }
}
