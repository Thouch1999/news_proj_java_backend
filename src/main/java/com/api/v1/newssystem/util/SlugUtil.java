package com.api.v1.newssystem.util;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@NoArgsConstructor
public final class SlugUtil {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    /**
     * Lower-case ASCII slug: accents are removed and everything that is not a-z or 0-9 becomes a hyphen.
     * Returns an empty string when the text has no usable letters (for example a Khmer-only name).
     */
    public static String slugify(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String ascii = MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
        String slug = NOT_ALPHANUMERIC.matcher(ascii).replaceAll("-");
        return EDGE_HYPHENS.matcher(slug).replaceAll("");
    }

    /**
     * Picks the slug to store. A slug sent by the admin is used as it is and must be free (409 if taken).
     * Otherwise the slug comes from the name, with -2, -3 ... added until it is unique.
     * A name that gives an empty slug needs an explicit slug (400).
     */
    public static String resolve(String requestedSlug, String name, Predicate<String> taken) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            String slug = requestedSlug.trim();
            if (taken.test(slug)) {
                throw new CustomException(HttpStatus.CONFLICT, MessageConstant.SLUG_ALREADY_EXISTS.getDefaultMessage());
            }
            return slug;
        }

        String base = slugify(name);
        if (base.isEmpty()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SLUG_REQUIRED.getDefaultMessage());
        }
        String candidate = base;
        int suffix = 2;
        while (taken.test(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
