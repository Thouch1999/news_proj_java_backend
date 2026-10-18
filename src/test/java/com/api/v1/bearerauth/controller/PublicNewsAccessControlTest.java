package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.config.SecurityConfig;
import com.api.v1.bearerauth.security.CustomAccessDeniedHandler;
import com.api.v1.bearerauth.security.JwtAuthEntrypoint;
import com.api.v1.bearerauth.security.JwtAuthFilter;
import com.api.v1.bearerauth.security.JwtService;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.service.PublicNewsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public reading endpoints need no login (anonymous, reader, author ... all get 200), while writing is still
 * not possible there. Checked against the real filter chain with a mocked service.
 */
@WebMvcTest(PublicNewsController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class PublicNewsAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicNewsService publicNewsService;
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

    private void stubLists() {
        when(publicNewsService.list(any(), any(), any(), any(), any(), anyInt(), any())).thenReturn(Page.empty());
        when(publicNewsService.latest(anyInt())).thenReturn(List.of());
        when(publicNewsService.mostViewed(anyInt())).thenReturn(List.of());
        when(publicNewsService.categorySections(anyInt())).thenReturn(List.of());
    }

    @Test
    void anonymousVisitor_canReadEveryPublicEndpoint() throws Exception {
        stubLists();

        mockMvc.perform(get("/api/v1/public/news")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/news/latest")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/news/most-viewed")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/news/category-sections")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/news/some-article")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "READER")
    void loggedInReader_canReadToo() throws Exception {
        stubLists();

        mockMvc.perform(get("/api/v1/public/news")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/news/some-article")).andExpect(status().isOk());
    }

    @Test
    void list_forwardsTheSearchFilters_andDefaultsThePage() throws Exception {
        stubLists();

        mockMvc.perform(get("/api/v1/public/news")
                        .param("keyword", "ព័ត៌មាន")
                        .param("category", "sports")
                        .param("tag", "football")
                        .param("from", "2026-01-05")
                        .param("to", "2026-01-10"))
                .andExpect(status().isOk());

        verify(publicNewsService).list(eq("ព័ត៌មាន"), eq("sports"), eq("football"),
                eq(LocalDate.of(2026, 1, 5)), eq(LocalDate.of(2026, 1, 10)), eq(1), isNull()); // no size: the service uses news_per_page
    }

    @Test
    void list_forwardsASizeThatIsSent() throws Exception {
        stubLists();

        mockMvc.perform(get("/api/v1/public/news").param("size", "12")).andExpect(status().isOk());

        verify(publicNewsService).list(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(12));
    }

    @Test
    void detail_ofAnArticleThatIsNotPublished_is404() throws Exception {
        when(publicNewsService.getBySlug(eq("draft-article"), any()))
                .thenThrow(new CustomException(HttpStatus.NOT_FOUND, MessageConstant.NEWS_NOT_FOUND.getDefaultMessage()));

        mockMvc.perform(get("/api/v1/public/news/draft-article"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void publicPath_isReadOnly_noWritesWithoutLogin() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        mockMvc.perform(post("/api/v1/public/news")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/public/news/some-article")).andExpect(status().isUnauthorized());
    }

    @Test
    void managementEndpoints_stillNeedALogin() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        mockMvc.perform(get("/api/v1/news/manage")).andExpect(status().isUnauthorized());
    }
}
