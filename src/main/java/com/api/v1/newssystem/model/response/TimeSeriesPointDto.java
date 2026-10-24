package com.api.v1.newssystem.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// One day ("2026-03-05") or one month ("2026-03") of a report, with its count.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeSeriesPointDto {
    private String period;
    private long count;
}
