package com.family.storage.auth;

import com.family.storage.common.ApiException;
import com.family.storage.auth.dto.AuthResponse;
import com.family.storage.auth.dto.ChangePasswordRequest;
import com.family.storage.auth.dto.LoginRequest;
import com.family.storage.auth.dto.RegisterRequest;
import com.family.storage.user.Role;
import com.family.storage.user.User;
import com.family.storage.user.UserRepository;
import com.family.storage.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：首用户管理员注册、登录、修改本人密码、当前用户信息。
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.count() > 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REGISTRATION_CLOSED", "注册已关闭，请联系管理员创建账号");
        }
        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USERNAME_TAKEN", "用户名已存在");
        }
        User u = new User();
        u.setUsername(req.username());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setDisplayName(req.displayName());
        u.setRole(Role.ADMIN);
        userRepository.save(u);
        return tokenResponse(u);
    }

    public AuthResponse login(LoginRequest req) {
        User u = userRepository.findByUsername(req.username())
                .filter(User::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误"));
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        return tokenResponse(u);
    }

    public UserResponse me(Long userId) {
        User u = userRepository.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        return UserResponse.of(u);
    }

    public void changePassword(Long userId, ChangePasswordRequest req) {
        User u = userRepository.findById(userId).orElseThrow();
        if (!passwordEncoder.matches(req.oldPassword(), u.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "原密码错误");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(u);
    }

    private AuthResponse tokenResponse(User u) {
        String token = jwtTokenProvider == null ? "" : jwtTokenProvider.generateToken(u);
        return new AuthResponse(token, UserResponse.of(u));
    }
}