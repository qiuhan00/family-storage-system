package com.family.storage.auth;

import com.family.storage.auth.dto.AuthResponse;
import com.family.storage.auth.dto.ChangePasswordRequest;
import com.family.storage.auth.dto.LoginRequest;
import com.family.storage.auth.dto.RegisterRequest;
import com.family.storage.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证端点（spec §5.1）：register/login 放行，me/password 需登录。
 * 登录/注册限流键 = username|IP（后端内存限流，count 记录于 LoginRateLimiter）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@RequestBody @Valid RegisterRequest req, HttpServletRequest request) {
        return authService.register(req, attemptKey(req.username(), request));
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody @Valid LoginRequest req, HttpServletRequest request) {
        return authService.login(req, attemptKey(req.username(), request));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return authService.me(userId);
    }

    @PutMapping("/password")
    public void changePassword(@AuthenticationPrincipal Long userId,
                               @RequestBody @Valid ChangePasswordRequest req) {
        authService.changePassword(userId, req);
    }

    /** 限流键：用户名|客户端IP（优先取 X-Forwarded-For 首个地址，nginx 反代场景）。 */
    private String attemptKey(String username, HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return (username == null ? "" : username) + "|" + ip;
    }
}