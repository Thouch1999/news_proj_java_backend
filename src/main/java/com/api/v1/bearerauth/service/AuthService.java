package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.common.response.TokenResponse;
import com.api.v1.bearerauth.model.request.AuthRequest;
import com.api.v1.bearerauth.model.request.ChangePasswordRequest;
import com.api.v1.bearerauth.model.request.RefreshTokenRequest;
import com.api.v1.bearerauth.model.request.RegisterRequest;
import com.api.v1.bearerauth.model.request.ResendOtpRequest;
import com.api.v1.bearerauth.model.request.ResetPasswordRequest;
import com.api.v1.bearerauth.model.request.SendOtpRequest;
import com.api.v1.bearerauth.model.request.VerifyOtpRequest;
import com.api.v1.bearerauth.model.response.AppUserDto;
import com.api.v1.bearerauth.model.response.LoginDto;

public interface AuthService {
    /** Creates a READER with emailVerified = false and sends a REGISTER OTP. */
    AppUserDto register(RegisterRequest request);

    /** Verifies the REGISTER OTP and marks the email as verified. */
    void verifyEmail(VerifyOtpRequest request);

    /** Resends an OTP for REGISTER or RESET_PASSWORD; the response never reveals whether the email exists. */
    void resendOtp(ResendOtpRequest request);

    /** READER gets tokens directly; ADMIN/EDITOR/AUTHOR get otpRequired and a LOGIN OTP. */
    LoginDto login(AuthRequest request);

    /** Verifies the LOGIN OTP and returns tokens, user info and roles. */
    LoginDto verifyLoginOtp(VerifyOtpRequest request);

    TokenResponse refresh(RefreshTokenRequest request);

    /** Sends a RESET_PASSWORD OTP; behaves the same whether or not the email exists. */
    void forgotPassword(SendOtpRequest request);

    void resetPassword(ResetPasswordRequest request);

    void changePassword(ChangePasswordRequest request);

    /** Deletes the user's refresh tokens and refuses the given access token (the Authorization header value) from now on. */
    void logout(String accessToken);
}
