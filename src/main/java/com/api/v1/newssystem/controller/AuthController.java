package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.common.response.SuccessResponse;
import com.api.v1.newssystem.common.response.TokenResponse;
import com.api.v1.newssystem.model.request.AuthRequest;
import com.api.v1.newssystem.model.request.ChangePasswordRequest;
import com.api.v1.newssystem.model.request.RefreshTokenRequest;
import com.api.v1.newssystem.model.request.RegisterRequest;
import com.api.v1.newssystem.model.request.ResendOtpRequest;
import com.api.v1.newssystem.model.request.ResetPasswordRequest;
import com.api.v1.newssystem.model.request.SendOtpRequest;
import com.api.v1.newssystem.model.request.VerifyOtpRequest;
import com.api.v1.newssystem.model.response.AppUserDto;
import com.api.v1.newssystem.model.response.LoginDto;
import com.api.v1.newssystem.service.AuthService;
import com.api.v1.newssystem.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<SuccessResponse<AppUserDto>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.CREATED, authService.register(request))
        );
    }

    @PostMapping("/verify-email")
    public ResponseEntity<SuccessResponse<Void>> verifyEmail(@Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<SuccessResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    @PostMapping("/login")
    public ResponseEntity<SuccessResponse<LoginDto>> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, authService.login(request))
        );
    }

    @PostMapping("/login/verify-otp")
    public ResponseEntity<SuccessResponse<LoginDto>> verifyLoginOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, authService.verifyLoginOtp(request))
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, authService.refresh(request))
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<SuccessResponse<Void>> forgotPassword(@Valid @RequestBody SendOtpRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<SuccessResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    @PostMapping("/change-password")
    public ResponseEntity<SuccessResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }

    @PostMapping("/logout")
    public ResponseEntity<SuccessResponse<Void>> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authService.logout(authorization);
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK));
    }
}
