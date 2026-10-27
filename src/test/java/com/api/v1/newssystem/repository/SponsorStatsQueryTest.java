package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.Sponsor;
import com.api.v1.newssystem.model.entity.SponsorCreative;
import com.api.v1.newssystem.model.entity.SponsorDailyStat;
import com.api.v1.newssystem.model.entity.SponsorPlacementChoice;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sponsor tables against the configured MySQL, in a transaction that is rolled back: the atomic counters (insert on the
 * first event of a day, "+ 1" after that), the daily rows, and the query that feeds the choice of a sponsor.
 */
@SpringBootTest
@Transactional
class SponsorStatsQueryTest {
    private static final String SIDEBAR = SponsorPlacement.ARTICLE_SIDEBAR.name();
    private static final String POPUP = SponsorPlacement.ARTICLE_POPUP.name();

    @Autowired
    private EntityManager em;
    @Autowired
    private SponsorRepository sponsorRepository;
    @Autowired
    private SponsorCreativeRepository creativeRepository;
    @Autowired
    private SponsorDailyStatRepository statRepository;
    @Autowired
    private SponsorPlacementChoiceRepository placementRepository;

    private String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString().substring(0, 8);
    }

    private Sponsor sponsor(String label, boolean active) {
        Sponsor sponsor = new Sponsor();
        sponsor.setName(label + " " + token);
        sponsor.setTitle("Title");
        sponsor.setCtaText("Go");
        sponsor.setTargetUrl("https://example.com/" + token);
        sponsor.setActive(active);
        return sponsorRepository.saveAndFlush(sponsor);
    }

    @Test
    void theCounters_insertOnTheFirstEvent_thenAddOne_perDayAndPlacement() {
        Sponsor sponsor = sponsor("counters", true);
        LocalDate monday = LocalDate.of(2026, 3, 2);
        LocalDate tuesday = LocalDate.of(2026, 3, 3);

        statRepository.addImpression(sponsor.getId(), SIDEBAR, "DESKTOP", monday);
        statRepository.addImpression(sponsor.getId(), SIDEBAR, "DESKTOP", monday);
        statRepository.addImpression(sponsor.getId(), SIDEBAR, "DESKTOP", monday);
        statRepository.addClick(sponsor.getId(), SIDEBAR, "DESKTOP", monday);
        statRepository.addClick(sponsor.getId(), SIDEBAR, "DESKTOP", tuesday); // a click first: the row is created by the click
        statRepository.addImpression(sponsor.getId(), POPUP, "MOBILE", monday); // the bar of a phone counts under the popup
        em.clear();

        List<SponsorDailyStat> rows = statRepository.findRange(sponsor.getId(), monday, tuesday);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(row -> row.getStatDate() + " " + row.getPlacement() + " " + row.getDevice() + " " + row.getImpressions() + "/" + row.getClicks())
                .containsExactly("2026-03-02 ARTICLE_POPUP MOBILE 1/0", "2026-03-02 ARTICLE_SIDEBAR DESKTOP 3/1", "2026-03-03 ARTICLE_SIDEBAR DESKTOP 0/1");
    }

    @Test
    void theRange_includesBothDays_andLeavesTheOthersOut() {
        Sponsor sponsor = sponsor("range", true);
        for (int day = 1; day <= 5; day++) {
            statRepository.addImpression(sponsor.getId(), SIDEBAR, "DESKTOP", LocalDate.of(2026, 4, day));
        }
        em.clear();

        List<SponsorDailyStat> rows = statRepository.findRange(sponsor.getId(), LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 4));

        assertThat(rows).extracting(SponsorDailyStat::getStatDate)
                .containsExactly(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 4));
    }

    @Test
    void theTotalsPerSponsor_addUpTheRange() {
        Sponsor first = sponsor("first", true);
        Sponsor second = sponsor("second", true);
        LocalDate day = LocalDate.of(2026, 5, 1);
        statRepository.addImpression(first.getId(), SIDEBAR, "DESKTOP", day);
        statRepository.addImpression(first.getId(), SponsorPlacement.ARTICLE_POPUP.name(), "DESKTOP", day);
        statRepository.addClick(first.getId(), SIDEBAR, "DESKTOP", day);
        statRepository.addImpression(second.getId(), SIDEBAR, "DESKTOP", day);
        em.clear();

        List<Object[]> totals = statRepository.totalsPerSponsor(day, day);

        assertThat(totals).anySatisfy(row -> {
            assertThat(row[0]).isEqualTo(first.getId());
            assertThat(((Number) row[1]).longValue()).isEqualTo(2);
            assertThat(((Number) row[2]).longValue()).isEqualTo(1);
        });
        assertThat(totals).anySatisfy(row -> assertThat(row[0]).isEqualTo(second.getId()));
    }

    @Test
    void deletingTheStatsOfASponsor_keepsTheOthers() {
        Sponsor first = sponsor("gone", true);
        Sponsor second = sponsor("kept", true);
        LocalDate day = LocalDate.of(2026, 6, 1);
        statRepository.addImpression(first.getId(), SIDEBAR, "DESKTOP", day);
        statRepository.addImpression(second.getId(), SIDEBAR, "DESKTOP", day);
        em.clear();

        statRepository.deleteAllBySponsorId(first.getId());
        em.flush();
        em.clear();

        assertThat(statRepository.findRange(first.getId(), day, day)).isEmpty();
        assertThat(statRepository.findRange(second.getId(), day, day)).hasSize(1);
    }

    @Test
    void theChoiceQuery_returnsOnlyCreativesOfActiveSponsorsForThePlacement() {
        Sponsor active = sponsor("active", true);
        Sponsor inactive = sponsor("inactive", false);
        creative(active, SponsorPlacement.ARTICLE_SIDEBAR);
        creative(active, SponsorPlacement.ARTICLE_POPUP);
        creative(inactive, SponsorPlacement.ARTICLE_SIDEBAR);
        choose(active, SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP);
        choose(inactive, SponsorPlacement.ARTICLE_SIDEBAR);
        em.clear();

        List<SponsorCreative> found = creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_SIDEBAR);

        assertThat(found).extracting(creative -> creative.getSponsor().getId()).contains(active.getId()).doesNotContain(inactive.getId());
        assertThat(found).allMatch(creative -> creative.getPlacement() == SponsorPlacement.ARTICLE_SIDEBAR);
    }

    @Test
    void aPlaceThatIsNotChosen_isNeverSelected_evenWithItsImage_andIsBackWhenChosenAgain() {
        Sponsor sponsor = sponsor("unchosen", true);
        creative(sponsor, SponsorPlacement.ARTICLE_SIDEBAR);
        creative(sponsor, SponsorPlacement.ARTICLE_POPUP);
        choose(sponsor, SponsorPlacement.ARTICLE_SIDEBAR); // the popup image is stored, but the popup is not chosen
        em.clear();

        assertThat(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_SIDEBAR)).extracting(c -> c.getSponsor().getId()).contains(sponsor.getId());
        assertThat(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_POPUP)).extracting(c -> c.getSponsor().getId()).doesNotContain(sponsor.getId());

        // checked again: no new image is needed
        choose(sponsor, SponsorPlacement.ARTICLE_POPUP);
        em.clear();
        assertThat(creativeRepository.findForActiveSponsors(SponsorPlacement.ARTICLE_POPUP)).extracting(c -> c.getSponsor().getId()).contains(sponsor.getId());
    }

    @Test
    void theDevice_isPartOfTheKey_sothePopupAndTheBarCountSeparately() {
        Sponsor sponsor = sponsor("device", true);
        LocalDate day = LocalDate.of(2026, 8, 1);
        statRepository.addImpression(sponsor.getId(), POPUP, "DESKTOP", day);
        statRepository.addImpression(sponsor.getId(), POPUP, "DESKTOP", day);
        statRepository.addImpression(sponsor.getId(), POPUP, "MOBILE", day);
        statRepository.addClick(sponsor.getId(), POPUP, "MOBILE", day);
        em.clear();

        List<SponsorDailyStat> rows = statRepository.findRange(sponsor.getId(), day, day);

        assertThat(rows).extracting(row -> row.getDevice() + " " + row.getImpressions() + "/" + row.getClicks()).containsExactly("DESKTOP 2/0", "MOBILE 1/1");
    }

    @Test
    void theSponsorListFilter_findsOnlyTheSponsorsThatChoseThePlace() {
        Sponsor sidebar = sponsor("onlysidebar", true);
        Sponsor popup = sponsor("onlypopup", true);
        choose(sidebar, SponsorPlacement.ARTICLE_SIDEBAR);
        choose(popup, SponsorPlacement.ARTICLE_POPUP);
        em.clear();

        var sidebarPage = sponsorRepository.search("%" + token + "%", "", SponsorPlacement.ARTICLE_SIDEBAR, java.time.Instant.now(), org.springframework.data.domain.PageRequest.of(0, 50));
        var anyPage = sponsorRepository.search("%" + token + "%", "", null, java.time.Instant.now(), org.springframework.data.domain.PageRequest.of(0, 50));

        assertThat(sidebarPage.getContent()).extracting(Sponsor::getId).containsExactly(sidebar.getId());
        assertThat(anyPage.getContent()).extracting(Sponsor::getId).contains(sidebar.getId(), popup.getId());
    }

    private void choose(Sponsor sponsor, SponsorPlacement... places) {
        for (SponsorPlacement place : places) {
            SponsorPlacementChoice choice = new SponsorPlacementChoice();
            choice.setSponsor(sponsor);
            choice.setPlacement(place);
            placementRepository.saveAndFlush(choice);
        }
    }

    private void creative(Sponsor sponsor, SponsorPlacement placement) {
        SponsorCreative creative = new SponsorCreative();
        creative.setSponsor(sponsor);
        creative.setPlacement(placement);
        creative.setImageUrl("/uploads/" + token + ".png");
        creativeRepository.saveAndFlush(creative);
    }

    @Test
    void theVideoCounters_insertOnTheFirstEvent_thenAddOne_withoutTouchingTheOthers() {
        Sponsor sponsor = sponsor("video", true);
        LocalDate day = LocalDate.of(2026, 7, 1);

        statRepository.addVideoPlay(sponsor.getId(), POPUP, "DESKTOP", day); // a play first: the row is created by the play
        statRepository.addVideoPlay(sponsor.getId(), POPUP, "DESKTOP", day);
        statRepository.addVideoComplete(sponsor.getId(), POPUP, "DESKTOP", day);
        statRepository.addImpression(sponsor.getId(), POPUP, "DESKTOP", day);
        statRepository.addClick(sponsor.getId(), POPUP, "DESKTOP", day);
        statRepository.addVideoComplete(sponsor.getId(), SIDEBAR, "DESKTOP", day); // a completion first on another placement
        em.clear();

        List<SponsorDailyStat> rows = statRepository.findRange(sponsor.getId(), day, day);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.getPlacement() + " " + row.getImpressions() + "/" + row.getClicks() + "/" + row.getVideoPlays() + "/" + row.getVideoCompletes())
                .containsExactly("ARTICLE_POPUP 1/1/2/1", "ARTICLE_SIDEBAR 0/0/0/1");
        List<Object[]> totals = statRepository.totalsPerSponsor(day, day);
        assertThat(totals).anySatisfy(row -> {
            assertThat(row[0]).isEqualTo(sponsor.getId());
            assertThat(((Number) row[3]).longValue()).isEqualTo(2);
            assertThat(((Number) row[4]).longValue()).isEqualTo(2);
        });
    }

    @Test
    void aCreative_isAnImageByDefault_andKeepsItsVideoColumns() {
        Sponsor sponsor = sponsor("media", true);
        SponsorCreative image = new SponsorCreative();
        image.setSponsor(sponsor);
        image.setPlacement(SponsorPlacement.ARTICLE_SIDEBAR);
        image.setImageUrl("/uploads/i.png");
        SponsorCreative tube = new SponsorCreative();
        tube.setSponsor(sponsor);
        tube.setPlacement(SponsorPlacement.ARTICLE_POPUP);
        tube.setImageUrl("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
        tube.setMediaType(com.api.v1.newssystem.model.enumerate.SponsorMediaType.YOUTUBE);
        tube.setYoutubeId("dQw4w9WgXcQ");
        creativeRepository.save(image);
        creativeRepository.save(tube);
        em.flush();
        em.clear();

        assertThat(creativeRepository.findBySponsorIdAndPlacement(sponsor.getId(), SponsorPlacement.ARTICLE_SIDEBAR).orElseThrow())
                .satisfies(found -> {
                    assertThat(found.getMediaType()).isEqualTo(com.api.v1.newssystem.model.enumerate.SponsorMediaType.IMAGE);
                    assertThat(found.getVideoUrl()).isNull();
                    assertThat(found.getYoutubeId()).isNull();
                });
        assertThat(creativeRepository.findBySponsorIdAndPlacement(sponsor.getId(), SponsorPlacement.ARTICLE_POPUP).orElseThrow())
                .satisfies(found -> {
                    assertThat(found.getMediaType()).isEqualTo(com.api.v1.newssystem.model.enumerate.SponsorMediaType.YOUTUBE);
                    assertThat(found.getYoutubeId()).isEqualTo("dQw4w9WgXcQ");
                });
    }

    @Test
    void aSponsor_usesTheSiteDefaultsUnlessItHasItsOwnTiming() {
        Sponsor plain = sponsor("plain", true);
        Sponsor own = sponsor("own", true);
        own.setPopupUseDefaults(false);
        own.setPopupTrigger(com.api.v1.newssystem.model.enumerate.PopupTrigger.DELAY);
        own.setPopupDelaySeconds(30);
        own.setPopupFrequency(com.api.v1.newssystem.model.enumerate.PopupFrequency.EVERY_N_DAYS);
        own.setPopupFrequencyDays(3);
        sponsorRepository.saveAndFlush(own);
        em.clear();

        Sponsor reloadedPlain = sponsorRepository.findById(plain.getId()).orElseThrow();
        Sponsor reloadedOwn = sponsorRepository.findById(own.getId()).orElseThrow();

        assertThat(reloadedPlain.isPopupUseDefaults()).isTrue();
        assertThat(reloadedPlain.getPopupTrigger()).isNull();
        assertThat(reloadedOwn.isPopupUseDefaults()).isFalse();
        assertThat(reloadedOwn.getPopupDelaySeconds()).isEqualTo(30);
        assertThat(reloadedOwn.getPopupFrequencyDays()).isEqualTo(3);
    }
}
