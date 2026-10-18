package com.api.v1.bearerauth.model.request;

import com.api.v1.bearerauth.model.enumerate.Roles;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminCreateUserRequest {
    @NotBlank(message = "Name can not be blank")
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Username allow only character")
    private String username;

    @Size(max = 255, message = "Full name must be at most 255 characters")
    private String fullName;

    @NotBlank(message = "Email can not be blank")
    @Email(message = "Invalid email address")
    private String email;

    private String address;

    @Pattern(regexp = "^0[1-9][0-9]{7,8}$", message = "Invalid phone number")
    private String phone;

    @NotBlank(message = "Password can not be blank")
    @Pattern(regexp = "^.{8,}$", message = "Password must be at least 8 characters long")
    private String password;

    @NotBlank(message = "Confirm password can not be blank")
    @Pattern(regexp = "^.{8,}$", message = "Password must be at least 8 characters long")
    private String confirm;

    @NotNull(message = "Roles can not be null")
    private Roles role;
}
