package com.api.v1.bearerauth.model.response;

import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One line of the recent activity: an article changed status (type STATUS_CHANGE) or someone commented (type COMMENT).
// Built by the queries in ReportRepository.
@Data
@NoArgsConstructor
public class ActivityItemDto {
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String COMMENT = "COMMENT";
    private static final int SNIPPET = 120;

    private String type;
    private String actorName;
    private Long newsId;
    private String newsTitle;
    private String newsSlug;
    // STATUS_CHANGE: the status before (null when the article was created) and after
    private NewsStatus fromStatus;
    private NewsStatus toStatus;
    // COMMENT: the start of the comment
    private String snippet;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime at;

    public ActivityItemDto(String fullName, String username, Long newsId, String newsTitle, String newsSlug,
                           NewsStatus fromStatus, NewsStatus toStatus, LocalDateTime at) {
        this.type = STATUS_CHANGE;
        this.actorName = name(fullName, username);
        this.newsId = newsId;
        this.newsTitle = newsTitle;
        this.newsSlug = newsSlug;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.at = at;
    }

    public ActivityItemDto(String fullName, String username, Long newsId, String newsTitle, String newsSlug,
                           String content, LocalDateTime at) {
        this.type = COMMENT;
        this.actorName = name(fullName, username);
        this.newsId = newsId;
        this.newsTitle = newsTitle;
        this.newsSlug = newsSlug;
        this.snippet = content != null && content.length() > SNIPPET ? content.substring(0, SNIPPET) + "…" : content;
        this.at = at;
    }

    private static String name(String fullName, String username) {
        return fullName != null && !fullName.isBlank() ? fullName : username;
    }
}
