package com.api.v1.newssystem.model.request;

import com.api.v1.newssystem.model.enumerate.Roles;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AppUserAddRequest {
    @NotBlank(message = "Name can not be blank")
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Username allow only character")
    private String username;

    private String fullName;

    private String address;

    @Pattern(regexp = "^0[1-9][0-9]{7,8}$", message = "Invalid phone number")
    private String phone;

    @NotBlank(message = "Email can not be blank")
    @Email(message = "Invalid email address")
    private String email;

    @NotBlank(message = "Password can not be blank")
    @Pattern(regexp = "^.{8,}$", message = "Password must be at least 8 characters long")
    private String password;

    @NotBlank(message = "Confirm password can not be blank")
    @Pattern(regexp = "^.{8,}$", message = "Password must be at least 8 characters long")
    private String confirm;

    @NotNull(message = "Roles can not be null")
    private Roles role;
}
