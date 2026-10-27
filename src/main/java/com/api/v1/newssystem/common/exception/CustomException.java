package com.api.v1.newssystem.common.exception;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.response.ErrorResponse;
import com.api.v1.newssystem.util.ApiResponseUtil;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CustomException extends RuntimeException {
    private final ErrorResponse errorResponse;
    private final HttpStatus httpStatus;

    // Constructor with only HttpStatus
    public CustomException(HttpStatus httpStatus) {
        super(httpStatus.getReasonPhrase());
        this.errorResponse = ApiResponseUtil.error(
                httpStatus,
                httpStatus.getReasonPhrase()
        );
        this.httpStatus = httpStatus;
    }

    // Constructor with just message
    public CustomException(String message) {
        super(message);
        this.errorResponse = ApiResponseUtil.error(
                HttpStatus.BAD_REQUEST,
                message
        );
        this.httpStatus = HttpStatus.BAD_REQUEST;
    }

    // Constructor with ErrorResponse
    public CustomException(ErrorResponse errorResponse, HttpStatus httpStatus) {
        super(errorResponse.getMessage());
        this.errorResponse = ApiResponseUtil.error(errorResponse);
        this.httpStatus = httpStatus;
    }

    // Constructor with HTTP status and message
    public CustomException(HttpStatus httpStatus, String message) {
        super(message);
        this.errorResponse = ApiResponseUtil.error(
                httpStatus,
                message
        );
        this.httpStatus = httpStatus;
    }

    // Constructor with a MessageConstant: its status, its code (in `error`) and its text
    public CustomException(MessageConstant message) {
        this(message, null);
    }

    // Same, with the seconds until the caller may try again (RATE_LIMITED, ACCOUNT_TEMPORARILY_LOCKED ...)
    public CustomException(MessageConstant message, Integer retryAfterSeconds) {
        super(message.getDefaultMessage());
        this.errorResponse = ApiResponseUtil.error(message, retryAfterSeconds);
        this.httpStatus = message.getStatus();
    }

    // Constructor with message, details, HTTP status, and path
    public CustomException(String message, String details, HttpStatus httpStatus, String path) {
        super(message);
        this.errorResponse = ApiResponseUtil.error(
                httpStatus,
                message,
                details,
                path
        );
        this.httpStatus = httpStatus;
    }
}