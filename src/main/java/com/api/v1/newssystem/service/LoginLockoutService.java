package com.api.v1.newssystem.service;

/**
 * Counts the failed logins (in memory, Caffeine) per account and per IP address in a window of
 * app.protection.lockout.window-seconds. An account is locked after account-max-failures failures and an IP address is
 * blocked after ip-max-failures, each for lock-seconds. A locked account or IP is refused even with the right password
 * (429 ACCOUNT_TEMPORARILY_LOCKED + retryAfterSeconds). The answer is the same whether the account exists or not: every
 * typed email is counted, and an unknown email counts against the IP address like any other.
 */
public interface LoginLockoutService {
    /** What one failure caused: the lock of the account and / or the block of the IP address (true only when it just happened). */
    record Failure(boolean accountLocked, boolean ipBlocked, int accountFailures, int ipFailures) {
    }

    /** Throws 429 ACCOUNT_TEMPORARILY_LOCKED (with retryAfterSeconds) when the email or the IP address of the request is locked. */
    void assertNotLocked(String email);

    /** Counts a failed login of `email` from the IP address of the current request. */
    Failure recordFailure(String email);

    /** A successful login: the counter of the account starts again (the one of the IP address is kept). */
    void recordSuccess(String email);

    /** Admin: lifts the lock and the counter of an account; true when it was locked. */
    boolean unlock(String email);

    /** Whether the account is locked now. */
    boolean isLocked(String email);
}
