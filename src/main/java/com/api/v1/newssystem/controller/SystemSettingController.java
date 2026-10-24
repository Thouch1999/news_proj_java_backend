package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.common.response.SuccessResponse;
import com.api.v1.newssystem.model.request.SettingsUpdateRequest;
import com.api.v1.newssystem.service.SystemSettingService;
import com.api.v1.newssystem.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SystemSettingController {
    private final SystemSettingService systemSettingService;

    // no login: the settings the website and the dashboard need on startup
    @GetMapping("/public")
    public ResponseEntity<SuccessResponse<Map<String, String>>> getPublic() {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, systemSettingService.getPublic())
        );
    }

    // ---- ADMIN only (enforced in SecurityConfig) ----

    @GetMapping
    public ResponseEntity<SuccessResponse<Map<String, Map<String, String>>>> getAll() {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, systemSettingService.getAll())
        );
    }

    @PutMapping("/{group}")
    public ResponseEntity<SuccessResponse<Map<String, String>>> updateGroup(@PathVariable String group,
                                                                            @Valid @RequestBody SettingsUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, systemSettingService.updateGroup(group, request))
        );
    }

    @PostMapping(value = "/images/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<Map<String, String>>> uploadImage(@PathVariable String type,
                                                                            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, systemSettingService.uploadImage(type, file))
        );
    }
}
