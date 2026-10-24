package com.api.v1.newssystem.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

// Serves the files saved by the file storage service at /uploads/** (public, read only).
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
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
