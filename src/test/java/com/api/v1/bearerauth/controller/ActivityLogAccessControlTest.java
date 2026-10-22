package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.config.SecurityConfig;
import com.api.v1.bearerauth.model.enumerate.ActivityAction;
import com.api.v1.bearerauth.model.enumerate.ActivityTargetType;
import com.api.v1.bearerauth.model.request.ActivityLogFilter;
import com.api.v1.bearerauth.model.response.ActivityLogDto;
import com.api.v1.bearerauth.model.response.RecentActivityDto;
import com.api.v1.bearerauth.security.CustomAccessDeniedHandler;
import com.api.v1.bearerauth.security.JwtAuthEntrypoint;
import com.api.v1.bearerauth.security.JwtAuthFilter;
import com.api.v1.bearerauth.security.JwtService;
import com.api.v1.bearerauth.service.ActivityLogQueryService;
import com.api.v1.bearerauth.service.AppUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may read the activity log (the real filter chain, a mocked service) and that nothing can change it through the API:
 * the ADMIN reads everything, the EDITOR only the content feed, everybody else nothing.
 */
@WebMvcTest(ActivityLogController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class ActivityLogAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityLogQueryService activityLogQueryService;
    @MockitoBean
    private AppUserService appUserService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class Config {
        @Bean
        AppProperty appProperty() {
            return new AppProperty();
        }
    }

    private List<RequestBuilder> adminOnly() {
        return List.of(
                get("/api/v1/activity-logs"),
                get("/api/v1/activity-logs/5"),
                get("/api/v1/activity-logs/target/NEWS/5"),
                get("/api/v1/activity-logs/export"));
    }

    private void assertAll(List<RequestBuilder> requests, ResultMatcher expected) throws Exception {
        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(expected);
        }
    }

    private void stubReads() {
        ActivityLogDto dto = new ActivityLogDto(5L, 1L, "Admin", "ADMIN", "203.0.113.9", "UA", ActivityAction.NEWS_PUBLISHED,
                ActivityTargetType.NEWS, 9L, "Cup final", Map.of("from", "PENDING"), LocalDateTime.of(2026, 3, 1, 9, 30));
        when(activityLogQueryService.list(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(dto)));
        when(activityLogQueryService.get(5L)).thenReturn(dto);
        when(activityLogQueryService.history(any(), any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(dto)));
        when(activityLogQueryService.exportCsv(any())).thenReturn("id\r\n".getBytes());
        when(activityLogQueryService.recent(anyInt())).thenReturn(List.of(
                new RecentActivityDto(5L, ActivityAction.NEWS_PUBLISHED, ActivityTargetType.NEWS, 9L, "Cup final", "Admin", LocalDateTime.of(2026, 3, 1, 9, 30))));
    }

    @Test
    void withoutLogin_everythingIs401() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertAll(adminOnly(), status().isUnauthorized());
        mockMvc.perform(get("/api/v1/activity-logs/recent")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"READER"})
    void aReader_isDeniedEverything() throws Exception {
        assertAll(adminOnly(), status().isForbidden());
        mockMvc.perform(get("/api/v1/activity-logs/recent")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"AUTHOR"})
    void anAuthor_isDeniedEverything() throws Exception {
        assertAll(adminOnly(), status().isForbidden());
        mockMvc.perform(get("/api/v1/activity-logs/recent")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"EDITOR"})
    void anEditor_readsOnlyTheContentFeed() throws Exception {
        stubReads();

        assertAll(adminOnly(), status().isForbidden());
        mockMvc.perform(get("/api/v1/activity-logs/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload[0].targetLabel").value("Cup final"))
                .andExpect(jsonPath("$.payload[0].ipAddress").doesNotExist())
                .andExpect(jsonPath("$.payload[0].details").doesNotExist());
        verify(activityLogQueryService).recent(10);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void theAdmin_readsEverything() throws Exception {
        stubReads();

        mockMvc.perform(get("/api/v1/activity-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload[0].action").value("NEWS_PUBLISHED"))
                .andExpect(jsonPath("$.payload[0].ipAddress").value("203.0.113.9"));
        mockMvc.perform(get("/api/v1/activity-logs/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.details.from").value("PENDING"));
        mockMvc.perform(get("/api/v1/activity-logs/target/NEWS/9")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/activity-logs/recent?limit=5")).andExpect(status().isOk());
        verify(activityLogQueryService).history(ActivityTargetType.NEWS, 9L, 1, 20);
        verify(activityLogQueryService).recent(5);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void theFiltersReachTheService_severalActionsIncluded() throws Exception {
        stubReads();

        mockMvc.perform(get("/api/v1/activity-logs")
                        .param("actorId", "4")
                        .param("action", "NEWS_PUBLISHED", "NEWS_REJECTED")
                        .param("targetType", "NEWS")
                        .param("targetId", "9")
                        .param("from", "2026-01-05")
                        .param("to", "2026-01-10")
                        .param("search", "cup")
                        .param("page", "2").param("size", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<ActivityLogFilter> filter = ArgumentCaptor.forClass(ActivityLogFilter.class);
        verify(activityLogQueryService).list(filter.capture(), eq(2), eq(50));
        assertThat(filter.getValue()).isEqualTo(new ActivityLogFilter(4L, List.of(ActivityAction.NEWS_PUBLISHED, ActivityAction.NEWS_REJECTED),
                ActivityTargetType.NEWS, 9L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 10), "cup"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void theExport_isAUtf8CsvFile_withTheSameFilters() throws Exception {
        stubReads();

        mockMvc.perform(get("/api/v1/activity-logs/export").param("action", "LOGIN_FAILED").param("search", "x"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"activity-log.csv\""))
                .andExpect(header().string("Cache-Control", "no-store"));

        ArgumentCaptor<ActivityLogFilter> filter = ArgumentCaptor.forClass(ActivityLogFilter.class);
        verify(activityLogQueryService).exportCsv(filter.capture());
        assertThat(filter.getValue().actions()).containsExactly(ActivityAction.LOGIN_FAILED);
        assertThat(filter.getValue().search()).isEqualTo("x");
    }

    // ---------------- an entry cannot be changed ----------------

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void evenTheAdmin_cannotWriteChangeOrDelete() throws Exception {
        stubReads();
        List<RequestBuilder> writes = List.of(
                post("/api/v1/activity-logs").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/activity-logs/5").contentType(MediaType.APPLICATION_JSON).content("{}"),
                patch("/api/v1/activity-logs/5").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/v1/activity-logs/5"),
                delete("/api/v1/activity-logs"),
                post("/api/v1/activity-logs/5/delete"),
                delete("/api/v1/activity-logs/target/NEWS/9"));

        for (RequestBuilder write : writes) {
            int code = mockMvc.perform(write).andReturn().getResponse().getStatus();
            assertThat(code < 200 || code >= 300).as(write.toString() + " -> " + code).isTrue();
        }
    }

    @Test
    void theController_hasReadOnlyEndpointsOnly() {
        for (Method method : ActivityLogController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertThat(method.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)).as(method.getName()).isTrue();
            assertThat(method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)).isFalse();
            assertThat(method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)).isFalse();
            assertThat(method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class)).isFalse();
            assertThat(method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)).isFalse();
            assertThat(method.isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class)).isFalse();
        }
    }

    @Test
    void theQueryService_andTheRepository_offerNoWayToChangeAnEntry() {
        for (Method method : ActivityLogQueryService.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("update").doesNotStartWith("delete").doesNotStartWith("save")
                    .doesNotStartWith("create").doesNotStartWith("remove");
        }
        for (Method method : com.api.v1.bearerauth.repository.ActivityLogRepository.class.getDeclaredMethods()) {
            if (!method.getName().equals("save") && !method.getName().equals("deleteOlderThan")) {
                assertThat(method.getName()).doesNotStartWith("update").doesNotStartWith("delete").doesNotStartWith("remove");
            }
        }
        assertThat(org.springframework.data.repository.CrudRepository.class.isAssignableFrom(com.api.v1.bearerauth.repository.ActivityLogRepository.class)).isFalse();
        // the entity has no setter
        for (Method method : com.api.v1.bearerauth.model.entity.ActivityLog.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("set");
        }
    }
}
