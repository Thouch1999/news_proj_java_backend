package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.common.response.PaginationResponse;
import com.api.v1.newssystem.common.response.SuccessResponse;
import com.api.v1.newssystem.model.enumerate.CommentStatus;
import com.api.v1.newssystem.model.request.CommentRequest;
import com.api.v1.newssystem.model.response.CommentDto;
import com.api.v1.newssystem.service.CommentService;
import com.api.v1.newssystem.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The comments of one article: everybody reads, a logged-in user writes (see SecurityConfig).
@RestController
@RequestMapping("/api/v1/news/{newsId}/comments")
@RequiredArgsConstructor
public class NewsCommentController {
    private final CommentService commentService;

    // Public: VISIBLE comments, newest first.
    @GetMapping
    public ResponseEntity<PaginationResponse<List<CommentDto>>> list(@PathVariable Long newsId,
                                                                     @RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                ApiResponseUtil.pagination(HttpStatus.OK, commentService.listVisible(newsId, page, size))
        );
    }

    // Logged in: only on a PUBLISHED article that has comments enabled.
    @PostMapping
    public ResponseEntity<SuccessResponse<CommentDto>> create(@PathVariable Long newsId,
                                                              @Valid @RequestBody CommentRequest request) {
        CommentDto created = commentService.create(newsId, request);
        // a comment held for review (a blocked word) is accepted, not created yet
        return ResponseEntity.ok(
                ApiResponseUtil.success(created.getStatus() == CommentStatus.HIDDEN ? HttpStatus.ACCEPTED : HttpStatus.CREATED, created)
        );
    }
}
