package com.api.v1.bearerauth.model.request;

import com.api.v1.bearerauth.model.enumerate.Roles;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeRoleRequest {
    @NotNull(message = "Roles can not be null")
    private Roles role;
}
