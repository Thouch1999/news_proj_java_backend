package com.api.v1.bearerauth.model.response;

import com.api.v1.bearerauth.model.enumerate.CommentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// A comment. `content` is plain text (not HTML): show it as text.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentDto {
    private Long id;
    private Long newsId;
    private String newsTitle;
    private String newsSlug;
    private Long userId;
    // the commenter's full name, or the username when there is none (never the e-mail)
    private String userName;
    private String content;
    private CommentStatus status;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime updatedAt;
}
