package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NewsRejectRequest {
    @NotBlank(message = "Rejection reason is required")
    @Size(max = 1000, message = "Rejection reason must be at most 1000 characters")
    private String reason;
}
