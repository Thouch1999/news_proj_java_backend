package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferSuperAdminRequest {
    @NotNull(message = "User can not be null")
    private Long userId;

    @NotBlank(message = "Password can not be blank")
    private String password;
}
