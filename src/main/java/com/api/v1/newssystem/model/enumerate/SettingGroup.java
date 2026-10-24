package com.api.v1.newssystem.model.enumerate;

import java.util.Arrays;
import java.util.Optional;

/** The groups of the system settings. `path` is the name used in the URL (/api/v1/settings/{group}) and in the table. */
public enum SettingGroup {
    GENERAL("general"),
    BRANDING("branding"),
    WEBSITE("website"),
    SEO("seo"),
    CONTENT("content");

    private final String path;

    SettingGroup(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public static Optional<SettingGroup> fromPath(String path) {
        return Arrays.stream(values()).filter(group -> group.path.equals(path)).findFirst();
    }
}
