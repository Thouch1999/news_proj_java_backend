package com.api.v1.newssystem.model.response;

import lombok.Data;
import lombok.NoArgsConstructor;

// How many articles a category has. Built by the aggregate query in ReportRepository.
@Data
@NoArgsConstructor
public class CategoryNewsCountDto {
    private Long categoryId;
    private String name;
    private String slug;
    private boolean active;
    // articles in any status
    private long newsCount;
    private long publishedCount;

    public CategoryNewsCountDto(Long categoryId, String name, String slug, Boolean active, Long newsCount, Long publishedCount) {
        this.categoryId = categoryId;
        this.name = name;
        this.slug = slug;
        this.active = Boolean.TRUE.equals(active);
        this.newsCount = newsCount == null ? 0 : newsCount;
        this.publishedCount = publishedCount == null ? 0 : publishedCount;
    }
}
