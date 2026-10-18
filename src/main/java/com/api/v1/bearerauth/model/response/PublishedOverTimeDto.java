package com.api.v1.bearerauth.model.response;

import com.api.v1.bearerauth.model.enumerate.ReportInterval;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

// Articles published per day or per month. Every day / month of the range has a point, with 0 when nothing was published.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublishedOverTimeDto {
    private ReportInterval interval;
    private LocalDate from;
    private LocalDate to;
    private long total;
    private List<TimeSeriesPointDto> points;
}
