package com.family.storage.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 开放注册请求（仅系统为空时可用，首个注册者为 ADMIN）。
 */
public record RegisterRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 6, max = 32) String password,
        @NotBlank @Size(max = 50) String displayName
) {
}