package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.model.entity.OtpVerification;
import com.api.v1.bearerauth.model.enumerate.OtpPurpose;
import com.api.v1.bearerauth.repository.OtpVerificationRepository;
import com.api.v1.bearerauth.service.MailService;
import com.api.v1.bearerauth.service.OtpService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpVerificationRepository otpRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AppProperty appProperty;

    @Override
    @Transactional
    public void sendOtp(String email, OtpPurpose purpose) {
        String key = normalize(email);
        AppProperty.Otp config = appProperty.getOtp();
        LocalDateTime now = LocalDateTime.now();

        otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(key, purpose).ifPresent(latest -> {
            if (latest.getCreatedAt() != null
                    && latest.getCreatedAt().plusSeconds(config.getResendCooldownSeconds()).isAfter(now)) {
                throw tooManyRequests(MessageConstant.OTP_RESEND_COOLDOWN);
            }
        });
        if (otpRepository.countByEmailAndCreatedAtAfter(key, now.minusHours(1)) >= config.getHourlyLimit()) {
            throw tooManyRequests(MessageConstant.OTP_RESEND_LIMIT_EXCEEDED);
        }

        // only the newest OTP for this email and purpose may be used
        List<OtpVerification> older = otpRepository.findAllByEmailAndPurposeAndConsumedAtIsNull(key, purpose);
        older.forEach(otp -> otp.setConsumedAt(now));
        otpRepository.saveAll(older);

        String code = generateCode(config.getLength());
        OtpVerification otp = new OtpVerification();
        otp.setEmail(key);
        otp.setPurpose(purpose);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setExpiresAt(now.plusSeconds(config.getTtlSeconds()));
        otpRepository.save(otp);

        mailService.sendOtpEmail(key, code); // @Async: returns immediately
    }

    // A wrong code must still persist the attempt counter, so CustomException does not roll back.
    @Override
    @Transactional(dontRollbackOn = CustomException.class)
    public void verifyOtp(String email, String otpCode, OtpPurpose purpose) {
        AppProperty.Otp config = appProperty.getOtp();
        OtpVerification otp = otpRepository
                .findFirstByEmailAndPurposeOrderByCreatedAtDesc(normalize(email), purpose)
                .orElseThrow(() -> badRequest(MessageConstant.OTP_EXPIRED));

        if (otp.getConsumedAt() != null || otp.getVerifiedAt() != null) {
            throw badRequest(MessageConstant.OTP_ALREADY_USED);
        }
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw badRequest(MessageConstant.OTP_EXPIRED);
        }
        if (otp.getAttemptCount() >= config.getMaxAttempts()) {
            throw tooManyRequests(MessageConstant.OTP_ATTEMPTS_EXCEEDED);
        }
        if (otpCode == null || !passwordEncoder.matches(otpCode, otp.getCodeHash())) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            otpRepository.save(otp);
            throw badRequest(MessageConstant.OTP_INVALID);
        }

        otp.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(otp);
    }

    @Override
    public boolean isVerified(String email, OtpPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        long windowMinutes = appProperty.getOtp().getVerifiedTtlMinutes();
        return otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(normalize(email), purpose)
                .filter(otp -> otp.getVerifiedAt() != null
                        && otp.getConsumedAt() == null
                        && otp.getVerifiedAt().plusMinutes(windowMinutes).isAfter(now))
                .isPresent();
    }

    @Override
    @Transactional
    public void consume(String email, OtpPurpose purpose) {
        otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(normalize(email), purpose)
                .filter(otp -> otp.getVerifiedAt() != null && otp.getConsumedAt() == null)
                .ifPresent(otp -> {
                    otp.setConsumedAt(LocalDateTime.now());
                    otpRepository.save(otp);
                });
    }

    private String generateCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }

    private CustomException badRequest(MessageConstant message) {
        return new CustomException(HttpStatus.BAD_REQUEST, message.getDefaultMessage());
    }

    private CustomException tooManyRequests(MessageConstant message) {
        return new CustomException(HttpStatus.TOO_MANY_REQUESTS, message.getDefaultMessage());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
