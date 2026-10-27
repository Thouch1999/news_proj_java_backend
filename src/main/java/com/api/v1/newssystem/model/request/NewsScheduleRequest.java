package com.api.v1.newssystem.model.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

// The time an article goes live (UTC, for example 2026-10-20T00:00:00Z). The window (5 minutes to 1 year from now) is checked in NewsService.
@Data
public class NewsScheduleRequest {
    @NotNull(message = "scheduledAt is required")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant scheduledAt;
}
