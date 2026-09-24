package com.family.storage.auth;

import com.family.storage.auth.dto.AuthResponse;
import com.family.storage.auth.dto.ChangePasswordRequest;
import com.family.storage.auth.dto.LoginRequest;
import com.family.storage.auth.dto.RegisterRequest;
import com.family.storage.user.dto.UserResponse;
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
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@RequestBody @Valid RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody @Valid LoginRequest req) {
        return authService.login(req);
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
}