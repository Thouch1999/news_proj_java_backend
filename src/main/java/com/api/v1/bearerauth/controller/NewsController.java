package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.response.PaginationResponse;
import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.api.v1.bearerauth.model.request.NewsImageRequest;
import com.api.v1.bearerauth.model.request.NewsRejectRequest;
import com.api.v1.bearerauth.model.request.NewsRequest;
import com.api.v1.bearerauth.model.response.NewsDto;
import com.api.v1.bearerauth.model.response.NewsImageDto;
import com.api.v1.bearerauth.model.response.NewsStatusHistoryDto;
import com.api.v1.bearerauth.model.response.NewsSummaryDto;
import com.api.v1.bearerauth.service.NewsService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Article management and editorial workflow for AUTHOR, EDITOR and ADMIN (enforced in SecurityConfig and in
// NewsService). An author works on their own articles, an editor reviews and edits, an admin does everything.
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {
    private final NewsService newsService;

    @PostMapping
    public ResponseEntity<SuccessResponse<NewsDto>> create(@Valid @RequestBody NewsRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.CREATED, newsService.create(request))
        );
    }

    @GetMapping("/mine")
    public ResponseEntity<PaginationResponse<List<NewsSummaryDto>>> listMine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) NewsStatus status) {
        return ResponseEntity.ok(
                ApiResponseUtil.pagination(HttpStatus.OK, newsService.listMine(status, page, size))
        );
    }

    // Role-scoped: an admin sees all articles (filters: status, authorId, categoryId), an author only their own.
    @GetMapping("/manage")
    public ResponseEntity<PaginationResponse<List<NewsSummaryDto>>> listManaged(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) NewsStatus status,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(
                ApiResponseUtil.pagination(HttpStatus.OK, newsService.listManaged(status, authorId, categoryId, page, size))
        );
    }

    @GetMapping("/manage/{id}")
    public ResponseEntity<SuccessResponse<NewsDto>> getManaged(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.getManaged(id))
        );
    }

    @GetMapping("/mine/{id}")
    public ResponseEntity<SuccessResponse<NewsDto>> getMine(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.getMine(id))
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<NewsDto>> update(@PathVariable Long id,
                                                           @Valid @RequestBody NewsRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.update(id, request))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse<Void>> delete(@PathVariable Long id) {
        newsService.delete(id);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    // ---- editorial workflow ----

    @PostMapping("/{id}/submit")
    public ResponseEntity<SuccessResponse<NewsDto>> submit(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.submit(id))
        );
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<SuccessResponse<NewsDto>> publish(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.publish(id))
        );
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<SuccessResponse<NewsDto>> reject(@PathVariable Long id,
                                                           @Valid @RequestBody NewsRejectRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.reject(id, request.getReason()))
        );
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<SuccessResponse<NewsDto>> archive(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.archive(id))
        );
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<SuccessResponse<NewsDto>> restore(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.restore(id))
        );
    }

    // EDITOR/ADMIN: allow or stop new comments on an article (existing comments stay).
    @PutMapping("/{id}/comments/enable")
    public ResponseEntity<SuccessResponse<NewsDto>> enableComments(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.setCommentsEnabled(id, true))
        );
    }

    @PutMapping("/{id}/comments/disable")
    public ResponseEntity<SuccessResponse<NewsDto>> disableComments(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.setCommentsEnabled(id, false))
        );
    }

    // The editors' review queue: PENDING articles, oldest first.
    @GetMapping("/review")
    public ResponseEntity<PaginationResponse<List<NewsSummaryDto>>> reviewQueue(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                ApiResponseUtil.pagination(HttpStatus.OK, newsService.listReviewQueue(page, size))
        );
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<SuccessResponse<List<NewsStatusHistoryDto>>> history(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.history(id))
        );
    }

    // ---- images ----

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<NewsImageDto>> addImage(@PathVariable Long id,
                                                                  @RequestParam("file") MultipartFile file,
                                                                  @RequestParam(required = false) String caption) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.CREATED, newsService.addImage(id, file, caption))
        );
    }

    @PutMapping("/{id}/images/{imageId}")
    public ResponseEntity<SuccessResponse<NewsImageDto>> updateImage(@PathVariable Long id,
                                                                     @PathVariable Long imageId,
                                                                     @Valid @RequestBody NewsImageRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.updateImage(id, imageId, request))
        );
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<SuccessResponse<Void>> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        newsService.deleteImage(id, imageId);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    @PostMapping(value = "/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<NewsDto>> setThumbnail(@PathVariable Long id,
                                                                 @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.setThumbnail(id, file))
        );
    }

    @DeleteMapping("/{id}/thumbnail")
    public ResponseEntity<SuccessResponse<NewsDto>> removeThumbnail(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, newsService.removeThumbnail(id))
        );
    }
}
