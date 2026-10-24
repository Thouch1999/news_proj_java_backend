package com.api.v1.newssystem.model.entity;

import com.api.v1.newssystem.model.enumerate.Roles;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@NullMarked
public class AppUser implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String address;
    private String phone;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private Roles role;

    private String fullName;

    @Column(length = 500)
    private String avatar;

    @Column(nullable = false, columnDefinition = "boolean not null default true")
    private boolean active = true;

    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean emailVerified = false;

    // 1 for the super admin and NULL for everybody else: the unique index lets the database allow only one (many NULLs are fine)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "super_admin", unique = true, columnDefinition = "TINYINT NULL")
    private Boolean superAdminMark;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime(6) not null default current_timestamp(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime(6) not null default current_timestamp(6)")
    private LocalDateTime updatedAt;

    public boolean isSuperAdmin() {
        return Boolean.TRUE.equals(superAdminMark);
    }

    // never stores false: a second false would break the unique index
    public void setSuperAdmin(boolean superAdmin) {
        this.superAdminMark = superAdmin ? Boolean.TRUE : null;
    }

    public String getName() {
        return this.username;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
