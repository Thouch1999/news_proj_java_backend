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
    private final Notification notification = new Notification();
    private final ActivityLog activityLog = new ActivityLog();
    // public URL of the React site (e.g. https://ponleunews.com): the canonical and og:url of the link previews (/preview/**)
    private String frontendUrl = "";
    // public URL of this backend: makes the image paths of the link previews absolute (e.g. /uploads/x.png)
    private String publicBaseUrl = "";
    // email of the user marked as the super admin on the first start, when no super admin exists yet (never used again)
    private String superAdminEmail = "";

    @Data
    public static class ActivityLog {
        // entries older than this many days are deleted by the cleanup job
        private int retentionDays = 365;
        // when the cleanup job runs (Spring cron: second minute hour day month weekday)
        private String cleanupCron = "0 45 3 * * *";
        // proxies (addresses or CIDR ranges, e.g. 10.0.0.0/8) whose X-Forwarded-For header is believed; empty: never
        private List<String> trustedProxies = new ArrayList<>();
    }

    @Data
    public static class Notification {
        // read notifications older than this many days are deleted by the cleanup job
        private int retentionDays = 90;
        // when the cleanup job runs (Spring cron: second minute hour day month weekday)
        private String cleanupCron = "0 30 3 * * *";
    }

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
        // an ADMIN account that app.super-admin-email can point to on the first start
        private final SeedUser superAdmin = new SeedUser();
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
