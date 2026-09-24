package com.family.storage.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改本人密码请求。
 */
public record ChangePasswordRequest(
        @NotBlank @Size(min = 6, max = 32) String oldPassword,
        @NotBlank @Size(min = 6, max = 32) String newPassword
) {
}