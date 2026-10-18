package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.common.response.TokenResponse;
import com.api.v1.bearerauth.config.MapperConfig;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.enumerate.OtpPurpose;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.request.AppUserAddRequest;
import com.api.v1.bearerauth.model.request.AuthRequest;
import com.api.v1.bearerauth.model.request.RefreshTokenRequest;
import com.api.v1.bearerauth.model.request.RegisterRequest;
import com.api.v1.bearerauth.model.request.ResendOtpRequest;
import com.api.v1.bearerauth.model.request.ResetPasswordRequest;
import com.api.v1.bearerauth.model.request.SendOtpRequest;
import com.api.v1.bearerauth.model.request.VerifyOtpRequest;
import com.api.v1.bearerauth.model.response.AppUserDto;
import com.api.v1.bearerauth.model.response.LoginDto;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.security.JwtService;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.service.AppUserTokenService;
import com.api.v1.bearerauth.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {
    private static final String EMAIL = "user@news.local";
    private static final String PASSWORD = "Password-123";

    @Mock
    private AppUserService appUserService;
    @Mock
    private AppUserTokenService tokenService;
    @Mock
    private JwtService jwtService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private OtpService otpService;
    @Mock
    private CurrentUserService currentUserService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(appUserService, tokenService, jwtService, appUserRepository, otpService,
                currentUserService, passwordEncoder, new MapperConfig().modelMapper());
    }

    private AppUser user(Roles role) {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("Test User");
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        return user;
    }

    private AuthRequest loginRequest(String password) {
        AuthRequest request = new AuthRequest();
        request.setEmail(" User@News.local ");
        request.setPassword(password);
        return request;
    }

    private VerifyOtpRequest otpRequest() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail(EMAIL);
        request.setOtp("123456");
        return request;
    }

    private void assertFails(Runnable call, MessageConstant expected, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(expected.getDefaultMessage());
                    assertThat(e.getHttpStatus()).isEqualTo(status);
                });
    }

    private void tokensAreIssued() {
        when(jwtService.generateAccess(any())).thenReturn(TokenResponse.builder().access("access").refresh("refresh").build());
    }

    @Test
    void registerThenVerifyEmail_createsUnverifiedReader_sendsOtp_andVerifies() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("New Reader");
        request.setFullName("New Reader Full");
        request.setAddress("Phnom Penh");
        request.setPhone("012345678");
        request.setEmail(" New@News.local ");
        request.setPassword(PASSWORD);
        request.setConfirm(PASSWORD);
        when(appUserRepository.existsByEmail("new@news.local")).thenReturn(false);
        when(appUserService.create(any())).thenReturn(new AppUserDto());

        authService.register(request);

        ArgumentCaptor<AppUserAddRequest> created = ArgumentCaptor.forClass(AppUserAddRequest.class);
        verify(appUserService).create(created.capture());
        assertThat(created.getValue().getRole()).isEqualTo(Roles.READER);
        assertThat(created.getValue().getFullName()).isEqualTo("New Reader Full");
        assertThat(created.getValue().getEmail()).isEqualTo("new@news.local");
        verify(otpService).sendOtp("new@news.local", OtpPurpose.REGISTER);

        AppUser saved = user(Roles.READER);
        saved.setEmailVerified(false);
        saved.setEmail("new@news.local");
        when(appUserRepository.findByEmail("new@news.local")).thenReturn(Optional.of(saved));
        VerifyOtpRequest verify = new VerifyOtpRequest();
        verify.setEmail("new@news.local");
        verify.setOtp("123456");

        authService.verifyEmail(verify);

        assertThat(saved.isEmailVerified()).isTrue();
        verify(otpService).verifyOtp("new@news.local", "123456", OtpPurpose.REGISTER);
        verify(otpService).consume("new@news.local", OtpPurpose.REGISTER);
    }

    @Test
    void register_duplicateEmail_isRejected() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(EMAIL);
        when(appUserRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertFails(() -> authService.register(request), MessageConstant.EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT);

        verify(appUserService, never()).create(any());
        verify(otpService, never()).sendOtp(anyString(), any());
    }

    @Test
    void login_unverifiedStaff_isRejected() {
        AppUser unverified = user(Roles.EDITOR);
        unverified.setEmailVerified(false);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverified));

        assertFails(() -> authService.login(loginRequest(PASSWORD)), MessageConstant.EMAIL_NOT_VERIFIED, HttpStatus.FORBIDDEN);

        verify(jwtService, never()).generateAccess(any());
    }

    @Test
    void login_unverifiedReader_isAllowed() {
        AppUser unverified = user(Roles.READER);
        unverified.setEmailVerified(false);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverified));
        tokensAreIssued();

        LoginDto result = authService.login(loginRequest(PASSWORD));

        assertThat(result.getToken().getAccess()).isEqualTo("access");
        assertThat(result.getUser().isEmailVerified()).isFalse();
        verify(otpService, never()).sendOtp(anyString(), any());
    }

    @Test
    void login_wrongPasswordOrUnknownEmail_isRejectedWithSameError() {
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(Roles.READER)));

        assertFails(() -> authService.login(loginRequest("Wrong-Password1")), MessageConstant.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);

        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertFails(() -> authService.login(loginRequest(PASSWORD)), MessageConstant.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);

        verify(jwtService, never()).generateAccess(any());
    }

    @Test
    void login_inactiveUser_isRejected() {
        AppUser inactive = user(Roles.READER);
        inactive.setActive(false);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(inactive));

        assertFails(() -> authService.login(loginRequest(PASSWORD)), MessageConstant.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN);

        verify(jwtService, never()).generateAccess(any());
    }

    @Test
    void refresh_deactivatedUser_isRejected() {
        AppUser inactive = user(Roles.READER);
        inactive.setActive(false);
        RefreshTokenRequest request = new RefreshTokenRequest("refresh");
        when(jwtService.extractUsername("refresh")).thenReturn(EMAIL);
        when(appUserService.loadUserByUsername(EMAIL)).thenReturn(inactive);

        assertFails(() -> authService.refresh(request), MessageConstant.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN);

        verify(jwtService, never()).generateRefresh(any());
    }

    @Test
    void login_reader_getsTokenWithoutOtp() {
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(Roles.READER)));
        tokensAreIssued();

        LoginDto result = authService.login(loginRequest(PASSWORD));

        assertThat(result.isOtpRequired()).isFalse();
        assertThat(result.getToken().getAccess()).isEqualTo("access");
        assertThat(result.getRoles()).containsExactly("READER");
        verify(otpService, never()).sendOtp(anyString(), any());
        verify(tokenService).save(any());
    }

    @Test
    void login_staff_needsLoginOtp_thenVerifyReturnsTokenAndRoles() {
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(Roles.EDITOR)));

        LoginDto first = authService.login(loginRequest(PASSWORD));

        assertThat(first.isOtpRequired()).isTrue();
        assertThat(first.getToken()).isNull();
        verify(otpService).sendOtp(EMAIL, OtpPurpose.LOGIN);
        verify(jwtService, never()).generateAccess(any());

        tokensAreIssued();
        LoginDto second = authService.verifyLoginOtp(otpRequest());

        verify(otpService).verifyOtp(EMAIL, "123456", OtpPurpose.LOGIN);
        verify(otpService).consume(EMAIL, OtpPurpose.LOGIN);
        assertThat(second.isOtpRequired()).isFalse();
        assertThat(second.getToken().getAccess()).isEqualTo("access");
        assertThat(second.getRoles()).containsExactly("EDITOR");
        assertThat(second.getUser().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void resetPassword_verifiesOtp_setsNewHashedPassword_andRevokesTokens() {
        AppUser existing = user(Roles.READER);
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(EMAIL);
        request.setOtp("123456");
        request.setPassword("New-Password-9");
        request.setConfirm("New-Password-9");

        authService.resetPassword(request);

        verify(otpService).verifyOtp(EMAIL, "123456", OtpPurpose.RESET_PASSWORD);
        verify(otpService).consume(EMAIL, OtpPurpose.RESET_PASSWORD);
        assertThat(passwordEncoder.matches("New-Password-9", existing.getPassword())).isTrue();
        verify(appUserRepository).save(existing);
        verify(tokenService).findAllByUser(existing);
    }

    @Test
    void resetPassword_confirmMismatch_doesNotBurnTheOtp() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(EMAIL);
        request.setOtp("123456");
        request.setPassword("New-Password-9");
        request.setConfirm("Different-9");

        assertFails(() -> authService.resetPassword(request), MessageConstant.CONFIRM_PASSWORD_NOT_MATCH, HttpStatus.BAD_REQUEST);

        verify(otpService, never()).verifyOtp(anyString(), anyString(), any());
    }

    @Test
    void forgotPassword_unknownEmail_succeedsSilentlyWithoutSendingOtp() {
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail(EMAIL);

        authService.forgotPassword(request);

        verify(otpService, never()).sendOtp(anyString(), any());
    }

    @Test
    void forgotPassword_throttledOtp_isSwallowedSoTheAccountIsNotRevealed() {
        when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(Roles.READER)));
        doThrow(new CustomException(HttpStatus.TOO_MANY_REQUESTS, MessageConstant.OTP_RESEND_COOLDOWN.getDefaultMessage()))
                .when(otpService).sendOtp(EMAIL, OtpPurpose.RESET_PASSWORD);
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail(EMAIL);

        authService.forgotPassword(request);

        verify(otpService).sendOtp(EMAIL, OtpPurpose.RESET_PASSWORD);
    }

    @Test
    void logout_deletesTheRefreshTokens_andRevokesTheGivenAccessToken() {
        authService.logout("Bearer abc");

        verify(appUserService).logout();
        verify(jwtService).revoke("Bearer abc");
    }

    @Test
    void resendOtp_loginPurpose_isRejected() {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setEmail(EMAIL);
        request.setPurpose(OtpPurpose.LOGIN);

        assertFails(() -> authService.resendOtp(request), MessageConstant.INVALID_PARAMETER, HttpStatus.BAD_REQUEST);

        verify(otpService, never()).sendOtp(anyString(), any());
    }
}
