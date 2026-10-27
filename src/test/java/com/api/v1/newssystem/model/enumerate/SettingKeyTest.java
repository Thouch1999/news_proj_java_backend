package com.api.v1.newssystem.model.enumerate;

import com.api.v1.newssystem.util.SettingValueValidator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The list of system settings is the contract of the feature: these tests fail when it drifts from the rules. */
class SettingKeyTest {

    private static Set<String> keysOf(SettingGroup group) {
        return SettingKey.inGroup(group).stream().map(SettingKey::getKey).collect(Collectors.toSet());
    }

    @Test
    void theGroupsHoldExactlyTheDocumentedKeys() {
        assertThat(keysOf(SettingGroup.GENERAL)).containsExactlyInAnyOrder(
                "site_name_en", "site_name_km", "dashboard_name", "tagline_en", "tagline_km", "default_language", "title_format");
        assertThat(keysOf(SettingGroup.BRANDING)).containsExactlyInAnyOrder(
                "logo_url", "logo_dark_url", "favicon_url", "primary_color", "accent_color");
        assertThat(keysOf(SettingGroup.WEBSITE)).containsExactlyInAnyOrder(
                "footer_description_en", "footer_description_km", "copyright_text", "contact_email", "contact_phone",
                "contact_address", "social_facebook", "social_telegram", "social_youtube", "social_tiktok");
        assertThat(keysOf(SettingGroup.SEO)).containsExactlyInAnyOrder(
                "meta_description_en", "meta_description_km", "share_image_url");
        assertThat(keysOf(SettingGroup.CONTENT)).containsExactlyInAnyOrder(
                "news_per_page", "comments_enabled_default", "comment_blocked_words", "show_view_count",
                "sponsors_enabled", "sponsor_popup_enabled", "popup_trigger", "popup_delay_seconds", "popup_scroll_percent",
                "popup_frequency", "popup_frequency_days",
                "sponsor_sidebar_count", "sponsor_sidebar_layout", "sponsor_sidebar_rotate", "sponsor_sidebar_interval_seconds");
    }

