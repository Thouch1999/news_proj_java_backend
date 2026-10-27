package com.api.v1.newssystem.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** The in-memory (Caffeine) caches of the application. */
@Configuration
@EnableCaching
public class CacheConfig {
    /** The system settings (see SystemSettingServiceImpl): cleared whenever a setting changes. */
    public static final String SETTINGS_CACHE = "settings";

    /** The link-preview pages (see PreviewServiceImpl), by article/category/home and language. */
    public static final String PREVIEW_CACHE = "preview";

    /** The sponsors that may be shown in a placement (see PromoServiceImpl): short-lived, cleared by every sponsor change. */
    public static final String PROMO_CACHE = "promos";

    /** How many impressions / clicks each IP address added in the current minute (see PromoRateLimiter). */
    public static final String PROMO_RATE_CACHE = "promo-rate";

    /** The counters of the rate limits (see RateLimitServiceImpl): the window is kept in the value, not in the cache expiry. */
    public static final String RATE_LIMIT_CACHE = "rate-limit";

    /** The failed logins per account and per IP address, and their locks (see LoginLockoutServiceImpl). */
    public static final String LOGIN_ATTEMPT_CACHE = "login-attempts";

    // one clock for the time rules of the protection (windows, locks, account age), so tests can move it
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SETTINGS_CACHE);
        // the expiry is only a safety net: a change of a setting clears the cache at once
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofHours(1)));
        // an edit of an article or of the settings clears the matching entries at once; ten minutes also bounds a change
        // of a category or a tag, which does not
        manager.registerCustomCache(PREVIEW_CACHE,
                Caffeine.newBuilder().maximumSize(2000).expireAfterWrite(Duration.ofMinutes(10)).build());
        // the list is rebuilt after about a minute even when nothing cleared it; the random pick is made per request
        manager.registerCustomCache(PROMO_CACHE,
                Caffeine.newBuilder().maximumSize(50).expireAfterWrite(Duration.ofSeconds(60)).build());
        // a fixed one-minute window per IP address and kind of event
        manager.registerCustomCache(PROMO_RATE_CACHE,
                Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(Duration.ofMinutes(1)).build());
        // the cache expiry only frees memory (the longest window is a day); the windows and locks are checked with the clock
        manager.registerCustomCache(RATE_LIMIT_CACHE,
                Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(Duration.ofDays(1)).build());
        manager.registerCustomCache(LOGIN_ATTEMPT_CACHE,
                Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(Duration.ofDays(1)).build());
        return manager;
    }
}
