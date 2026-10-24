package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.common.response.SuccessResponse;
import com.api.v1.newssystem.model.request.CategoryRequest;
import com.api.v1.newssystem.model.response.CategoryDto;
import com.api.v1.newssystem.service.CategoryService;
import com.api.v1.newssystem.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    // ---- public ----

    @GetMapping
    public ResponseEntity<SuccessResponse<List<CategoryDto>>> list() {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, categoryService.findActive())
        );
    }

    @GetMapping("/{slug}")
    public ResponseEntity<SuccessResponse<CategoryDto>> get(@PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, categoryService.findBySlug(slug))
        );
    }

    // ---- admin only (enforced in SecurityConfig) ----

    @GetMapping("/admin")
    public ResponseEntity<SuccessResponse<List<CategoryDto>>> listForAdmin() {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, categoryService.findAllForAdmin())
        );
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<CategoryDto>> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.CREATED, categoryService.create(request))
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<CategoryDto>> update(@PathVariable Long id,
                                                               @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, categoryService.update(id, request))
        );
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<SuccessResponse<CategoryDto>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, categoryService.setActive(id, true))
        );
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<SuccessResponse<CategoryDto>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, categoryService.setActive(id, false))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse<Void>> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }
}
