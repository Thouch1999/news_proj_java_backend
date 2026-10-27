package com.api.v1.newssystem.service;

import com.api.v1.newssystem.common.constant.MessageConstant;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

public interface FileStorageService {
    /**
     * What an upload must be: the allowed types (detected extensions: jpg, png, gif, webp, ico, and mp4 / webm for the sponsor videos), the largest size, and
     * the messages to answer with when the file is too large or of another type.
     */
    record Rules(Set<String> extensions, long maxBytes, MessageConstant tooLarge, MessageConstant typeNotAllowed) {
    }

    /**
     * Saves an uploaded image under a random name and returns its public path (for example /uploads/<uuid>.png).
     * The type is detected from the file content, not from the file name or the Content-Type header.
     * Allowed: JPEG, PNG, GIF and WebP, up to app.upload.max-file-size-bytes.
     */
    String store(MultipartFile file);

    /** The same with other rules (for example the site images: no GIF, an ICO for the favicon, a smaller size). */
    String store(MultipartFile file, Rules rules);

    /**
     * Cuts the largest square out of the center of an image stored by store() and saves it as a size x size PNG under a
     * new random name (the image service of the sponsors: the bottom bar picture made from the popup picture). Returns its
     * public path, or null when the source cannot be read as a picture here (a WebP, or a file that is gone): the caller
     * then keeps using the source.
     */
    String storeSquareCrop(String sourceUrl, int size);

    /** Deletes a file previously returned by store(); anything else is ignored. */
    void delete(String url);
}
