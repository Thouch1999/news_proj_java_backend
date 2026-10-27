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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A file saved in the upload folder is served to anyone at /uploads/<name>
 * (the folder is a temporary one, nothing is written to the project).
 */
@WebMvcTest(NewsController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, CustomAccessDeniedHandler.class, JwtAuthEntrypoint.class})
class UploadServingTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] VIDEO = "0123456789abcdefghij".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final Path FOLDER = createFolder();

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

    // AppProperty is bound from the properties, so the temporary folder is given as a property
    @DynamicPropertySource
    static void uploadFolder(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", FOLDER::toString);
    }

    @TestConfiguration
    static class Config {
        @Bean
        AppProperty appProperty() {
            return new AppProperty();
        }
    }

    private static Path createFolder() {
        try {
            Path folder = Files.createTempDirectory("uploads-test");
            Files.write(folder.resolve("photo.png"), PNG);
            Files.write(folder.resolve("clip.mp4"), VIDEO);
            Files.write(folder.resolve("clip.webm"), VIDEO);
            return folder;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void deleteFolder() throws IOException {
        try (Stream<Path> files = Files.walk(FOLDER)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void anAnonymousVisitorCanDownloadAStoredFile() throws Exception {
        byte[] body = mockMvc.perform(get("/uploads/photo.png"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isEqualTo(PNG);
    }

    @Test
    void aMissingFileIsA404_andTheFolderCannotBeEscaped() throws Exception {
        mockMvc.perform(get("/uploads/other.png")).andExpect(status().isNotFound());
        mockMvc.perform(get("/uploads/../photo.png")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/uploads/%2e%2e/photo.png")).andExpect(status().is4xxClientError());
    }

    @Test
    void aVideo_isServedWithALongCache_andAcceptsRangeRequests() throws Exception {
        mockMvc.perform(get("/uploads/clip.mp4"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("max-age=31536000"),
                        org.hamcrest.Matchers.containsString("public"),
                        org.hamcrest.Matchers.containsString("immutable"))))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Type", "video/mp4"));

        // video seeking and Safari ask for a part of the file
        byte[] part = mockMvc.perform(get("/uploads/clip.mp4").header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/" + VIDEO.length))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(part, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("2345");

        mockMvc.perform(get("/uploads/clip.webm").header("Range", "bytes=0-0"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Type", "video/webm"));
    }

    @Test
    void anImage_isStillServedWithoutTheLongVideoCache() throws Exception {
        String cache = mockMvc.perform(get("/uploads/photo.png"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Cache-Control");
        assertThat(cache == null || !cache.contains("immutable")).isTrue();
    }
}
