package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.SecurityConfig;
import com.api.v1.newssystem.model.response.AppUserDto;
import com.api.v1.newssystem.security.CustomAccessDeniedHandler;
import com.api.v1.newssystem.security.JwtAuthEntrypoint;
import com.api.v1.newssystem.security.JwtAuthFilter;
import com.api.v1.newssystem.security.JwtService;
import com.api.v1.newssystem.service.AppUserService;
import com.api.v1.newssystem.service.RoleService;
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

    private static String createBody(String role) {
        return "{\"username\":\"New Person\",\"email\":\"new@news.local\",\"password\":\"Password-123\",\"confirm\":\"Password-123\",\"role\":" + role + "}";
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canCreateAUserWithEachOfTheFourRoles() throws Exception {
        when(appUserService.createStaff(any())).thenReturn(new AppUserDto());

        for (String role : List.of("ADMIN", "EDITOR", "AUTHOR", "READER")) {
            mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(createBody("\"" + role + "\"")))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(201));
        }
        org.mockito.ArgumentCaptor<com.api.v1.newssystem.model.request.AdminCreateUserRequest> sent =
                org.mockito.ArgumentCaptor.forClass(com.api.v1.newssystem.model.request.AdminCreateUserRequest.class);
        org.mockito.Mockito.verify(appUserService, org.mockito.Mockito.times(4)).createStaff(sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getAllValues()).extracting("role")
                .containsExactly(com.api.v1.newssystem.model.enumerate.Roles.ADMIN, com.api.v1.newssystem.model.enumerate.Roles.EDITOR,
                        com.api.v1.newssystem.model.enumerate.Roles.AUTHOR, com.api.v1.newssystem.model.enumerate.Roles.READER);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_anUnknownOrMissingRole_isRejected_andNothingIsCreated() throws Exception {
        // the project's convention: an unreadable or invalid body answers 200 with the real code (400) in the body
        for (String role : List.of("\"SUPERUSER\"", "\"admin\"", "\"\"", "null", "7")) {
            mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(createBody(role)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(400));
        }
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"New Person\",\"email\":\"new@news.local\",\"password\":\"Password-123\",\"confirm\":\"Password-123\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(400));

        org.mockito.Mockito.verify(appUserService, org.mockito.Mockito.never()).createStaff(any());
    }

    @Test
    @WithMockUser(roles = {"EDITOR", "AUTHOR", "READER"})
    void nonAdmins_stillCannotCreateAnyRole() throws Exception {
        for (String role : List.of("ADMIN", "EDITOR", "AUTHOR", "READER")) {
            mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(createBody("\"" + role + "\"")))
                    .andExpect(status().isForbidden());
        }
        org.mockito.Mockito.verify(appUserService, org.mockito.Mockito.never()).createStaff(any());
    }

    @Test
    @WithMockUser(roles = "READER")
    void anyLoggedInUser_canReachOwnProfile() throws Exception {
        when(appUserService.current()).thenReturn(new AppUserDto());

        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isOk());
    }
}
