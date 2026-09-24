package com.family.storage.user.dto;

import com.family.storage.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户创建/更新请求（ADMIN）。password 仅在创建时使用：缺省则随机生成 8 位。
 * 更新时 username/password 字段被忽略（用户名不可改）。
 */
public record UserRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 50) String displayName,
        Role role,
        @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(min = 6, max = 32) String password
) {
}