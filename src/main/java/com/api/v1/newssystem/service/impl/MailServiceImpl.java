package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.AsyncConfig;
import com.api.v1.newssystem.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final AppProperty appProperty;

    @Value("${spring.mail.username:}")
    private String from;

    @Override
    @Async(AsyncConfig.MAIL_EXECUTOR)
    public void sendOtpEmail(String to, String otpCode) {
        try {
            Context context = new Context();
            context.setVariable("otpCode", otpCode);
            long ttlSeconds = appProperty.getOtp().getTtlSeconds();
            context.setVariable("ttlMinutes", (ttlSeconds + 59) / 60);
            String html = templateEngine.process("mail/otp-email", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            if (!from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setTo(to);
            helper.setSubject("Your verification code");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("OTP email sent to {}", to);
        } catch (MessagingException | RuntimeException e) {
            // runs on a background thread: there is no caller to throw to, so log it
            log.error("Failed to send OTP email to {}", to, e);
        }
    }
}
