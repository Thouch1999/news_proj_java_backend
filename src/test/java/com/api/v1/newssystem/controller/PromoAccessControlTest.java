package com.api.v1.newssystem.controller;

import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.config.SecurityConfig;
import com.api.v1.newssystem.model.response.PromoDto;
import com.api.v1.newssystem.model.response.SponsorDto;
import com.api.v1.newssystem.security.ClientIpResolver;
import com.api.v1.newssystem.security.CustomAccessDeniedHandler;
import com.api.v1.newssystem.security.JwtAuthEntrypoint;
import com.api.v1.newssystem.security.JwtAuthFilter;
import com.api.v1.newssystem.security.JwtService;
import com.api.v1.newssystem.service.AppUserService;
import com.api.v1.newssystem.service.PromoService;
import com.api.v1.newssystem.service.SponsorService;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may call the promo endpoints, against the real filter chain: the public ones (pick, impression, click) need no
 * login, the management under /api/v1/admin/promos is for the ADMIN only. The services are mocked.
 */
@WebMvcTest({PromoController.class, SponsorController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class, ClientIpResolver.class})
class PromoAccessControlTest {
    private static final String STORED_URL = "https://sponsor.example/landing";
    private static final String BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36";
    private static final String SPONSOR_BODY = "{\"name\":\"Acme\",\"title\":\"Save\",\"ctaText\":\"Go\",\"targetUrl\":\"https://acme.example\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PromoService promoService;
    @MockitoBean
    private SponsorService sponsorService;
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

