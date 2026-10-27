package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.enumerate.RateLimitAction;

/**
 * Fixed-window counters in memory (Caffeine), one per action and subject (an IP address or a user id). The limits are
 * in app.protection.rate-limit. Each call counts one try; the try over the limit is refused with 429 RATE_LIMITED and
 * the seconds until the window ends (retryAfterSeconds in the body and the Retry-After header).
 */
public interface RateLimitService {
    /** Counts one try of `action` by `subject`; throws 429 RATE_LIMITED when the limit of the window is already used. */
    void check(RateLimitAction action, String subject);

    /** `check` for the client IP address of the current request (the address of ClientIpResolver). */
    void checkIp(RateLimitAction action);

    /** `check` for a user. */
    void checkUser(RateLimitAction action, Long userId);
}
