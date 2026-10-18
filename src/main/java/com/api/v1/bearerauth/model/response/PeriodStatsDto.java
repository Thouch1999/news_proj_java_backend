package com.api.v1.bearerauth.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// The numbers of one period (both days included). For an AUTHOR, newUsers is 0 and the rest is about their own articles.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeriodStatsDto {
    private long newUsers;
    // articles published in the period
    private long published;
    // views recorded in the period
    private long views;
    // comments written in the period
    private long comments;
}
