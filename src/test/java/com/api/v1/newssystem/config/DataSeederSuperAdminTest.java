package com.api.v1.newssystem.config;

import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Role;
import com.api.v1.newssystem.model.entity.UserRole;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederSuperAdminTest {
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private SystemSettingRepository systemSettingRepository;

    private final AppProperty appProperty = new AppProperty();
    private DataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DataSeeder(appProperty, new BCryptPasswordEncoder(), appUserRepository, roleRepository, permissionRepository,
                rolePermissionRepository, userRoleRepository, categoryRepository, tagRepository, systemSettingRepository);
    }

    private AppUser user(Roles role, boolean active) {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setEmail("boss@news.local");
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    @Test
    void marksTheConfiguredAdmin_whenNoSuperAdminExists() {
        appProperty.setSuperAdminEmail(" Boss@News.local ");
        AppUser boss = user(Roles.ADMIN, true);
        when(appUserRepository.existsBySuperAdminMarkTrue()).thenReturn(false);
        when(appUserRepository.findByEmail("boss@news.local")).thenReturn(Optional.of(boss));

        seeder.markSuperAdmin();

        assertThat(boss.isSuperAdmin()).isTrue();
        verify(appUserRepository).save(boss);
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void addsTheAdminRole_whenTheConfiguredUserLacksIt() {
        appProperty.setSuperAdminEmail("boss@news.local");
        AppUser boss = user(Roles.EDITOR, true);
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        when(appUserRepository.existsBySuperAdminMarkTrue()).thenReturn(false);
        when(appUserRepository.findByEmail("boss@news.local")).thenReturn(Optional.of(boss));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRoleRepository.findAllByUserId(7L)).thenReturn(List.of());

        seeder.markSuperAdmin();

        assertThat(boss.getRole()).isEqualTo(Roles.ADMIN);
        assertThat(boss.isSuperAdmin()).isTrue();
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    void isSkipped_whenASuperAdminAlreadyExists_andTheConfigIsNotRead() {
        appProperty.setSuperAdminEmail("somebody-else@news.local");
        when(appUserRepository.existsBySuperAdminMarkTrue()).thenReturn(true);

        seeder.markSuperAdmin();

        verify(appUserRepository, never()).findByEmail(any());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void doesNotCrash_whenTheEmailIsMissing_unknown_orInactive() {
        when(appUserRepository.existsBySuperAdminMarkTrue()).thenReturn(false);

        appProperty.setSuperAdminEmail("");
        seeder.markSuperAdmin();

        appProperty.setSuperAdminEmail("nobody@news.local");
        when(appUserRepository.findByEmail("nobody@news.local")).thenReturn(Optional.empty());
        seeder.markSuperAdmin();

        appProperty.setSuperAdminEmail("boss@news.local");
        AppUser inactive = user(Roles.ADMIN, false);
        when(appUserRepository.findByEmail("boss@news.local")).thenReturn(Optional.of(inactive));
        seeder.markSuperAdmin();

        assertThat(inactive.isSuperAdmin()).isFalse();
        verify(appUserRepository, never()).save(any());
    }
}
