package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.RolePermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findAllByRoleId(Long roleId);
    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);
}
