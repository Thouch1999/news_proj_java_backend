package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.model.enumerate.OtpPurpose;

public interface OtpService {
    /**
     * Creates a new OTP (only its hash is stored), invalidates older unused OTPs for the same
     * email and purpose, enforces the resend cooldown and hourly limit, then emails the code.
     */
    void sendOtp(String email, OtpPurpose purpose);

    /** Verifies the latest OTP for the email and purpose and marks it as verified. */
    void verifyOtp(String email, String otpCode, OtpPurpose purpose);

    /** True if the latest OTP was verified, not yet consumed, and is still inside the verified window. */
    boolean isVerified(String email, OtpPurpose purpose);

    /** Marks the verified OTP as consumed once the action it protected has completed. */
    void consume(String email, OtpPurpose purpose);
}
