package com.api.v1.bearerauth.model.response;

import com.api.v1.bearerauth.model.enumerate.ActivityAction;
import com.api.v1.bearerauth.model.enumerate.ActivityTargetType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

// One activity log entry (ADMIN). `details` is filled when one entry is read; in a list only for the few actions whose
// sentence needs it (USER_CREATED, USER_ROLES_CHANGED, COMMENTS_TOGGLED, PASSWORD_CHANGED).
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActivityLogDto {
    private Long id;
    private Long actorId;
    private String actorName;
    private String actorRoles;
    private String ipAddress;
    private String userAgent;
    private ActivityAction action;
    private ActivityTargetType targetType;
    private Long targetId;
    private String targetLabel;
    private Map<String, Object> details;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
}
