package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.common.response.SuccessResponse;
import com.api.v1.newssystem.model.response.RoleDto;
import com.api.v1.newssystem.service.RoleService;
import com.api.v1.newssystem.util.ApiResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<SuccessResponse<List<RoleDto>>> list() {
        return ResponseEntity.ok(
                ApiResponseUtil.success(HttpStatus.OK, roleService.findAll())
        );
    }
}
