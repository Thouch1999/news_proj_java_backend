package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The per-call rules of the file storage (used by the site images); the default rules are in LocalFileStorageServiceImplTest. */
class LocalFileStorageRulesTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] ICO = {0, 0, 1, 0, 1, 0, 16, 16, 0, 0, 1, 0};
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};

    @TempDir
    Path folder;

    private LocalFileStorageServiceImpl storage;

    @BeforeEach
    void setUp() {
        AppProperty properties = new AppProperty();
        properties.getUpload().setDir(folder.toString());
        storage = new LocalFileStorageServiceImpl(properties);
    }

    private MockMultipartFile upload(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private FileStorageService.Rules rules(Set<String> extensions, long maxBytes) {
        return new FileStorageService.Rules(extensions, maxBytes, MessageConstant.SETTING_IMAGE_TOO_LARGE, MessageConstant.SETTING_IMAGE_TYPE_NOT_ALLOWED);
    }

    @Test
    void anIco_isStored_whenTheRulesAllowIt_andDeletedAgain() throws Exception {
        String url = storage.store(upload("favicon.ico", ICO), rules(Set.of("png", "ico"), 1024));

        assertThat(url).matches("^/uploads/[0-9a-f-]{36}\\.ico$");
        Path stored = folder.resolve(url.substring("/uploads/".length()));
        assertThat(Files.readAllBytes(stored)).isEqualTo(ICO);

        storage.delete(url);
        assertThat(stored).doesNotExist();
    }

    @Test
    void theDefaultRules_stillRefuseAnIco() {
        assertThatThrownBy(() -> storage.store(upload("favicon.ico", ICO)))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(e.getMessage()).isEqualTo(MessageConstant.FILE_TYPE_NOT_ALLOWED.getDefaultMessage());
                });
    }

    @Test
    void aTypeThatIsNotInTheRules_isRefused_withTheMessageOfTheRules() {
        assertThatThrownBy(() -> storage.store(upload("a.gif", GIF), rules(Set.of("png"), 1024)))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(e.getMessage()).isEqualTo(MessageConstant.SETTING_IMAGE_TYPE_NOT_ALLOWED.getDefaultMessage());
                });
    }

    @Test
    void theSizeLimitOfTheRules_wins_overTheDefaultLimit() {
        byte[] bigger = new byte[2048];
        System.arraycopy(PNG, 0, bigger, 0, PNG.length);

        assertThatThrownBy(() -> storage.store(upload("a.png", bigger), rules(Set.of("png"), 1024)))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo(MessageConstant.SETTING_IMAGE_TOO_LARGE.getDefaultMessage());
                });
        // the default rules (5 MB) accept the same file
        assertThat(storage.store(upload("a.png", bigger))).endsWith(".png");
    }
}
