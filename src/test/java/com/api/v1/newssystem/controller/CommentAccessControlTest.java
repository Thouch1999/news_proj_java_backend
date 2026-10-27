package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.SecurityConfig;
import com.api.v1.newssystem.security.CustomAccessDeniedHandler;
import com.api.v1.newssystem.security.JwtAuthEntrypoint;
import com.api.v1.newssystem.security.JwtAuthFilter;
import com.api.v1.newssystem.security.JwtService;
import com.api.v1.newssystem.service.AppUserService;
import com.api.v1.newssystem.service.CommentService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may call the comment endpoints, checked against the real filter chain with mocked services:
 * everybody reads, any logged-in user writes and deletes (own: the service decides), only EDITOR and ADMIN moderate.
 */
@WebMvcTest({NewsCommentController.class, CommentController.class, NewsController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class CommentAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;
    @MockitoBean
    private NewsService newsService;
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

    private static final String COMMENT = "{\"content\":\"Nice article\"}";

    private RequestBuilder read() {
        return get("/api/v1/news/1/comments");
    }

    private RequestBuilder write() {
        return post("/api/v1/news/1/comments").contentType(MediaType.APPLICATION_JSON).content(COMMENT);
    }

    private RequestBuilder deleteComment() {
        return delete("/api/v1/comments/5");
    }

    private RequestBuilder listAll() {
        return get("/api/v1/comments");
    }

    private RequestBuilder hide() {
        return put("/api/v1/comments/5/hide");
    }

    private RequestBuilder unhide() {
        return put("/api/v1/comments/5/unhide");
    }

    private RequestBuilder enable() {
        return put("/api/v1/news/1/comments/enable");
    }

    private RequestBuilder disable() {
        return put("/api/v1/news/1/comments/disable");
    }

    private void all(ResultMatcher expected, RequestBuilder... requests) throws Exception {
        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(expected);
        }
    }

    private void stubPages() {
        when(commentService.create(any(), any())).thenReturn(new com.api.v1.newssystem.model.response.CommentDto());
        when(commentService.listVisible(any(), anyInt(), anyInt())).thenReturn(Page.empty());
        when(commentService.listAll(any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());
    }

    @Test
    void anonymous_canOnlyReadComments() throws Exception {
        stubPages();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        all(status().isOk(), read());
        all(status().isUnauthorized(), write(), deleteComment(), listAll(), hide(), unhide(), enable(), disable());
    }

    @Test
    @WithMockUser(roles = "READER")
    void reader_canReadWriteAndDelete_butNotModerate() throws Exception {
        stubPages();

        all(status().isOk(), read(), write(), deleteComment());
        all(status().isForbidden(), listAll(), hide(), unhide(), enable(), disable());
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void author_canReadWriteAndDelete_butNotModerate() throws Exception {
        stubPages();

        all(status().isOk(), read(), write(), deleteComment());
        all(status().isForbidden(), listAll(), hide(), unhide(), enable(), disable());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void editor_canDoEverything() throws Exception {
        stubPages();

        all(status().isOk(), read(), write(), deleteComment(), listAll(), hide(), unhide(), enable(), disable());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canDoEverything() throws Exception {
        stubPages();

        all(status().isOk(), read(), write(), deleteComment(), listAll(), hide(), unhide(), enable(), disable());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void listAll_forwardsTheFilters() throws Exception {
        stubPages();

        mockMvc.perform(get("/api/v1/comments").param("status", "HIDDEN").param("newsId", "3").param("userId", "4").param("page", "2"))
                .andExpect(status().isOk());

        verify(commentService).listAll(com.api.v1.newssystem.model.enumerate.CommentStatus.HIDDEN, 3L, 4L, 2, 10);
    }

    @Test
    @WithMockUser(roles = "READER")
    void write_withABlankComment_isRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/v1/news/1/comments").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(jsonPath("$.code").value(400));

        org.mockito.Mockito.verifyNoInteractions(commentService);
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void theArticleManagementEndpoints_areUnchanged_byTheCommentRules() throws Exception {
        // GET /news/{id}/comments is public, but the rest of /api/v1/news/** still follows its own rules
        when(newsService.listManaged(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/news/manage")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/news/review")).andExpect(status().isForbidden());
    }
}
