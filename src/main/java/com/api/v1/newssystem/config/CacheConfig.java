package com.api.v1.newssystem.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** The in-memory (Caffeine) caches of the application. */
@Configuration
@EnableCaching
public class CacheConfig {
    /** The system settings (see SystemSettingServiceImpl): cleared whenever a setting changes. */
    public static final String SETTINGS_CACHE = "settings";

    /** The link-preview pages (see PreviewServiceImpl), by article/category/home and language. */
    public static final String PREVIEW_CACHE = "preview";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SETTINGS_CACHE);
        // the expiry is only a safety net: a change of a setting clears the cache at once
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofHours(1)));
        // an edit of an article or of the settings clears the matching entries at once; ten minutes also bounds a change
        // of a category or a tag, which does not
        manager.registerCustomCache(PREVIEW_CACHE,
                Caffeine.newBuilder().maximumSize(2000).expireAfterWrite(Duration.ofMinutes(10)).build());
        return manager;
    }
}
