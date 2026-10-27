package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.AppProperty;
import com.api.v1.newssystem.service.FileStorageService;
import com.api.v1.newssystem.util.VideoInspector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|gif|webp|ico|mp4|webm)$");

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
    public String storeSquareCrop(String sourceUrl, int size) {
        Path source = pathOf(sourceUrl);
        if (source == null || size <= 0) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(source.toFile()); // null for a type the JDK cannot read (WebP)
            if (image == null) {
                return null;
            }
            int side = Math.min(image.getWidth(), image.getHeight());
            BufferedImage square = image.getSubimage((image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side);
            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = out.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(square, 0, 0, size, size, null);
            } finally {
                graphics.dispose();
            }
            Path root = root();
            Files.createDirectories(root);
            String name = UUID.randomUUID() + ".png";
            ImageIO.write(out, "png", root.resolve(name).toFile());
            return URL_PREFIX + name;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not cut a square out of {}", sourceUrl, e);
            return null;
        }
    }

    // the file of a path returned by store(), or null for anything else
    private Path pathOf(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return null;
        }
        String name = url.substring(URL_PREFIX.length());
        if (!STORED_NAME.matcher(name).matches()) {
            return null;
        }
        Path file = root().resolve(name);
        return Files.isRegularFile(file) ? file : null;
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

    // magic numbers of the allowed image and video types
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
        // the sponsor videos: the container only, the codec and the duration are checked by the caller (VideoInspector)
        return VideoInspector.containerOf(h);
    }
}
