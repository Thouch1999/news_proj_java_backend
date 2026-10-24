package com.api.v1.newssystem.model.response;

import com.api.v1.newssystem.model.enumerate.NotificationType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

// A notification of the logged-in user. The frontend builds the shown text from `type` + `data`;
// `title` and `message` are a plain English fallback. `link` is the frontend path to open (null when there is none).
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDto {
    private Long id;
    private NotificationType type;
    private Map<String, Object> data;
    private int groupCount;
    private boolean read;
    private String title;
    private String message;
    private String link;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime updatedAt;
}
