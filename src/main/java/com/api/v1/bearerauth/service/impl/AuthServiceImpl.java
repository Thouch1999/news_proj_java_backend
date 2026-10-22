package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.common.response.TokenResponse;
import com.api.v1.bearerauth.model.enumerate.ActivityAction;
import com.api.v1.bearerauth.model.enumerate.ActivityTargetType;
import com.api.v1.bearerauth.service.ActivityLogService;
import com.api.v1.bearerauth.util.ActivityDetails;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.AppUserToken;
import com.api.v1.bearerauth.model.enumerate.OtpPurpose;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.request.AppUserAddRequest;
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
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.security.JwtService;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.service.AppUserTokenService;
import com.api.v1.bearerauth.service.AuthService;
import com.api.v1.bearerauth.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import com.api.v1.bearerauth.service.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AppUserService appUserService;
    private final AppUserTokenService tokenService;
    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;
    private final OtpService otpService;
    private final CurrentUserService currentUserService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ModelMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ActivityLogService activityLogService;
    // for the writes that must be saved together with their activity log entry (the methods themselves are not
    // transactional: a wrong OTP must still count as an attempt)
    private final PlatformTransactionManager transactionManager;

    @Override
    public AppUserDto register(RegisterRequest request) {
        String email = normalize(request.getEmail());
        if (appUserRepository.existsByEmail(email)) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.EMAIL_ALREADY_EXISTS.getDefaultMessage());
        }

        AppUserAddRequest addRequest = new AppUserAddRequest();
        addRequest.setUsername(request.getUsername());
        addRequest.setFullName(request.getFullName());
        addRequest.setAddress(request.getAddress());
        addRequest.setPhone(request.getPhone());
        addRequest.setEmail(email);
        addRequest.setPassword(request.getPassword());
        addRequest.setConfirm(request.getConfirm());
        addRequest.setRole(Roles.READER); // public registration only grants READER

        AppUserDto created = appUserService.create(addRequest); // BCrypt-encodes the password, emailVerified = false
        otpService.sendOtp(email, OtpPurpose.REGISTER);
        return created;
    }

    @Override
    public void verifyEmail(VerifyOtpRequest request) {
        String email = normalize(request.getEmail());
        otpService.verifyOtp(email, request.getOtp(), OtpPurpose.REGISTER);

        AppUser user = findUser(email);
        boolean firstVerification = !user.isEmailVerified();
        user.setEmailVerified(true);
        appUserRepository.save(user);
        otpService.consume(email, OtpPurpose.REGISTER);
        if (firstVerification) {
            eventPublisher.publishEvent(NotificationEvent.newUser(user)); // the admins hear of a verified account, not of every sign-up
        }
    }

    @Override
    public void resendOtp(ResendOtpRequest request) {
        String email = normalize(request.getEmail());
        switch (request.getPurpose()) {
            case REGISTER -> appUserRepository.findByEmail(email)
                    .filter(user -> !user.isEmailVerified())
                    .ifPresent(user -> otpService.sendOtp(email, OtpPurpose.REGISTER));
            case RESET_PASSWORD -> sendResetPasswordOtp(email);
            default -> throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
    }

    @Override
    public LoginDto login(AuthRequest request) {
        String email = normalize(request.getEmail());
        // same error for an unknown email and a wrong password, so accounts cannot be enumerated
        AppUser user = appUserRepository.findByEmail(email)
                .filter(found -> passwordEncoder.matches(request.getPassword(), found.getPassword()))
                .orElse(null);
        if (user == null) {
            failedLogin(request.getEmail(), "invalid credentials");
            throw new CustomException(HttpStatus.UNAUTHORIZED, MessageConstant.INVALID_CREDENTIALS.getDefaultMessage());
        }
        checkCanLoginOrLog(user, request.getEmail());

        if (user.getRole() == Roles.READER) {
            return loginResult(user);
        }
        otpService.sendOtp(email, OtpPurpose.LOGIN); // staff roles need a second factor
        return LoginDto.builder().otpRequired(true).build();
    }

    @Override
    public LoginDto verifyLoginOtp(VerifyOtpRequest request) {
        String email = normalize(request.getEmail());
        try {
            otpService.verifyOtp(email, request.getOtp(), OtpPurpose.LOGIN);
        } catch (CustomException e) {
            failedLogin(request.getEmail(), "invalid otp"); // the code that was typed is never kept
            throw e;
        }

        AppUser user = findUser(email);
        checkCanLoginOrLog(user, request.getEmail());
        otpService.consume(email, OtpPurpose.LOGIN);
        return loginResult(user);
    }

    @Override
    public TokenResponse refresh(RefreshTokenRequest request) {
        String username = jwtService.extractUsername(request.getRefresh());

        UserDetails user = appUserService.loadUserByUsername(username);
        if (!user.isEnabled()) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.ACCOUNT_DISABLED.getDefaultMessage());
        }

        if (!jwtService.validateRefreshToken(request.getRefresh(), user)) {
            throw new CustomException(HttpStatus.UNAUTHORIZED, MessageConstant.INVALID_REFRESH_TOKEN.getDefaultMessage());
        }

        return jwtService.generateRefresh(user);
    }

    @Override
    public void forgotPassword(SendOtpRequest request) {
        sendResetPasswordOtp(normalize(request.getEmail()));
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String email = normalize(request.getEmail());
        confirm(request.getPassword(), request.getConfirm()); // checked first so a typo does not burn the OTP
        otpService.verifyOtp(email, request.getOtp(), OtpPurpose.RESET_PASSWORD);

        AppUser user = findUser(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        savePasswordWithLog(user, "reset");
        otpService.consume(email, OtpPurpose.RESET_PASSWORD);
        revokeRefreshTokens(user);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        confirm(request.getPassword(), request.getConfirm());
        AppUser user = appUserRepository.findById(currentUserService.getCurrentUser().getId())
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.USER_NOT_FOUND.getDefaultMessage()));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.CURRENT_PASSWORD_INCORRECT.getDefaultMessage());
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        savePasswordWithLog(user, "change");
        revokeRefreshTokens(user);
    }

    @Override
    public void logout(String accessToken) {
        appUserService.logout();
        jwtService.revoke(accessToken);
    }

    private void sendResetPasswordOtp(String email) {
        appUserRepository.findByEmail(email)
                .filter(AppUser::isActive)
                .ifPresent(user -> {
                    try {
                        otpService.sendOtp(email, OtpPurpose.RESET_PASSWORD);
                    } catch (CustomException e) {
                        // a cooldown or limit error would reveal that the account exists
                        if (e.getHttpStatus() != HttpStatus.TOO_MANY_REQUESTS) {
                            throw e;
                        }
                    }
                });
    }

    private void checkCanLogin(AppUser user) {
        if (!user.isActive()) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.ACCOUNT_DISABLED.getDefaultMessage());
        }
        // email verification is optional for READER; staff roles must have a verified email
        if (user.getRole() != Roles.READER && !user.isEmailVerified()) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.EMAIL_NOT_VERIFIED.getDefaultMessage());
        }
    }

    // the new password and its PASSWORD_CHANGED entry are saved together (the entry never holds the password)
    private void savePasswordWithLog(AppUser user, String method) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            appUserRepository.save(user);
            activityLogService.recordFor(user, ActivityAction.PASSWORD_CHANGED, ActivityTargetType.AUTH, user.getId(),
                    user.getEmail(), ActivityDetails.of("method", method));
        });
    }

    // a failed login has no transaction: it is written on its own (the typed email is the label; no password is ever read)
    private void failedLogin(String typedEmail, String reason) {
        activityLogService.recordLogin(ActivityAction.LOGIN_FAILED, null, typedEmail, ActivityDetails.of("reason", reason));
    }

    private void checkCanLoginOrLog(AppUser user, String typedEmail) {
        try {
            checkCanLogin(user);
        } catch (CustomException e) {
            failedLogin(typedEmail, user.isActive() ? "email not verified" : "account disabled");
            throw e;
        }
    }

    private LoginDto loginResult(AppUser user) {
        activityLogService.recordLogin(ActivityAction.LOGIN_SUCCESS, user, user.getEmail(), null);
        TokenResponse token = jwtService.generateAccess(user);

        AppUserToken appUserToken = new AppUserToken();
        appUserToken.setRefresh(token.getRefresh());
        appUserToken.setUser(user);
        tokenService.save(appUserToken);

        return LoginDto.builder()
                .token(token)
                .user(mapper.map(user, AppUserDto.class))
                .roles(List.of(user.getRole().name()))
                .build();
    }

    private void revokeRefreshTokens(AppUser user) {
        tokenService.findAllByUser(user).ifPresent(tokenService::deleteAll);
    }

    private AppUser findUser(String email) {
        return appUserRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.USER_NOT_FOUND.getDefaultMessage()));
    }

    private void confirm(String password, String confirm) {
        if (!password.equals(confirm)) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.CONFIRM_PASSWORD_NOT_MATCH.getDefaultMessage());
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
