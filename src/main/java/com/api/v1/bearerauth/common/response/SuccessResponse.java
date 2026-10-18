package com.api.v1.bearerauth.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SuccessResponse<T> {
    private final String message = "Successfully";
    private String status;
    private Integer code;
    private Date localDateTime;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T payload;
}
