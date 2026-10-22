package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.enumerate.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The unique index on users.super_admin: one 1, any number of NULL. Everything is rolled back. */
@SpringBootTest
@Transactional
class SuperAdminUniqueTest {

    @Autowired
    private AppUserRepository appUserRepository;

    private AppUser admin() {
        AppUser user = new AppUser();
        user.setUsername("Test Admin");
        user.setEmail("super-admin-test-" + UUID.randomUUID() + "@news.local");
        user.setPassword("x");
        user.setRole(Roles.ADMIN);
        user.setActive(true);
        user.setEmailVerified(true);
        return user;
    }

    @Test
    void theDatabaseRefusesASecondSuperAdmin_butAllowsManyWithoutTheMark() {
        // a mark that exists already (the real super admin) would also be refused, so clear it inside this rolled-back transaction
        appUserRepository.findBySuperAdminMarkTrue().ifPresent(existing -> {
            existing.setSuperAdmin(false);
            appUserRepository.saveAndFlush(existing);
        });

        appUserRepository.saveAndFlush(admin());
        appUserRepository.saveAndFlush(admin()); // two without the mark: fine (NULL, NULL)

        AppUser first = admin();
        first.setSuperAdmin(true);
        appUserRepository.saveAndFlush(first);
        assertThat(appUserRepository.findBySuperAdminMarkTrue()).contains(first);

        AppUser second = admin();
        second.setSuperAdmin(true);
        assertThatThrownBy(() -> appUserRepository.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void setSuperAdminFalse_storesNull_neverFalse() {
        AppUser user = admin();
        user.setSuperAdmin(true);
        user.setSuperAdmin(false);
        assertThat(user.isSuperAdmin()).isFalse();
    }
}
