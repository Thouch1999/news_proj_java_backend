package com.api.v1.bearerauth.repository;

import com.api.v1.bearerauth.model.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    boolean existsBySettingKey(String settingKey);

    List<SystemSetting> findAllBySettingKeyIn(Collection<String> settingKeys);
}
