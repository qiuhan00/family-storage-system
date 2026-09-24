package com.family.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.family.storage.common.ApiException;
import com.family.storage.settings.AppSetting;
import com.family.storage.settings.AppSettingRepository;
import com.family.storage.settings.SettingsService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 应用设置业务测试（Task 8，spec §5.9）：
 * getInt 兜底默认值；update 仅允许白名单键（expiry.remind.days）且值必须为正整数。
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {
    @Mock AppSettingRepository repo;

    private SettingsService svc() {
        return new SettingsService(repo);
    }

    @Test
    void getIntReturnsDefaultWhenMissing() {
        when(repo.findById("expiry.remind.days")).thenReturn(Optional.empty());
        assertEquals(7, svc().getInt("expiry.remind.days", 7));
    }

    @Test
    void getIntReturnsStoredValue() {
        AppSetting s = new AppSetting();
        s.setKey("expiry.remind.days");
        s.setValue("3");
        when(repo.findById("expiry.remind.days")).thenReturn(Optional.of(s));
        assertEquals(3, svc().getInt("expiry.remind.days", 7));
    }

    @Test
    void updateRejectsUnknownKey() {
        assertThrows(ApiException.class, () -> svc().update("sms.enabled", "true"));
    }

    @Test
    void updateRejectsNonNumericValue() {
        assertThrows(ApiException.class, () -> svc().update("expiry.remind.days", "abc"));
    }

    @Test
    void updatePersistsValidValue() {
        svc().update("expiry.remind.days", "10");
        verify(repo).save(argThat(a -> "expiry.remind.days".equals(a.getKey())
                && "10".equals(a.getValue())));
    }
}