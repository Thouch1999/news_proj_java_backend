package com.api.v1.newssystem.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token can not be blank")
    private String refresh;
}
