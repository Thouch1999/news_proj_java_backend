package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.config.SecurityConfig;
import com.api.v1.bearerauth.model.response.PreviewDto;
import com.api.v1.bearerauth.security.CustomAccessDeniedHandler;
import com.api.v1.bearerauth.security.JwtAuthEntrypoint;
import com.api.v1.bearerauth.security.JwtAuthFilter;
import com.api.v1.bearerauth.security.JwtService;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.service.PreviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTML of the link previews, rendered by the real Thymeleaf template: the Open Graph tags, the escaping, UTF-8,
 * the 404 that shows only the home content, the cache header and that no login is needed.
 */
@WebMvcTest(PreviewController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class PreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreviewService previewService;
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

    private static PreviewDto.PreviewDtoBuilder article(String title) {
        return PreviewDto.builder()
                .language("en").pageTitle(title + " | Ponleu News").title(title).description("A short summary.")
                .ogType("article").siteName("Ponleu News").url("https://ponleunews.com/news/my-article").locale("en_US")
                .imageUrl("https://api.ponleunews.com/uploads/a.png").imageWidth(1200).imageHeight(630).imageAlt(title)
                .publishedTime("2026-03-01T09:30:00+07:00").modifiedTime("2026-03-02T10:00:00+07:00")
                .section("Sports").tags(List.of("Football", "Cup"));
    }

    private static PreviewDto home() {
        return PreviewDto.builder()
                .language("en").pageTitle("Ponleu News").title("Ponleu News").description("All the news")
                .ogType("website").siteName("Ponleu News").url("https://ponleunews.com/").locale("en_US")
                .imageUrl("https://api.ponleunews.com/uploads/logo.png").imageAlt("Ponleu News").tags(List.of()).build();
    }

    private String html(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void publishedArticle_hasAllTheTags_withoutALogin() throws Exception {
        when(previewService.news(eq("my-article"), isNull())).thenReturn(Optional.of(article("Cup final").build()));

        mockMvc.perform(get("/preview/news/my-article"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "public, max-age=600"));
        String html = html("/preview/news/my-article");

        assertThat(html).contains(
                "<title>Cup final | Ponleu News</title>",
                "<meta name=\"description\" content=\"A short summary.\">",
                "<link rel=\"canonical\" href=\"https://ponleunews.com/news/my-article\">",
                "<meta property=\"og:type\" content=\"article\">",
                "<meta property=\"og:site_name\" content=\"Ponleu News\">",
                "<meta property=\"og:title\" content=\"Cup final\">",
                "<meta property=\"og:description\" content=\"A short summary.\">",
                "<meta property=\"og:url\" content=\"https://ponleunews.com/news/my-article\">",
                "<meta property=\"og:locale\" content=\"en_US\">",
                "<meta property=\"og:image\" content=\"https://api.ponleunews.com/uploads/a.png\">",
                "<meta property=\"og:image:width\" content=\"1200\">",
                "<meta property=\"og:image:height\" content=\"630\">",
                "<meta property=\"og:image:alt\" content=\"Cup final\">",
                "<meta property=\"article:published_time\" content=\"2026-03-01T09:30:00+07:00\">",
                "<meta property=\"article:modified_time\" content=\"2026-03-02T10:00:00+07:00\">",
                "<meta property=\"article:section\" content=\"Sports\">",
                "<meta property=\"article:tag\" content=\"Football\">",
                "<meta property=\"article:tag\" content=\"Cup\">",
                "<meta name=\"twitter:card\" content=\"summary_large_image\">",
                "<meta name=\"twitter:title\" content=\"Cup final\">",
                "<meta name=\"twitter:description\" content=\"A short summary.\">",
                "<meta name=\"twitter:image\" content=\"https://api.ponleunews.com/uploads/a.png\">",
                "<meta http-equiv=\"refresh\" content=\"0;url=https://ponleunews.com/news/my-article\">",
                "<h1>Cup final</h1>",
                ">Read the article</a>");
        assertThat(html).contains("href=\"https://ponleunews.com/news/my-article\"");
    }

    @Test
    void imageSizeIsLeftOut_whenNotKnown() throws Exception {
        when(previewService.home(any())).thenReturn(home());

        String html = html("/preview");

        assertThat(html).contains("og:image\"").doesNotContain("og:image:width").doesNotContain("og:image:height");
    }

    @Test
    void quotesAndAngleBrackets_inTheTitleAndSummary_areEscaped() throws Exception {
        String title = "He said \"hi\" <script>alert('x')</script> & more";
        when(previewService.news(eq("my-article"), isNull())).thenReturn(Optional.of(
                article(title).pageTitle(title).description("<b>bold</b> \"q\" & <i>").imageAlt(title).tags(List.of("a\"b<c>")).build()));

        String html = html("/preview/news/my-article");

        assertThat(html).doesNotContain("<script>").doesNotContain("<b>bold").doesNotContain("<i>");
        assertThat(html).contains("content=\"He said &quot;hi&quot; &lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; more\"");
        assertThat(html).contains("<h1>He said &quot;hi&quot; &lt;script&gt;");
        assertThat(html).contains("content=\"&lt;b&gt;bold&lt;/b&gt; &quot;q&quot; &amp; &lt;i&gt;\"");
        assertThat(html).contains("<meta property=\"article:tag\" content=\"a&quot;b&lt;c&gt;\">");
        assertThat(html).contains("<title>He said &quot;hi&quot; &lt;script&gt;");
    }

    @Test
    void khmerTitle_isKhmerLocale_andUtf8() throws Exception {
        PreviewDto khmer = article("ព័ត៌មានថ្មីៗនៅកម្ពុជា").locale("km_KH").language("km").siteName("ព័ត៌មានពន្លឺ")
                .description("សង្ខេបអត្ថបទ").tags(List.of("កីឡា")).build();
        when(previewService.news(eq("my-article"), eq("km"))).thenReturn(Optional.of(khmer));

        var result = mockMvc.perform(get("/preview/news/my-article").param("lang", "km"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andReturn();
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<meta property=\"og:locale\" content=\"km_KH\">");
        assertThat(html).contains("<meta property=\"og:title\" content=\"ព័ត៌មានថ្មីៗនៅកម្ពុជា\">");
        assertThat(html).contains("<meta property=\"og:description\" content=\"សង្ខេបអត្ថបទ\">");
        assertThat(html).contains("<meta property=\"article:tag\" content=\"កីឡា\">");
        assertThat(html).contains("<meta charset=\"UTF-8\">");
        // the bytes are UTF-8: the Khmer letter KA (U+1780) is E1 9E 80
        assertThat(result.getResponse().getContentAsByteArray()).containsSequence((byte) 0xE1, (byte) 0x9E, (byte) 0x80);
    }

    @Test
    void anArticleThatIsNotPublished_is404_withTheHomeContent_neverItsTitle() throws Exception {
        when(previewService.news(eq("secret-draft"), isNull())).thenReturn(Optional.empty());
        when(previewService.home(isNull())).thenReturn(home());

        var result = mockMvc.perform(get("/preview/news/secret-draft"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn();
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<meta property=\"og:title\" content=\"Ponleu News\">")
                .contains("<meta property=\"og:type\" content=\"website\">")
                .doesNotContain("secret-draft").doesNotContain("article:");
    }

    @Test
    void aCategoryThatDoesNotExist_is404_withTheHomeContent() throws Exception {
        when(previewService.category(eq("nothing"), isNull())).thenReturn(Optional.empty());
        when(previewService.home(isNull())).thenReturn(home());

        mockMvc.perform(get("/preview/category/nothing")).andExpect(status().isNotFound());
        assertThat(html("/preview/category/nothing")).contains("content=\"Ponleu News\"").doesNotContain("nothing");
    }

    @Test
    void category_isAWebsitePreview() throws Exception {
        when(previewService.category(eq("sports"), isNull())).thenReturn(Optional.of(
                PreviewDto.builder().language("en").pageTitle("Sports | Ponleu News").title("Sports").description("Matches")
                        .ogType("website").siteName("Ponleu News").url("https://ponleunews.com/category/sports").locale("en_US")
                        .tags(List.of()).build()));

        mockMvc.perform(get("/preview/category/sports")).andExpect(status().isOk());
        String html = html("/preview/category/sports");

        assertThat(html).contains("<meta property=\"og:type\" content=\"website\">")
                .contains("<link rel=\"canonical\" href=\"https://ponleunews.com/category/sports\">")
                .doesNotContain("og:image").doesNotContain("article:");
    }

    @Test
    void home_isOpen_andPassesTheLanguage() throws Exception {
        when(previewService.home(any())).thenReturn(home());

        mockMvc.perform(get("/preview").param("lang", "km"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=600"));

        verify(previewService).home("km");
    }

    @Test
    void thePreviewPaths_areReadOnly() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        mockMvc.perform(post("/preview/news/my-article")).andExpect(status().isUnauthorized());
    }
}
