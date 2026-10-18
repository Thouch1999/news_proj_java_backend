package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.model.entity.OtpVerification;
import com.api.v1.bearerauth.model.enumerate.OtpPurpose;
import com.api.v1.bearerauth.repository.OtpVerificationRepository;
import com.api.v1.bearerauth.service.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {
    private static final String EMAIL = "reader@news.local";
    private static final String CODE = "123456";
    private static final OtpPurpose PURPOSE = OtpPurpose.REGISTER;

    @Mock
    private OtpVerificationRepository otpRepository;
    @Mock
    private MailService mailService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpServiceImpl(otpRepository, passwordEncoder, mailService, new AppProperty());
    }

    private OtpVerification storedOtp() {
        OtpVerification otp = new OtpVerification();
        otp.setEmail(EMAIL);
        otp.setPurpose(PURPOSE);
        otp.setCodeHash(passwordEncoder.encode(CODE));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(3));
        otp.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        return otp;
    }

    private void latestIs(OtpVerification otp) {
        when(otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(EMAIL, PURPOSE)).thenReturn(Optional.of(otp));
    }

    private void assertFails(Runnable call, MessageConstant expected, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(expected.getDefaultMessage());
                    assertThat(e.getHttpStatus()).isEqualTo(status);
                });
    }

    @Test
    void sendOtp_storesOnlyTheHash_setsExpiry_emailsCode_andInvalidatesOlderOtps() {
        OtpVerification older = storedOtp();
        when(otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(EMAIL, PURPOSE)).thenReturn(Optional.empty());
        when(otpRepository.countByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(otpRepository.findAllByEmailAndPurposeAndConsumedAtIsNull(EMAIL, PURPOSE)).thenReturn(List.of(older));

        otpService.sendOtp("  Reader@News.local ", PURPOSE);

        ArgumentCaptor<String> sentCode = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendOtpEmail(anyString(), sentCode.capture());
        ArgumentCaptor<OtpVerification> saved = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpRepository).save(saved.capture());

        assertThat(sentCode.getValue()).matches("\\d{6}");
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getCodeHash()).isNotEqualTo(sentCode.getValue());
        assertThat(passwordEncoder.matches(sentCode.getValue(), saved.getValue().getCodeHash())).isTrue();
        assertThat(saved.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(older.getConsumedAt()).isNotNull();
    }

    @Test
    void verifyOtp_success_marksVerified() {
        OtpVerification otp = storedOtp();
        latestIs(otp);

        otpService.verifyOtp(EMAIL, CODE, PURPOSE);

        assertThat(otp.getVerifiedAt()).isNotNull();
        assertThat(otp.getAttemptCount()).isZero();
        assertThat(otpService.isVerified(EMAIL, PURPOSE)).isTrue();

        otpService.consume(EMAIL, PURPOSE);
        assertThat(otp.getConsumedAt()).isNotNull();
        assertThat(otpService.isVerified(EMAIL, PURPOSE)).isFalse();
    }

    @Test
    void verifyOtp_wrongCode_increasesAttemptCount() {
        OtpVerification otp = storedOtp();
        latestIs(otp);

        assertFails(() -> otpService.verifyOtp(EMAIL, "000000", PURPOSE), MessageConstant.OTP_INVALID, HttpStatus.BAD_REQUEST);

        assertThat(otp.getAttemptCount()).isEqualTo(1);
        assertThat(otp.getVerifiedAt()).isNull();
        verify(otpRepository).save(otp);
    }

    @Test
    void verifyOtp_expired_isRejected() {
        OtpVerification otp = storedOtp();
        otp.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        latestIs(otp);

        assertFails(() -> otpService.verifyOtp(EMAIL, CODE, PURPOSE), MessageConstant.OTP_EXPIRED, HttpStatus.BAD_REQUEST);

        assertThat(otp.getVerifiedAt()).isNull();
    }

    @Test
    void verifyOtp_tooManyAttempts_isRejectedEvenWithCorrectCode() {
        OtpVerification otp = storedOtp();
        otp.setAttemptCount(new AppProperty().getOtp().getMaxAttempts());
        latestIs(otp);

        assertFails(() -> otpService.verifyOtp(EMAIL, CODE, PURPOSE), MessageConstant.OTP_ATTEMPTS_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS);

        assertThat(otp.getVerifiedAt()).isNull();
    }

    @Test
    void verifyOtp_reusedCode_isRejected() {
        OtpVerification otp = storedOtp();
        latestIs(otp);

        otpService.verifyOtp(EMAIL, CODE, PURPOSE);

        assertFails(() -> otpService.verifyOtp(EMAIL, CODE, PURPOSE), MessageConstant.OTP_ALREADY_USED, HttpStatus.BAD_REQUEST);
    }

    @Test
    void sendOtp_withinCooldown_isRejected() {
        OtpVerification recent = storedOtp();
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        latestIs(recent);

        assertFails(() -> otpService.sendOtp(EMAIL, PURPOSE), MessageConstant.OTP_RESEND_COOLDOWN, HttpStatus.TOO_MANY_REQUESTS);

        verify(otpRepository, never()).save(any());
        verify(mailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void sendOtp_overHourlyLimit_isRejected() {
        when(otpRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(EMAIL, PURPOSE)).thenReturn(Optional.empty());
        when(otpRepository.countByEmailAndCreatedAtAfter(anyString(), any()))
                .thenReturn((long) new AppProperty().getOtp().getHourlyLimit());

        assertFails(() -> otpService.sendOtp(EMAIL, PURPOSE), MessageConstant.OTP_RESEND_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS);

        verify(mailService, never()).sendOtpEmail(anyString(), anyString());
    }
}
