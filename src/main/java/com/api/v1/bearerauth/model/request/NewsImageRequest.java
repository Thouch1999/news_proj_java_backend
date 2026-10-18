package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NewsImageRequest {
    // null keeps the current value
    @Size(max = 255, message = "Caption must be at most 255 characters")
    private String caption;

    @PositiveOrZero(message = "Sort order can not be negative")
    private Integer sortOrder;
}
