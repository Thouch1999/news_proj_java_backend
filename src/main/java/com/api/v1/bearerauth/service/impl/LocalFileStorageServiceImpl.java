package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.config.AppProperty;
import com.api.v1.bearerauth.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {
    private static final String URL_PREFIX = "/uploads/";
    private static final Pattern STORED_NAME =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|gif|webp|ico)$");

    private static final Set<String> DEFAULT_TYPES = Set.of("jpg", "png", "gif", "webp");

    private final AppProperty appProperty;

    @Override
    public String store(MultipartFile file) {
        return store(file, new Rules(DEFAULT_TYPES, appProperty.getUpload().getMaxFileSizeBytes(),
                MessageConstant.FILE_TOO_LARGE, MessageConstant.FILE_TYPE_NOT_ALLOWED));
    }

    @Override
    public String store(MultipartFile file, Rules rules) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.FILE_EMPTY.getDefaultMessage());
        }
        if (file.getSize() > rules.maxBytes()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, rules.tooLarge().getDefaultMessage());
        }

        try {
            byte[] header;
            try (InputStream in = file.getInputStream()) {
                header = in.readNBytes(12);
            }
            String extension = detectExtension(header);
            if (extension == null || !rules.extensions().contains(extension)) {
                throw new CustomException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, rules.typeNotAllowed().getDefaultMessage());
            }

            Path root = root();
            Files.createDirectories(root);
            String name = UUID.randomUUID() + "." + extension; // never the client's file name
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
            return URL_PREFIX + name;
        } catch (IOException e) {
            log.error("Could not store the uploaded file", e);
            throw new CustomException(HttpStatus.INTERNAL_SERVER_ERROR, MessageConstant.FILE_STORAGE_ERROR.getDefaultMessage());
        }
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        String name = url.substring(URL_PREFIX.length());
        if (!STORED_NAME.matcher(name).matches()) {
            return; // only files this service created are ever deleted
        }
        try {
            Files.deleteIfExists(root().resolve(name));
        } catch (IOException e) {
            log.warn("Could not delete the file {}", name, e);
        }
    }

    private Path root() {
        return Paths.get(appProperty.getUpload().getDir()).toAbsolutePath().normalize();
    }

    // magic numbers of the allowed image types
    static String detectExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return "png";
        }
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8' && (h[4] == '7' || h[4] == '9') && h[5] == 'a') {
            return "gif";
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        if (h.length >= 4 && h[0] == 0 && h[1] == 0 && h[2] == 1 && h[3] == 0) {
            return "ico";
        }
        return null;
    }
}
