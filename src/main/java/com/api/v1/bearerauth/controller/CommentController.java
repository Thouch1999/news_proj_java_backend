package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.response.PaginationResponse;
import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.enumerate.CommentStatus;
import com.api.v1.bearerauth.model.response.CommentDto;
import com.api.v1.bearerauth.service.CommentService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// One comment by its id: delete (the author, or an editor/admin) and moderation (EDITOR/ADMIN, see SecurityConfig).
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    // EDITOR/ADMIN: all comments with optional filters (status, newsId, userId), newest first.
    @GetMapping
    public ResponseEntity<PaginationResponse<List<CommentDto>>> listAll(@RequestParam(defaultValue = "1") int page,
                                                                        @RequestParam(defaultValue = "10") int size,
                                                                        @RequestParam(required = false) CommentStatus status,
                                                                        @RequestParam(required = false) Long newsId,
                                                                        @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(
                ApiResponseUtil.pagination(HttpStatus.OK, commentService.listAll(status, newsId, userId, page, size))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse<Void>> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    @PutMapping("/{id}/hide")
    public ResponseEntity<SuccessResponse<CommentDto>> hide(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, commentService.hide(id))
        );
    }

    @PutMapping("/{id}/unhide")
    public ResponseEntity<SuccessResponse<CommentDto>> unhide(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, commentService.unhide(id))
        );
    }
}
