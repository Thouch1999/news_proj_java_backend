package com.api.v1.newssystem.model.request;

import com.api.v1.newssystem.model.enumerate.OtpPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResendOtpRequest {
    @NotBlank(message = "Email can not be blank")
    @Email(message = "Invalid email address")
    private String email;

    @NotNull(message = "Purpose can not be null")
    private OtpPurpose purpose;
}
