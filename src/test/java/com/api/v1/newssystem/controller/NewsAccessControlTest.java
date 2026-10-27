package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.SecurityConfig;
import com.api.v1.newssystem.security.CustomAccessDeniedHandler;
import com.api.v1.newssystem.security.JwtAuthEntrypoint;
import com.api.v1.newssystem.security.JwtAuthFilter;
import com.api.v1.newssystem.security.JwtService;
import com.api.v1.newssystem.service.AppUserService;
import com.api.v1.newssystem.service.NewsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Only an AUTHOR, an EDITOR or an ADMIN may use the article endpoints (reader and anonymous users may not), the
 * workflow endpoints are limited per role, and the uploaded files are public. Checked against the real filter chain with a mocked service.
 */
@WebMvcTest(NewsController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class NewsAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsService newsService;
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

    private List<RequestBuilder> endpoints() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        return List.<RequestBuilder>of(
                post("/api/v1/news").contentType(MediaType.APPLICATION_JSON).content(JSON),
                get("/api/v1/news/mine"),
                get("/api/v1/news/mine/1"),
                get("/api/v1/news/manage"),
                get("/api/v1/news/manage/1"),
                put("/api/v1/news/1").contentType(MediaType.APPLICATION_JSON).content(JSON),
                delete("/api/v1/news/1"),
                multipart("/api/v1/news/1/images").file(file),
                put("/api/v1/news/1/images/2").contentType(MediaType.APPLICATION_JSON).content(JSON),
                delete("/api/v1/news/1/images/2"),
                multipart("/api/v1/news/1/thumbnail").file(file),
                delete("/api/v1/news/1/thumbnail")
        );
    }

    private void assertAll(ResultMatcher expected) throws Exception {
        for (RequestBuilder endpoint : endpoints()) {
            mockMvc.perform(endpoint).andExpect(expected);
        }
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void author_canUseEveryEndpoint() throws Exception {
        when(newsService.listMine(any(), anyInt(), anyInt())).thenReturn(Page.empty());
        when(newsService.listManaged(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());

        assertAll(status().isOk());
    }

    @Test
    @WithMockUser(roles = "READER")
    void reader_isDenied() throws Exception {
        assertAll(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void editor_canUseEveryManagementEndpoint() throws Exception {
        // an editor reads and edits articles; what each role may do with an article is decided in NewsService
        when(newsService.listMine(any(), anyInt(), anyInt())).thenReturn(Page.empty());
        when(newsService.listManaged(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());

        assertAll(status().isOk());
    }

    // ---- editorial workflow endpoints ----

    private static final String REASON = "{\"reason\":\"Add sources\"}";

    private RequestBuilder submit() {
        return post("/api/v1/news/1/submit");
    }

    private RequestBuilder publish() {
        return post("/api/v1/news/1/publish");
    }

    private RequestBuilder reject() {
        return post("/api/v1/news/1/reject").contentType(MediaType.APPLICATION_JSON).content(REASON);
    }

    private RequestBuilder archive() {
        return post("/api/v1/news/1/archive");
    }

    private RequestBuilder restore() {
        return post("/api/v1/news/1/restore");
    }

    private RequestBuilder reviewQueue() {
        return get("/api/v1/news/review");
    }

    private RequestBuilder history() {
        return get("/api/v1/news/1/history");
    }

    private void assertOnly(ResultMatcher expected, RequestBuilder... endpoints) throws Exception {
        for (RequestBuilder endpoint : endpoints) {
            mockMvc.perform(endpoint).andExpect(expected);
        }
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void workflow_author_mayOnlySubmitAndReadHistory() throws Exception {
        assertOnly(status().isOk(), submit(), history());
        assertOnly(status().isForbidden(), publish(), reject(), archive(), restore(), reviewQueue());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void workflow_editor_mayReviewButNotSubmitOrRestore() throws Exception {
        when(newsService.listReviewQueue(anyInt(), anyInt())).thenReturn(Page.empty());

        assertOnly(status().isOk(), publish(), reject(), archive(), reviewQueue(), history());
        assertOnly(status().isForbidden(), submit(), restore());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void workflow_admin_mayDoEverything() throws Exception {
        when(newsService.listReviewQueue(anyInt(), anyInt())).thenReturn(Page.empty());

        assertOnly(status().isOk(), submit(), publish(), reject(), archive(), restore(), reviewQueue(), history());
    }

    @Test
    @WithMockUser(roles = "READER")
    void workflow_reader_isDenied() throws Exception {
        assertOnly(status().isForbidden(), submit(), publish(), reject(), archive(), restore(), reviewQueue(), history());
    }

    @Test
    void workflow_anonymous_isUnauthorized() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertOnly(status().isUnauthorized(), submit(), publish(), reject(), archive(), restore(), reviewQueue(), history());
    }

    // ---- scheduled publishing ----

    private static final String SCHEDULE = "{\"scheduledAt\":\"2026-10-20T00:00:00Z\"}";

    private RequestBuilder[] scheduleEndpoints() {
        return new RequestBuilder[]{
                post("/api/v1/news/1/schedule").contentType(MediaType.APPLICATION_JSON).content(SCHEDULE),
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/news/1/schedule")
                        .contentType(MediaType.APPLICATION_JSON).content(SCHEDULE),
                delete("/api/v1/news/1/schedule"),
                get("/api/v1/news/scheduled/upcoming")};
    }

    @Test
    @WithMockUser(roles = {"EDITOR"})
    void schedule_editor_isAllowed() throws Exception {
        assertOnly(status().isOk(), scheduleEndpoints());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void schedule_admin_isAllowed() throws Exception {
        assertOnly(status().isOk(), scheduleEndpoints());
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void schedule_author_isForbidden() throws Exception {
        assertOnly(status().isForbidden(), scheduleEndpoints());
    }

    @Test
    @WithMockUser(roles = "READER")
    void schedule_reader_isForbidden() throws Exception {
        assertOnly(status().isForbidden(), scheduleEndpoints());
    }

    @Test
    void schedule_anonymous_isUnauthorized() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertOnly(status().isUnauthorized(), scheduleEndpoints());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canUseEveryEndpoint() throws Exception {
        when(newsService.listManaged(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());
        when(newsService.listMine(any(), anyInt(), anyInt())).thenReturn(Page.empty());

        assertAll(status().isOk());
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertAll(status().isUnauthorized());
    }

    @Test
    void uploadedFiles_arePublic() throws Exception {
        // no such file: 404 (not found), which shows the request was not stopped by security (401)
        mockMvc.perform(get("/uploads/missing.png")).andExpect(status().isNotFound());
    }
}
