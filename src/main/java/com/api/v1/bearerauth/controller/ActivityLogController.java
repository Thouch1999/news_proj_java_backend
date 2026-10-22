package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.response.PaginationResponse;
import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.enumerate.ActivityAction;
import com.api.v1.bearerauth.model.enumerate.ActivityTargetType;
import com.api.v1.bearerauth.model.request.ActivityLogFilter;
import com.api.v1.bearerauth.model.response.ActivityLogDto;
import com.api.v1.bearerauth.model.response.RecentActivityDto;
import com.api.v1.bearerauth.service.ActivityLogQueryService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// The activity log (who did what): READ ONLY, so there is no POST, PUT, PATCH or DELETE here and an entry cannot be changed
// through the API. The ADMIN reads everything; the EDITOR only /recent (see SecurityConfig).
@RestController
@RequestMapping("/api/v1/activity-logs")
@RequiredArgsConstructor
public class ActivityLogController {
    private final ActivityLogQueryService activityLogQueryService;

    // Newest first. Filters (all optional): actorId, action (repeat it for several), targetType, targetId, from / to (days,
    // yyyy-MM-dd, both included), search (in the target label and the actor name).
    @GetMapping
    public ResponseEntity<PaginationResponse<List<ActivityLogDto>>> list(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) List<ActivityAction> action,
            @RequestParam(required = false) ActivityTargetType targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActivityLogFilter filter = new ActivityLogFilter(actorId, action, targetType, targetId, from, to, search);
        return ResponseEntity.ok(ApiResponseUtil.pagination(HttpStatus.OK, activityLogQueryService.list(filter, page, size)));
    }

    // The same filters as the list, as a CSV file of at most 10,000 rows (UTF-8 with a BOM).
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) List<ActivityAction> action,
            @RequestParam(required = false) ActivityTargetType targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search) {
        ActivityLogFilter filter = new ActivityLogFilter(actorId, action, targetType, targetId, from, to, search);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity-log.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(activityLogQueryService.exportCsv(filter));
    }

    // The latest actions about articles and comments (the dashboard card "Recent activity"); ADMIN and EDITOR.
    @GetMapping("/recent")
    public ResponseEntity<SuccessResponse<List<RecentActivityDto>>> recent(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, activityLogQueryService.recent(limit)));
    }

    // Every entry about one article, user ... (type NEWS, COMMENT, USER, CATEGORY, TAG, SETTINGS or AUTH), newest first.
    @GetMapping("/target/{type}/{id}")
    public ResponseEntity<PaginationResponse<List<ActivityLogDto>>> history(@PathVariable ActivityTargetType type,
                                                                            @PathVariable Long id,
                                                                            @RequestParam(defaultValue = "1") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponseUtil.pagination(HttpStatus.OK, activityLogQueryService.history(type, id, page, size)));
    }

    // One entry with its details.
    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<ActivityLogDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, activityLogQueryService.get(id)));
    }
}
