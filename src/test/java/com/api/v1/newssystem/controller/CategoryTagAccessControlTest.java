package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.SecurityConfig;
import com.api.v1.newssystem.security.CustomAccessDeniedHandler;
import com.api.v1.newssystem.security.JwtAuthEntrypoint;
import com.api.v1.newssystem.security.JwtAuthFilter;
import com.api.v1.newssystem.security.JwtService;
import com.api.v1.newssystem.service.AppUserService;
import com.api.v1.newssystem.service.CategoryService;
import com.api.v1.newssystem.service.TagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the public/admin rules for categories and tags against the real filter chain.
 * Services are mocked, so no database is used.
 */
@WebMvcTest({CategoryController.class, TagController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class CategoryTagAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private TagService tagService;
    @MockitoBean
    private AppUserService appUserService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private ObjectMapper objectMapper;

    private static final String JSON = "{}";

    @TestConfiguration
    static class Config {
        @Bean
        AppProperty appProperty() {
            return new AppProperty();
        }
    }

    private List<MockHttpServletRequestBuilder> publicEndpoints() {
        return List.of(
                get("/api/v1/categories"),
                get("/api/v1/categories/sports"),
                get("/api/v1/tags"),
                get("/api/v1/tags/politics")
        );
    }

    private List<MockHttpServletRequestBuilder> adminEndpoints() {
        return List.of(
                get("/api/v1/categories/admin"),
                post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON).content(JSON),
                put("/api/v1/categories/1").contentType(MediaType.APPLICATION_JSON).content(JSON),
                put("/api/v1/categories/1/activate"),
                put("/api/v1/categories/1/deactivate"),
                delete("/api/v1/categories/1"),
                post("/api/v1/tags").contentType(MediaType.APPLICATION_JSON).content(JSON),
                put("/api/v1/tags/1").contentType(MediaType.APPLICATION_JSON).content(JSON),
                delete("/api/v1/tags/1")
        );
    }

    private void assertAll(List<MockHttpServletRequestBuilder> endpoints, ResultMatcher expected) throws Exception {
        for (MockHttpServletRequestBuilder endpoint : endpoints) {
            mockMvc.perform(endpoint).andExpect(expected);
        }
    }

    @Test
    void anonymous_canReadPublicEndpoints() throws Exception {
        assertAll(publicEndpoints(), status().isOk());
    }

    @Test
    void anonymous_isUnauthorizedOnAdminEndpoints() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertAll(adminEndpoints(), status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    void reader_canReadPublic_butIsDeniedOnAdminEndpoints() throws Exception {
        assertAll(publicEndpoints(), status().isOk());
        assertAll(adminEndpoints(), status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void editor_isDeniedOnAdminEndpoints() throws Exception {
        assertAll(adminEndpoints(), status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void author_isDeniedOnAdminEndpoints() throws Exception {
        assertAll(adminEndpoints(), status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canListCategoriesForAdmin_andDeleteATag() throws Exception {
        mockMvc.perform(get("/api/v1/categories/admin")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/tags/1")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/categories/1")).andExpect(status().isOk());
    }
}
