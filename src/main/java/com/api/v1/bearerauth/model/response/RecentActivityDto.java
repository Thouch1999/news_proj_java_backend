package com.api.v1.bearerauth.model.response;

import com.api.v1.bearerauth.model.enumerate.ActivityAction;
import com.api.v1.bearerauth.model.enumerate.ActivityTargetType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One line of the dashboard card "Recent activity" (EDITOR and ADMIN): what was done to which article or comment, and by
// whom. No IP address, no User-Agent, no details.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecentActivityDto {
    private Long id;
    private ActivityAction action;
    private ActivityTargetType targetType;
    private Long targetId;
    private String targetLabel;
    private String actorName;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
}
