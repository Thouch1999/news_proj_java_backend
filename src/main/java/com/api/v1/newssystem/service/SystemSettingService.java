package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.request.SettingsUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface SystemSettingService {
    /** The settings every visitor may read, as one flat object (key to value). Cached. */
    Map<String, String> getPublic();

    /** Every setting, grouped: group name to (key to value). ADMIN. */
    Map<String, Map<String, String>> getAll();

    /**
     * Changes several settings of one group in one transaction: either every value is valid and saved, or nothing is
     * changed. Returns the values of the group. ADMIN.
     */
    Map<String, String> updateGroup(String group, SettingsUpdateRequest request);

    /**
     * Saves an uploaded site image (imageType: logo, logo-dark, favicon or share-image) in the matching setting and
     * deletes the file it replaces. Returns the setting key and the new path. ADMIN.
     */
    Map<String, String> uploadImage(String imageType, MultipartFile file);

    /** The default page size of the public news lists (setting news_per_page). */
    int getNewsPerPage();

    /** Whether the comments of a new article are open when the author does not say (setting comments_enabled_default). */
    boolean isCommentsEnabledDefault();
}
