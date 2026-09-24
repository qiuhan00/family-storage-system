package com.family.storage.settings;

import com.family.storage.common.ApiException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 应用设置业务（spec §5.9）：getInt 读整数设置（缺失时兜底默认值）；
 * update 仅接受白名单键 expiry.remind.days 且值必须为正整数。
 * 写操作权限由 SecurityConfig 的 PUT /api/settings → ADMIN 约束。
 */
@Service
@RequiredArgsConstructor
public class SettingsService {
    public static final String EXPIRY_REMIND_DAYS = "expiry.remind.days";
    public static final int DEFAULT_EXPIRY_REMIND_DAYS = 7;
    private static final Set<String> WHITELIST = Set.of(EXPIRY_REMIND_DAYS);

    private final AppSettingRepository appSettingRepository;

    public int getInt(String key, int defaultValue) {
        return appSettingRepository.findById(key)
                .map(s -> parseOrDefault(s.getValue(), defaultValue))
                .orElse(defaultValue);
    }

    /**
     * 公共设置视图：{expiry_remind_days: 7}。
     */
    public Map<String, Object> all() {
        return Map.of("expiry_remind_days", getInt(EXPIRY_REMIND_DAYS, DEFAULT_EXPIRY_REMIND_DAYS));
    }

    public void update(String key, String value) {
        if (!WHITELIST.contains(key)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SETTING_KEY", "不支持该设置项: " + key);
        }
        int parsed = parsePositiveInt(key, value);
        AppSetting setting = appSettingRepository.findById(key).orElseGet(() -> {
            AppSetting s = new AppSetting();
            s.setKey(key);
            return s;
        });
        setting.setValue(String.valueOf(parsed));
        appSettingRepository.save(setting);
    }

    private int parsePositiveInt(String key, String value) {
        try {
            int v = Integer.parseInt(value.trim());
            if (v <= 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SETTING_VALUE",
                    "设置值必须为正整数: " + key);
        }
    }

    private int parseOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}