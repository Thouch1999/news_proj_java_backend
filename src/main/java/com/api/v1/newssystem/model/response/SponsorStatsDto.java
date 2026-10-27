package com.api.v1.newssystem.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** The impressions, clicks and video plays of one sponsor between two days (UTC, both included). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SponsorStatsDto {
    private Long sponsorId;
    private String sponsorName;
    private LocalDate from;
    private LocalDate to;
    private Totals totals;
    // one point per day of the range (zeros included), all placements together
    private List<Point> byDay;
    // one point per chosen-able placement (ARTICLE_SIDEBAR, ARTICLE_POPUP), the devices added together
    private List<Point> byPlacement;
    // the popup split by device: DESKTOP (the popup) and MOBILE (the bottom bar of a phone)
    private List<Point> byDevice;
    // the stored rows: one per day and placement that had an event
    private List<Point> rows;

    /**
     * clickRate is clicks / impressions in percent, two decimals (0 without impressions); completionRate is
     * videoCompletes / videoPlays in percent, two decimals (0 without plays).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Totals {
        private long impressions;
        private long clicks;
        private double clickRate;
        private long videoPlays;
        private long videoCompletes;
        private double completionRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Point {
        // yyyy-MM-dd (UTC); null in byPlacement
        private String date;
        // null in byDay
        private String placement;
        // DESKTOP or MOBILE; only in byDevice and rows
        private String device;
        private long impressions;
        private long clicks;
        private double clickRate;
        private long videoPlays;
        private long videoCompletes;
        private double completionRate;
    }
}
