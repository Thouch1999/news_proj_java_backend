package com.api.v1.newssystem.model.enumerate;

import java.util.Arrays;
import java.util.Optional;

/**
 * What a creative shows: a picture, a video file of ours, or a YouTube video. Which type a placement accepts is decided by
 * allowedIn(): the sidebar takes an image or a video file, the popup also a YouTube video, the phone bar only an image.
 * A new type is added here: the column is VARCHAR.
 */
public enum SponsorMediaType {
    IMAGE, VIDEO_FILE, YOUTUBE;

    public boolean allowedIn(SponsorPlacement placement) {
        return switch (placement) {
            case ARTICLE_SIDEBAR -> this == IMAGE || this == VIDEO_FILE;
            case ARTICLE_POPUP -> true;
            case MOBILE_BOTTOM_BAR -> this == IMAGE;
        };
    }

    public boolean isVideo() {
        return this != IMAGE;
    }

    public static Optional<SponsorMediaType> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String name = text.trim();
        return Arrays.stream(values()).filter(type -> type.name().equalsIgnoreCase(name)).findFirst();
    }
}
