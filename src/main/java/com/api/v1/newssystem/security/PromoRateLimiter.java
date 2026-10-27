package com.api.v1.newssystem.security;

import com.api.v1.newssystem.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits how many impressions or clicks one IP address can add per minute (a fixed window, in memory, in the Caffeine
 * cache CacheConfig.PROMO_RATE_CACHE), so a script cannot inflate the numbers of a sponsor. The address is the one of
 * ClientIpResolver, so a header written by the client is not believed.
 */
@Component
@RequiredArgsConstructor
public class PromoRateLimiter {
    private final CacheManager cacheManager;

    /** True when the event may be counted; false once `limitPerMinute` events of this kind came from this address. */
    public boolean tryAcquire(String kind, String ip, int limitPerMinute) {
        Cache cache = cacheManager.getCache(CacheConfig.PROMO_RATE_CACHE);
        if (cache == null) {
            return true;
        }
        AtomicInteger count = cache.get(kind + '|' + (ip == null ? "unknown" : ip), AtomicInteger::new);
        return count != null && count.incrementAndGet() <= limitPerMinute;
    }
}
