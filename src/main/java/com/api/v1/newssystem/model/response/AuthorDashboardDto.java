package com.api.v1.newssystem.model.response;

import com.api.v1.newssystem.model.enumerate.NewsStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

// The numbers of an author's own dashboard. Every status is present, with 0 when there is none.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthorDashboardDto {
    private long totalNews;
    private Map<NewsStatus, Long> newsByStatus;
    // views of the author's PUBLISHED articles
    private long totalViews;
    // all comments on the author's articles
    private long totalComments;
}
