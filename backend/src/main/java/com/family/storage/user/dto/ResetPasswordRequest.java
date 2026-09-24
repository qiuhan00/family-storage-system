package com.family.storage.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置指定用户密码请求。
 */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 6, max = 32) String newPassword
) {
}