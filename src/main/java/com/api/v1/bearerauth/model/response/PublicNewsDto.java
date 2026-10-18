package com.api.v1.bearerauth.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// A whole PUBLISHED article for readers.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicNewsDto {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String content;
    private String thumbnail;
    private String authorName;
    private CategoryDto category;
    private List<TagDto> tags;
    private List<NewsImageDto> images;
    private boolean commentsEnabled;
    // includes the view that opening this article may have just added
    private long viewCount;
    // visible comments only
    private long commentCount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime publishedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime updatedAt;
    // true / false when the user is logged in (did they save it?); left out of the JSON for a visitor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean bookmarked;
}
