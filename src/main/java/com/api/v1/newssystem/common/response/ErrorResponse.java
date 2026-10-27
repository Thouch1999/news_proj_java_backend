package com.api.v1.newssystem.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private int code;
    private String status;
    private String error;
    private String message;
    private Map<String, String> details;
    private Date localDateTime;
    private String path;
    // seconds until the caller may try again (rate limit, lockout); also sent as the Retry-After header
    private Integer retryAfterSeconds;
}
