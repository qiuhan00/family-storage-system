package com.family.storage.auth.dto;

import com.family.storage.user.dto.UserResponse;

/**
 * 认证响应：JWT + 用户信息。
 */
public record AuthResponse(String token, UserResponse user) {
}