    private List<RequestBuilder> adminEndpoints() {
        return List.of(
                get("/api/v1/admin/promos"),
                get("/api/v1/admin/promos/5"),
                post("/api/v1/admin/promos").contentType(MediaType.APPLICATION_JSON).content(SPONSOR_BODY),
                put("/api/v1/admin/promos/5").contentType(MediaType.APPLICATION_JSON).content(SPONSOR_BODY),
                put("/api/v1/admin/promos/5/activate"),
                put("/api/v1/admin/promos/5/deactivate"),
                delete("/api/v1/admin/promos/5"),
                multipart("/api/v1/admin/promos/5/creatives/ARTICLE_SIDEBAR").file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1})),
                delete("/api/v1/admin/promos/5/creatives/ARTICLE_SIDEBAR"),
                multipart("/api/v1/admin/promos/5/logo").file(new MockMultipartFile("file", "l.png", "image/png", new byte[]{1})),
                delete("/api/v1/admin/promos/5/logo"),
                get("/api/v1/admin/promos/5/stats"),
                get("/api/v1/admin/promos/stats/summary"),
                get("/api/v1/admin/promos/stats/export")
        );
    }

    private void assertAll(List<RequestBuilder> requests, ResultMatcher expected) throws Exception {
        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(expected);
        }
    }

    // ---- public ----

    @Test
    void pick_needsNoLogin_andIsNeverCached() throws Exception {
        when(promoService.pick("ARTICLE_POPUP", "sports", "km")).thenReturn(Optional.of(
                new PromoDto(7L, "ARTICLE_POPUP", "Title", "Text", "Go", "Sponsored", "/uploads/a.png", 300, 250, "alt", "/api/v1/promos/7/click?placement=ARTICLE_SIDEBAR",
                        "IMAGE", null, null, null)));

        mockMvc.perform(get("/api/v1/promos").param("placement", "ARTICLE_POPUP").param("categorySlug", "sports").param("lang", "km"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.payload.id").value(7))
                .andExpect(jsonPath("$.payload.clickUrl").value("/api/v1/promos/7/click?placement=ARTICLE_SIDEBAR"));
    }

    @Test
    void pick_withNoSponsor_isAnEmptyPayload() throws Exception {
        when(promoService.pick(any(), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/promos").param("placement", "ARTICLE_POPUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload").doesNotExist());
    }

    @Test
    void impression_needsNoLogin_andPassesTheUserAgentAndTheConnectionAddress() throws Exception {
        mockMvc.perform(post("/api/v1/promos/7/impression")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placement\":\"ARTICLE_SIDEBAR\"}")
                        .header("User-Agent", BROWSER)
                        .header("X-Forwarded-For", "6.6.6.6")) // not believed: no trusted proxy is configured
                .andExpect(status().isOk());

        verify(promoService).recordImpression(eq(7L), eq("ARTICLE_SIDEBAR"), eq("127.0.0.1"), eq(BROWSER));
    }

    @Test
    void click_needsNoLogin_andRedirectsOnlyToTheStoredAddress() throws Exception {
        when(promoService.recordClick(eq(7L), eq("ARTICLE_SIDEBAR"), any(), any())).thenReturn(STORED_URL);

        // whatever the request says (an open redirect attempt), the target is the one the service returns
        mockMvc.perform(get("/api/v1/promos/7/click")
                        .param("placement", "ARTICLE_SIDEBAR")
                        .param("url", "https://evil.example")
                        .param("redirect", "https://evil.example")
                        .param("next", "//evil.example")
                        .header("Referer", "https://evil.example")
                        .header("User-Agent", BROWSER))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", STORED_URL))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void click_ofAnUnknownSponsor_isTheErrorOfTheService_notARedirect() throws Exception {
        when(promoService.recordClick(eq(99L), any(), any(), any()))
                .thenThrow(new CustomException(HttpStatus.NOT_FOUND, "Sponsor not found"));

        mockMvc.perform(get("/api/v1/promos/99/click").param("placement", "ARTICLE_SIDEBAR"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"));
    }

    // ---- management ----

    @Test
    void withoutLogin_theManagementEndpoints_are401() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}"); // the body of the 401 answer
        assertAll(adminEndpoints(), status().isUnauthorized());
        verifyNoInteractions(sponsorService);
    }

    @Test
    @WithMockUser(roles = "READER")
    void aReader_isDeniedTheManagement() throws Exception {
        assertAll(adminEndpoints(), status().isForbidden());
        verifyNoInteractions(sponsorService);
    }

    @Test
    @WithMockUser(roles = "AUTHOR")
    void anAuthor_isDeniedTheManagement() throws Exception {
        assertAll(adminEndpoints(), status().isForbidden());
        verifyNoInteractions(sponsorService);
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void anEditor_isDeniedTheManagement() throws Exception {
        assertAll(adminEndpoints(), status().isForbidden());
        verifyNoInteractions(sponsorService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theAdmin_mayManageSponsors() throws Exception {
        SponsorDto dto = new SponsorDto();
        dto.setId(5L);
        dto.setName("Acme");
        when(sponsorService.get(5L)).thenReturn(dto);
        when(sponsorService.create(any())).thenReturn(dto);
        when(sponsorService.setActive(5L, true)).thenReturn(dto);
        when(sponsorService.exportCsv(isNull(), isNull(), isNull())).thenReturn("a,b\r\n".getBytes());

        mockMvc.perform(get("/api/v1/admin/promos/5")).andExpect(status().isOk()).andExpect(jsonPath("$.payload.name").value("Acme"));
        mockMvc.perform(post("/api/v1/admin/promos").contentType(MediaType.APPLICATION_JSON).content(SPONSOR_BODY)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/promos/5/activate")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/promos/5")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/promos/stats/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"));
        verify(sponsorService).delete(5L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aSponsorWithoutAName_isAValidationError_withTheCodeInTheBody() throws Exception {
        // the project's convention: a validation error of the request body answers 200 with the real code in the body
        mockMvc.perform(post("/api/v1/admin/promos").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verify(sponsorService, never()).create(any());
    }

    @Test
    void videoEvents_needNoLogin_andPassTheUserAgentAndTheConnectionAddress() throws Exception {
        mockMvc.perform(post("/api/v1/promos/7/video-play")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placement\":\"ARTICLE_POPUP\"}")
                        .header("User-Agent", BROWSER)
                        .header("X-Forwarded-For", "6.6.6.6")) // not believed: no trusted proxy is configured
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/promos/7/video-complete")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placement\":\"ARTICLE_SIDEBAR\"}")
                        .header("User-Agent", BROWSER))
                .andExpect(status().isOk());

        verify(promoService).recordVideoPlay(eq(7L), eq("ARTICLE_POPUP"), eq("127.0.0.1"), eq(BROWSER));
        verify(promoService).recordVideoComplete(eq(7L), eq("ARTICLE_SIDEBAR"), eq("127.0.0.1"), eq(BROWSER));
    }

    @Test
    void videoEvents_needAPlacement() throws Exception {
        mockMvc.perform(post("/api/v1/promos/7/video-play").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(400)); // a validation error: HTTP 200, the real code in the body
    }

    @Test
    void theSidebar_answersTheSlideshow_withoutLogin_andNeverCached() throws Exception {
        PromoDto one = new PromoDto(7L, "ARTICLE_SIDEBAR", "Title", "Text", "Go", "Sponsored", "/uploads/a.png", 300, 250, "alt",
                "/api/v1/promos/7/click?placement=ARTICLE_SIDEBAR", "IMAGE", null, null, null);
        when(promoService.pickSidebar("sports", "en")).thenReturn(Optional.of(
                new com.api.v1.newssystem.model.response.PromoSidebarDto("GRID_2", 4, true, 8, List.of(one))));

        mockMvc.perform(get("/api/v1/promos").param("placement", "article_sidebar").param("categorySlug", "sports").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.payload.layout").value("GRID_2"))
                .andExpect(jsonPath("$.payload.perSlide").value(4))
                .andExpect(jsonPath("$.payload.rotate").value(true))
                .andExpect(jsonPath("$.payload.intervalSeconds").value(8))
                .andExpect(jsonPath("$.payload.items[0].id").value(7));
    }

    @Test
    void theSidebar_withNoSponsor_isAnEmptyPayload() throws Exception {
        when(promoService.pickSidebar(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/promos").param("placement", "ARTICLE_SIDEBAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload").doesNotExist());
    }
}
