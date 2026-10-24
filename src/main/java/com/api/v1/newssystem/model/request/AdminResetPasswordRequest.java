package com.api.v1.newssystem.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminResetPasswordRequest {
    @NotBlank(message = "Password can not be blank")
    @Pattern(regexp = "^.{8,}$", message = "Password must be at least 8 characters long")
    private String password;

    @NotBlank(message = "Confirm password can not be blank")
    @Pattern(regexp = "^.{8,}$", message = "Password must be at least 8 characters long")
    private String confirm;
}
