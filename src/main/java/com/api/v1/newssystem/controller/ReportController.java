package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.common.response.SuccessResponse;
import com.api.v1.newssystem.model.enumerate.ReportInterval;
import com.api.v1.newssystem.model.response.ActivityItemDto;
import com.api.v1.newssystem.model.response.AuthorDashboardDto;
import com.api.v1.newssystem.model.response.CategoryNewsCountDto;
import com.api.v1.newssystem.model.response.DashboardSummaryDto;
import com.api.v1.newssystem.model.response.PublishedOverTimeDto;
import com.api.v1.newssystem.model.response.TopNewsDto;
import com.api.v1.newssystem.model.response.TrendsDto;
import com.api.v1.newssystem.service.ReportService;
import com.api.v1.newssystem.util.ApiResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// Dashboard numbers. EDITOR and ADMIN read the whole-site reports. An AUTHOR reads /author and the lists, series, trends
// and activity, which then cover only their own articles (see SecurityConfig).
// Dates are yyyy-MM-dd and both days are included.
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/summary")
    public ResponseEntity<SuccessResponse<DashboardSummaryDto>> summary() {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, reportService.summary()));
    }

    @GetMapping("/news-by-category")
    public ResponseEntity<SuccessResponse<List<CategoryNewsCountDto>>> newsPerCategory() {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, reportService.newsPerCategory()));
    }

    @GetMapping("/most-viewed")
    public ResponseEntity<SuccessResponse<List<TopNewsDto>>> mostViewed(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, reportService.mostViewed(limit, from, to)));
    }

    @GetMapping("/most-commented")
    public ResponseEntity<SuccessResponse<List<TopNewsDto>>> mostCommented(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, reportService.mostCommented(limit, from, to)));
    }

    @GetMapping("/published")
    public ResponseEntity<SuccessResponse<PublishedOverTimeDto>> publishedOverTime(
            @RequestParam(defaultValue = "DAY") ReportInterval interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, reportService.publishedOverTime(interval, from, to)));
    }

    @GetMapping("/views")
    public ResponseEntity<SuccessResponse<PublishedOverTimeDto>> viewsOverTime(
            @RequestParam(defaultValue = "DAY") ReportInterval interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, reportService.viewsOverTime(interval, from, to)));
    }

    @GetMapping("/trends")
    public ResponseEntity<SuccessResponse<TrendsDto>> trends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, reportService.trends(from, to)));
    }

    @GetMapping("/activity")
    public ResponseEntity<SuccessResponse<List<ActivityItemDto>>> activity(@RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, reportService.recentActivity(limit)));
    }

    @GetMapping("/author")
    public ResponseEntity<SuccessResponse<AuthorDashboardDto>> authorDashboard() {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, reportService.authorDashboard()));
    }
}
