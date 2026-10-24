package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.model.request.ActivityLogFilter;
import com.api.v1.newssystem.model.response.ActivityLogDto;
import com.api.v1.newssystem.model.response.RecentActivityDto;
import org.springframework.data.domain.Page;

import java.util.List;

/** Reads the activity log. ADMIN, except recent() which an EDITOR may read too. Page is 1-based; newest first. */
public interface ActivityLogQueryService {
    /** The most rows of one CSV export. */
    int EXPORT_MAX_ROWS = 10_000;

    /** A page of entries (without details) that match the filter. 400 when `from` is after `to`. */
    Page<ActivityLogDto> list(ActivityLogFilter filter, int page, int size);

    /** One entry with its details. 404 when it does not exist. */
    ActivityLogDto get(Long id);

    /** Every entry about one target (one article, one user ...), newest first. */
    Page<ActivityLogDto> history(ActivityTargetType targetType, Long targetId, int page, int size);

    /** The entries that match the filter as CSV (UTF-8 with a BOM), at most EXPORT_MAX_ROWS; safe against CSV injection. */
    byte[] exportCsv(ActivityLogFilter filter);

    /** The latest actions about articles and comments only (no login, user or settings entries). */
    List<RecentActivityDto> recent(int limit);
}
