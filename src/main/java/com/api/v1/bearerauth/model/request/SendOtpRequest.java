package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendOtpRequest {
    @NotBlank(message = "Email can not be blank")
    @Email(message = "Invalid email address")
    private String email;
}
