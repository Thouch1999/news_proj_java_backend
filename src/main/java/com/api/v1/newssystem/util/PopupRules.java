package com.api.v1.newssystem.util;

import com.api.v1.newssystem.model.enumerate.PopupFrequency;
import com.api.v1.newssystem.model.enumerate.PopupTrigger;
import com.api.v1.newssystem.model.enumerate.SettingKey;
import com.api.v1.newssystem.model.response.PopupRulesDto;

/** Builds the popup rules the page follows: the site defaults (always valid) and, per sponsor, its own values over them. */
public final class PopupRules {
    private PopupRules() {
    }

    /**
     * The site defaults from the saved setting texts. A text that is missing or not valid (an old value, or one written by
     * hand in the database) is replaced by the default of the setting, and a number is moved inside the safety limits.
     */
    public static PopupRulesDto defaults(String trigger, String delay, String scroll, String frequency, String days) {
        return new PopupRulesDto(
                enumName(trigger, PopupTrigger.values(), SettingKey.POPUP_TRIGGER),
                clamp(number(delay, SettingKey.POPUP_DELAY_SECONDS), SettingKey.POPUP_DELAY_MIN, SettingKey.POPUP_DELAY_MAX),
                clamp(number(scroll, SettingKey.POPUP_SCROLL_PERCENT), SettingKey.SCROLL_PERCENT_MIN, SettingKey.SCROLL_PERCENT_MAX),
                enumName(frequency, PopupFrequency.values(), SettingKey.POPUP_FREQUENCY),
                clamp(number(days, SettingKey.POPUP_FREQUENCY_DAYS), SettingKey.FREQUENCY_DAYS_MIN, SettingKey.FREQUENCY_DAYS_MAX));
    }

    /** The rules of a sponsor: its own values when it does not use the defaults (a missing one comes from the defaults). */
    public static PopupRulesDto effective(boolean useDefaults, PopupTrigger trigger, Integer delay, Integer scroll,
                                          PopupFrequency frequency, Integer days, PopupRulesDto defaults) {
        if (useDefaults) {
            return defaults;
        }
        return new PopupRulesDto(
                trigger == null ? defaults.getTrigger() : trigger.name(),
                delay == null ? defaults.getDelaySeconds() : clamp(delay, SettingKey.POPUP_DELAY_MIN, SettingKey.POPUP_DELAY_MAX),
                scroll == null ? defaults.getScrollPercent() : clamp(scroll, SettingKey.SCROLL_PERCENT_MIN, SettingKey.SCROLL_PERCENT_MAX),
                frequency == null ? defaults.getFrequency() : frequency.name(),
                days == null ? defaults.getFrequencyDays() : clamp(days, SettingKey.FREQUENCY_DAYS_MIN, SettingKey.FREQUENCY_DAYS_MAX));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int number(String text, SettingKey setting) {
        try {
            return Integer.parseInt(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            return Integer.parseInt(setting.getDefaultValue());
        }
    }

    private static String enumName(String text, Enum<?>[] allowed, SettingKey setting) {
        for (Enum<?> option : allowed) {
            if (option.name().equals(text)) {
                return option.name();
            }
        }
        return setting.getDefaultValue();
    }
}
