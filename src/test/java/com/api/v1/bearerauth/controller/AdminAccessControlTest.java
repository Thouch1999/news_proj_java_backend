package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.config.SecurityConfig;
import com.api.v1.bearerauth.model.response.AppUserDto;
import com.api.v1.bearerauth.security.CustomAccessDeniedHandler;
import com.api.v1.bearerauth.security.JwtAuthEntrypoint;
import com.api.v1.bearerauth.security.JwtAuthFilter;
import com.api.v1.bearerauth.security.JwtService;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.service.RoleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the role rules in SecurityConfig against the real filter chain.
 * Services are mocked, so no database is used.
 */
@WebMvcTest({AppUserController.class, RoleController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class AdminAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserService appUserService;
    @MockitoBean
    private RoleService roleService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private ObjectMapper objectMapper;

    private static final String JSON = "{}";

    // SecurityConfig reads the CORS origins from AppProperty
    @TestConfiguration
    static class Config {
        @Bean
        AppProperty appProperty() {
            return new AppProperty();
        }
    }

    private List<MockHttpServletRequestBuilder> adminEndpoints() {
        return List.of(
                get("/api/v1/users"),
                get("/api/v1/users/1"),
                post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(JSON),
                put("/api/v1/users/1/activate"),
                put("/api/v1/users/1/deactivate"),
                put("/api/v1/users/1/role").contentType(MediaType.APPLICATION_JSON).content(JSON),
                put("/api/v1/users/1/password").contentType(MediaType.APPLICATION_JSON).content(JSON),
                get("/api/v1/roles")
        );
    }

    private void assertEveryAdminEndpoint(org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        for (MockHttpServletRequestBuilder endpoint : adminEndpoints()) {
            mockMvc.perform(endpoint).andExpect(expected);
        }
    }

    @Test
    @WithMockUser(roles = "READER")
    void reader_isDeniedOnAllAdminEndpoints() throws Exception {
        assertEveryAdminEndpoint(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void author_isDeniedOnAllAdminEndpoints() throws Exception {
        assertEveryAdminEndpoint(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void editor_isDeniedOnAllAdminEndpoints() throws Exception {
        assertEveryAdminEndpoint(status().isForbidden());
    }

    @Test
    void anonymous_isUnauthorizedOnAllAdminEndpoints() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        assertEveryAdminEndpoint(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canListUsersAndRoles() throws Exception {
        when(appUserService.findAll(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());
        when(roleService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/roles")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "READER")
    void anyLoggedInUser_canReachOwnProfile() throws Exception {
        when(appUserService.current()).thenReturn(new AppUserDto());

        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isOk());
    }
}
