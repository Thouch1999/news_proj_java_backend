package com.api.v1.bearerauth.model.enumerate;

import com.api.v1.bearerauth.util.SettingValueValidator;
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
                "news_per_page", "comments_enabled_default", "show_view_count");
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
    void onlyTheEditorSideSettingIsNotPublic() {
        assertThat(Arrays.stream(SettingKey.values()).filter(s -> !s.isPublicSetting()).map(SettingKey::getKey))
                .containsExactly("comments_enabled_default");
    }

    @Test
    void theDefaultsAreTheCurrentSiteTexts() {
        assertThat(SettingKey.SITE_NAME_EN.getDefaultValue()).isEqualTo("News Portal");
        assertThat(SettingKey.DASHBOARD_NAME.getDefaultValue()).isEqualTo("News Dashboard");
        assertThat(SettingKey.DEFAULT_LANGUAGE.getDefaultValue()).isEqualTo("en");
        assertThat(SettingKey.NEWS_PER_PAGE.getDefaultValue()).isEqualTo("10");
        assertThat(SettingKey.FOOTER_DESCRIPTION_EN.getDefaultValue()).startsWith("The latest and most read news");
    }
}
