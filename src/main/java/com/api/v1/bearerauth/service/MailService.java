package com.api.v1.bearerauth.service;

public interface MailService {
    void sendOtpEmail(String to, String otpCode);
}
