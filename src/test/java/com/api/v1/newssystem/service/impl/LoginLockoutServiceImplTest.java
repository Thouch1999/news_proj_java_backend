package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.CacheConfig;
import com.api.v1.newssystem.security.ClientIpResolver;
import com.api.v1.newssystem.service.LoginLockoutService;
import com.api.v1.newssystem.util.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginLockoutServiceImplTest {
    private MutableClock clock;
    private LoginLockoutServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        service = new LoginLockoutServiceImpl(new CacheConfig().cacheManager(), new AppProperty(), new ClientIpResolver(new AppProperty()), clock);
        requestFrom("10.0.0.1");
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

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(email);
        }
    }

    private void assertLocked(String email, int retryAfter) {
        assertThatThrownBy(() -> service.assertNotLocked(email)).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(e.getErrorResponse().getError()).isEqualTo("ACCOUNT_TEMPORARILY_LOCKED");
            assertThat(e.getErrorResponse().getRetryAfterSeconds()).isEqualTo(retryAfter);
        });
    }

    @Test
    void theAccountIsLockedByTheFifthFailure_notBefore() {
        fail("a@news.local", 4);
        assertThatCode(() -> service.assertNotLocked("a@news.local")).doesNotThrowAnyException();

        LoginLockoutService.Failure fifth = service.recordFailure("a@news.local");

        assertThat(fifth.accountLocked()).isTrue();
        assertThat(fifth.ipBlocked()).isFalse();
        assertLocked("a@news.local", 900);
        assertThat(service.isLocked("a@news.local")).isTrue();
    }

    @Test
    void onlyTheFifthFailureReportsTheLock() {
        fail("a@news.local", 4);
        assertThat(service.recordFailure("a@news.local").accountLocked()).isTrue();
        // an attempt while locked is refused before it is counted, so the lock is reported once
        assertThat(service.recordFailure("a@news.local").accountLocked()).isFalse();
    }

    @Test
    void theLockEndsAfterFifteenMinutes_andTheCountStartsAgain() {
        fail("a@news.local", 5);
        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertLocked("a@news.local", 1);

        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> service.assertNotLocked("a@news.local")).doesNotThrowAnyException();
        assertThat(service.isLocked("a@news.local")).isFalse();
        // a new count: four failures are not a lock
        fail("a@news.local", 4);
        assertThatCode(() -> service.assertNotLocked("a@news.local")).doesNotThrowAnyException();
    }

    @Test
    void failuresOlderThanTheWindowAreForgotten() {
        fail("a@news.local", 4);
        clock.advance(Duration.ofMinutes(15));
        fail("a@news.local", 4);

        assertThatCode(() -> service.assertNotLocked("a@news.local")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulLoginResetsTheCounterOfTheAccount() {
        fail("a@news.local", 4);
        service.recordSuccess("a@news.local");
        fail("a@news.local", 4);

        assertThatCode(() -> service.assertNotLocked("a@news.local")).doesNotThrowAnyException();
    }

    @Test
    void theEmailIsComparedWithoutCaseAndSpaces() {
        fail(" A@News.local ", 5);

        assertLocked("a@news.local", 900);
    }

    @Test
    void anotherAccountOnAnotherAddressIsNotAffected() {
        fail("a@news.local", 5);
        requestFrom("10.0.0.2");

        assertThatCode(() -> service.assertNotLocked("b@news.local")).doesNotThrowAnyException();
    }

    @Test
    void unlockLiftsTheLockAndTheCounter_andSaysWhetherItWasLocked() {
        assertThat(service.unlock("a@news.local")).isFalse();
        fail("a@news.local", 5);
        requestFrom("10.0.0.2"); // the account lock is not about the address

        assertThat(service.unlock("a@news.local")).isTrue();

        assertThatCode(() -> service.assertNotLocked("a@news.local")).doesNotThrowAnyException();
        fail("a@news.local", 4); // the counter started again
        assertThatCode(() -> service.assertNotLocked("a@news.local")).doesNotThrowAnyException();
    }

    @Test
    void unknownEmailsCountAgainstTheIpAddress_andTheIpIsBlockedAtTwenty() {
        for (int i = 1; i <= 19; i++) {
            LoginLockoutService.Failure failure = service.recordFailure("nobody" + i + "@news.local");
            assertThat(failure.ipBlocked()).isFalse();
            assertThat(failure.accountLocked()).isFalse();
        }
        assertThat(service.recordFailure("nobody20@news.local").ipBlocked()).isTrue();

        // every account from this address is refused now, even one that never failed
        assertLocked("someone-else@news.local", 900);
        // another address is fine
        requestFrom("10.0.0.2");
        assertThatCode(() -> service.assertNotLocked("someone-else@news.local")).doesNotThrowAnyException();
    }

    @Test
    void theIpBlockEndsAfterFifteenMinutes() {
        fail("x@news.local", 1); // the account counter does not matter here
        for (int i = 1; i <= 20; i++) {
            service.recordFailure("nobody" + i + "@news.local");
        }
        assertLocked("someone@news.local", 900);

        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> service.assertNotLocked("someone@news.local")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessDoesNotResetTheCounterOfTheIpAddress() {
        for (int i = 1; i <= 19; i++) {
            service.recordFailure("nobody" + i + "@news.local");
        }
        service.recordSuccess("real@news.local");

        assertThat(service.recordFailure("nobody20@news.local").ipBlocked()).isTrue();
    }

    @Test
    void theLimitsComeFromTheConfiguration() {
        AppProperty properties = new AppProperty();
        properties.getProtection().getLockout().setAccountMaxFailures(2);
        properties.getProtection().getLockout().setLockSeconds(30);
        service = new LoginLockoutServiceImpl(new CacheConfig().cacheManager(), properties, new ClientIpResolver(properties), clock);

        fail("a@news.local", 2);

        assertLocked("a@news.local", 30);
    }
}
