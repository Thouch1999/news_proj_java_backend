package com.api.v1.newssystem.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Helps the services to describe an action for the activity log, and cleans every description before it is saved:
 * a value that could be a secret never reaches the log (keys that look like a password, an OTP, a token ...), a text is
 * cut, and nothing but maps, lists, text, numbers and booleans is kept.
 */
public final class ActivityDetails {
    /** A text in a description (a rejection reason). */
    public static final int TEXT_MAX = 500;
    /** A short field of an edit (a title, a summary, a name) before and after. */
    public static final int SHORT_MAX = 200;
    /** The whole description as JSON; a longer one is replaced by {"truncated":true}. */
    public static final int JSON_MAX = 8000;

    private static final Pattern SECRET_KEY =
            Pattern.compile(".*(password|passwd|otp|token|secret|authorization|cookie|credential).*", Pattern.CASE_INSENSITIVE);
    private static final int MAX_DEPTH = 5;

    private ActivityDetails() {
    }

    /** A description from key/value pairs; a pair whose value is null is left out. */
    public static Map<String, Object> of(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            if (keysAndValues[i + 1] != null) {
                map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
            }
        }
        return map;
    }

    /** {"before": x, "after": y} with short texts. */
    public static Map<String, Object> change(Object before, Object after) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("before", shortValue(before));
        change.put("after", shortValue(after));
        return change;
    }

    /** The changed fields of an edit: {"changes": {"title": {"before": "A", "after": "B"}, "content": "content changed"}}. */
    public static final class Changes {
        private final Map<String, Object> changes = new LinkedHashMap<>();

        /** A short field; recorded only when it really differs. */
        public Changes field(String name, Object before, Object after) {
            if (!Objects.equals(normalize(before), normalize(after))) {
                changes.put(name, change(before, after));
            }
            return this;
        }

        /** A long field (an article's content): only the note, never the text. */
        public Changes note(String name, boolean changed) {
            if (changed) {
                changes.put(name, name + " changed");
            }
            return this;
        }

        public boolean isEmpty() {
            return changes.isEmpty();
        }

        public Map<String, Object> toDetails() {
            return of("changes", changes);
        }

        private static Object normalize(Object value) {
            if (value instanceof String text) {
                return text.isBlank() ? null : text.trim();
            }
            return value;
        }
    }

    /** A copy that is safe to save: no secret keys, short texts, only plain values. */
    public static Map<String, Object> sanitize(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        Map<String, Object> clean = cleanMap(details, 0);
        return clean.isEmpty() ? null : clean;
    }

    public static String shorten(String text, int max) {
        if (text == null) {
            return null;
        }
        if (text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, max - 1)) + "…";
    }

    /** One line of plain text: control characters become spaces, then cut. Used for the labels and the User-Agent. */
    public static String oneLine(String text, int max) {
        if (text == null) {
            return null;
        }
        String clean = text.replaceAll("[\\p{Cntrl}]+", " ").trim();
        return clean.isEmpty() ? null : shorten(clean, max);
    }

    private static Object shortValue(Object value) {
        return value instanceof String text ? shorten(text, SHORT_MAX) : plain(value, 1);
    }

    private static Map<String, Object> cleanMap(Map<?, ?> map, int depth) {
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (SECRET_KEY.matcher(key).matches()) {
                continue;
            }
            Object value = plain(entry.getValue(), depth + 1);
            if (value != null) {
                clean.put(key, value);
            }
        }
        return clean;
    }

    private static Object plain(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            return null;
        }
        if (value instanceof String text) {
            return shorten(text, TEXT_MAX);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return cleanMap(map, depth);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>();
            for (Object item : collection) {
                Object clean = plain(item, depth + 1);
                if (clean != null) {
                    list.add(clean);
                }
            }
            return list;
        }
        return shorten(value.toString(), TEXT_MAX); // an enum, a date
    }
}
