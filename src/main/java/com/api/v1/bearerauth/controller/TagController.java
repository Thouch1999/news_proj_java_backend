package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.request.TagRequest;
import com.api.v1.bearerauth.model.response.TagDto;
import com.api.v1.bearerauth.service.TagService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;

    // ---- public ----

    @GetMapping
    public ResponseEntity<SuccessResponse<List<TagDto>>> list() {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, tagService.findAll())
        );
    }

    @GetMapping("/{slug}")
    public ResponseEntity<SuccessResponse<TagDto>> get(@PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, tagService.findBySlug(slug))
        );
    }

    // ---- admin only (enforced in SecurityConfig) ----

    @PostMapping
    public ResponseEntity<SuccessResponse<TagDto>> create(@Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.CREATED, tagService.create(request))
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<TagDto>> update(@PathVariable Long id,
                                                          @Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, tagService.update(id, request))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse<Void>> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }
}
