package com.api.v1.newssystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// The period asked for and the period of the same length right before it, for the "vs previous period" trends.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrendsDto {
    private LocalDate from;
    private LocalDate to;
    private LocalDate previousFrom;
    private LocalDate previousTo;
    private PeriodStatsDto current;
    private PeriodStatsDto previous;
}
