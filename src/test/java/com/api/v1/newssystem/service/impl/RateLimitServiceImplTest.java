package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.common.exception.GlobalExceptionHandler;
import com.api.v1.newssystem.common.response.ErrorResponse;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.CacheConfig;
import com.api.v1.newssystem.model.enumerate.RateLimitAction;
import com.api.v1.newssystem.security.ClientIpResolver;
import com.api.v1.newssystem.util.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every limit triggers at the right count and starts again after its window (a controllable clock, small limits). */
class RateLimitServiceImplTest {
    private MutableClock clock;
    private AppProperty properties;
    private RateLimitServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        properties = new AppProperty();
        AppProperty.RateLimit limits = properties.getProtection().getRateLimit();
        limits.setRegister(new AppProperty.Limit(2, 3600));
        limits.setLogin(new AppProperty.Limit(3, 60));
        limits.setOtp(new AppProperty.Limit(2, 3600));
        limits.setCommentPerMinute(new AppProperty.Limit(2, 60));
        limits.setCommentPerDay(new AppProperty.Limit(4, 86400));
        limits.setSearch(new AppProperty.Limit(3, 60));
        service = new RateLimitServiceImpl(new CacheConfig().cacheManager(), properties, new ClientIpResolver(properties), clock);
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

    private CustomException limited(Runnable call) {
        return (CustomException) catchThrowable(call);
    }

    private Throwable catchThrowable(Runnable call) {
        try {
            call.run();
            return null;
        } catch (Throwable e) {
            return e;
        }
    }

    @Test
    void everyActionRefusesTheTryAfterItsLimit_andAnswersRateLimitedWith429() {
        record Case(RateLimitAction action, int max) {
        }
        for (Case c : new Case[]{new Case(RateLimitAction.REGISTER, 2), new Case(RateLimitAction.LOGIN, 3),
                new Case(RateLimitAction.OTP, 2), new Case(RateLimitAction.COMMENT_PER_MINUTE, 2),
                new Case(RateLimitAction.COMMENT_PER_DAY, 4), new Case(RateLimitAction.SEARCH, 3)}) {
            for (int i = 0; i < c.max(); i++) {
                service.check(c.action(), "subject");
            }
            assertThatThrownBy(() -> service.check(c.action(), "subject"))
                    .as(c.action().name())
                    .isInstanceOfSatisfying(CustomException.class, e -> {
                        assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                        assertThat(e.getErrorResponse().getError()).isEqualTo("RATE_LIMITED");
                        assertThat(e.getErrorResponse().getRetryAfterSeconds()).isPositive();
                    });
        }
    }

