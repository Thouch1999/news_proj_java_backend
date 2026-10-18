package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.response.PaginationResponse;
import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.response.PublicCategorySectionDto;
import com.api.v1.bearerauth.model.response.PublicNewsDto;
import com.api.v1.bearerauth.model.response.PublicNewsSummaryDto;
import com.api.v1.bearerauth.service.PublicNewsService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// Public reading API: no login needed (see SecurityConfig). Only PUBLISHED articles are ever returned.
@RestController
@RequestMapping("/api/v1/public/news")
@RequiredArgsConstructor
public class PublicNewsController {
    private final PublicNewsService publicNewsService;

    // Newest first. Optional filters: keyword (title, summary, content), category slug, tag slug,
    // from / to (publish dates, yyyy-MM-dd, both included). Without `size` the page size is the setting news_per_page.
    @GetMapping
    public ResponseEntity<PaginationResponse<List<PublicNewsSummaryDto>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                ApiResponseUtil.pagination(HttpStatus.OK, publicNewsService.list(keyword, category, tag, from, to, page, size))
        );
    }

    @GetMapping("/latest")
    public ResponseEntity<SuccessResponse<List<PublicNewsSummaryDto>>> latest(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, publicNewsService.latest(limit))
        );
    }

    @GetMapping("/most-viewed")
    public ResponseEntity<SuccessResponse<List<PublicNewsSummaryDto>>> mostViewed(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, publicNewsService.mostViewed(limit))
        );
    }

    // Home page sections: every active category that has news, with its newest articles.
    @GetMapping("/category-sections")
    public ResponseEntity<SuccessResponse<List<PublicCategorySectionDto>>> categorySections(@RequestParam(defaultValue = "4") int perCategory) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, publicNewsService.categorySections(perCategory))
        );
    }

    // Opening an article records a view (see PublicNewsService.getBySlug).
    @GetMapping("/{slug}")
    public ResponseEntity<SuccessResponse<PublicNewsDto>> getBySlug(@PathVariable String slug, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, publicNewsService.getBySlug(slug, request.getRemoteAddr()))
        );
    }
}
