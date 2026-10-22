package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.response.PaginationResponse;
import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.request.AdminCreateUserRequest;
import com.api.v1.bearerauth.model.request.AdminResetPasswordRequest;
import com.api.v1.bearerauth.model.request.ChangeRoleRequest;
import com.api.v1.bearerauth.model.request.TransferSuperAdminRequest;
import com.api.v1.bearerauth.model.request.UpdateProfileRequest;
import com.api.v1.bearerauth.model.response.AppUserDto;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class AppUserController {
    private final AppUserService appUserService;

    // ---- own profile (any logged-in user) ----

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<AppUserDto>> me() {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, appUserService.current())
        );
    }

    @PutMapping("/me")
    public ResponseEntity<SuccessResponse<AppUserDto>> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, appUserService.update(request))
        );
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<AppUserDto>> updateAvatar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, appUserService.updateAvatar(file))
        );
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<SuccessResponse<AppUserDto>> removeAvatar() {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, appUserService.removeAvatar())
        );
    }

    // ---- admin only (enforced in SecurityConfig) ----

    @GetMapping
    public ResponseEntity<PaginationResponse<List<AppUserDto>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Roles role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean emailVerified) {
        return ResponseEntity.ok(
                ApiResponseUtil.pagination(HttpStatus.OK,
                        appUserService.findAll(search, role, active, emailVerified, page, size))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<AppUserDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, appUserService.findById(id))
        );
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<AppUserDto>> create(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.CREATED, appUserService.createStaff(request))
        );
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<SuccessResponse<AppUserDto>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, appUserService.setActive(id, true))
        );
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<SuccessResponse<AppUserDto>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, appUserService.setActive(id, false))
        );
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<SuccessResponse<AppUserDto>> changeRole(@PathVariable Long id,
                                                                  @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, appUserService.changeRole(id, request))
        );
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<SuccessResponse<Void>> resetPassword(@PathVariable Long id,
                                                               @Valid @RequestBody AdminResetPasswordRequest request) {
        appUserService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    // only the super admin may call it: checked in the service (the route is open to every admin)
    @PostMapping("/super-admin/transfer")
    public ResponseEntity<SuccessResponse<AppUserDto>> transferSuperAdmin(@Valid @RequestBody TransferSuperAdminRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, appUserService.transferSuperAdmin(request))
        );
    }
}
