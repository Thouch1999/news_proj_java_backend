package com.api.v1.newssystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** The totals of every sponsor between two days (UTC, both included). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SponsorSummaryDto {
    private LocalDate from;
    private LocalDate to;
    private SponsorStatsDto.Totals totals;
    private List<SponsorTotals> sponsors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SponsorTotals {
        private Long sponsorId;
        private String sponsorName;
        private long impressions;
        private long clicks;
        private double clickRate;
        private long videoPlays;
        private long videoCompletes;
        private double completionRate;
    }
}
