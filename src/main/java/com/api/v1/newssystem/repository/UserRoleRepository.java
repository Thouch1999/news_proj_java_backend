package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.UserRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findAllByUserId(Long userId);
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);
}
