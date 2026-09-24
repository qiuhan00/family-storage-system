package com.family.storage.user.dto;

import com.family.storage.user.Role;
import com.family.storage.user.User;

/**
 * 用户对外响应：id/username/displayName/role/enabled（spec §5.1/§5.2；enabled 供 ADMIN 启停回显）。
 */
public record UserResponse(Long id, String username, String displayName, Role role, boolean enabled) {
    public static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isEnabled());
    }
}