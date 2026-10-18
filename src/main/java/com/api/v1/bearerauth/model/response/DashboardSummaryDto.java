package com.api.v1.bearerauth.model.response;

import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.api.v1.bearerauth.model.enumerate.Roles;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

// The numbers of the editor / admin dashboard. Every role and every status is present, with 0 when there is none.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardSummaryDto {
    private long totalUsers;
    private Map<Roles, Long> usersByRole;
    private long totalNews;
    private Map<NewsStatus, Long> newsByStatus;
    // the sum of the view counters of all articles
    private long totalViews;
    // all comments, visible and hidden
    private long totalComments;
    private long hiddenComments;
    // users who have not verified their email yet
    private long unverifiedUsers;
}
