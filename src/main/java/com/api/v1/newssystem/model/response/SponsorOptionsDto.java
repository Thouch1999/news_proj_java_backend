package com.api.v1.newssystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What the admin form needs to explain itself: the current site defaults of the popup and the limits it enforces. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SponsorOptionsDto {
    private PopupRulesDto popupDefaults;
    // the display settings of the sidebar slideshow (the form needs the layout: a logo is wanted for LOGOS_4)
    private SidebarSettingsDto sidebar;
    private Limits limits;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Limits {
        private int popupDelayMin;
        private int popupDelayMax;
        private int popupScrollMin;
        private int popupScrollMax;
        private int popupFrequencyDaysMin;
        private int popupFrequencyDaysMax;
        private long imageMaxBytes;
        private long videoMaxBytes;
        private int videoMaxSeconds;
    }
}
