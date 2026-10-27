package com.api.v1.newssystem.model.enumerate;

/** What is rate limited (see RateLimitService); the limit of each one is in app.protection.rate-limit. */
public enum RateLimitAction {
    REGISTER,
    LOGIN,
    OTP,
    COMMENT_PER_MINUTE,
    COMMENT_PER_DAY,
    SEARCH
}
