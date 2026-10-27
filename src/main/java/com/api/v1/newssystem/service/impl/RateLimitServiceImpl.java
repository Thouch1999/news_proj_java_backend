package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.CacheConfig;
import com.api.v1.newssystem.model.enumerate.RateLimitAction;
import com.api.v1.newssystem.security.ClientIpResolver;
import com.api.v1.newssystem.service.RateLimitService;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RateLimitServiceImpl implements RateLimitService {
    // the window starts with the first try; a window that has ended is replaced by a new one
    private record Window(Instant start, int count) {
    }

    private final Cache<Object, Object> counters;
    private final AppProperty appProperty;
    private final ClientIpResolver clientIpResolver;
    private final Clock clock;

    public RateLimitServiceImpl(CacheManager cacheManager, AppProperty appProperty, ClientIpResolver clientIpResolver, Clock clock) {
        this.counters = ((CaffeineCache) cacheManager.getCache(CacheConfig.RATE_LIMIT_CACHE)).getNativeCache();
        this.appProperty = appProperty;
        this.clientIpResolver = clientIpResolver;
        this.clock = clock;
    }

    @Override
    public void check(RateLimitAction action, String subject) {
        AppProperty.Limit limit = limitOf(action);
        Duration window = Duration.ofSeconds(limit.getWindowSeconds());
        Instant now = clock.instant();
        AtomicLong retryAfter = new AtomicLong(0);

        // compute is atomic per key, so two requests at the same time cannot both take the last place
        counters.asMap().compute(action.name() + '|' + subject, (key, current) -> {
            Window state = (Window) current;
            if (state == null || !state.start().plus(window).isAfter(now)) {
                return new Window(now, 1);
            }
            if (state.count() >= limit.getMax()) {
                retryAfter.set(secondsUntil(state.start().plus(window), now));
                return state;
            }
            return new Window(state.start(), state.count() + 1);
        });

        if (retryAfter.get() > 0) {
            throw new CustomException(MessageConstant.RATE_LIMITED, (int) retryAfter.get());
        }
    }

    @Override
    public void checkIp(RateLimitAction action) {
        String ip = clientIpResolver.current();
        check(action, ip == null ? "unknown" : ip);
    }

    @Override
    public void checkUser(RateLimitAction action, Long userId) {
        check(action, "user-" + userId);
    }

    private AppProperty.Limit limitOf(RateLimitAction action) {
        AppProperty.RateLimit limits = appProperty.getProtection().getRateLimit();
        return switch (action) {
            case REGISTER -> limits.getRegister();
            case LOGIN -> limits.getLogin();
            case OTP -> limits.getOtp();
            case COMMENT_PER_MINUTE -> limits.getCommentPerMinute();
            case COMMENT_PER_DAY -> limits.getCommentPerDay();
            case SEARCH -> limits.getSearch();
        };
    }

    // whole seconds, rounded up, never 0 (the caller must wait at least a second)
    static long secondsUntil(Instant end, Instant now) {
        return Math.max(1, (Duration.between(now, end).toMillis() + 999) / 1000);
    }
}
