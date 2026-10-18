package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.model.response.RoleDto;

import java.util.List;

public interface RoleService {
    /** All roles with the names of their permissions (read-only). */
    List<RoleDto> findAll();
}
