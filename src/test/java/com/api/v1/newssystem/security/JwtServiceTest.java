package com.api.v1.newssystem.security;

import com.api.v1.newssystem.common.response.TokenResponse;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.model.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Access tokens: valid until they expire, and refused from the moment of logout. */
class JwtServiceTest {

    private JwtService jwtService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        AppProperty properties = new AppProperty();
        properties.getJwt().setSecret("a-test-secret-that-is-long-enough-for-hmac-sha-256-signing");
        properties.getJwt().setAccessExpiration(900_000L);
        properties.getJwt().setRefreshExpiration(604_800_000L);
        jwtService = new JwtService(properties);
        user = new AppUser();
        user.setEmail("reader@news.local");
    }

    @Test
    void aFreshAccessToken_isValid() {
        TokenResponse tokens = jwtService.generateAccess(user);

        assertThat(jwtService.validateAccessToken(tokens.getAccess(), user)).isTrue();
    }

    @Test
    void aRevokedAccessToken_isRefused_withOrWithoutTheBearerPrefix() {
        String first = jwtService.generateAccess(user).getAccess();
        String second = jwtService.generateAccess(user).getAccess();

        jwtService.revoke("Bearer " + first);
        jwtService.revoke(second);

        assertThat(jwtService.validateAccessToken(first, user)).isFalse();
        assertThat(jwtService.validateAccessToken(second, user)).isFalse();
    }

    @Test
    void revokingOneToken_doesNotTouchAnotherTokenOfTheSameUser() {
        String logoutToken = jwtService.generateAccess(user).getAccess();
        String otherDevice = jwtService.generateAccess(user).getAccess(); // same second, same user

        jwtService.revoke(logoutToken);

        assertThat(jwtService.validateAccessToken(logoutToken, user)).isFalse();
        assertThat(jwtService.validateAccessToken(otherDevice, user)).isTrue();
    }

    @Test
    void revokingNothingUsable_isIgnored() {
        assertThatCode(() -> {
            jwtService.revoke(null);
            jwtService.revoke("");
            jwtService.revoke("Bearer not-a-token");
        }).doesNotThrowAnyException();
    }
}
