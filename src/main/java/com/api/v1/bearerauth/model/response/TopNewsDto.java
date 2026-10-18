package com.api.v1.bearerauth.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One row of a "most viewed" / "most commented" list. `count` is the number the list is ranked by.
// Built by the aggregate queries in ReportRepository.
@Data
@NoArgsConstructor
public class TopNewsDto {
    private Long id;
    private String title;
    private String slug;
    private String categoryName;
    private String authorName;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime publishedAt;
    // path of the cover image, may be null
    private String thumbnail;
    private long count;

    public TopNewsDto(Long id, String title, String slug, String categoryName, String authorFullName, String authorUsername,
                      LocalDateTime publishedAt, String thumbnail, Long count) {
        this.id = id;
        this.title = title;
        this.slug = slug;
        this.categoryName = categoryName;
        this.authorName = authorFullName != null && !authorFullName.isBlank() ? authorFullName : authorUsername;
        this.publishedAt = publishedAt;
        this.thumbnail = thumbnail;
        this.count = count == null ? 0 : count;
    }
}
