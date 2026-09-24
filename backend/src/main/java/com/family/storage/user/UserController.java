package com.family.storage.user;

import com.family.storage.common.PageResponse;
import com.family.storage.user.dto.ResetPasswordRequest;
import com.family.storage.user.dto.StatusRequest;
import com.family.storage.user.dto.UserRequest;
import com.family.storage.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理端点（spec §5.2，SecurityConfig 限定仅 ADMIN）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public PageResponse<UserResponse> list(Pageable pageable) {
        return userService.list(pageable);
    }

    @PostMapping
    public UserResponse create(@RequestBody @Valid UserRequest req) {
        return userService.create(req);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody @Valid UserRequest req) {
        return userService.update(id, req);
    }

    @PatchMapping("/{id}/status")
    public UserResponse setStatus(@PathVariable Long id, @RequestBody StatusRequest body) {
        return userService.setStatus(id, body.enabled());
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id, @RequestBody @Valid ResetPasswordRequest body) {
        userService.resetPassword(id, body);
    }
}