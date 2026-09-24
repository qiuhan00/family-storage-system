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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 认证服务：首用户管理员注册、登录、修改本人密码、当前用户信息。
 * - M7：register 首用户 ADMIN 判定在 JVM 锁 + 事务内完成，并发首个注册者
 *   被串行化，后到者在 count() 复查时看到已提交的首个用户而收到 REGISTRATION_CLOSED。
 * - M3：login/register 接入 {@link LoginRateLimiter}（用户名+IP 维度），
 *   连续 5 次失败锁定 5 分钟，成功即清零，防暴力破解。
 */
@Service
public class AuthService {

    /** 首用户注册临界区锁：确保 count()>0 判定与保存在同一临界区内。 */
    private final Object bootstrapLock = new Object();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginRateLimiter loginRateLimiter;
    private final TransactionTemplate txTemplate;

    /**
     * 生产构造器：限流器与事务模板均为必填。
     * 事务模板用于让「count 判定 + save」在锁内完成提交，彻底闭合 TOCTOU 窗口。
     */
    @Autowired
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider, LoginRateLimiter loginRateLimiter,
                       TransactionTemplate txTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginRateLimiter = loginRateLimiter;
        this.txTemplate = txTemplate;
    }

    /** 便捷构造器（限流器/事务模板为 null 时对应能力降级为无操作）：供既有单元测试复用。 */
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this(userRepository, passwordEncoder, jwtTokenProvider, null, null);
    }

    public AuthResponse register(RegisterRequest req) {
        return register(req, null);
    }

    /**
     * @param attemptKey 限流键（用户名|IP），可为 null（走无限制路径，主要用于单元测试）。
     */
    public AuthResponse register(RegisterRequest req, String attemptKey) {
        if (loginRateLimiter != null) {
            loginRateLimiter.checkBlocked(attemptKey);
        }
        try {
            AuthResponse response;
            synchronized (bootstrapLock) {
                response = (txTemplate != null)
                        ? txTemplate.execute(status -> doRegister(req))
                        : doRegister(req);
            }
            if (loginRateLimiter != null) {
                loginRateLimiter.clear(attemptKey);
            }
            return response;
        } catch (ApiException e) {
            if (loginRateLimiter != null && "USERNAME_TAKEN".equals(e.getCode())) {
                loginRateLimiter.recordFailure(attemptKey);
            }
            throw e;
        }
    }

    private AuthResponse doRegister(RegisterRequest req) {
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
        return login(req, null);
    }

    /**
     * @param attemptKey 限流键（用户名|IP），可为 null（走无限制路径，主要用于单元测试）。
     */
    public AuthResponse login(LoginRequest req, String attemptKey) {
        if (loginRateLimiter != null) {
            loginRateLimiter.checkBlocked(attemptKey);
        }
        User u = userRepository.findByUsername(req.username())
                .filter(User::isEnabled)
                .orElseThrow(() -> {
                    if (loginRateLimiter != null) {
                        loginRateLimiter.recordFailure(attemptKey);
                    }
                    return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
                });
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            if (loginRateLimiter != null) {
                loginRateLimiter.recordFailure(attemptKey);
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        if (loginRateLimiter != null) {
            loginRateLimiter.clear(attemptKey);
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