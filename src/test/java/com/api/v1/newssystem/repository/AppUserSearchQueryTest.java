package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.enumerate.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-only check that the optional-filter query in AppUserRepository runs against the
 * configured MySQL with null and non-null parameters. It does not write any data.
 */
@SpringBootTest
class AppUserSearchQueryTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void search_withoutFilters_returnsPage() {
        assertThat(appUserRepository.search(null, null, null, null, PageRequest.of(0, 5))).isNotNull();
    }

    @Test
    void search_withEveryFilter_runsWithoutError() {
        assertThat(appUserRepository.search("news", Roles.ADMIN, true, true, PageRequest.of(0, 5)).getContent())
                .allSatisfy(user -> {
                    assertThat(user.getRole()).isEqualTo(Roles.ADMIN);
                    assertThat(user.isActive()).isTrue();
                    assertThat(user.isEmailVerified()).isTrue();
                });
    }
}
