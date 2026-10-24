package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;

import java.util.Map;

/**
 * Writes the activity log (who did what). The other services call it; reading it is ActivityLogQueryService.
 * Nothing here ever changes or deletes an entry, except deleteExpired (the retention job).
 */
public interface ActivityLogService {
    /**
     * Adds an entry in the SAME transaction as the action (the caller must be @Transactional, else this throws): the entry
     * exists only when the action was really saved, and a failed or rolled-back action leaves nothing behind.
     * The actor is the logged-in user, the address and the User-Agent come from the current request.
     * `targetLabel` is a snapshot (an article title, a username); `details` is cleaned before it is saved (see
     * ActivityDetails): secrets are dropped, texts are cut. `targetId` and `details` may be null.
     */
    void record(ActivityAction action, ActivityTargetType targetType, Long targetId, String targetLabel, Map<String, Object> details);

    /** Like record, for an action done by `actor` who is not the logged-in user (a password reset with the OTP). */
    void recordFor(AppUser actor, ActivityAction action, ActivityTargetType targetType, Long targetId, String targetLabel, Map<String, Object> details);

    /**
     * Adds a LOGIN_SUCCESS or LOGIN_FAILED entry (target AUTH) in a transaction of its own, because a failed login has no
     * transaction to join. `actor` is the user who logged in (null for a failure); `label` is the email that was typed.
     * It never throws: if the entry cannot be written, that is logged and the login goes on.
     */
    void recordLogin(ActivityAction action, AppUser actor, String label, Map<String, Object> details);

    /** Deletes the entries older than app.activity-log.retention-days; returns how many. */
    int deleteExpired();
}