    @Test
    void theSponsorSettingsArePublic_withTheirDefaultsAndRanges() {
        assertThat(SettingKey.SPONSORS_ENABLED.isPublicSetting()).isTrue();
        assertThat(SettingKey.SPONSOR_POPUP_ENABLED.isPublicSetting()).isTrue();
        assertThat(SettingKey.POPUP_DELAY_SECONDS.getDefaultValue()).isEqualTo("15");
        assertThat(SettingKey.POPUP_SCROLL_PERCENT.getDefaultValue()).isEqualTo("50");
        // the safety limits: the delay 5 to 120 seconds, the scroll 20 to 90 percent
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_DELAY_SECONDS, "5")).isNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_DELAY_SECONDS, "120")).isNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_DELAY_SECONDS, "4")).isNotNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_DELAY_SECONDS, "0")).isNotNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_DELAY_SECONDS, "121")).isNotNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_SCROLL_PERCENT, "20")).isNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_SCROLL_PERCENT, "90")).isNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_SCROLL_PERCENT, "19")).isNotNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_SCROLL_PERCENT, "91")).isNotNull();
        assertThat(SettingValueValidator.problem(SettingKey.SPONSORS_ENABLED, "maybe")).isNotNull();
    }

    @Test
    void theBlockedWordsOfTheComments_areAnOptionalPrivateMultilineText() {
        SettingKey words = SettingKey.COMMENT_BLOCKED_WORDS;
        assertThat(words.getKey()).isEqualTo("comment_blocked_words");
        assertThat(words.getGroup()).isEqualTo(SettingGroup.CONTENT);
        assertThat(words.getType()).isEqualTo(SettingType.TEXT);
        assertThat(words.isPublicSetting()).isFalse();
        assertThat(words.isRequired()).isFalse();
        assertThat(words.getDefaultValue()).isEmpty();
        // one word or phrase per line, English and Khmer; no HTML
        assertThat(SettingValueValidator.problem(words, "casino\nfree money\nល្បែង")).isNull();
        assertThat(SettingValueValidator.problem(words, "")).isNull();
        assertThat(SettingValueValidator.problem(words, "<script>")).isNotNull();
    }

    @Test
    void keysAreUnique_lowerSnakeCase_andFitTheColumn() {
        List<String> keys = Arrays.stream(SettingKey.values()).map(SettingKey::getKey).toList();
        assertThat(keys).doesNotHaveDuplicates();
        assertThat(keys).allMatch(key -> key.matches("^[a-z]+(_[a-z]+)*$") && key.length() <= 100);
    }

    @Test
    void groupPathsMatchTheUrl_andResolveBack() {
        for (SettingGroup group : SettingGroup.values()) {
            assertThat(SettingGroup.fromPath(group.getPath())).contains(group);
        }
        assertThat(SettingGroup.fromPath("unknown")).isEmpty();
        assertThat(SettingKey.fromKey("not_a_key")).isEmpty();
    }

    @Test
    void everyDefaultValueIsValid_forItsOwnRules() {
        for (SettingKey setting : SettingKey.values()) {
            String value = SettingValueValidator.clean(setting.getDefaultValue());
            // an image has no default (empty), everything else must pass the same checks as a value from the admin
            assertThat(SettingValueValidator.problem(setting, value)).as("default of %s", setting.getKey()).isNull();
        }
    }

    @Test
    void theRequiredNamesAreRequired_andTheImageSettingsAreTheFourDocumentedOnes() {
        assertThat(SettingKey.SITE_NAME_EN.isRequired()).isTrue();
        assertThat(SettingKey.DASHBOARD_NAME.isRequired()).isTrue();
        assertThat(Arrays.stream(SettingKey.values()).filter(s -> s.getType() == SettingType.IMAGE).map(SettingKey::getKey))
                .containsExactlyInAnyOrder("logo_url", "logo_dark_url", "favicon_url", "share_image_url");
        assertThat(Arrays.stream(SettingKey.values()).filter(s -> s.getType() == SettingType.IMAGE)).allMatch(s -> s.getDefaultValue().isEmpty());
    }

    @Test
    void onlyTheEditorSideSettingsAreNotPublic() {
        assertThat(Arrays.stream(SettingKey.values()).filter(s -> !s.isPublicSetting()).map(SettingKey::getKey))
                .containsExactlyInAnyOrder("comments_enabled_default", "comment_blocked_words");
    }

    @Test
    void theDefaultsAreTheCurrentSiteTexts() {
        assertThat(SettingKey.SITE_NAME_EN.getDefaultValue()).isEqualTo("News Portal");
        assertThat(SettingKey.DASHBOARD_NAME.getDefaultValue()).isEqualTo("News Dashboard");
        assertThat(SettingKey.DEFAULT_LANGUAGE.getDefaultValue()).isEqualTo("en");
        assertThat(SettingKey.NEWS_PER_PAGE.getDefaultValue()).isEqualTo("10");
        assertThat(SettingKey.FOOTER_DESCRIPTION_EN.getDefaultValue()).startsWith("The latest and most read news");
    }

    @Test
    void thePopupTimingDefaults_areValid_andTheirRulesAreEnforced() {
        assertThat(SettingKey.POPUP_TRIGGER.getDefaultValue()).isEqualTo("FIRST_OF_BOTH");
        assertThat(SettingKey.POPUP_FREQUENCY.getDefaultValue()).isEqualTo("PER_DAY");
        assertThat(SettingKey.POPUP_FREQUENCY_DAYS.getDefaultValue()).isEqualTo("3");
        for (SettingKey key : new SettingKey[]{SettingKey.POPUP_TRIGGER, SettingKey.POPUP_DELAY_SECONDS, SettingKey.POPUP_SCROLL_PERCENT,
                SettingKey.POPUP_FREQUENCY, SettingKey.POPUP_FREQUENCY_DAYS}) {
            assertThat(key.isPublicSetting()).as(key.getKey()).isTrue();
            assertThat(SettingValueValidator.problem(key, key.getDefaultValue())).as(key.getKey()).isNull();
        }
        for (String trigger : new String[]{"DELAY", "SCROLL", "FIRST_OF_BOTH"}) {
            assertThat(SettingValueValidator.problem(SettingKey.POPUP_TRIGGER, trigger)).isNull();
        }
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_TRIGGER, "delay")).isNotNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_TRIGGER, "ALWAYS")).isNotNull();
        for (String frequency : new String[]{"PER_VISIT", "PER_DAY", "EVERY_N_DAYS"}) {
            assertThat(SettingValueValidator.problem(SettingKey.POPUP_FREQUENCY, frequency)).isNull();
        }
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_FREQUENCY, "HOURLY")).isNotNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_FREQUENCY_DAYS, "1")).isNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_FREQUENCY_DAYS, "30")).isNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_FREQUENCY_DAYS, "0")).isNotNull();
        assertThat(SettingValueValidator.problem(SettingKey.POPUP_FREQUENCY_DAYS, "31")).isNotNull();
    }

    @Test
    void theSidebarSlideshowSettings_arePublic_withTheirDefaultsAndRanges() {
        assertThat(SettingKey.SPONSOR_SIDEBAR_COUNT.getDefaultValue()).isEqualTo("1");
        assertThat(SettingKey.SPONSOR_SIDEBAR_LAYOUT.getDefaultValue()).isEqualTo("LIST_1");
        assertThat(SettingKey.SPONSOR_SIDEBAR_ROTATE.getDefaultValue()).isEqualTo("true");
        assertThat(SettingKey.SPONSOR_SIDEBAR_INTERVAL_SECONDS.getDefaultValue()).isEqualTo("8");
        for (SettingKey key : new SettingKey[]{SettingKey.SPONSOR_SIDEBAR_COUNT, SettingKey.SPONSOR_SIDEBAR_LAYOUT, SettingKey.SPONSOR_SIDEBAR_ROTATE,
                SettingKey.SPONSOR_SIDEBAR_INTERVAL_SECONDS}) {
            assertThat(key.isPublicSetting()).as(key.getKey()).isTrue();
            assertThat(SettingValueValidator.problem(key, key.getDefaultValue())).as(key.getKey()).isNull();
        }
        // sponsors per slide: 1 to 12
        for (String ok : new String[]{"1", "6", "12"}) {
            assertThat(SettingValueValidator.problem(SettingKey.SPONSOR_SIDEBAR_COUNT, ok)).as(ok).isNull();
        }
        for (String bad : new String[]{"0", "13", "-1", "abc", "1.5"}) {
            assertThat(SettingValueValidator.problem(SettingKey.SPONSOR_SIDEBAR_COUNT, bad)).as(bad).isNotNull();
        }
        // change every: 5 to 60 seconds
        for (String ok : new String[]{"5", "8", "60"}) {
            assertThat(SettingValueValidator.problem(SettingKey.SPONSOR_SIDEBAR_INTERVAL_SECONDS, ok)).as(ok).isNull();
        }
        for (String bad : new String[]{"4", "0", "61", "abc"}) {
            assertThat(SettingValueValidator.problem(SettingKey.SPONSOR_SIDEBAR_INTERVAL_SECONDS, bad)).as(bad).isNotNull();
        }
        // layout: one of the three, exactly
        for (String ok : new String[]{"LIST_1", "GRID_2", "LOGOS_4"}) {
            assertThat(SettingValueValidator.problem(SettingKey.SPONSOR_SIDEBAR_LAYOUT, ok)).as(ok).isNull();
        }
        for (String bad : new String[]{"list_1", "GRID_3", "TILES", "LOGOS_5"}) {
            assertThat(SettingValueValidator.problem(SettingKey.SPONSOR_SIDEBAR_LAYOUT, bad)).as(bad).isNotNull();
        }
        assertThat(SettingValueValidator.problem(SettingKey.SPONSOR_SIDEBAR_ROTATE, "maybe")).isNotNull();
        assertThat(SettingValueValidator.problem(SettingKey.SPONSOR_SIDEBAR_ROTATE, "false")).isNull();
    }
}
