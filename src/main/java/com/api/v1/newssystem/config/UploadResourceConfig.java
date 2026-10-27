package com.api.v1.newssystem.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

// Serves the files saved by the file storage service at /uploads/** (public, read only); videos with a long cache.
@Configuration
@RequiredArgsConstructor
public class UploadResourceConfig implements WebMvcConfigurer {
    private final AppProperty appProperty;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(appProperty.getUpload().getDir()).toAbsolutePath().normalize();
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/"; // the folder may not exist yet; it is created by the first upload
        }
        // The sponsor videos: the names are random and never reused, so the browser may keep a video for a year. The handler
        // answers HTTP range requests (206) by itself, which video seeking and Safari need.
        registry.addResourceHandler("/uploads/*.mp4", "/uploads/*.webm")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
