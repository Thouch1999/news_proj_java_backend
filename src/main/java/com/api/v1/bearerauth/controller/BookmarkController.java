package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.response.PaginationResponse;
import com.api.v1.bearerauth.model.response.BookmarkDto;
import com.api.v1.bearerauth.service.BookmarkService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// My saved news: any logged-in user.
@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {
    private final BookmarkService bookmarkService;

    @GetMapping
    public ResponseEntity<PaginationResponse<List<BookmarkDto>>> list(@RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                ApiResponseUtil.pagination(HttpStatus.OK, bookmarkService.list(page, size))
        );
    }
}
