package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.common.response.SuccessResponse;
import com.api.v1.newssystem.service.BookmarkService;
import com.api.v1.newssystem.util.ApiResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Save / unsave one article for the logged-in user (any role, see SecurityConfig). Both calls can be repeated safely.
@RestController
@RequestMapping("/api/v1/news/{newsId}/bookmark")
@RequiredArgsConstructor
public class NewsBookmarkController {
    private final BookmarkService bookmarkService;

    // 201 when the bookmark was created, 200 when the article was already saved.
    @PostMapping
    public ResponseEntity<SuccessResponse<Void>> add(@PathVariable Long newsId) {
        boolean created = bookmarkService.add(newsId);
        return ResponseEntity.ok(ApiResponseUtil.success(created ? HttpStatus.CREATED : HttpStatus.OK));
    }

    @DeleteMapping
    public ResponseEntity<SuccessResponse<Void>> remove(@PathVariable Long newsId) {
        bookmarkService.remove(newsId);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }
}