    @Test
    void theWindowEnds_andTheCounterStartsAgain() {
        service.check(RateLimitAction.SEARCH, "a");
        service.check(RateLimitAction.SEARCH, "a");
        service.check(RateLimitAction.SEARCH, "a");
        assertThatThrownBy(() -> service.check(RateLimitAction.SEARCH, "a")).isInstanceOf(CustomException.class);

        clock.advance(Duration.ofSeconds(59));
        assertThatThrownBy(() -> service.check(RateLimitAction.SEARCH, "a")).isInstanceOf(CustomException.class);

        clock.advance(Duration.ofSeconds(1)); // the 60 seconds are over
        assertThatCode(() -> {
            service.check(RateLimitAction.SEARCH, "a");
            service.check(RateLimitAction.SEARCH, "a");
            service.check(RateLimitAction.SEARCH, "a");
        }).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.check(RateLimitAction.SEARCH, "a")).isInstanceOf(CustomException.class);
    }

    @Test
    void retryAfterSecondsIsTheTimeLeftInTheWindow() {
        service.check(RateLimitAction.COMMENT_PER_MINUTE, "u");
        clock.advance(Duration.ofSeconds(20));
        service.check(RateLimitAction.COMMENT_PER_MINUTE, "u");

        CustomException e = limited(() -> service.check(RateLimitAction.COMMENT_PER_MINUTE, "u"));
        assertThat(e.getErrorResponse().getRetryAfterSeconds()).isEqualTo(40);

        clock.advance(Duration.ofSeconds(15));
        e = limited(() -> service.check(RateLimitAction.COMMENT_PER_MINUTE, "u"));
        assertThat(e.getErrorResponse().getRetryAfterSeconds()).isEqualTo(25);
    }

    @Test
    void subjectsAndActionsAreCountedApart() {
        service.check(RateLimitAction.REGISTER, "1.1.1.1");
        service.check(RateLimitAction.REGISTER, "1.1.1.1");
        assertThatThrownBy(() -> service.check(RateLimitAction.REGISTER, "1.1.1.1")).isInstanceOf(CustomException.class);

        assertThatCode(() -> service.check(RateLimitAction.REGISTER, "2.2.2.2")).doesNotThrowAnyException(); // another address
        assertThatCode(() -> service.check(RateLimitAction.OTP, "1.1.1.1")).doesNotThrowAnyException();      // another action
    }

    @Test
    void theLimitComesFromTheConfiguration() {
        properties.getProtection().getRateLimit().setSearch(new AppProperty.Limit(1, 60));

        service.check(RateLimitAction.SEARCH, "x");
        assertThatThrownBy(() -> service.check(RateLimitAction.SEARCH, "x")).isInstanceOf(CustomException.class);
    }

    @Test
    void checkIp_usesTheAddressOfTheCurrentRequest() {
        requestFrom("10.0.0.1");
        service.checkIp(RateLimitAction.REGISTER);
        service.checkIp(RateLimitAction.REGISTER);
        assertThatThrownBy(() -> service.checkIp(RateLimitAction.REGISTER)).isInstanceOf(CustomException.class);

        requestFrom("10.0.0.2");
        assertThatCode(() -> service.checkIp(RateLimitAction.REGISTER)).doesNotThrowAnyException();
    }

    @Test
    void checkIp_ignoresAForgedForwardedForHeader_whenTheProxyIsNotTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "9.9.9.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        service.checkIp(RateLimitAction.REGISTER);
        service.checkIp(RateLimitAction.REGISTER);

        // a different forged address does not give a new allowance
        MockHttpServletRequest forged = new MockHttpServletRequest();
        forged.setRemoteAddr("10.0.0.1");
        forged.addHeader("X-Forwarded-For", "8.8.8.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(forged));
        assertThatThrownBy(() -> service.checkIp(RateLimitAction.REGISTER)).isInstanceOf(CustomException.class);
    }

    @Test
    void checkIp_usesTheForwardedClient_whenTheProxyIsTrusted() {
        properties.getActivityLog().setTrustedProxies(java.util.List.of("10.0.0.0/8"));
        service = new RateLimitServiceImpl(new CacheConfig().cacheManager(), properties, new ClientIpResolver(properties), clock);

        for (String client : new String[]{"5.5.5.5", "6.6.6.6"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", client);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            service.checkIp(RateLimitAction.REGISTER);
            service.checkIp(RateLimitAction.REGISTER);
            assertThatThrownBy(() -> service.checkIp(RateLimitAction.REGISTER)).isInstanceOf(CustomException.class);
        }
    }

    @Test
    void checkUser_countsPerUser() {
        service.checkUser(RateLimitAction.COMMENT_PER_MINUTE, 1L);
        service.checkUser(RateLimitAction.COMMENT_PER_MINUTE, 1L);
        assertThatThrownBy(() -> service.checkUser(RateLimitAction.COMMENT_PER_MINUTE, 1L)).isInstanceOf(CustomException.class);
        assertThatCode(() -> service.checkUser(RateLimitAction.COMMENT_PER_MINUTE, 2L)).doesNotThrowAnyException();
    }

    @Test
    void theErrorIsInTheProjectFormat_withRetryAfterInTheBodyAndTheHeader() {
        service.check(RateLimitAction.LOGIN, "ip");
        service.check(RateLimitAction.LOGIN, "ip");
        service.check(RateLimitAction.LOGIN, "ip");
        CustomException e = limited(() -> service.check(RateLimitAction.LOGIN, "ip"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler().handleCustom(e, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        ErrorResponse body = response.getBody();
        assertThat(body.getCode()).isEqualTo(429);
        assertThat(body.getError()).isEqualTo("RATE_LIMITED");
        assertThat(body.getMessage()).isEqualTo(MessageConstant.RATE_LIMITED.getDefaultMessage());
        assertThat(body.getRetryAfterSeconds()).isEqualTo(60);
        assertThat(body.getPath()).isEqualTo("/api/v1/auth/login");
    }
}
