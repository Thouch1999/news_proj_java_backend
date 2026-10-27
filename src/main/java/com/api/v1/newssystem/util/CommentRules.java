package com.api.v1.newssystem.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The text rules of the comment protection: how many links a comment has, whether two comments are the same, and
 * whether a comment holds a blocked word. Pure functions, so they are easy to test.
 */
public final class CommentRules {
    // a link: http(s)://..., www..., a bare domain with a common spam TLD (optionally with a path) or a known short link
    private static final Pattern LINK = Pattern.compile(
            "(?i)(?:https?://|www\\.)\\S+"
                    + "|\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:com|net|org|info|biz|xyz|top|site|online|link|click|shop|club|vip)\\b(?:/\\S*)?"
                    + "|\\b(?:bit\\.ly|t\\.co|tinyurl\\.com|t\\.me)/\\S*");
    // zero-width characters: Khmer text often has U+200B between words, and spammers use them to hide a word
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final int MAX_WORD_IN_NOTE = 100;

    private CommentRules() {
    }

    public static int countLinks(String content) {
        int links = 0;
        var matcher = LINK.matcher(content == null ? "" : content);
        while (matcher.find()) {
            links++;
        }
        return links;
    }

    /** True when both texts are the same after trimming, joining white space and ignoring case and invisible characters. */
    public static boolean isSameText(String a, String b) {
        return a != null && b != null && compact(a).equals(compact(b));
    }

    /** The blocked words of the setting: one per line, trimmed, blank lines dropped. */
    public static List<String> parseBlockedWords(String setting) {
        List<String> words = new ArrayList<>();
        if (setting != null) {
            for (String line : setting.split("\\R")) {
                String word = line.trim();
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
        }
        return words;
    }

    /**
     * The first blocked word (as written in the setting, cut for the note) that the comment contains, or null. It is a
     * plain substring match ignoring case: Khmer has no spaces between words, so a match by whole word would not work.
     */
    public static String findBlockedWord(String content, List<String> blockedWords) {
        if (content == null || blockedWords == null) {
            return null;
        }
        String text = compact(content);
        for (String word : blockedWords) {
            String needle = compact(word);
            if (!needle.isEmpty() && text.contains(needle)) {
                return word.length() > MAX_WORD_IN_NOTE ? word.substring(0, MAX_WORD_IN_NOTE) : word;
            }
        }
        return null;
    }

    private static String compact(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        normalized = INVISIBLE.matcher(normalized).replaceAll("");
        return SPACES.matcher(normalized).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }
}
