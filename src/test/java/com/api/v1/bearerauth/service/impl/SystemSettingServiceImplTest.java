package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.CacheConfig;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.SystemSetting;
import com.api.v1.bearerauth.model.enumerate.SettingKey;
import com.api.v1.bearerauth.model.request.SettingsUpdateRequest;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.repository.SystemSettingRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The rules of the system settings, with the repository mocked and a real Caffeine cache. */
@ExtendWith(MockitoExtension.class)
class SystemSettingServiceImplTest {
    private static final long ADMIN_ID = 1L;

    @Mock
    private SystemSettingRepository systemSettingRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private FileStorageService fileStorageService;

    private final CacheManager cacheManager = new CacheConfig().cacheManager();
    private final List<SystemSetting> table = new ArrayList<>(); // what the "database" holds
    private SystemSettingServiceImpl service;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        service = new SystemSettingServiceImpl(systemSettingRepository, appUserRepository, currentUserService,
                fileStorageService, cacheManager);
        admin = new AppUser();
        admin.setId(ADMIN_ID);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(admin);
        lenient().when(appUserRepository.getReferenceById(anyLong())).thenReturn(admin);
        lenient().when(systemSettingRepository.findAll()).thenAnswer(call -> List.copyOf(table));
        lenient().when(systemSettingRepository.findAllBySettingKeyIn(anyCollection())).thenAnswer(call -> {
            Collection<String> keys = call.getArgument(0);
            return table.stream().filter(row -> keys.contains(row.getSettingKey())).toList();
        });
        // saving puts a new row into the table; a row that is already there was changed in place
        lenient().when(systemSettingRepository.saveAll(any())).thenAnswer(call -> {
            Iterable<SystemSetting> rows = call.getArgument(0);
            rows.forEach(row -> {
                if (!table.contains(row)) table.add(row);
            });
            return List.copyOf(table);
        });
        lenient().when(systemSettingRepository.save(any())).thenAnswer(call -> {
            SystemSetting row = call.getArgument(0);
            if (!table.contains(row)) table.add(row);
            return row;
        });
    }

    private SystemSetting row(String key, String value) {
        SystemSetting row = new SystemSetting();
        row.setSettingKey(key);
        row.setSettingValue(value);
        table.add(row);
        return row;
    }

    private SettingsUpdateRequest request(String key, String value) {
        SettingsUpdateRequest request = new SettingsUpdateRequest();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(key, value);
        request.setValues(values);
        return request;
    }

    private CustomException failure(Runnable call) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(CustomException.class);
        return (CustomException) thrown;
    }

    /** The update is refused with VALIDATION_FAILED (400) and the problem is reported under the key. */
    private void assertRejected(String group, String key, String value) {
        CustomException e = failure(() -> service.updateGroup(group, request(key, value)));
        assertThat(e.getHttpStatus()).as("%s = %s", key, value).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getMessage()).isEqualTo(MessageConstant.VALIDATION_FAILED.getDefaultMessage());
        assertThat(e.getErrorResponse().getDetails()).as("%s = %s", key, value).containsKey(key);
    }

    private void assertAccepted(String group, String key, String value, String saved) {
        Map<String, String> result = service.updateGroup(group, request(key, value));
        assertThat(result).as("%s = %s", key, value).containsEntry(key, saved);
    }

    // ---------------- reading ----------------

    @Test
    void getPublic_hidesTheSettingsThatAreNotPublic() {
        row("site_name_en", "My News");
        row("comments_enabled_default", "false"); // not public
        row("legacy_key_from_an_old_version", "secret"); // not in the list of keys

        Map<String, String> publicSettings = service.getPublic();

        assertThat(publicSettings).containsEntry("site_name_en", "My News");
        assertThat(publicSettings).doesNotContainKey("comments_enabled_default");
        assertThat(publicSettings).doesNotContainKey("legacy_key_from_an_old_version");
        Set<String> expected = new java.util.HashSet<>();
        for (SettingKey setting : SettingKey.values()) {
            if (setting.isPublicSetting()) expected.add(setting.getKey());
        }
        assertThat(publicSettings.keySet()).isEqualTo(expected);
    }

    @Test
    void getPublic_aSettingWithoutARowReadsAsItsDefault() {
        Map<String, String> publicSettings = service.getPublic();

        assertThat(publicSettings).containsEntry("site_name_en", "News Portal")
                .containsEntry("dashboard_name", "News Dashboard")
                .containsEntry("default_language", "en")
                .containsEntry("news_per_page", "10");
    }

    @Test
    void getAll_isGroupedAndShowsEverySettingToTheAdmin() {
        row("comments_enabled_default", "false");

        Map<String, Map<String, String>> all = service.getAll();

        assertThat(all.keySet()).containsExactly("general", "branding", "website", "seo", "content");
        assertThat(all.get("general")).containsKeys("site_name_en", "site_name_km", "dashboard_name", "title_format");
        assertThat(all.get("content")).containsEntry("comments_enabled_default", "false").containsKey("news_per_page");
        int total = all.values().stream().mapToInt(Map::size).sum();
        assertThat(total).isEqualTo(SettingKey.values().length);
    }

    @Test
    void contentSettings_areReadAsTypedValues_withTheDefaultWhenTheValueIsBroken() {
        row("news_per_page", "25");
        row("comments_enabled_default", "false");
        assertThat(service.getNewsPerPage()).isEqualTo(25);
        assertThat(service.isCommentsEnabledDefault()).isFalse();

        table.clear();
        cacheManager.getCache(CacheConfig.SETTINGS_CACHE).clear();
        row("news_per_page", "not a number");
        assertThat(service.getNewsPerPage()).isEqualTo(10);
        assertThat(service.isCommentsEnabledDefault()).isTrue();
    }

    // ---------------- validation ----------------

    @Test
    void update_requiredNames_cannotBeBlank() {
        assertRejected("general", "site_name_en", "   ");
        assertRejected("general", "site_name_en", null);
        assertRejected("general", "dashboard_name", "");
    }

    @Test
    void update_textsHaveAMaximumLength() {
        assertRejected("general", "site_name_en", "x".repeat(101));
        assertRejected("general", "tagline_en", "x".repeat(201));
        assertRejected("website", "footer_description_en", "x".repeat(501));
        assertRejected("website", "contact_phone", "1".repeat(31));
        assertAccepted("general", "site_name_en", "x".repeat(100), "x".repeat(100));
    }

    @Test
    void update_colorsMustBeHexRrggbb() {
        assertRejected("branding", "primary_color", "red");
        assertRejected("branding", "primary_color", "#12345");
        assertRejected("branding", "primary_color", "#1234567");
        assertRejected("branding", "accent_color", "123456");
        assertRejected("branding", "accent_color", "#GGGGGG");
        assertAccepted("branding", "primary_color", "#1a2b3c", "#1A2B3C");
    }

    @Test
    void update_contactEmailMustLookLikeAnEmail() {
        assertRejected("website", "contact_email", "not-an-email");
        assertRejected("website", "contact_email", "a@b");
        assertAccepted("website", "contact_email", " news@example.com ", "news@example.com");
        assertAccepted("website", "contact_email", "", ""); // optional
    }

    @Test
    void update_socialLinksMustBeHttpsUrls() {
        assertRejected("website", "social_facebook", "http://facebook.com/news");
        assertRejected("website", "social_telegram", "javascript:alert(1)");
        assertRejected("website", "social_youtube", "https://");
        assertRejected("website", "social_tiktok", "https://tik tok.com/@news");
        assertRejected("website", "social_facebook", "facebook.com/news");
        assertAccepted("website", "social_facebook", "https://facebook.com/news", "https://facebook.com/news");
    }

    @Test
    void update_newsPerPageIsBetween5And50() {
        assertRejected("content", "news_per_page", "4");
        assertRejected("content", "news_per_page", "51");
        assertRejected("content", "news_per_page", "ten");
        assertRejected("content", "news_per_page", "-5");
        assertAccepted("content", "news_per_page", "5", "5");
        assertAccepted("content", "news_per_page", "50", "50");
    }

    @Test
    void update_defaultLanguageIsEnOrKm_andBooleansAreTrueOrFalse() {
        assertRejected("general", "default_language", "fr");
        assertAccepted("general", "default_language", "km", "km");
        assertRejected("content", "comments_enabled_default", "yes");
        assertAccepted("content", "show_view_count", "FALSE", "false");
    }

    @Test
    void update_textIsPlainText_noHtml() {
        assertRejected("website", "footer_description_en", "<script>alert(1)</script>");
        assertRejected("general", "site_name_en", "News <b>Portal</b>");
        assertRejected("seo", "meta_description_en", "a > b");
        assertRejected("general", "tagline_en", "two\nlines"); // a STRING is one line
        assertAccepted("website", "footer_description_km", "ព័ត៌មានថ្មីៗ\nបន្ទាត់ទីពីរ", "ព័ត៌មានថ្មីៗ\nបន្ទាត់ទីពីរ");
    }

    @Test
    void update_anImageSettingIsNotSetThroughThePut_butCanBeCleared() {
        assertRejected("branding", "logo_url", "https://evil.example/logo.png");
        assertRejected("seo", "share_image_url", "/uploads/whatever.png");
        assertAccepted("branding", "logo_url", "", "");
    }

    @Test
    void update_unknownKeysAndKeysOfAnotherGroupAreRejected() {
        assertRejected("general", "not_a_setting", "x");
        assertRejected("general", "news_per_page", "10"); // belongs to content
        CustomException e = failure(() -> service.updateGroup("nope", request("site_name_en", "x")));
        assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo(MessageConstant.SETTING_GROUP_UNKNOWN.getDefaultMessage());
    }

    @Test
    void update_oneInvalidValueMeansNothingIsSaved() {
        SettingsUpdateRequest request = new SettingsUpdateRequest();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("site_name_en", "Good name"); // valid
        values.put("primary_color", "not a color"); // invalid
        request.setValues(values);

        CustomException e = failure(() -> service.updateGroup("general", request));

        // site_name_en is in `general`, primary_color is in `branding`: both are reported
        assertThat(e.getErrorResponse().getDetails()).containsKey("primary_color");
        verify(systemSettingRepository, never()).saveAll(any());
        verify(systemSettingRepository, never()).save(any());
        assertThat(table).isEmpty();
    }

    @Test
    void update_severalValuesOfOneGroupAreSavedTogether_withTheAdminAndTheTime() {
        SystemSetting existing = row("site_name_en", "Old name");
        SettingsUpdateRequest request = new SettingsUpdateRequest();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("site_name_en", "  New name  ");
        values.put("tagline_en", "Fresh news");
        values.put("default_language", "km");
        request.setValues(values);

        Map<String, String> result = service.updateGroup("general", request);

        assertThat(result).containsEntry("site_name_en", "New name").containsEntry("tagline_en", "Fresh news").containsEntry("default_language", "km");
        assertThat(result).containsKey("dashboard_name"); // the whole group comes back
        ArgumentCaptor<Collection<SystemSetting>> saved = ArgumentCaptor.forClass(Collection.class);
        verify(systemSettingRepository, times(1)).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(3);
        assertThat(existing.getSettingValue()).isEqualTo("New name");
        assertThat(existing.getUpdatedBy()).isSameAs(admin);
        SystemSetting created = table.stream().filter(r -> r.getSettingKey().equals("tagline_en")).findFirst().orElseThrow();
        assertThat(created.getSettingGroup()).isEqualTo("general");
        assertThat(created.isPublicSetting()).isTrue();
        assertThat(created.getValueType()).isEqualTo(SettingKey.TAGLINE_EN.getType());
        assertThat(created.getUpdatedAt()).isNotNull();
    }

    @Test
    void update_anOptionalValueCanBeClearedWithNullOrEmpty() {
        row("tagline_en", "Old tagline");

        assertThat(service.updateGroup("general", request("tagline_en", null))).containsEntry("tagline_en", "");
        assertThat(table.get(0).getSettingValue()).isEmpty();
    }

    @Test
    void update_clearingAnImageDeletesItsFile() {
        row("logo_url", "/uploads/old-logo.png");

        service.updateGroup("branding", request("logo_url", ""));

        verify(fileStorageService).delete("/uploads/old-logo.png");
    }

    // ---------------- cache ----------------

    @Test
    void thePublicSettingsAreCached_andLoadedAgainAfterAnUpdate() {
        row("site_name_en", "First name");

        assertThat(service.getPublic()).containsEntry("site_name_en", "First name");
        assertThat(service.getPublic()).containsEntry("site_name_en", "First name");
        assertThat(service.getNewsPerPage()).isEqualTo(10);
        verify(systemSettingRepository, times(1)).findAll(); // the three reads shared one load

        service.updateGroup("general", request("site_name_en", "Second name"));
        service.updateGroup("content", request("news_per_page", "20"));

        assertThat(service.getPublic()).containsEntry("site_name_en", "Second name");
        assertThat(service.getNewsPerPage()).isEqualTo(20);
        verify(systemSettingRepository, times(2)).findAll(); // loaded again, once, after the changes
    }

    @Test
    void aFailedUpdateKeepsTheCache() {
        row("site_name_en", "First name");
        service.getPublic();

        assertThatThrownBy(() -> service.updateGroup("general", request("site_name_en", ""))).isInstanceOf(CustomException.class);
        service.getPublic();

        verify(systemSettingRepository, times(1)).findAll();
    }

    @Test
    void insideATransaction_theCacheIsClearedAndOldFilesDeletedOnlyAfterTheCommit() {
        row("site_name_en", "First name");
        row("logo_url", "/uploads/old-logo.png");
        service.getPublic(); // cached

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateGroup("general", request("site_name_en", "Changed name"));
            service.updateGroup("branding", request("logo_url", ""));

            // not committed yet: readers still get the cached values and the old file is still there
            assertThat(service.getPublic()).containsEntry("site_name_en", "First name");
            verify(fileStorageService, never()).delete(any());

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(service.getPublic()).containsEntry("site_name_en", "Changed name");
        verify(fileStorageService).delete("/uploads/old-logo.png");
    }

    @Test
    void insideATransaction_aRollbackLeavesTheCacheAndTheFilesAlone() {
        row("site_name_en", "First name");
        row("logo_url", "/uploads/old-logo.png");
        service.getPublic();

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateGroup("branding", request("logo_url", ""));
            // rolled back: afterCommit is never called
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }

        verify(fileStorageService, never()).delete(any());
        assertThat(service.getPublic()).containsEntry("site_name_en", "First name");
        verify(systemSettingRepository, times(1)).findAll();
    }

    // ---------------- image upload (the file checks themselves are in SystemSettingImageUploadTest) ----------------

    @Test
    void uploadImage_savesThePathInTheMatchingSetting_deletesTheOldFile_andClearsTheCache() {
        SystemSetting logo = row("logo_url", "/uploads/old-logo.png");
        service.getPublic(); // fills the cache
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3});
        when(fileStorageService.store(any(), any())).thenReturn("/uploads/new-logo.png");

        Map<String, String> result = service.uploadImage("logo", file);

        assertThat(result).containsExactlyEntriesOf(Map.of("logo_url", "/uploads/new-logo.png"));
        assertThat(logo.getSettingValue()).isEqualTo("/uploads/new-logo.png");
        assertThat(logo.getUpdatedBy()).isSameAs(admin);
        verify(fileStorageService).delete("/uploads/old-logo.png");
        assertThat(service.getPublic()).containsEntry("logo_url", "/uploads/new-logo.png");
    }

    @Test
    void uploadImage_everyImageTypeHasItsSetting_andOnlyTheFaviconMayBeAnIco() {
        when(fileStorageService.store(any(), any())).thenReturn("/uploads/x.png");
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});
        Map<String, String> keys = Map.of("logo", "logo_url", "logo-dark", "logo_dark_url", "favicon", "favicon_url", "share-image", "share_image_url");

        keys.forEach((type, key) -> assertThat(service.uploadImage(type, file)).containsKey(key));

        ArgumentCaptor<FileStorageService.Rules> rules = ArgumentCaptor.forClass(FileStorageService.Rules.class);
        verify(fileStorageService, times(4)).store(any(), rules.capture());
        long withIco = rules.getAllValues().stream().filter(r -> r.extensions().contains("ico")).count();
        assertThat(withIco).isEqualTo(1);
        assertThat(rules.getAllValues()).allSatisfy(r -> {
            assertThat(r.extensions()).contains("png", "jpg", "webp").doesNotContain("gif", "svg");
            assertThat(r.maxBytes()).isEqualTo(2L * 1024 * 1024);
        });
    }

    @Test
    void uploadImage_unknownType_is404() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});

        CustomException e = failure(() -> service.uploadImage("banner", file));

        assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo(MessageConstant.SETTING_IMAGE_TYPE_UNKNOWN.getDefaultMessage());
        verify(fileStorageService, never()).store(any(), any());
    }
}
