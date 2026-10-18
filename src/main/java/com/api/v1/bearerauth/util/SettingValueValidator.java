package com.api.v1.bearerauth.util;

import com.api.v1.bearerauth.model.enumerate.SettingKey;
import com.api.v1.bearerauth.model.enumerate.SettingType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks and cleans the text value of one system setting. Every setting is plain text: the characters &lt; and &gt;
 * are refused, so no HTML (and no script) can be saved, and the website shows the value as text.
 */
public final class SettingValueValidator {
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s<>]+@[^@\\s<>]+\\.[^@\\s<>]+$");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("^\\d{1,9}$");

    private SettingValueValidator() {
    }

    /** The value as it will be saved: null becomes empty, line breaks are unified, the ends are trimmed. */
    public static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    /** The problem with an already cleaned value (a short sentence for the field errors), or null when it is fine. */
    public static String problem(SettingKey setting, String value) {
        if (value.isEmpty()) {
            return setting.isRequired() ? "is required" : null;
        }
        if (value.length() > setting.getMaxLength()) {
            return "must be at most " + setting.getMaxLength() + " characters";
        }
        if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
            return "must be plain text (the characters < and > are not allowed)";
        }
        if (hasControlCharacters(value, setting.getType() == SettingType.TEXT)) {
            return setting.getType() == SettingType.TEXT ? "contains characters that are not allowed" : "must be a single line";
        }
        String byType = switch (setting.getType()) {
            case STRING, TEXT -> null;
            case NUMBER -> WHOLE_NUMBER.matcher(value).matches() ? null : "must be a whole number";
            case BOOLEAN -> isBoolean(value) ? null : "must be true or false";
            case COLOR -> COLOR.matcher(value).matches() ? null : "must be a color like #1A2B3C";
            case URL -> isHttpsUrl(value) ? null : "must be an https address";
            case IMAGE -> "can only be changed with the image upload (an empty value removes the image)";
        };
        if (byType != null) {
            return byType;
        }
        return switch (setting.getRule()) {
            case NONE -> null;
            case LANGUAGE -> SettingKey.LANGUAGES.contains(value) ? null : "must be one of: " + String.join(", ", SettingKey.LANGUAGES);
            case EMAIL -> EMAIL.matcher(value).matches() ? null : "must be a valid email address";
            case NEWS_PER_PAGE -> {
                int number = Integer.parseInt(value);
                yield number >= SettingKey.NEWS_PER_PAGE_MIN && number <= SettingKey.NEWS_PER_PAGE_MAX
                        ? null
                        : "must be between " + SettingKey.NEWS_PER_PAGE_MIN + " and " + SettingKey.NEWS_PER_PAGE_MAX;
            }
        };
    }

    /** The value in its saved form: a colour in capitals, a boolean in lower case, a number without leading zeros. */
    public static String normalize(SettingKey setting, String value) {
        if (value.isEmpty()) {
            return value;
        }
        return switch (setting.getType()) {
            case COLOR -> value.toUpperCase(Locale.ROOT);
            case BOOLEAN -> value.toLowerCase(Locale.ROOT);
            case NUMBER -> String.valueOf(Integer.parseInt(value));
            default -> value;
        };
    }

    private static boolean isBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
    }

    private static boolean isHttpsUrl(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i)) || Character.isISOControl(value.charAt(i))) {
                return false;
            }
        }
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    // tabs and line breaks are fine in a TEXT value; every other control character is not
    private static boolean hasControlCharacters(String value, boolean allowLineBreaks) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = allowLineBreaks && (c == '\n' || c == '\t');
            if (Character.isISOControl(c) && !allowed) {
                return true;
            }
        }
        return false;
    }
}
