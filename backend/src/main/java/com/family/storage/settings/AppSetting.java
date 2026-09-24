package com.family.storage.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 应用设置实体（映射 V1 迁移 app_settings 表，schema 由 Flyway 管理，ddl-auto=none）。
 * 键值均为字符串，业务侧按需解析（如 expiry.remind.days 解析为正整数）。
 */
@Entity
@Table(name = "app_settings")
public class AppSetting {
    @Id
    @Column(name = "key", nullable = false, length = 50)
    private String key;

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}