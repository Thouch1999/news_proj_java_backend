package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.SecurityConfig;
import com.api.v1.newssystem.security.CustomAccessDeniedHandler;
import com.api.v1.newssystem.security.JwtAuthEntrypoint;
import com.api.v1.newssystem.security.JwtAuthFilter;
import com.api.v1.newssystem.security.JwtService;
import com.api.v1.newssystem.service.AppUserService;
import com.api.v1.newssystem.service.BookmarkService;
import com.api.v1.newssystem.service.NewsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bookmarks need a login but no special role: a reader can use them (while a reader still cannot use the article
 * management endpoints that share the /api/v1/news prefix). Checked against the real filter chain.
 */
@WebMvcTest({NewsBookmarkController.class, BookmarkController.class, NewsController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class BookmarkAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookmarkService bookmarkService;
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

    private RequestBuilder save() {
        return post("/api/v1/news/1/bookmark");
    }

    private RequestBuilder unsave() {
        return delete("/api/v1/news/1/bookmark");
    }

    private RequestBuilder mine() {
        return get("/api/v1/bookmarks");
    }

    private void all(ResultMatcher expected, RequestBuilder... requests) throws Exception {
        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(expected);
        }
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        all(status().isUnauthorized(), save(), unsave(), mine());
    }

    @Test
    @WithMockUser(roles = "READER")
    void reader_canUseBookmarks_butNotTheArticleManagement() throws Exception {
        when(bookmarkService.list(anyInt(), anyInt())).thenReturn(Page.empty());

        all(status().isOk(), save(), unsave(), mine());
        all(status().isForbidden(), get("/api/v1/news/manage"));
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void author_canUseBookmarks() throws Exception {
        when(bookmarkService.list(anyInt(), anyInt())).thenReturn(Page.empty());

        all(status().isOk(), save(), unsave(), mine());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void editor_canUseBookmarks() throws Exception {
        when(bookmarkService.list(anyInt(), anyInt())).thenReturn(Page.empty());

        all(status().isOk(), save(), unsave(), mine());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canUseBookmarks() throws Exception {
        when(bookmarkService.list(anyInt(), anyInt())).thenReturn(Page.empty());

        all(status().isOk(), save(), unsave(), mine());
    }

    @Test
    @WithMockUser(roles = "READER")
    void save_answers201WhenCreated_and200WhenItAlreadyExisted() throws Exception {
        when(bookmarkService.add(1L)).thenReturn(true, false);

        mockMvc.perform(save()).andExpect(jsonPath("$.code").value(201));
        mockMvc.perform(save()).andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(roles = "READER")
    void list_defaultsToTheFirstPageOfTen() throws Exception {
        when(bookmarkService.list(anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(mine()).andExpect(status().isOk());

        org.mockito.Mockito.verify(bookmarkService).list(1, 10);
    }
}
