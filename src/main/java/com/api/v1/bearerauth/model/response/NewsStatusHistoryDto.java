package com.api.v1.bearerauth.model.response;

import com.api.v1.bearerauth.model.enumerate.NewsStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// One status change of an article (fromStatus is null for the first row, "created").
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewsStatusHistoryDto {
    private Long id;
    private NewsStatus fromStatus;
    private NewsStatus toStatus;
    private Long changedById;
    private String changedByName;
    private String note;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime changedAt;
}
