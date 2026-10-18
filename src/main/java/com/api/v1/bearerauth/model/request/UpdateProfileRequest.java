package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Name can not be blank")
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Username allow only character")
    private String username;

    @Size(max = 255, message = "Full name must be at most 255 characters")
    private String fullName;

    private String address;

    @Pattern(regexp = "^0[1-9][0-9]{7,8}$", message = "Invalid phone number")
    private String phone;

    @Size(max = 500, message = "Avatar must be at most 500 characters")
    private String avatar;
}
