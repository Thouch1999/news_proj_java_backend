package com.api.v1.newssystem.model.response;

import com.api.v1.newssystem.model.enumerate.NewsStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// A whole article as its author sees it.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewsDto {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String content;
    private String thumbnail;
    private NewsStatus status;
    private Long authorId;
    private String authorName;
    private CategoryDto category;
    private List<TagDto> tags;
    private List<NewsImageDto> images;
    private boolean commentsEnabled;
    // why an editor rejected the article; kept while the author edits a REJECTED article
    private String rejectionReason;
    // true when the logged-in user may edit it: an author in DRAFT or REJECTED, an admin always
    private boolean editable;
    private long viewCount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime publishedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime updatedAt;
}
