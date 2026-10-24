package com.api.v1.newssystem.model.response;

import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One row of the author's article list (no content).
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewsSummaryDto {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String thumbnail;
    private NewsStatus status;
    private Long authorId;
    private String authorName;
    private String categoryName;
    private String rejectionReason;
    private boolean editable;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime updatedAt;
}
