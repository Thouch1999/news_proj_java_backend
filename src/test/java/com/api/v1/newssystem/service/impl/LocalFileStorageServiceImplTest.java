package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceImplTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @TempDir
    Path folder;

    private LocalFileStorageServiceImpl storage;

    @BeforeEach
    void setUp() {
        AppProperty properties = new AppProperty();
        properties.getUpload().setDir(folder.toString());
        properties.getUpload().setMaxFileSizeBytes(1024);
        storage = new LocalFileStorageServiceImpl(properties);
    }

    private MockMultipartFile upload(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private void assertFails(Runnable call, MessageConstant expected, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(expected.getDefaultMessage());
                    assertThat(e.getHttpStatus()).isEqualTo(status);
                });
    }

    @Test
    void store_savesTheImageUnderARandomName_andReturnsItsPublicPath() throws IOException {
        String url = storage.store(upload("holiday photo.png", "image/png", PNG));

        assertThat(url).matches("^/uploads/[0-9a-f-]{36}\\.png$");
        assertThat(url).doesNotContain("holiday");
        assertThat(Files.readAllBytes(folder.resolve(url.substring("/uploads/".length())))).isEqualTo(PNG);
    }

    @Test
    void store_detectsTheTypeFromTheContent() {
        assertThat(storage.store(upload("a.bin", "application/octet-stream", JPEG))).endsWith(".jpg");
        assertThat(storage.store(upload("a.bin", "application/octet-stream", GIF))).endsWith(".gif");
        assertThat(storage.store(upload("a.bin", "application/octet-stream", WEBP))).endsWith(".webp");
        assertThat(storage.store(upload("a.jpg", "image/jpeg", PNG))).endsWith(".png");
    }

    @Test
    void store_rejectsFilesThatAreNotImages_whateverTheirNameOrContentTypeSay() throws IOException {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);

        assertFails(() -> storage.store(upload("evil.png", "image/png", html)), MessageConstant.FILE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertFails(() -> storage.store(upload("x.jpg", "image/jpeg", svg)), MessageConstant.FILE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertFails(() -> storage.store(upload("x.png", "image/png", new byte[]{1, 2, 3})), MessageConstant.FILE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        try (var files = Files.list(folder)) {
            assertThat(files.count()).isZero();
        }
    }

    @Test
    void store_rejectsEmptyMissingAndTooLargeFiles() {
        assertFails(() -> storage.store(upload("a.png", "image/png", new byte[0])), MessageConstant.FILE_EMPTY, HttpStatus.BAD_REQUEST);
        assertFails(() -> storage.store(null), MessageConstant.FILE_EMPTY, HttpStatus.BAD_REQUEST);

        byte[] big = new byte[2048];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertFails(() -> storage.store(upload("big.png", "image/png", big)), MessageConstant.FILE_TOO_LARGE, HttpStatus.BAD_REQUEST);
    }

    @Test
    void delete_removesAStoredFile() {
        String url = storage.store(upload("a.png", "image/png", PNG));
        Path stored = folder.resolve(url.substring("/uploads/".length()));
        assertThat(stored).exists();

        storage.delete(url);

        assertThat(stored).doesNotExist();
    }

    @Test
    void delete_ignoresAnythingThisServiceDidNotCreate() throws IOException {
        Path other = Files.write(folder.resolve("keep.png"), PNG);
        Path outside = Files.write(folder.getParent().resolve("outside-" + folder.getFileName() + ".txt"), new byte[]{1});

        storage.delete("/uploads/keep.png");
        storage.delete("/uploads/../" + outside.getFileName());
        storage.delete("https://example.com/uploads/x.png");
        storage.delete(null);

        assertThat(other).exists();
        assertThat(outside).exists();
        Files.deleteIfExists(outside);
    }
}
