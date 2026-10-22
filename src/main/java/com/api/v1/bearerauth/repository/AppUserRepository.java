package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.enumerate.Roles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<AppUser> findBySuperAdminMarkTrue();
    boolean existsBySuperAdminMarkTrue();
    long countByRoleAndActiveTrue(Roles role);
    List<AppUser> findByRoleInAndActiveTrue(Collection<Roles> roles);

    @Query("""
            select u from AppUser u
            where (:search is null
                   or lower(u.username) like lower(concat('%', :search, '%'))
                   or lower(u.fullName) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%')))
              and (:role is null or u.role = :role)
              and (:active is null or u.active = :active)
              and (:emailVerified is null or u.emailVerified = :emailVerified)
            """)
    Page<AppUser> search(@Param("search") String search,
                         @Param("role") Roles role,
                         @Param("active") Boolean active,
                         @Param("emailVerified") Boolean emailVerified,
                         Pageable pageable);
}
