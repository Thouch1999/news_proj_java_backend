package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.CacheConfig;
import com.api.v1.newssystem.security.ClientIpResolver;
import com.api.v1.newssystem.service.LoginLockoutService;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LoginLockoutServiceImpl implements LoginLockoutService {
    private static final int MAX_EMAIL_KEY = 255;

    // the failures since `start`; `lockedUntil` is set when the limit was reached
    private record Attempts(Instant start, int failures, Instant lockedUntil) {
        boolean lockedAt(Instant now) {
            return lockedUntil != null && lockedUntil.isAfter(now);
        }
    }

    private final Cache<Object, Object> attempts;
    private final AppProperty appProperty;
    private final ClientIpResolver clientIpResolver;
    private final Clock clock;

    public LoginLockoutServiceImpl(CacheManager cacheManager, AppProperty appProperty, ClientIpResolver clientIpResolver, Clock clock) {
        this.attempts = ((CaffeineCache) cacheManager.getCache(CacheConfig.LOGIN_ATTEMPT_CACHE)).getNativeCache();
        this.appProperty = appProperty;
        this.clientIpResolver = clientIpResolver;
        this.clock = clock;
    }

    @Override
    public void assertNotLocked(String email) {
        Instant now = clock.instant();
        Instant until = latest(lockedUntil(accountKey(email), now), lockedUntil(ipKey(), now));
        if (until != null) {
            // the same answer for a locked account and a blocked IP address
            throw new CustomException(MessageConstant.ACCOUNT_TEMPORARILY_LOCKED,
                    (int) RateLimitServiceImpl.secondsUntil(until, now));
        }
    }

    @Override
    public Failure recordFailure(String email) {
        AppProperty.Lockout config = appProperty.getProtection().getLockout();
        Counted account = count(accountKey(email), config.getAccountMaxFailures());
        Counted ip = count(ipKey(), config.getIpMaxFailures());
        return new Failure(account.justLocked, ip.justLocked, account.failures, ip.failures);
    }

    @Override
    public void recordSuccess(String email) {
        attempts.invalidate(accountKey(email));
    }

    @Override
    public boolean unlock(String email) {
        boolean wasLocked = isLocked(email);
        attempts.invalidate(accountKey(email));
        return wasLocked;
    }

    @Override
    public boolean isLocked(String email) {
        return lockedUntil(accountKey(email), clock.instant()) != null;
    }

    // ---- helpers ----

    // the result of one count: how many failures there are and whether this one caused the lock
    private static final class Counted {
        int failures;
        boolean justLocked;
    }

    private Counted count(String key, int maxFailures) {
        AppProperty.Lockout config = appProperty.getProtection().getLockout();
        Duration window = Duration.ofSeconds(config.getWindowSeconds());
        Duration lock = Duration.ofSeconds(config.getLockSeconds());
        Instant now = clock.instant();
        AtomicReference<Counted> result = new AtomicReference<>(new Counted());

        // compute is atomic per key: failures at the same time cannot be lost
        attempts.asMap().compute(key, (k, current) -> {
            Attempts state = (Attempts) current;
            Counted counted = result.get();
            if (state != null && state.lockedAt(now)) { // already locked: nothing more to count
                counted.failures = state.failures();
                return state;
            }
            // a window that has ended, or a lock that has ended, starts the count again
            boolean fresh = state == null || state.lockedUntil() != null || !state.start().plus(window).isAfter(now);
            int failures = fresh ? 1 : state.failures() + 1;
            Instant start = fresh ? now : state.start();
            counted.failures = failures;
            if (failures >= maxFailures) {
                counted.justLocked = true;
                return new Attempts(start, failures, now.plus(lock));
            }
            return new Attempts(start, failures, null);
        });
        return result.get();
    }

    private Instant lockedUntil(String key, Instant now) {
        Attempts state = (Attempts) attempts.getIfPresent(key);
        return state != null && state.lockedAt(now) ? state.lockedUntil() : null;
    }

    private static Instant latest(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }

    private static String accountKey(String email) {
        String typed = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return "account|" + (typed.length() > MAX_EMAIL_KEY ? typed.substring(0, MAX_EMAIL_KEY) : typed);
    }

    private String ipKey() {
        String ip = clientIpResolver.current();
        return "ip|" + (ip == null ? "unknown" : ip);
    }
}
