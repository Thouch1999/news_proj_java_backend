package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.config.CacheConfig;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.repository.SystemSettingRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The site image rules end to end: the settings service with the real file storage in a temporary folder.
 * PNG, JPG and WebP only (the favicon also ICO), no GIF and no SVG, 2 MB at most, and the type is read from the content.
 */
@ExtendWith(MockitoExtension.class)
class SystemSettingImageUploadTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};
    private static final byte[] ICO = {0, 0, 1, 0, 1, 0, 16, 16, 0, 0, 1, 0};
    private static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path folder;

    @Mock
    private SystemSettingRepository systemSettingRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserService currentUserService;

    private SystemSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        AppProperty properties = new AppProperty();
        properties.getUpload().setDir(folder.toString());
        service = new SystemSettingServiceImpl(systemSettingRepository, appUserRepository, currentUserService,
                new LocalFileStorageServiceImpl(properties), new CacheConfig().cacheManager());
        AppUser admin = new AppUser();
        admin.setId(1L);
        lenient().when(currentUserService.getCurrentUser()).thenReturn(admin);
        lenient().when(appUserRepository.getReferenceById(anyLong())).thenReturn(admin);
        lenient().when(systemSettingRepository.findAllBySettingKeyIn(anyCollection())).thenReturn(List.of());
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private long filesInFolder() throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.count();
        }
    }

    private void assertRejected(String type, MockMultipartFile file, MessageConstant message, HttpStatus status) throws IOException {
        Throwable thrown = catchThrowable(() -> service.uploadImage(type, file));
        assertThat(thrown).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getMessage()).isEqualTo(message.getDefaultMessage());
            assertThat(e.getHttpStatus()).isEqualTo(status);
        });
        verify(systemSettingRepository, never()).save(any()); // the setting did not change
        assertThat(filesInFolder()).isZero(); // and nothing was stored
    }

    @Test
    void png_jpg_andWebp_areAccepted_forEveryImage() {
        for (String type : List.of("logo", "logo-dark", "favicon", "share-image")) {
            for (MockMultipartFile image : List.of(file("a.png", "image/png", PNG), file("a.jpg", "image/jpeg", JPEG), file("a.webp", "image/webp", WEBP))) {
                Map<String, String> result = service.uploadImage(type, image);
                assertThat(result.values()).singleElement().asString().startsWith("/uploads/");
            }
        }
    }

    @Test
    void anSvg_isRefused_evenWithAnImageContentTypeAndAPngName() throws IOException {
        assertRejected("logo", file("logo.svg", "image/svg+xml", SVG), MessageConstant.SETTING_IMAGE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertRejected("share-image", file("logo.png", "image/png", SVG), MessageConstant.SETTING_IMAGE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void theRealTypeIsChecked_notTheNameOrTheContentType() throws IOException {
        byte[] text = "this is not an image".getBytes(StandardCharsets.UTF_8);
        assertRejected("logo", file("logo.png", "image/png", text), MessageConstant.SETTING_IMAGE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertRejected("logo-dark", file("logo.jpg", "image/jpeg", new byte[]{'M', 'Z', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}), MessageConstant.SETTING_IMAGE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void aGif_isRefused() throws IOException {
        assertRejected("logo", file("anim.gif", "image/gif", GIF), MessageConstant.SETTING_IMAGE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void anIco_isAcceptedForTheFaviconOnly() throws IOException {
        assertThat(service.uploadImage("favicon", file("favicon.ico", "image/x-icon", ICO)).get("favicon_url")).endsWith(".ico");
        try (Stream<Path> stored = Files.list(folder)) {
            stored.forEach(path -> path.toFile().delete());
        }
        org.mockito.Mockito.clearInvocations(systemSettingRepository); // the favicon above was saved

        assertRejected("logo", file("logo.ico", "image/x-icon", ICO), MessageConstant.SETTING_IMAGE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertRejected("share-image", file("share.ico", "image/x-icon", ICO), MessageConstant.SETTING_IMAGE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void moreThan2Mb_isRefused_justUnder2MbIsAccepted() throws IOException {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertRejected("logo", file("big.png", "image/png", big), MessageConstant.SETTING_IMAGE_TOO_LARGE, HttpStatus.BAD_REQUEST);

        byte[] fits = new byte[2 * 1024 * 1024];
        System.arraycopy(PNG, 0, fits, 0, PNG.length);
        assertThat(service.uploadImage("logo", file("ok.png", "image/png", fits))).containsKey("logo_url");
    }

    @Test
    void anEmptyFile_isRefused() throws IOException {
        assertRejected("logo", file("empty.png", "image/png", new byte[0]), MessageConstant.FILE_EMPTY, HttpStatus.BAD_REQUEST);
    }
}
