package com.api.v1.newssystem.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Name can not be blank")
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Username allow only character")
    private String username;

    @Size(max = 255, message = "Full name must be at most 255 characters")
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

    // honeypot: a hidden field that people never fill in; when it has a value the request is ignored (see the protection rules)
    @Size(max = 255, message = "Website must be at most 255 characters")
    private String website;
}
