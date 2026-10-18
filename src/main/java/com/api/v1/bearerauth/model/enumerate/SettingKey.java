package com.api.v1.bearerauth.model.enumerate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The one list of system settings: key, group, type, default value, whether the public endpoint shows it, the longest
 * text allowed, whether it may not be empty and an extra rule. A key that is not here is rejected.
 */
public enum SettingKey {

    // ---- general ----
    SITE_NAME_EN("site_name_en", SettingGroup.GENERAL, SettingType.STRING, "News Portal", true, 100, true, Rule.NONE),
    SITE_NAME_KM("site_name_km", SettingGroup.GENERAL, SettingType.STRING, "វិបផតថលព័ត៌មាន", true, 100, false, Rule.NONE),
    DASHBOARD_NAME("dashboard_name", SettingGroup.GENERAL, SettingType.STRING, "News Dashboard", true, 100, true, Rule.NONE),
    TAGLINE_EN("tagline_en", SettingGroup.GENERAL, SettingType.STRING, "", true, 200, false, Rule.NONE),
    TAGLINE_KM("tagline_km", SettingGroup.GENERAL, SettingType.STRING, "", true, 200, false, Rule.NONE),
    DEFAULT_LANGUAGE("default_language", SettingGroup.GENERAL, SettingType.STRING, "en", true, 2, true, Rule.LANGUAGE),
    TITLE_FORMAT("title_format", SettingGroup.GENERAL, SettingType.STRING, "{page} | {site}", true, 100, true, Rule.NONE),

    // ---- branding ----
    LOGO_URL("logo_url", SettingGroup.BRANDING, SettingType.IMAGE, "", true, 500, false, Rule.NONE),
    LOGO_DARK_URL("logo_dark_url", SettingGroup.BRANDING, SettingType.IMAGE, "", true, 500, false, Rule.NONE),
    FAVICON_URL("favicon_url", SettingGroup.BRANDING, SettingType.IMAGE, "", true, 500, false, Rule.NONE),
    PRIMARY_COLOR("primary_color", SettingGroup.BRANDING, SettingType.COLOR, "#27306D", true, 7, true, Rule.NONE),
    ACCENT_COLOR("accent_color", SettingGroup.BRANDING, SettingType.COLOR, "#DA0000", true, 7, true, Rule.NONE),

    // ---- website ----
    FOOTER_DESCRIPTION_EN("footer_description_en", SettingGroup.WEBSITE, SettingType.TEXT,
            "The latest and most read news, with categories, search and comments. Log in to bookmark the articles you want to read later.",
            true, 500, false, Rule.NONE),
    FOOTER_DESCRIPTION_KM("footer_description_km", SettingGroup.WEBSITE, SettingType.TEXT,
            "ព័ត៌មានថ្មីៗ និងអានច្រើនបំផុត មានប្រភេទ ការស្វែងរក និងមតិយោបល់។ ចូលគណនីដើម្បីរក្សាទុកអត្ថបទដែលអ្នកចង់អានពេលក្រោយ។",
            true, 500, false, Rule.NONE),
    COPYRIGHT_TEXT("copyright_text", SettingGroup.WEBSITE, SettingType.STRING, "", true, 200, false, Rule.NONE),
    CONTACT_EMAIL("contact_email", SettingGroup.WEBSITE, SettingType.STRING, "", true, 254, false, Rule.EMAIL),
    CONTACT_PHONE("contact_phone", SettingGroup.WEBSITE, SettingType.STRING, "", true, 30, false, Rule.NONE),
    CONTACT_ADDRESS("contact_address", SettingGroup.WEBSITE, SettingType.TEXT, "", true, 300, false, Rule.NONE),
    SOCIAL_FACEBOOK("social_facebook", SettingGroup.WEBSITE, SettingType.URL, "", true, 300, false, Rule.NONE),
    SOCIAL_TELEGRAM("social_telegram", SettingGroup.WEBSITE, SettingType.URL, "", true, 300, false, Rule.NONE),
    SOCIAL_YOUTUBE("social_youtube", SettingGroup.WEBSITE, SettingType.URL, "", true, 300, false, Rule.NONE),
    SOCIAL_TIKTOK("social_tiktok", SettingGroup.WEBSITE, SettingType.URL, "", true, 300, false, Rule.NONE),

    // ---- seo ----
    META_DESCRIPTION_EN("meta_description_en", SettingGroup.SEO, SettingType.TEXT, "", true, 300, false, Rule.NONE),
    META_DESCRIPTION_KM("meta_description_km", SettingGroup.SEO, SettingType.TEXT, "", true, 300, false, Rule.NONE),
    SHARE_IMAGE_URL("share_image_url", SettingGroup.SEO, SettingType.IMAGE, "", true, 500, false, Rule.NONE),

    // ---- content ----
    NEWS_PER_PAGE("news_per_page", SettingGroup.CONTENT, SettingType.NUMBER, "10", true, 2, true, Rule.NEWS_PER_PAGE),
    // only the editors' side needs this one, so it is not public
    COMMENTS_ENABLED_DEFAULT("comments_enabled_default", SettingGroup.CONTENT, SettingType.BOOLEAN, "true", false, 5, true, Rule.NONE),
    SHOW_VIEW_COUNT("show_view_count", SettingGroup.CONTENT, SettingType.BOOLEAN, "true", true, 5, true, Rule.NONE);

    /** An extra check on top of the type. */
    public enum Rule {
        NONE,
        LANGUAGE,      // en or km
        EMAIL,
        NEWS_PER_PAGE  // 5 to 50
    }

    public static final int NEWS_PER_PAGE_MIN = 5;
    public static final int NEWS_PER_PAGE_MAX = 50;
    public static final List<String> LANGUAGES = List.of("en", "km");

    private final String key;
    private final SettingGroup group;
    private final SettingType type;
    private final String defaultValue;
    private final boolean publicSetting;
    private final int maxLength;
    private final boolean required;
    private final Rule rule;

    SettingKey(String key, SettingGroup group, SettingType type, String defaultValue, boolean publicSetting,
               int maxLength, boolean required, Rule rule) {
        this.key = key;
        this.group = group;
        this.type = type;
        this.defaultValue = defaultValue;
        this.publicSetting = publicSetting;
        this.maxLength = maxLength;
        this.required = required;
        this.rule = rule;
    }

    public String getKey() {
        return key;
    }

    public SettingGroup getGroup() {
        return group;
    }

    public SettingType getType() {
        return type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isPublicSetting() {
        return publicSetting;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public boolean isRequired() {
        return required;
    }

    public Rule getRule() {
        return rule;
    }

    public static Optional<SettingKey> fromKey(String key) {
        return Arrays.stream(values()).filter(setting -> setting.key.equals(key)).findFirst();
    }

    public static List<SettingKey> inGroup(SettingGroup group) {
        return Arrays.stream(values()).filter(setting -> setting.group == group).toList();
    }
}
