package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.SecurityConfig;
import com.api.v1.newssystem.model.enumerate.ReportInterval;
import com.api.v1.newssystem.security.CustomAccessDeniedHandler;
import com.api.v1.newssystem.security.JwtAuthEntrypoint;
import com.api.v1.newssystem.security.JwtAuthFilter;
import com.api.v1.newssystem.security.JwtService;
import com.api.v1.newssystem.service.AppUserService;
import com.api.v1.newssystem.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may read which report, checked against the real filter chain with a mocked service: the whole-site reports are
 * for EDITOR and ADMIN, /author is for AUTHOR only.
 */
@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class ReportAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;
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

    private RequestBuilder[] siteReports() {
        return new RequestBuilder[]{
                get("/api/v1/reports/summary"),
                get("/api/v1/reports/news-by-category"),
                get("/api/v1/reports/most-viewed"),
                get("/api/v1/reports/most-commented"),
                get("/api/v1/reports/published"),
                get("/api/v1/reports/views"),
                get("/api/v1/reports/trends"),
                get("/api/v1/reports/activity")
        };
    }

    // the reports an AUTHOR may read too (the service then limits them to the author's own articles)
    private RequestBuilder[] scopedReports() {
        return new RequestBuilder[]{
                get("/api/v1/reports/most-viewed"),
                get("/api/v1/reports/most-commented"),
                get("/api/v1/reports/published"),
                get("/api/v1/reports/views"),
                get("/api/v1/reports/trends"),
                get("/api/v1/reports/activity")
        };
    }

    // the reports that stay for EDITOR and ADMIN only
    private RequestBuilder[] staffOnlyReports() {
        return new RequestBuilder[]{
                get("/api/v1/reports/summary"),
                get("/api/v1/reports/news-by-category")
        };
    }

    private void all(ResultMatcher expected, RequestBuilder... requests) throws Exception {
        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(expected);
        }
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        all(status().isUnauthorized(), siteReports());
        all(status().isUnauthorized(), get("/api/v1/reports/author"));
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void editor_readsTheSiteReports_butNotTheAuthorDashboard() throws Exception {
        all(status().isOk(), siteReports());
        all(status().isForbidden(), get("/api/v1/reports/author"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_readsTheSiteReports_butNotTheAuthorDashboard() throws Exception {
        all(status().isOk(), siteReports());
        all(status().isForbidden(), get("/api/v1/reports/author"));
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void author_readsTheirOwnDashboardAndTheScopedReports_butNotTheSummaryOrCategories() throws Exception {
        all(status().isOk(), get("/api/v1/reports/author"));
        all(status().isOk(), scopedReports());
        all(status().isForbidden(), staffOnlyReports());
    }

    @Test
    @WithMockUser(roles = "READER")
    void reader_readsNothing() throws Exception {
        all(status().isForbidden(), siteReports());
        all(status().isForbidden(), get("/api/v1/reports/author"));
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void parameters_areForwarded_withDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/reports/most-viewed").param("limit", "3").param("from", "2026-01-05").param("to", "2026-01-10"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/most-commented")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/published").param("interval", "MONTH").param("from", "2026-01-01")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/published")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/views").param("interval", "MONTH").param("to", "2026-02-01")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/trends").param("from", "2026-01-01").param("to", "2026-01-31")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reports/activity")).andExpect(status().isOk());

        verify(reportService).mostViewed(3, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 10));
        verify(reportService).mostCommented(10, null, null);
        verify(reportService).publishedOverTime(ReportInterval.MONTH, LocalDate.of(2026, 1, 1), null);
        verify(reportService).publishedOverTime(ReportInterval.DAY, null, null);
        verify(reportService).viewsOverTime(ReportInterval.MONTH, null, LocalDate.of(2026, 2, 1));
        verify(reportService).trends(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        verify(reportService).recentActivity(8);
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void aBadDateOrInterval_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/reports/published").param("interval", "WEEK")).andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/v1/reports/most-viewed").param("from", "05-01-2026")).andExpect(jsonPath("$.code").value(400));
    }
}
