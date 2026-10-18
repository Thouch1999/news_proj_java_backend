package com.api.v1.bearerauth.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PaginationResponse<T> {
    private final String message = "Successfully";
    private String status;
    private Integer code;
    private Date localDateTime;
    private T payload;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
    private boolean hasNext;
    private boolean hasPrevious;
}
