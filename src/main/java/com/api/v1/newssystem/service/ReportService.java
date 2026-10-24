package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.enumerate.ReportInterval;
import com.api.v1.newssystem.model.response.ActivityItemDto;
import com.api.v1.newssystem.model.response.AuthorDashboardDto;
import com.api.v1.newssystem.model.response.CategoryNewsCountDto;
import com.api.v1.newssystem.model.response.DashboardSummaryDto;
import com.api.v1.newssystem.model.response.PublishedOverTimeDto;
import com.api.v1.newssystem.model.response.TopNewsDto;
import com.api.v1.newssystem.model.response.TrendsDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Numbers for dashboards. EDITOR and ADMIN get the whole-site reports, an AUTHOR gets their own dashboard.
 * Everything is computed by aggregate queries (count / sum / group by), never by loading rows.
 * Dates are inclusive days; a date range is "from 00:00 of `from` up to the end of `to`".
 */
public interface ReportService {
    /** EDITOR/ADMIN: users by role, articles by status, total views and comments, hidden comments, unverified users. */
    DashboardSummaryDto summary();

    /** EDITOR/ADMIN: every category with its article count (and how many are published), the largest first. */
    List<CategoryNewsCountDto> newsPerCategory();

    /**
     * EDITOR/ADMIN (whole site) or AUTHOR (own articles only): the PUBLISHED articles with the most views. Without dates it ranks by the article's all-time view
     * counter; with `from` and/or `to` it ranks by the views recorded in that period. `limit` is 1-50.
     */
    List<TopNewsDto> mostViewed(int limit, LocalDate from, LocalDate to);

    /** EDITOR/ADMIN (whole site) or AUTHOR (own articles only): the PUBLISHED articles with the most visible comments (all-time, or written in the period). */
    List<TopNewsDto> mostCommented(int limit, LocalDate from, LocalDate to);

    /**
     * EDITOR/ADMIN (whole site) or AUTHOR (own articles only): articles published per day or per month between `from` and `to` (by publish date, also those
     * archived since). Defaults: `to` is today, `from` is 29 days before (DAY) or 11 months before (MONTH).
     * At most 366 days or 60 months.
     */
    PublishedOverTimeDto publishedOverTime(ReportInterval interval, LocalDate from, LocalDate to);

    /**
     * EDITOR/ADMIN (whole site) or AUTHOR (own articles only): views recorded per day or per month, same range rules
     * and shape as publishedOverTime.
     */
    PublishedOverTimeDto viewsOverTime(ReportInterval interval, LocalDate from, LocalDate to);

    /**
     * EDITOR/ADMIN (whole site) or AUTHOR (own articles only): the numbers of [from, to] and of the period of the same
     * length right before it. Defaults: `to` is today, `from` is 29 days before. At most 366 days.
     */
    TrendsDto trends(LocalDate from, LocalDate to);

    /**
     * EDITOR/ADMIN (whole site) or AUTHOR (own articles only): the latest status changes and comments, newest first.
     * `limit` is 1-50.
     */
    List<ActivityItemDto> recentActivity(int limit);

    /** AUTHOR: the logged-in author's article counts by status, the views of their published articles and their comments. */
    AuthorDashboardDto authorDashboard();
}
