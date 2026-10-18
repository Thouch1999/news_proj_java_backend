package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.config.SecurityConfig;
import com.api.v1.bearerauth.security.CustomAccessDeniedHandler;
import com.api.v1.bearerauth.security.JwtAuthEntrypoint;
import com.api.v1.bearerauth.security.JwtAuthFilter;
import com.api.v1.bearerauth.security.JwtService;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.service.SystemSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks who may call the settings endpoints against the real filter chain: the public ones need no login, everything
 * else is for the ADMIN. The service is mocked, so no database is used.
 */
@WebMvcTest(SystemSettingController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class SystemSettingAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemSettingService systemSettingService;
    @MockitoBean
    private AppUserService appUserService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private ObjectMapper objectMapper;

    private static final String BODY = "{\"values\":{\"site_name_en\":\"My News\"}}";

    @TestConfiguration
    static class Config {
        @Bean
        AppProperty appProperty() {
            return new AppProperty();
        }
    }

    private List<RequestBuilder> adminEndpoints() {
        return List.of(
                get("/api/v1/settings"),
                put("/api/v1/settings/general").contentType(MediaType.APPLICATION_JSON).content(BODY),
                multipart("/api/v1/settings/images/logo").file(new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1}))
        );
    }

    private void assertAll(List<RequestBuilder> requests, ResultMatcher expected) throws Exception {
        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(expected);
        }
    }

    @Test
    void thePublicSettings_needNoLogin() throws Exception {
        when(systemSettingService.getPublic()).thenReturn(Map.of("site_name_en", "My News"));

        mockMvc.perform(get("/api/v1/settings/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.site_name_en").value("My News"));
    }

    @Test
    void withoutLogin_theAdminEndpoints_are401() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}"); // the body of the 401 answer
        assertAll(adminEndpoints(), status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    void aReader_isDeniedTheAdminEndpoints_butReadsThePublicSettings() throws Exception {
        assertAll(adminEndpoints(), status().isForbidden());
        mockMvc.perform(get("/api/v1/settings/public")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void anAuthor_isDeniedTheAdminEndpoints() throws Exception {
        assertAll(adminEndpoints(), status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void anEditor_isDeniedTheAdminEndpoints() throws Exception {
        assertAll(adminEndpoints(), status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theAdmin_mayUseEveryEndpoint() throws Exception {
        when(systemSettingService.getAll()).thenReturn(Map.of("general", Map.of("site_name_en", "My News")));
        when(systemSettingService.updateGroup(eq("general"), any())).thenReturn(Map.of("site_name_en", "My News"));
        when(systemSettingService.uploadImage(eq("logo"), any())).thenReturn(Map.of("logo_url", "/uploads/x.png"));

        mockMvc.perform(get("/api/v1/settings")).andExpect(status().isOk()).andExpect(jsonPath("$.payload.general.site_name_en").value("My News"));
        mockMvc.perform(put("/api/v1/settings/general").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/settings/images/logo").file(new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.logo_url").value("/uploads/x.png"));
        verify(systemSettingService).updateGroup(eq("general"), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anEmptyBody_isAValidationError_withTheCodeInTheBody() throws Exception {
        // the project's convention: a validation error of the request body answers 200 with the real code in the body
        mockMvc.perform(put("/api/v1/settings/general").contentType(MediaType.APPLICATION_JSON).content("{\"values\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.details.values").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aBusinessError_ofTheService_keepsItsRealHttpStatus() throws Exception {
        when(systemSettingService.updateGroup(eq("nope"), any()))
                .thenThrow(new CustomException(HttpStatus.NOT_FOUND, "Unknown settings group"));

        mockMvc.perform(put("/api/v1/settings/nope").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }
}
