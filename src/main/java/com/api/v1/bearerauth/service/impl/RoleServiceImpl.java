package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.model.response.RoleDto;
import com.api.v1.bearerauth.repository.RolePermissionRepository;
import com.api.v1.bearerauth.repository.RoleRepository;
import com.api.v1.bearerauth.service.RoleService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional
    public List<RoleDto> findAll() {
        return roleRepository.findAll().stream()
                .map(role -> new RoleDto(
                        role.getId(),
                        role.getName(),
                        role.getDescription(),
                        rolePermissionRepository.findAllByRoleId(role.getId()).stream()
                                .map(link -> link.getPermission().getName())
                                .sorted()
                                .toList()))
                .toList();
    }
}
