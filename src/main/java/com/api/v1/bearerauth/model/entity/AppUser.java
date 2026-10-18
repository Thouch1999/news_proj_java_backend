package com.api.v1.bearerauth.model.entity;

import com.api.v1.bearerauth.model.enumerate.Roles;
import jakarta.persistence.*;
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

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime(6) not null default current_timestamp(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime(6) not null default current_timestamp(6)")
    private LocalDateTime updatedAt;

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
