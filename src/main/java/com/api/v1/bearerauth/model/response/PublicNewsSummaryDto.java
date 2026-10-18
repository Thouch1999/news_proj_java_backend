package com.api.v1.bearerauth.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// One article card on the public site (no content, no status: everything here is PUBLISHED).
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicNewsSummaryDto {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String thumbnail;
    private String categoryName;
    private String categorySlug;
    private String authorName;
    private List<TagDto> tags;
    private long viewCount;
    private long commentCount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime publishedAt;
}
