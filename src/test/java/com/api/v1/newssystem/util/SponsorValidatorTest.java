package com.api.v1.newssystem.util;

import com.api.v1.newssystem.model.enumerate.PopupTrigger;
import com.api.v1.newssystem.model.enumerate.PopupFrequency;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import com.api.v1.newssystem.model.request.SponsorRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SponsorValidatorTest {

    private SponsorRequest valid() {
        SponsorRequest request = new SponsorRequest();
        request.setName("Acme Bank");
        request.setTitle("Save more");
        request.setDescription("Open an account today");
        request.setCtaText("Learn more");
        request.setTargetUrl("https://acme.example/offer?utm=news");
        request.setWeight(10);
        request.setPlacements(List.of(SponsorPlacement.ARTICLE_SIDEBAR));
        return request;
    }

    @Test
    void aValidRequest_hasNoProblem() {
        assertThat(SponsorValidator.problems(valid())).isEmpty();
    }

    @Test
    void theTargetUrl_mustBeHttps() {
        for (String url : new String[]{"http://acme.example", "javascript:alert(1)", "ftp://acme.example", "//acme.example",
                "acme.example", "https://", "https://user:pass@acme.example", "https://acme .example", "data:text/html,x"}) {
            SponsorRequest request = valid();
            request.setTargetUrl(url);
            assertThat(SponsorValidator.problems(request)).as(url).containsKey("targetUrl");
        }
    }

    @Test
    void theWeight_isFrom1To100() {
        for (int weight : new int[]{0, -1, 101}) {
            SponsorRequest request = valid();
            request.setWeight(weight);
            assertThat(SponsorValidator.problems(request)).as("weight " + weight).containsKey("weight");
        }
        for (int weight : new int[]{1, 100}) {
            SponsorRequest request = valid();
            request.setWeight(weight);
            assertThat(SponsorValidator.problems(request)).as("weight " + weight).isEmpty();
        }
    }

    @Test
    void endAt_mustBeAfterStartAt() {
        SponsorRequest request = valid();
        request.setStartAt(Instant.parse("2026-11-01T00:00:00Z"));
        request.setEndAt(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(SponsorValidator.problems(request)).containsKey("endAt");

        request.setEndAt(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(SponsorValidator.problems(request)).containsKey("endAt");

        request.setEndAt(Instant.parse("2026-12-01T00:00:00Z"));
        assertThat(SponsorValidator.problems(request)).isEmpty();
    }

    @Test
    void atLeastOnePlace_isRequired_andOnlyTheSidebarAndThePopupCanBeChosen() {
        for (List<SponsorPlacement> bad : java.util.Arrays.<List<SponsorPlacement>>asList(null, List.of(), List.of(SponsorPlacement.MOBILE_BOTTOM_BAR),
                java.util.Arrays.asList(SponsorPlacement.ARTICLE_SIDEBAR, null))) {
            SponsorRequest request = valid();
            request.setPlacements(bad);
            assertThat(SponsorValidator.problems(request)).as(String.valueOf(bad)).containsEntry("placements", bad != null && !bad.isEmpty() ? "Only the sidebar and the popup can be chosen." : "Choose at least one place.");
        }
        for (List<SponsorPlacement> good : List.of(List.of(SponsorPlacement.ARTICLE_SIDEBAR), List.of(SponsorPlacement.ARTICLE_POPUP),
                List.of(SponsorPlacement.ARTICLE_SIDEBAR, SponsorPlacement.ARTICLE_POPUP))) {
            SponsorRequest request = valid();
            request.setPlacements(good);
            assertThat(SponsorValidator.problems(request)).as(good.toString()).isEmpty();
        }
    }

    @Test
    void textsArePlain_andLengthLimited() {
        SponsorRequest request = valid();
        request.setName("<b>Acme</b>");
        request.setTitle("Line one\nline two");
        request.setDescription("x".repeat(301));
        request.setCtaText("Click <here>");
        Map<String, String> problems = SponsorValidator.problems(request);
        assertThat(problems).containsKeys("name", "title", "description", "ctaText");
    }

    @Test
    void requiredTexts_cannotBeBlank() {
        SponsorRequest request = valid();
        request.setName(" ");
        request.setTitle(null);
        request.setCtaText("");
        request.setTargetUrl(null);
        assertThat(SponsorValidator.problems(request)).containsKeys("name", "title", "ctaText", "targetUrl");
    }

    @Test
    void khmerText_isFine() {
        SponsorRequest request = valid();
        request.setName("ក្រុមហ៊ុន ណាមួយ");
        request.setTitle("ការផ្តល់ជូនពិសេស");
        assertThat(SponsorValidator.problems(request)).isEmpty();
    }

    @Test
    void altText_isPlainTextUpTo200() {
        assertThat(SponsorValidator.altTextProblem("A banner")).isNull();
        assertThat(SponsorValidator.altTextProblem("")).isNull();
        assertThat(SponsorValidator.altTextProblem("<script>")).isNotNull();
        assertThat(SponsorValidator.altTextProblem("x".repeat(201))).isNotNull();
    }

    // ---- popup timing ----

    private SponsorRequest own(PopupTrigger trigger, Integer delay, Integer scroll, PopupFrequency frequency, Integer days) {
        SponsorRequest request = valid();
        request.setPopupUseDefaults(false);
        request.setPopupTrigger(trigger);
        request.setPopupDelaySeconds(delay);
        request.setPopupScrollPercent(scroll);
        request.setPopupFrequency(frequency);
        request.setPopupFrequencyDays(days);
        return request;
    }

    @Test
    void theSiteDefaults_needNoPopupFields_andIgnoreWhateverIsSent() {
        assertThat(SponsorValidator.usesOwnPopupRules(valid())).isFalse(); // missing means the defaults
        SponsorRequest request = valid();
        request.setPopupUseDefaults(true);
        request.setPopupDelaySeconds(1); // out of the limits, but not used
        request.setPopupScrollPercent(99);
        request.setPopupFrequencyDays(500);
        assertThat(SponsorValidator.problems(request)).isEmpty();
    }

    @Test
    void ownRules_aValidSetIsAccepted_atEveryLimit() {
        assertThat(SponsorValidator.problems(own(PopupTrigger.DELAY, 30, null, PopupFrequency.EVERY_N_DAYS, 3))).isEmpty();
        assertThat(SponsorValidator.problems(own(PopupTrigger.SCROLL, null, 60, PopupFrequency.PER_DAY, null))).isEmpty();
        assertThat(SponsorValidator.problems(own(PopupTrigger.FIRST_OF_BOTH, 5, 20, PopupFrequency.PER_VISIT, null))).isEmpty();
        assertThat(SponsorValidator.problems(own(PopupTrigger.FIRST_OF_BOTH, 120, 90, PopupFrequency.EVERY_N_DAYS, 30))).isEmpty();
        assertThat(SponsorValidator.problems(own(PopupTrigger.FIRST_OF_BOTH, 5, 20, PopupFrequency.EVERY_N_DAYS, 1))).isEmpty();
    }

    @Test
    void ownRules_theDelayIsFive_to120Seconds() {
        for (int delay : new int[]{-1, 0, 3, 4, 121, 300}) {
            assertThat(SponsorValidator.problems(own(PopupTrigger.DELAY, delay, null, PopupFrequency.PER_DAY, null)))
                    .as("delay " + delay).containsKey("popupDelaySeconds");
        }
    }

    @Test
    void ownRules_theScrollIs20_to90Percent() {
        for (int scroll : new int[]{0, 19, 91, 100}) {
            assertThat(SponsorValidator.problems(own(PopupTrigger.SCROLL, null, scroll, PopupFrequency.PER_DAY, null)))
                    .as("scroll " + scroll).containsKey("popupScrollPercent");
        }
    }

    @Test
    void ownRules_theFrequencyDaysAre1To30_andRequiredForEveryNDays() {
        for (int days : new int[]{0, -2, 31}) {
            assertThat(SponsorValidator.problems(own(PopupTrigger.DELAY, 10, null, PopupFrequency.EVERY_N_DAYS, days)))
                    .as("days " + days).containsKey("popupFrequencyDays");
        }
        assertThat(SponsorValidator.problems(own(PopupTrigger.DELAY, 10, null, PopupFrequency.EVERY_N_DAYS, null)))
                .containsEntry("popupFrequencyDays", "is required");
    }

    @Test
    void ownRules_whatTheTriggerUses_isRequired_andANumberThatIsGivenIsStillChecked() {
        assertThat(SponsorValidator.problems(own(null, 10, 50, null, null)))
                .containsKeys("popupTrigger", "popupFrequency");
        assertThat(SponsorValidator.problems(own(PopupTrigger.DELAY, null, 50, PopupFrequency.PER_DAY, null))).containsKey("popupDelaySeconds");
        assertThat(SponsorValidator.problems(own(PopupTrigger.SCROLL, 10, null, PopupFrequency.PER_DAY, null))).containsKey("popupScrollPercent");
        assertThat(SponsorValidator.problems(own(PopupTrigger.FIRST_OF_BOTH, 10, null, PopupFrequency.PER_DAY, null))).containsKey("popupScrollPercent");
        assertThat(SponsorValidator.problems(own(PopupTrigger.FIRST_OF_BOTH, null, 50, PopupFrequency.PER_DAY, null))).containsKey("popupDelaySeconds");
        // the delay is not used by SCROLL, but a value that is sent must still be inside the limits
        assertThat(SponsorValidator.problems(own(PopupTrigger.SCROLL, 2, 50, PopupFrequency.PER_DAY, null))).containsKey("popupDelaySeconds");
    }
}
