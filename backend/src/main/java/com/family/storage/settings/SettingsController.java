package com.family.storage.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用设置端点（spec §5.9，/api/settings）：GET 登录成员可读；
 * PUT 仅 ADMIN（SecurityConfig 路由约束），body 形如
 * {"key":"expiry.remind.days","value":"7"}。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {
    private final SettingsService settingsService;

    @GetMapping
    public Map<String, Object> get() {
        return settingsService.all();
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody @Valid SettingsUpdateRequest req) {
        settingsService.update(req.key(), req.value());
        return settingsService.all();
    }

    /** 设置更新请求体：key 白名单 + value 数字校验在 SettingsService 内完成。 */
    public record SettingsUpdateRequest(
            @NotBlank(message = "key 不能为空") String key,
            @NotBlank(message = "value 不能为空") String value) {
    }
}