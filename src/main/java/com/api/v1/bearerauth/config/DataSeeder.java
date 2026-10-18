package com.api.v1.bearerauth.config;

import com.api.v1.bearerauth.model.entity.*;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.enumerate.SettingKey;
import com.api.v1.bearerauth.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds roles, permissions, users, categories, tags and the default system settings on startup.
 * Every item is created only if it does not exist yet, so restarts are safe.
 */
@Component
@Order(1) // before DemoNewsSeeder, which needs these users, categories and tags
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final AppProperty appProperty;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final SystemSettingRepository systemSettingRepository;

    // Permissions per role, based on section 6 of the proposal.
    private static final Map<Roles, List<String>> ROLE_PERMISSIONS = new LinkedHashMap<>();

    static {
        ROLE_PERMISSIONS.put(Roles.ADMIN, List.of(
                "USER_MANAGE", "ROLE_MANAGE", "CATEGORY_MANAGE", "TAG_MANAGE", "NEWS_MANAGE",
                "COMMENT_MODERATE", "DASHBOARD_VIEW", "REPORT_VIEW", "SETTINGS_MANAGE"));
        ROLE_PERMISSIONS.put(Roles.EDITOR, List.of(
                "NEWS_VIEW_SUBMITTED", "NEWS_REVIEW", "NEWS_EDIT", "NEWS_APPROVE", "NEWS_REJECT",
                "NEWS_PUBLISH", "NEWS_ARCHIVE", "STATISTICS_VIEW"));
        ROLE_PERMISSIONS.put(Roles.AUTHOR, List.of(
                "NEWS_CREATE", "NEWS_EDIT_OWN", "NEWS_SAVE_DRAFT", "NEWS_SUBMIT",
                "NEWS_STATUS_VIEW", "NEWS_VIEW_OWN_PUBLISHED"));
        ROLE_PERMISSIONS.put(Roles.READER, List.of(
                "NEWS_READ", "NEWS_SEARCH", "NEWS_FILTER", "COMMENT_CREATE",
                "BOOKMARK_MANAGE", "PROFILE_MANAGE"));
    }

    private static final Map<Roles, String> ROLE_DESCRIPTIONS = Map.of(
            Roles.ADMIN, "Manage the overall system",
            Roles.EDITOR, "Review and publish news",
            Roles.AUTHOR, "Create and submit news",
            Roles.READER, "Read and interact with published news");

    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("Technology", "technology");
        CATEGORIES.put("Business", "business");
        CATEGORIES.put("Sports", "sports");
        CATEGORIES.put("Entertainment", "entertainment");
        CATEGORIES.put("Education", "education");
        CATEGORIES.put("Health", "health");
        CATEGORIES.put("Society", "society");
    }

    private static final Map<String, String> TAGS = new LinkedHashMap<>();

    static {
        TAGS.put("Breaking News", "breaking-news");
        TAGS.put("Politics", "politics");
        TAGS.put("Economy", "economy");
        TAGS.put("Culture", "culture");
        TAGS.put("Innovation", "innovation");
        TAGS.put("Local", "local");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRolesAndPermissions();
        seedUsers();
        seedCategories();
        seedTags();
        seedSettings();
    }

    // every setting of the SettingKey list that has no row yet gets its default value; saved values are never touched
    private void seedSettings() {
        for (SettingKey setting : SettingKey.values()) {
            if (!systemSettingRepository.existsBySettingKey(setting.getKey())) {
                SystemSetting row = new SystemSetting();
                row.setSettingKey(setting.getKey());
                row.setSettingValue(setting.getDefaultValue());
                row.setSettingGroup(setting.getGroup().getPath());
                row.setValueType(setting.getType());
                row.setPublicSetting(setting.isPublicSetting());
                systemSettingRepository.save(row);
            }
        }
    }

    private void seedRolesAndPermissions() {
        ROLE_PERMISSIONS.forEach((roleName, permissionNames) -> {
            Role role = roleRepository.findByName(roleName.name()).orElseGet(() -> {
                Role created = new Role();
                created.setName(roleName.name());
                created.setDescription(ROLE_DESCRIPTIONS.get(roleName));
                return roleRepository.save(created);
            });

            for (String permissionName : permissionNames) {
                Permission permission = permissionRepository.findByName(permissionName).orElseGet(() -> {
                    Permission created = new Permission();
                    created.setName(permissionName);
                    return permissionRepository.save(created);
                });

                if (!rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId())) {
                    RolePermission link = new RolePermission();
                    link.setRole(role);
                    link.setPermission(permission);
                    rolePermissionRepository.save(link);
                }
            }
        });
    }

    private void seedUsers() {
        AppProperty.Seed seed = appProperty.getSeed();
        seedUser(Roles.ADMIN, seed.getAdmin());
        seedUser(Roles.EDITOR, seed.getEditor());
        seedUser(Roles.AUTHOR, seed.getAuthor());
        seedUser(Roles.READER, seed.getReader());
    }

    private void seedUser(Roles roleName, AppProperty.SeedUser config) {
        if (config.getEmail() == null || config.getEmail().isBlank()) {
            return;
        }
        AppUser user = appUserRepository.findByEmail(config.getEmail()).orElse(null);
        if (user == null) {
            if (config.getPassword() == null || config.getPassword().isBlank()) {
                log.warn("Seed user for role {} skipped: no password configured", roleName);
                return;
            }
            user = new AppUser();
            user.setUsername(config.getUsername());
            user.setFullName(config.getUsername());
            user.setEmail(config.getEmail());
            user.setPassword(passwordEncoder.encode(config.getPassword()));
            user.setRole(roleName);
            user.setActive(true);
            user.setEmailVerified(true);
            user = appUserRepository.save(user);
        }

        Role role = roleRepository.findByName(roleName.name()).orElseThrow();
        if (!userRoleRepository.existsByUserIdAndRoleId(user.getId(), role.getId())) {
            UserRole link = new UserRole();
            link.setUser(user);
            link.setRole(role);
            userRoleRepository.save(link);
        }
    }

    private void seedCategories() {
        CATEGORIES.forEach((name, slug) -> {
            if (!categoryRepository.existsBySlug(slug) && !categoryRepository.existsByName(name)) {
                Category category = new Category();
                category.setName(name);
                category.setSlug(slug);
                category.setActive(true);
                categoryRepository.save(category);
            }
        });
    }

    private void seedTags() {
        TAGS.forEach((name, slug) -> {
            if (!tagRepository.existsBySlug(slug) && !tagRepository.existsByName(name)) {
                Tag tag = new Tag();
                tag.setName(name);
                tag.setSlug(slug);
                tagRepository.save(tag);
            }
        });
    }
}
