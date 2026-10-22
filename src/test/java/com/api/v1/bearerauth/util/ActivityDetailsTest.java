package com.api.v1.bearerauth.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityDetailsTest {

    @Test
    void secretKeys_areDropped_atEveryLevel() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("refreshToken", "abc");
        inner.put("ok", "yes");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("password", "Secret-123");
        details.put("currentPassword", "Secret-123");
        details.put("otp", "123456");
        details.put("accessToken", "jwt");
        details.put("Authorization", "Bearer x");
        details.put("clientSecret", "s");
        details.put("reason", "Add sources");
        details.put("nested", inner);

        Map<String, Object> clean = ActivityDetails.sanitize(details);

        assertThat(clean).containsOnlyKeys("reason", "nested");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) clean.get("nested");
        assertThat(nested).containsOnlyKeys("ok");
    }

    @Test
    void longTexts_areCut_andOnlyPlainValuesAreKept() {
        Map<String, Object> clean = ActivityDetails.sanitize(ActivityDetails.of(
                "reason", "x".repeat(2000), "count", 3, "flag", true, "list", List.of("a", "b"), "nothing", null));

        assertThat(((String) clean.get("reason")).length()).isEqualTo(ActivityDetails.TEXT_MAX);
        assertThat(clean).containsEntry("count", 3).containsEntry("flag", true).containsEntry("list", List.of("a", "b"));
        assertThat(clean).doesNotContainKey("nothing");
    }

    @Test
    void emptyDetails_becomeNull() {
        assertThat(ActivityDetails.sanitize(null)).isNull();
        assertThat(ActivityDetails.sanitize(Map.of())).isNull();
        assertThat(ActivityDetails.sanitize(Map.of("password", "x"))).isNull();
    }

    @Test
    void changes_listOnlyWhatDiffers_andNeverTheContent() {
        ActivityDetails.Changes changes = new ActivityDetails.Changes()
                .field("title", "Old", "New")
                .field("summary", "same", "same")
                .field("blank", null, "  ")
                .note("content", true);

        Map<String, Object> details = changes.toDetails();

        @SuppressWarnings("unchecked")
        Map<String, Object> list = (Map<String, Object>) details.get("changes");
        assertThat(list).containsOnlyKeys("title", "content");
        assertThat(list.get("content")).isEqualTo("content changed");
        assertThat(list.get("title")).isEqualTo(Map.of("before", "Old", "after", "New"));
        assertThat(new ActivityDetails.Changes().field("a", 1, 1).note("content", false).isEmpty()).isTrue();
    }

    @Test
    void shortFields_areCutAt200Characters() {
        String before = "a".repeat(500);
        Map<String, Object> change = ActivityDetails.change(before, "b");

        assertThat(((String) change.get("before")).length()).isEqualTo(ActivityDetails.SHORT_MAX);
        assertThat(change.get("after")).isEqualTo("b");
    }

    @Test
    void oneLine_removesControlCharacters_andCuts() {
        assertThat(ActivityDetails.oneLine("a\r\nb\tc", 50)).isEqualTo("a b c");
        assertThat(ActivityDetails.oneLine("   ", 50)).isNull();
        assertThat(ActivityDetails.oneLine(null, 50)).isNull();
        assertThat(ActivityDetails.oneLine("x".repeat(300), 255).length()).isEqualTo(255);
    }
}
