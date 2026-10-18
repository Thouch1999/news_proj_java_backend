package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank(message = "Email can not be blank")
    @Email(message = "Invalid email address")
    private String email;

    @NotBlank(message = "OTP can not be blank")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    private String otp;
}
