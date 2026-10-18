package com.api.v1.bearerauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperty {
    private final Jwt jwt = new Jwt();
    private final Otp otp = new Otp();
    private final Seed seed = new Seed();
    private final Cors cors = new Cors();
    private final Upload upload = new Upload();

    @Data
    public static class Upload {
        // folder for uploaded images; served publicly under /uploads/**
        private String dir = "uploads";
        private long maxFileSizeBytes = 5L * 1024 * 1024;
        private int maxImagesPerNews = 10;
    }

    @Data
    public static class Cors {
        // origins allowed to call the API from a browser (never "*")
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Data
    public static class Seed {
        private final SeedUser admin = new SeedUser();
        private final SeedUser editor = new SeedUser();
        private final SeedUser author = new SeedUser();
        private final SeedUser reader = new SeedUser();
        // DEVELOPMENT only: insert the 20 demo news articles (see DemoNewsSeeder); false unless application-dev.yaml turns it on
        private boolean demoNews = false;
    }

    @Data
    public static class SeedUser {
        private String username;
        private String email;
        private String password;
    }

    @Data
    public static class Jwt {
        private Long accessExpiration;
        private Long refreshExpiration;
        private String secret;
    }

    @Data
    public static class Otp {
        private int length = 6;
        private long ttlSeconds = 60;
        private long verifiedTtlMinutes = 10;
        private int maxAttempts = 5;
        private long resendCooldownSeconds = 60;
        private int hourlyLimit = 5;
    }
}
