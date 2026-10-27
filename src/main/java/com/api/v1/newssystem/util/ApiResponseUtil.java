package com.api.v1.newssystem.util;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.response.ErrorResponse;
import com.api.v1.newssystem.common.response.PaginationResponse;
import com.api.v1.newssystem.common.response.SuccessResponse;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

import java.util.Date;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
public final class ApiResponseUtil {
    public static <T> SuccessResponse<T> success(HttpStatus status) {
        return SuccessResponse.<T>builder()
                .status(status.getReasonPhrase())
                .code(status.value())
                .localDateTime(new Date())
                .build();
    }

    public static <T> SuccessResponse<T> success(HttpStatus status, T payload) {
        return SuccessResponse.<T>builder()
                .payload(payload)
                .status(status.getReasonPhrase())
                .code(status.value())
                .localDateTime(new Date())
                .build();
    }

    public static <T> PaginationResponse<List<T>> pagination(HttpStatus status, Page<T> page) {
        return PaginationResponse.<List<T>>builder()
                .payload(page.getContent())
                .page(page.getNumber() + 1)
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .status(status.getReasonPhrase())
                .code(status.value())
                .localDateTime(new Date())
                .build();
    }

    public static ErrorResponse error(HttpStatus status, String error, String message) {
        return ErrorResponse.builder()
                .code(status.value())
                .status(status.getReasonPhrase())
                .error(error)
                .message(message)
                .localDateTime(new Date())
                .build();
    }

    public static ErrorResponse error(HttpStatus status, String message) {
        return error(
                status,
                status.getReasonPhrase(),
                message
        );
    }

    public static ErrorResponse error(HttpStatus status, String error, String message, String path) {
        return ErrorResponse.builder()
                .code(status.value())
                .status(status.getReasonPhrase())
                .error(error)
                .message(message)
                .localDateTime(new Date())
                .path(path)
                .build();
    }

    public static ErrorResponse error(HttpStatus status, String message, Map<String, String> details, String path) {
        return ErrorResponse.builder()
                .code(status.value())
                .status(status.getReasonPhrase())
                .error(status.getReasonPhrase())
                .message(message)
                .details(details)
                .localDateTime(new Date())
                .path(path)
                .build();
    }

    public static ErrorResponse error(HttpStatus status, String error, String message, Map<String, String> details, String path) {
        return ErrorResponse.builder()
                .code(status.value())
                .status(status.getReasonPhrase())
                .error(error)
                .message(message)
                .details(details)
                .localDateTime(new Date())
                .path(path)
                .build();
    }

    /** A business error of a MessageConstant: the code is in `error`, the text in `message`; `retryAfterSeconds` may be null. */
    public static ErrorResponse error(MessageConstant message, Integer retryAfterSeconds) {
        return ErrorResponse.builder()
                .code(message.getStatus().value())
                .status(message.getStatus().getReasonPhrase())
                .error(message.getCode())
                .message(message.getDefaultMessage())
                .retryAfterSeconds(retryAfterSeconds)
                .localDateTime(new Date())
                .build();
    }

    public static ErrorResponse error(ErrorResponse errorResponse) {
        return errorResponse;
    }
}
