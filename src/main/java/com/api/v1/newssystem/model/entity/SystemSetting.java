package com.api.v1.newssystem.model.entity;

import com.api.v1.newssystem.model.enumerate.SettingType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/** One system setting (key and text value). The list of valid keys is the SettingKey enum. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@NullMarked
@Table(name = "system_settings", options = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
        uniqueConstraints = @UniqueConstraint(name = "uk_system_settings_key", columnNames = "setting_key"))
public class SystemSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, columnDefinition = "TEXT")
    private String settingValue = "";

    @Column(name = "setting_group", nullable = false, length = 50)
    private String settingGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private SettingType valueType;

    @Column(name = "is_public", nullable = false)
    private boolean publicSetting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @Nullable
    private AppUser updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
