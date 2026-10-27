package com.api.v1.newssystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The rules of the sponsor popup that the page follows: the sponsor's own, or the site defaults. Always complete (every field
 * has a value) and always inside the safety limits; the fields the trigger does not use are still filled, the page ignores them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PopupRulesDto {
    // DELAY, SCROLL or FIRST_OF_BOTH
    private String trigger;
    private int delaySeconds;
    private int scrollPercent;
    // PER_VISIT, PER_DAY or EVERY_N_DAYS
    private String frequency;
    private int frequencyDays;
}
