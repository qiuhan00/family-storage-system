package com.family.storage.settings;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 应用设置仓储（主键为 key 字符串）。
 */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}