package com.family.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.family.storage.settings.AppSetting;
import com.family.storage.settings.AppSettingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设置表持久化回归（D2 修复）：app_settings 的 key/value 是 MySQL 保留字，
 * 需验证 UPDATE（而非仅 SELECT）路径在反引号转义下可用。
 */
@SpringBootTest
class AppSettingRepositoryTest {

    @Autowired
    private AppSettingRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void updateOnReservedWordColumnsSucceeds() {
        String key = "expiry.remind.days";
        AppSetting setting = repository.findById(key).orElseGet(() -> {
            AppSetting created = new AppSetting();
            created.setKey(key);
            created.setValue("7");
            return repository.saveAndFlush(created);
        });
        setting.setValue("9");
        repository.saveAndFlush(setting); // 触发 UPDATE ... SET `value`=? WHERE `key`=?
        entityManager.clear();

        AppSetting after = repository.findById(key).orElseThrow();
        assertEquals("9", after.getValue());
    }
}