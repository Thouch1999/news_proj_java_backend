package com.api.v1.newssystem.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the video ID out of a YouTube link. Only four forms are accepted: youtube.com/watch?v=ID, youtu.be/ID,
 * youtube.com/shorts/ID and youtube.com/embed/ID, on the exact hosts youtube.com, www.youtube.com, m.youtube.com and youtu.be
 * (so youtube.com.evil.com, notyoutube.com or a link with user info are refused). Only the 11-character ID is ever stored:
 * the page builds the embed address itself.
 */
public final class YouTubeUrl {
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Set<String> HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
    private static final String SHORT_HOST = "youtu.be";

    private YouTubeUrl() {
    }

    /** The 11-character video ID of a valid link, or empty. */
    public static Optional<String> videoId(String text) {
        if (text == null) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(text.strip());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                || host == null || uri.getUserInfo() != null || uri.getPort() != -1) {
            return Optional.empty();
        }
        host = host.toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (host.equals(SHORT_HOST)) {
            return validId(onlySegment(path));
        }
        if (!HOSTS.contains(host)) {
            return Optional.empty();
        }
        if (path.equals("/watch")) {
            return validId(queryValue(uri.getRawQuery(), "v"));
        }
        if (path.startsWith("/shorts/")) {
            return validId(onlySegment(path.substring("/shorts".length())));
        }
        if (path.startsWith("/embed/")) {
            return validId(onlySegment(path.substring("/embed".length())));
        }
        return Optional.empty();
    }

    /** The picture YouTube makes for a video (the page and the admin show it before the video is played). */
    public static String thumbnailUrl(String videoId) {
        return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
    }

    // "/ID" or "/ID/" gives ID; anything with more path after the ID gives null
    private static String onlySegment(String path) {
        if (!path.startsWith("/")) {
            return null;
        }
        String rest = path.substring(1);
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return rest.indexOf('/') >= 0 ? null : rest;
    }

    private static String queryValue(String query, String name) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(name)) {
                return pair.substring(equals + 1);
            }
        }
        return null;
    }

    private static Optional<String> validId(String candidate) {
        return candidate != null && ID.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
    }
}
