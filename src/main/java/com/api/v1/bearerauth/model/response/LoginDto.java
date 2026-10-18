package com.api.v1.bearerauth.model.response;

import com.api.v1.bearerauth.common.response.TokenResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of a login step. When {@code otpRequired} is true no token is issued yet and the client
 * must call /login/verify-otp; otherwise token, user and roles are present.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginDto {
    private boolean otpRequired;
    private TokenResponse token;
    private AppUserDto user;
    private List<String> roles;
}
