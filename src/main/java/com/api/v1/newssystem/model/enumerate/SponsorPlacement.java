package com.api.v1.newssystem.model.enumerate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Where a sponsor creative is shown, with the size it is displayed at (width x height in pixels). The upload should be
 * twice that size for retina screens (the bottom bar excepted). A new placement is added here: the columns are VARCHAR.
 * <p>
 * The admin chooses only ARTICLE_SIDEBAR and ARTICLE_POPUP (see CHOOSABLE). MOBILE_BOTTOM_BAR is how ARTICLE_POPUP is
 * delivered on a phone: it has its own (optional) image but is never chosen, and its statistics count under ARTICLE_POPUP
 * with the device MOBILE (see counted() and device()).
 */
public enum SponsorPlacement {
    ARTICLE_SIDEBAR(300, 250),
    ARTICLE_POPUP(600, 400),
    MOBILE_BOTTOM_BAR(120, 120);

    /** The placements an admin can choose for a sponsor. */
    public static final List<SponsorPlacement> CHOOSABLE = List.of(ARTICLE_SIDEBAR, ARTICLE_POPUP);

    private final int width;
    private final int height;

    SponsorPlacement(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isChoosable() {
        return CHOOSABLE.contains(this);
    }

    /** The placement the sponsor must have chosen to be shown here: the bottom bar is part of the popup. */
    public SponsorPlacement chosen() {
        return this == MOBILE_BOTTOM_BAR ? ARTICLE_POPUP : this;
    }

    /** The placement the statistics of this delivery format are counted under (the same as chosen()). */
    public SponsorPlacement counted() {
        return chosen();
    }

    /** The device the statistics of this delivery format are counted for. */
    public SponsorDevice device() {
        return this == MOBILE_BOTTOM_BAR ? SponsorDevice.MOBILE : SponsorDevice.DESKTOP;
    }

    /** The placement named `text` (any case), or empty. */
    public static Optional<SponsorPlacement> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String name = text.trim();
        return Arrays.stream(values()).filter(placement -> placement.name().equalsIgnoreCase(name)).findFirst();
    }
}
