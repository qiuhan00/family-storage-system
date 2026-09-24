package com.family.storage.user;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.family.storage.common.ApiException;
import com.family.storage.common.PageResponse;
import com.family.storage.user.dto.ResetPasswordRequest;
import com.family.storage.user.dto.UserRequest;
import com.family.storage.user.dto.UserResponse;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户管理（仅 ADMIN）：分页列表、创建成员、更新信息、启停、重置密码。
 * 用户名在更新时保持不变（spec §5.2：更新昵称/角色/联系方式）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String RANDOM_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.of(userRepository.findAll(pageable).map(UserResponse::of));
    }

    public UserResponse create(UserRequest req) {
        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USERNAME_TAKEN", "用户名已存在");
        }
        User u = new User();
        u.setUsername(req.username());
        String raw = (req.password() != null && !req.password().isBlank()) ? req.password() : randomPassword();
        u.setPasswordHash(passwordEncoder.encode(raw));
        u.setDisplayName(req.displayName());
        u.setRole(req.role() != null ? req.role() : Role.MEMBER);
        u.setEmail(req.email());
        u.setPhone(req.phone());
        userRepository.save(u);
        return UserResponse.of(u);
    }

    public UserResponse update(Long id, UserRequest req) {
        User u = find(id);
        // M6：把启用态 ADMIN 降级为 MEMBER 前，若系统已无其他启用 ADMIN，拒绝
        if (u.isEnabled() && u.getRole() == Role.ADMIN && req.role() != null && req.role() != Role.ADMIN) {
            ensureAnotherEnabledAdminExists(u);
        }
        u.setDisplayName(req.displayName());
        if (req.role() != null) {
            u.setRole(req.role());
        }
        u.setEmail(req.email());
        u.setPhone(req.phone());
        userRepository.save(u);
        return UserResponse.of(u);
    }

    public UserResponse setStatus(Long id, boolean enabled) {
        User u = find(id);
        // M6：停用启用态 ADMIN 前，若系统已无其他启用 ADMIN，拒绝，保持系统可管理性
        if (!enabled && u.isEnabled() && u.getRole() == Role.ADMIN) {
            ensureAnotherEnabledAdminExists(u);
        }
        u.setEnabled(enabled);
        userRepository.save(u);
        return UserResponse.of(u);
    }

    /** 校验除目标用户外仍有启用 ADMIN；否则抛 400 LAST_ADMIN。 */
    private void ensureAnotherEnabledAdminExists(User target) {
        long enabledAdmins = userRepository.countByEnabledTrueAndRole(Role.ADMIN);
        if (enabledAdmins <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_ADMIN",
                    "系统需保留至少一名启用状态的管理员，请先启用其他管理员");
        }
    }

    public void resetPassword(Long id, ResetPasswordRequest req) {
        User u = find(id);
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(u);
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString();
    }
}