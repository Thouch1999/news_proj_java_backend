package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.common.response.TokenResponse;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.CacheConfig;
import com.api.v1.newssystem.config.MapperConfig;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.OtpPurpose;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.request.AuthRequest;
import com.api.v1.newssystem.model.request.RegisterRequest;
import com.api.v1.newssystem.model.request.ResendOtpRequest;
import com.api.v1.newssystem.model.request.SendOtpRequest;
import com.api.v1.newssystem.model.request.VerifyOtpRequest;
import com.api.v1.newssystem.model.response.AppUserDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.security.ClientIpResolver;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.security.JwtService;
import com.api.v1.newssystem.service.ActivityLogService;
import com.api.v1.newssystem.service.AppUserService;
import com.api.v1.newssystem.service.AppUserTokenService;
import com.api.v1.newssystem.service.OtpService;
import com.api.v1.newssystem.util.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Login lockout, the per-IP limits and the honeypot of the register request, with the real counters and a controllable clock. */
@ExtendWith(MockitoExtension.class)
class AuthServiceProtectionTest {
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
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final MutableClock clock = new MutableClock();
    private AppProperty properties;
    private AuthServiceImpl authService;
    private AppUser reader;

    @BeforeEach
    void setUp() {
        properties = new AppProperty();
        build();
        requestFrom("10.0.0.1");
        reader = new AppUser();
        reader.setId(1L);
        reader.setUsername("Reader");
        reader.setEmail(EMAIL);
        reader.setPassword(passwordEncoder.encode(PASSWORD));
        reader.setRole(Roles.READER);
        reader.setActive(true);
        reader.setEmailVerified(true);
        lenient().when(appUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(reader));
        lenient().when(jwtService.generateAccess(any())).thenReturn(TokenResponse.builder().access("access").refresh("refresh").build());
    }

