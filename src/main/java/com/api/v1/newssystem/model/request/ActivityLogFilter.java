package com.api.v1.newssystem.model.request;

import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;

import java.time.LocalDate;
import java.util.List;

/** The filters of the activity log list and export; every one is optional (null or empty means "any"). */
public record ActivityLogFilter(
        Long actorId,
        List<ActivityAction> actions,
        ActivityTargetType targetType,
        Long targetId,
        LocalDate from,   // first day, included
        LocalDate to,     // last day, included
        String search     // in the target label and the actor name
) {
    public static ActivityLogFilter none() {
        return new ActivityLogFilter(null, null, null, null, null, null, null);
    }
}
