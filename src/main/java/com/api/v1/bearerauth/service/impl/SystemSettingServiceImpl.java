package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.CacheConfig;
import com.api.v1.bearerauth.model.enumerate.ActivityAction;
import com.api.v1.bearerauth.model.enumerate.ActivityTargetType;
import com.api.v1.bearerauth.service.ActivityLogService;
import com.api.v1.bearerauth.util.ActivityDetails;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.SystemSetting;
import com.api.v1.bearerauth.model.enumerate.SettingGroup;
import com.api.v1.bearerauth.model.enumerate.SettingKey;
import com.api.v1.bearerauth.model.enumerate.SettingType;
import com.api.v1.bearerauth.model.request.SettingsUpdateRequest;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.repository.SystemSettingRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.FileStorageService;
import com.api.v1.bearerauth.service.SystemSettingService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import com.api.v1.bearerauth.util.SettingValueValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;

/**
 * The values are read from the cache (Caffeine, see CacheConfig); every change clears it after the transaction has
 * committed, so the next read loads the new values. A setting that has no row yet reads as its default.
 */
@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {
    static final long IMAGE_MAX_BYTES = 2L * 1024 * 1024;
    // no GIF and no SVG (an SVG can carry scripts); the favicon may also be an ICO
    private static final Set<String> IMAGE_TYPES = Set.of("png", "jpg", "webp");
    private static final Set<String> FAVICON_TYPES = Set.of("png", "jpg", "webp", "ico");
    private static final Map<String, SettingKey> IMAGE_KEYS = Map.of(
            "logo", SettingKey.LOGO_URL,
            "logo-dark", SettingKey.LOGO_DARK_URL,
            "favicon", SettingKey.FAVICON_URL,
            "share-image", SettingKey.SHARE_IMAGE_URL);

    private static final String ALL_VALUES = "values";
    private static final String PUBLIC_VALUES = "public";

    private final SystemSettingRepository systemSettingRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final CacheManager cacheManager;
    private final ActivityLogService activityLogService;

    // ---- reading ----

    @Override
    public Map<String, String> getPublic() {
        return cached(PUBLIC_VALUES, () -> {
            Map<String, String> result = new LinkedHashMap<>();
            values().forEach((setting, value) -> {
                if (setting.isPublicSetting()) {
                    result.put(setting.getKey(), value);
                }
            });
            return Map.copyOf(result);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> getAll() {
        Map<SettingKey, String> values = values();
        Map<String, Map<String, String>> grouped = new LinkedHashMap<>();
        for (SettingGroup group : SettingGroup.values()) {
            Map<String, String> inGroup = new LinkedHashMap<>();
            SettingKey.inGroup(group).forEach(setting -> inGroup.put(setting.getKey(), values.get(setting)));
            grouped.put(group.getPath(), inGroup);
        }
        return grouped;
    }

    @Override
    public int getNewsPerPage() {
        String value = values().get(SettingKey.NEWS_PER_PAGE);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Integer.parseInt(SettingKey.NEWS_PER_PAGE.getDefaultValue());
        }
    }

    @Override
    public boolean isCommentsEnabledDefault() {
        return Boolean.parseBoolean(values().get(SettingKey.COMMENTS_ENABLED_DEFAULT));
    }

    // ---- writing ----

    @Override
    @Transactional
    public Map<String, String> updateGroup(String groupPath, SettingsUpdateRequest request) {
        SettingGroup group = SettingGroup.fromPath(groupPath)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SETTING_GROUP_UNKNOWN.getDefaultMessage()));

        // check everything first: one wrong value means nothing is changed
        Map<String, String> problems = new TreeMap<>();
        Map<SettingKey, String> cleaned = new EnumMap<>(SettingKey.class);
        for (Map.Entry<String, String> entry : request.getValues().entrySet()) {
            SettingKey setting = SettingKey.fromKey(entry.getKey()).orElse(null);
            if (setting == null) {
                problems.put(String.valueOf(entry.getKey()), "is not a known setting");
                continue;
            }
            if (setting.getGroup() != group) {
                problems.put(setting.getKey(), "does not belong to the group " + group.getPath());
                continue;
            }
            String value = SettingValueValidator.clean(entry.getValue());
            String problem = SettingValueValidator.problem(setting, value);
            if (problem != null) {
                problems.put(setting.getKey(), problem);
            } else {
                cleaned.put(setting, SettingValueValidator.normalize(setting, value));
            }
        }
        if (!problems.isEmpty()) {
            throw new CustomException(
                    ApiResponseUtil.error(HttpStatus.BAD_REQUEST, MessageConstant.VALIDATION_FAILED.getDefaultMessage(), problems, null),
                    HttpStatus.BAD_REQUEST);
        }

        Map<String, SystemSetting> rows = rowsOf(new HashSet<>(SettingKey.inGroup(group)));
        AppUser admin = currentAdmin();
        List<SystemSetting> changed = new ArrayList<>();
        List<String> replacedFiles = new ArrayList<>();
        Map<String, Object> changedKeys = new LinkedHashMap<>();
        cleaned.forEach((setting, value) -> {
            SystemSetting row = rows.computeIfAbsent(setting.getKey(), key -> newRow(setting));
            if (!row.getSettingValue().equals(value)) {
                changedKeys.put(setting.getKey(), changeOf(setting, row.getSettingValue(), value));
            }
            if (setting.getType() == SettingType.IMAGE && !row.getSettingValue().equals(value)) {
                replacedFiles.add(row.getSettingValue()); // an image was cleared: its file goes
            }
            touch(row, value, admin);
            changed.add(row);
        });
        systemSettingRepository.saveAll(changed);
        if (!changedKeys.isEmpty()) {
            activityLogService.record(ActivityAction.SETTINGS_UPDATED, ActivityTargetType.SETTINGS, null, group.getPath(),
                    ActivityDetails.of("group", group.getPath(), "changed", changedKeys));
        }
        afterCommit(this::clearCache);
        afterCommit(() -> replacedFiles.forEach(fileStorageService::delete));

        // the answer comes from the rows, not from the cache: the change is not committed yet, and a read of the cache here
        // could keep values that are rolled back
        Map<String, String> result = new LinkedHashMap<>();
        for (SettingKey setting : SettingKey.inGroup(group)) {
            SystemSetting row = rows.get(setting.getKey());
            result.put(setting.getKey(), row == null ? setting.getDefaultValue() : row.getSettingValue());
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, String> uploadImage(String imageType, MultipartFile file) {
        SettingKey setting = IMAGE_KEYS.get(imageType);
        if (setting == null) {
            throw new CustomException(HttpStatus.NOT_FOUND, MessageConstant.SETTING_IMAGE_TYPE_UNKNOWN.getDefaultMessage());
        }
        FileStorageService.Rules rules = new FileStorageService.Rules(
                setting == SettingKey.FAVICON_URL ? FAVICON_TYPES : IMAGE_TYPES,
                IMAGE_MAX_BYTES,
                MessageConstant.SETTING_IMAGE_TOO_LARGE,
                MessageConstant.SETTING_IMAGE_TYPE_NOT_ALLOWED);
        String url = fileStorageService.store(file, rules);

        SystemSetting row = rowsOf(Set.of(setting)).computeIfAbsent(setting.getKey(), key -> newRow(setting));
        String previous = row.getSettingValue();
        touch(row, url, currentAdmin());
        systemSettingRepository.save(row);
        activityLogService.record(ActivityAction.SETTINGS_UPDATED, ActivityTargetType.SETTINGS, null, setting.getGroup().getPath(),
                ActivityDetails.of("group", setting.getGroup().getPath(),
                        "changed", Map.of(setting.getKey(), changeOf(setting, previous, url))));
        afterCommit(this::clearCache);
        afterCommit(() -> fileStorageService.delete(previous));

        return Map.of(setting.getKey(), url);
    }

    // ---- helpers ----

    // every setting, a missing row reads as the default; cached
    private Map<SettingKey, String> values() {
        Map<String, String> byKey = cached(ALL_VALUES, () -> {
            Map<String, String> loaded = new HashMap<>();
            for (SettingKey setting : SettingKey.values()) {
                loaded.put(setting.getKey(), setting.getDefaultValue());
            }
            systemSettingRepository.findAll().forEach(row -> {
                if (SettingKey.fromKey(row.getSettingKey()).isPresent()) {
                    loaded.put(row.getSettingKey(), row.getSettingValue());
                }
            });
            return Map.copyOf(loaded);
        });
        Map<SettingKey, String> result = new EnumMap<>(SettingKey.class);
        for (SettingKey setting : SettingKey.values()) {
            result.put(setting, byKey.get(setting.getKey()));
        }
        return result;
    }

    private Map<String, SystemSetting> rowsOf(Set<SettingKey> settings) {
        Map<String, SystemSetting> rows = new HashMap<>();
        systemSettingRepository.findAllBySettingKeyIn(settings.stream().map(SettingKey::getKey).toList())
                .forEach(row -> rows.put(row.getSettingKey(), row));
        return rows;
    }

    // what the activity log keeps of a changed setting: the value before and after for a public setting (nothing in it is
    // secret), and only the fact that it changed for any other
    private Object changeOf(SettingKey setting, String before, String after) {
        return setting.isPublicSetting() ? ActivityDetails.change(before, after) : "changed";
    }

    private SystemSetting newRow(SettingKey setting) {
        SystemSetting row = new SystemSetting();
        row.setSettingKey(setting.getKey());
        row.setSettingGroup(setting.getGroup().getPath());
        row.setValueType(setting.getType());
        row.setPublicSetting(setting.isPublicSetting());
        row.setSettingValue(setting.getDefaultValue());
        return row;
    }

    private void touch(SystemSetting row, String value, AppUser admin) {
        row.setSettingValue(value);
        row.setUpdatedBy(admin);
        row.setUpdatedAt(LocalDateTime.now());
    }

    private AppUser currentAdmin() {
        return appUserRepository.getReferenceById(currentUserService.getCurrentUser().getId());
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Callable<T> loader) {
        Cache cache = cacheManager.getCache(CacheConfig.SETTINGS_CACHE);
        return cache == null ? call(loader) : (T) cache.get(key, loader);
    }

    private static <T> T call(Callable<T> loader) {
        try {
            return loader.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void clearCache() {
        Cache cache = cacheManager.getCache(CacheConfig.SETTINGS_CACHE);
        if (cache != null) {
            cache.clear();
        }
        // the link previews show the site name, the descriptions and the images of the settings
        Cache previews = cacheManager.getCache(CacheConfig.PREVIEW_CACHE);
        if (previews != null) {
            previews.clear();
        }
    }

    // After the commit: the cache is cleared only when the change is really saved (a reader cannot load the old rows into
    // it meanwhile), and an old file is deleted only when the new value is saved. Without a transaction it runs at once.
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
