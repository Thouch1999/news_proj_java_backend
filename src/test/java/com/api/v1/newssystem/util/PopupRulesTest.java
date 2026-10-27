package com.api.v1.newssystem.util;

import com.api.v1.newssystem.model.enumerate.PopupFrequency;
import com.api.v1.newssystem.model.enumerate.PopupTrigger;
import com.api.v1.newssystem.model.response.PopupRulesDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PopupRulesTest {
    private final PopupRulesDto defaults = PopupRules.defaults("FIRST_OF_BOTH", "15", "50", "PER_DAY", "3");

    @Test
    void theDefaults_comeFromTheSettings() {
        assertThat(defaults).isEqualTo(new PopupRulesDto("FIRST_OF_BOTH", 15, 50, "PER_DAY", 3));
    }

    @Test
    void anOldOrBrokenSetting_isMovedInsideTheLimits_orReplacedByTheDefault() {
        // 0 seconds and 100 percent were allowed before the limits: they are clamped when delivered
        assertThat(PopupRules.defaults("DELAY", "0", "100", "EVERY_N_DAYS", "99"))
                .isEqualTo(new PopupRulesDto("DELAY", 5, 90, "EVERY_N_DAYS", 30));
        assertThat(PopupRules.defaults("DELAY", "999", "1", "PER_VISIT", "0"))
                .isEqualTo(new PopupRulesDto("DELAY", 120, 20, "PER_VISIT", 1));
        assertThat(PopupRules.defaults("sometimes", "abc", null, "", " "))
                .isEqualTo(new PopupRulesDto("FIRST_OF_BOTH", 15, 50, "PER_DAY", 3));
    }

    @Test
    void aSponsorThatUsesTheDefaults_getsTheSiteDefaults_whateverItStored() {
        PopupRulesDto rules = PopupRules.effective(true, PopupTrigger.SCROLL, 99, 99, PopupFrequency.PER_VISIT, 9, defaults);

        assertThat(rules).isSameAs(defaults);
    }

    @Test
    void aSponsorWithItsOwnRules_getsThem_andTheDefaultsFillWhatItDoesNotSet() {
        PopupRulesDto own = PopupRules.effective(false, PopupTrigger.DELAY, 30, null, PopupFrequency.EVERY_N_DAYS, 3, defaults);

        assertThat(own).isEqualTo(new PopupRulesDto("DELAY", 30, 50, "EVERY_N_DAYS", 3));
        assertThat(PopupRules.effective(false, null, null, 60, null, null, defaults))
                .isEqualTo(new PopupRulesDto("FIRST_OF_BOTH", 15, 60, "PER_DAY", 3));
    }

    @Test
    void aStoredValueOutsideTheLimits_isClampedWhenDelivered() {
        PopupRulesDto rules = PopupRules.effective(false, PopupTrigger.FIRST_OF_BOTH, 1, 95, PopupFrequency.EVERY_N_DAYS, 400, defaults);

        assertThat(rules.getDelaySeconds()).isEqualTo(5);
        assertThat(rules.getScrollPercent()).isEqualTo(90);
        assertThat(rules.getFrequencyDays()).isEqualTo(30);
    }
}
