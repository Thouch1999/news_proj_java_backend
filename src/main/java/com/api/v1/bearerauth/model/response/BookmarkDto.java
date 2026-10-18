package com.api.v1.bearerauth.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One saved article of the logged-in user: the article card and when it was saved.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookmarkDto {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime bookmarkedAt;
    private PublicNewsSummaryDto news;
}
