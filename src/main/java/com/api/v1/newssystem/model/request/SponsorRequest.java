package com.api.v1.newssystem.model.request;

import com.api.v1.newssystem.model.enumerate.PopupFrequency;
import com.api.v1.newssystem.model.enumerate.PopupTrigger;
import com.api.v1.newssystem.model.enumerate.SponsorLanguage;
import com.api.v1.newssystem.model.enumerate.SponsorPlacement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Create or edit a sponsor. The https address, the plain texts, the weight, the dates and the categories are checked in
 * SponsorValidator (business errors with the field errors in `details`). `active` is read on create only (default off);
 * afterwards use activate / deactivate. startAt and endAt are UTC (for example 2026-11-01T00:00:00Z).
 */
@Data
public class SponsorRequest {
    @NotBlank(message = "Name can not be blank")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @NotBlank(message = "Title can not be blank")
    @Size(max = 150, message = "Title must be at most 150 characters")
    private String title;

    @Size(max = 300, message = "Description must be at most 300 characters")
    private String description;

    @NotBlank(message = "Button text can not be blank")
    @Size(max = 50, message = "Button text must be at most 50 characters")
    private String ctaText;

    @NotBlank(message = "Target URL can not be blank")
    @Size(max = 500, message = "Target URL must be at most 500 characters")
    private String targetUrl;

    private Boolean active;

    private Instant startAt;

    private Instant endAt;

    // 1 to 100, default 10
    private Integer weight;

    // default ALL
    private SponsorLanguage language;

    // where the sponsor is shown: ARTICLE_SIDEBAR and/or ARTICLE_POPUP (the popup is also the phone bar); at least one
    private List<SponsorPlacement> placements;

    // The popup timing. Missing or true: the site defaults decide and the fields below are ignored. False: the sponsor's own
    // rules; the trigger and the frequency are required, the numbers inside the limits (see SponsorValidator).
    private Boolean popupUseDefaults;
    private PopupTrigger popupTrigger;
    private Integer popupDelaySeconds;
    private Integer popupScrollPercent;
    private PopupFrequency popupFrequency;
    private Integer popupFrequencyDays;

    // optional targeting; empty or missing means every category
    private List<Long> categoryIds;
}