    // the services are built again when a test changes the limits (the counters start empty)
    private void build() {
        CacheManager cacheManager = new CacheConfig().cacheManager();
        ClientIpResolver ipResolver = new ClientIpResolver(properties);
        authService = new AuthServiceImpl(appUserService, tokenService, jwtService, appUserRepository, otpService,
                currentUserService, passwordEncoder, new MapperConfig().modelMapper(), eventPublisher, activityLogService,
                transactionManager,
                new RateLimitServiceImpl(cacheManager, properties, ipResolver, clock),
                new LoginLockoutServiceImpl(cacheManager, properties, ipResolver, clock), ipResolver);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void requestFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private AuthRequest login(String email, String password) {
        AuthRequest request = new AuthRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private void wrongPassword(int times) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> authService.login(login(EMAIL, "wrong-password")))
                    .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getMessage())
                            .isEqualTo(MessageConstant.INVALID_CREDENTIALS.getDefaultMessage()));
        }
    }

    private void assertLocked(Runnable call, int retryAfter) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(e.getErrorResponse().getError()).isEqualTo("ACCOUNT_TEMPORARILY_LOCKED");
            assertThat(e.getErrorResponse().getRetryAfterSeconds()).isEqualTo(retryAfter);
        });
    }

    // ---------------- lockout ----------------

    @Test
    void afterFiveFailures_evenTheCorrectPasswordIsRefused_andTheLockIsLogged() {
        wrongPassword(5);

        assertLocked(() -> authService.login(login(EMAIL, PASSWORD)), 900);

        verify(jwtService, never()).generateAccess(any());
        verify(activityLogService, times(1)).recordLogin(eq(ActivityAction.ACCOUNT_LOCKED), isNull(), eq(EMAIL), any());
        verify(activityLogService, never()).recordLogin(eq(ActivityAction.LOGIN_SUCCESS), any(), any(), any());
    }

    @Test
    void fourFailuresDoNotLock() {
        wrongPassword(4);

        assertThat(authService.login(login(EMAIL, PASSWORD)).getToken()).isNotNull();
    }

    @Test
    void theLockExpiresAfterFifteenMinutes() {
        wrongPassword(5);
        clock.advance(Duration.ofMinutes(14).plusSeconds(30));
        assertLocked(() -> authService.login(login(EMAIL, PASSWORD)), 30);

        clock.advance(Duration.ofSeconds(30));

        assertThat(authService.login(login(EMAIL, PASSWORD)).getToken()).isNotNull();
    }

    @Test
    void aSuccessfulLoginResetsTheAccountCounter() {
        wrongPassword(4);
        authService.login(login(EMAIL, PASSWORD));
        wrongPassword(4);

        assertThat(authService.login(login(EMAIL, PASSWORD)).getToken()).isNotNull();
    }

    @Test
    void aLockedAccountIsLockedFromEveryAddress() {
        wrongPassword(5);
        requestFrom("10.0.0.99");

        assertLocked(() -> authService.login(login(EMAIL, PASSWORD)), 900);
    }

    @Test
    void aWrongOtpAtTheSecondStepCountsAsAFailedLogin() {
        reader.setRole(Roles.EDITOR);
        org.mockito.Mockito.doThrow(new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.OTP_INVALID.getDefaultMessage()))
                .when(otpService).verifyOtp(anyString(), anyString(), eq(OtpPurpose.LOGIN));
        VerifyOtpRequest otp = new VerifyOtpRequest();
        otp.setEmail(EMAIL);
        otp.setOtp("000000");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.verifyLoginOtp(otp)).isInstanceOf(CustomException.class);
        }

        assertLocked(() -> authService.login(login(EMAIL, PASSWORD)), 900);
    }

    // ---------------- never reveal whether an account exists ----------------

    @Test
    void unknownEmailsAnswerLikeAWrongPassword_andCountAgainstTheIp() {
        for (int i = 1; i <= 20; i++) {
            String typed = "nobody" + i + "@news.local";
            assertThatThrownBy(() -> authService.login(login(typed, "whatever-1")))
                    .isInstanceOfSatisfying(CustomException.class, e -> {
                        assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(e.getMessage()).isEqualTo(MessageConstant.INVALID_CREDENTIALS.getDefaultMessage());
                    });
        }

        // the address is blocked: a real user with the right password is refused from it
        assertLocked(() -> authService.login(login(EMAIL, PASSWORD)), 900);
        verify(activityLogService, times(1)).recordLogin(eq(ActivityAction.IP_BLOCKED), isNull(), eq("10.0.0.1"), any());
        // no account existed, so no ACCOUNT_LOCKED entry could point at one
        verify(activityLogService, never()).recordLogin(eq(ActivityAction.ACCOUNT_LOCKED), any(), any(), any());

        // another address is fine
        requestFrom("10.0.0.2");
        assertThat(authService.login(login(EMAIL, PASSWORD)).getToken()).isNotNull();
    }

    @Test
    void anUnknownEmailIsLockedLikeARealOne_soTheAnswerDoesNotRevealIt() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(login("ghost@news.local", "whatever-1"))).isInstanceOf(CustomException.class);
        }

        assertLocked(() -> authService.login(login("ghost@news.local", "whatever-1")), 900);
    }

    @Test
    void failuresFromOneAddressSpreadOverManyAccounts_blockTheAddressAtTwenty_notEarlier() {
        for (int i = 1; i <= 19; i++) {
            String typed = "nobody" + i + "@news.local";
            assertThatThrownBy(() -> authService.login(login(typed, "whatever-1"))).isInstanceOf(CustomException.class);
        }

        assertThat(authService.login(login(EMAIL, PASSWORD)).getToken()).isNotNull(); // 19 failures: still open
    }

    // ---------------- rate limits per IP ----------------

    @Test
    void loginIsRateLimitedPerIp() {
        properties.getProtection().getRateLimit().setLogin(new AppProperty.Limit(2, 60));
        build();
        authService.login(login(EMAIL, PASSWORD));
        authService.login(login(EMAIL, PASSWORD));

        assertThatThrownBy(() -> authService.login(login(EMAIL, PASSWORD))).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getErrorResponse().getError()).isEqualTo("RATE_LIMITED");
            assertThat(e.getErrorResponse().getRetryAfterSeconds()).isEqualTo(60);
        });
        clock.advance(Duration.ofSeconds(60));
        assertThatCode(() -> authService.login(login(EMAIL, PASSWORD))).doesNotThrowAnyException();
    }

    private RegisterRequest registration(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("New Reader");
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setConfirm(PASSWORD);
        return request;
    }

    @Test
    void registerIsRateLimitedPerIp_andTheLimitIsNotUsedUpByAnotherAddress() {
        properties.getProtection().getRateLimit().setRegister(new AppProperty.Limit(1, 3600));
        build();
        org.mockito.Mockito.when(appUserService.create(any())).thenReturn(new AppUserDto());
        authService.register(registration("a@news.local"));

        assertThatThrownBy(() -> authService.register(registration("b@news.local")))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorResponse().getError()).isEqualTo("RATE_LIMITED"));
        verify(appUserService, times(1)).create(any());

        requestFrom("10.0.0.2");
        assertThatCode(() -> authService.register(registration("c@news.local"))).doesNotThrowAnyException();
    }

    @Test
    void resendOtpAndForgotPasswordShareTheOtpLimitPerIp() {
        properties.getProtection().getRateLimit().setOtp(new AppProperty.Limit(2, 3600));
        build();
        ResendOtpRequest resend = new ResendOtpRequest();
        resend.setEmail(EMAIL);
        resend.setPurpose(OtpPurpose.RESET_PASSWORD);
        SendOtpRequest forgot = new SendOtpRequest();
        forgot.setEmail(EMAIL);

        authService.resendOtp(resend);
        authService.forgotPassword(forgot);

        assertThatThrownBy(() -> authService.forgotPassword(forgot)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorResponse().getError()).isEqualTo("RATE_LIMITED"));
        assertThatThrownBy(() -> authService.resendOtp(resend)).isInstanceOf(CustomException.class);
        verify(otpService, times(2)).sendOtp(EMAIL, OtpPurpose.RESET_PASSWORD);
    }

    // ---------------- honeypot ----------------

    @Test
    void aFilledHoneypotOnRegisterPretendsSuccess_butSavesAndSendsNothing() {
        RegisterRequest bot = registration(" Bot@News.local ");
        bot.setWebsite("http://spam.example");
        properties.getProtection().getRateLimit().setRegister(new AppProperty.Limit(1, 3600));
        build();

        AppUserDto answer = authService.register(bot);
        authService.register(bot); // does not use the register limit either

        assertThat(answer.getEmail()).isEqualTo("bot@news.local");
        assertThat(answer.getRole()).isEqualTo(Roles.READER);
        assertThat(answer.getId()).isNotNull();
        verify(appUserService, never()).create(any());
        verify(otpService, never()).sendOtp(anyString(), any());
        verify(appUserRepository, never()).save(any());
        verify(appUserRepository, never()).existsByEmail(anyString());
    }
}
