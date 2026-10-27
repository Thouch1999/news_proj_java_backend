package com.api.v1.newssystem.util;

import com.api.v1.newssystem.model.enumerate.PopupFrequency;
import com.api.v1.newssystem.model.enumerate.PopupTrigger;
import com.api.v1.newssystem.model.enumerate.SettingKey;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import com.api.v1.newssystem.model.request.SponsorRequest;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Checks a sponsor request (the field errors go into `details` of a 400 VALIDATION_FAILED). Every text is plain text on one
 * line: the characters &lt; and &gt; and control characters are refused, so no HTML can be saved.
 */
public final class SponsorValidator {
    public static final int WEIGHT_MIN = 1;
    public static final int WEIGHT_MAX = 100;
    public static final int WEIGHT_DEFAULT = 10;
    public static final int ALT_TEXT_MAX = 200;

    private SponsorValidator() {
    }

    /** The problems of the request by field; empty when it is fine. Does not look at the categories (they need the database). */
    public static Map<String, String> problems(SponsorRequest request) {
        Map<String, String> problems = new TreeMap<>();
        plainText(problems, "name", request.getName(), 150, true);
        plainText(problems, "title", request.getTitle(), 150, true);
        plainText(problems, "description", request.getDescription(), 300, false);
        plainText(problems, "ctaText", request.getCtaText(), 50, true);

        String url = request.getTargetUrl() == null ? "" : request.getTargetUrl().strip();
        if (url.isEmpty()) {
            problems.put("targetUrl", "is required");
        } else if (url.length() > 500) {
            problems.put("targetUrl", "must be at most 500 characters");
        } else if (!SettingValueValidator.isHttpsUrl(url)) {
            problems.put("targetUrl", "must be an https address");
        }

        List<SponsorPlacement> chosen = request.getPlacements();
        if (chosen == null || chosen.isEmpty()) {
            problems.put("placements", "Choose at least one place.");
        } else if (chosen.stream().anyMatch(placement -> placement == null || !placement.isChoosable())) {
            problems.put("placements", "Only the sidebar and the popup can be chosen.");
        }

        Integer weight = request.getWeight();
        if (weight != null && (weight < WEIGHT_MIN || weight > WEIGHT_MAX)) {
            problems.put("weight", "must be between " + WEIGHT_MIN + " and " + WEIGHT_MAX);
        }
        if (request.getStartAt() != null && request.getEndAt() != null && !request.getEndAt().isAfter(request.getStartAt())) {
            problems.put("endAt", "must be after the start");
        }
        popupProblems(problems, request);
        return problems;
    }

    /** True when the sponsor has its own popup rules (popupUseDefaults is false); missing or true means the site defaults. */
    public static boolean usesOwnPopupRules(SponsorRequest request) {
        return Boolean.FALSE.equals(request.getPopupUseDefaults());
    }

    // The own popup rules, against the safety limits of the settings. With the site defaults nothing else is looked at.
    private static void popupProblems(Map<String, String> problems, SponsorRequest request) {
        if (!usesOwnPopupRules(request)) {
            return;
        }
        PopupTrigger trigger = request.getPopupTrigger();
        PopupFrequency frequency = request.getPopupFrequency();
        if (trigger == null) {
            problems.put("popupTrigger", "is required");
        }
        if (frequency == null) {
            problems.put("popupFrequency", "is required");
        }
        number(problems, "popupDelaySeconds", request.getPopupDelaySeconds(), SettingKey.POPUP_DELAY_MIN, SettingKey.POPUP_DELAY_MAX,
                trigger != null && trigger != PopupTrigger.SCROLL);
        number(problems, "popupScrollPercent", request.getPopupScrollPercent(), SettingKey.SCROLL_PERCENT_MIN, SettingKey.SCROLL_PERCENT_MAX,
                trigger != null && trigger != PopupTrigger.DELAY);
        number(problems, "popupFrequencyDays", request.getPopupFrequencyDays(), SettingKey.FREQUENCY_DAYS_MIN, SettingKey.FREQUENCY_DAYS_MAX,
                frequency == PopupFrequency.EVERY_N_DAYS);
    }

    // a number inside the limits when it is given (even when the trigger does not use it); missing only when it is required
    private static void number(Map<String, String> problems, String field, Integer value, int min, int max, boolean required) {
        if (value == null) {
            if (required) {
                problems.put(field, "is required");
            }
        } else if (value < min || value > max) {
            problems.put(field, "must be between " + min + " and " + max);
        }
    }

    /** The problem with an alt text (plain text, 200 characters at most; empty is fine), or null. */
    public static String altTextProblem(String altText) {
        Map<String, String> problems = new TreeMap<>();
        plainText(problems, "altText", altText, ALT_TEXT_MAX, false);
        return problems.get("altText");
    }

    private static void plainText(Map<String, String> problems, String field, String raw, int max, boolean required) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            if (required) {
                problems.put(field, "is required");
            }
            return;
        }
        if (value.length() > max) {
            problems.put(field, "must be at most " + max + " characters");
        } else if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            problems.put(field, "must be plain text (the characters < and > are not allowed)");
        } else if (value.chars().anyMatch(Character::isISOControl)) {
            problems.put(field, "must be a single line");
        }
    }
}
