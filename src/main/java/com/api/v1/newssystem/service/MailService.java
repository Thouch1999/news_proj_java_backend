package com.api.v1.newssystem.service;

public interface MailService {
    void sendOtpEmail(String to, String otpCode);
}
