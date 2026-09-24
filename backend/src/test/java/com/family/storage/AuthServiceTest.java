package com.family.storage;

import com.family.storage.auth.AuthService;
import com.family.storage.auth.JwtTokenProvider;
import com.family.storage.auth.LoginRateLimiter;
import com.family.storage.auth.dto.LoginRequest;
import com.family.storage.auth.dto.RegisterRequest;
import com.family.storage.common.ApiException;
import com.family.storage.user.Role;
import com.family.storage.user.User;
import com.family.storage.user.UserRepository;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository userRepository;
    @Mock JwtTokenProvider jwtTokenProvider;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private AuthService svc() {
        return new AuthService(userRepository, encoder, jwtTokenProvider);
    }

    /** 带内存限流器的服务（无事务模板，测试 M3 路径）。 */
    private AuthService svcWithLimiter() {
        return new AuthService(userRepository, encoder, jwtTokenProvider, new LoginRateLimiter(), null);
    }

    /**
     * 带事务模板的服务（mock 事务管理器：getTransaction 返回 status、commit/rollback 均为空操作，
     * 模拟「锁内完成提交」的编排，用于 M7 临界区测试）。
     */
    private AuthService svcWithTx() {
        PlatformTransactionManager ptm = mock(PlatformTransactionManager.class);
        when(ptm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new AuthService(userRepository, encoder, jwtTokenProvider, null,
                new TransactionTemplate(ptm));
    }

    @Test
    void firstRegisteredUserBecomesAdmin() {
        when(userRepository.count()).thenReturn(0L);
        when(jwtTokenProvider.generateToken(any())).thenReturn("tok");
        var user = svc().register(new RegisterRequest("dad", "pass123", "老爸")).user();
        assertEquals(Role.ADMIN, user.role());
    }

    @Test
    void registerWhenNonEmptyIsForbidden() {
        when(userRepository.count()).thenReturn(3L);
        assertThrows(ApiException.class, () ->
                svc().register(new RegisterRequest("kid", "pass123", "小孩")));
    }

    @Test
    void passwordIsStoredHashed() {
        when(userRepository.count()).thenReturn(0L);
        when(jwtTokenProvider.generateToken(any())).thenReturn("tok");
        svc().register(new RegisterRequest("mom", "pass123", "老妈"));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertNotEquals("pass123", captor.getValue().getPasswordHash());
        assertTrue(encoder.matches("pass123", captor.getValue().getPasswordHash()));
    }

    @Test
    void garbageTokenParsingFails() {
        // 用真实 provider（32+ 字节密钥）验证垃圾串抛 JwtException，避免只测 mock
        var provider = new com.family.storage.auth.JwtTokenProvider(
                "unit-test-secret-key-at-least-32-bytes-long", 1L);
        assertThrows(io.jsonwebtoken.JwtException.class, () -> provider.parseUserId("not-a-jwt"));
    }

    // ---- M3：登录/注册限流 ----

    @Test
    void loginLockedAfterFiveConsecutiveFailures() {
        User u = new User();
        u.setUsername("alice");
        u.setPasswordHash(encoder.encode("right-pass"));
        u.setEnabled(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        AuthService svc = svcWithLimiter();
        String key = "alice|1.2.3.4";
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class, () ->
                    svc.login(new LoginRequest("alice", "wrong"), key));
        }
        ApiException ex = assertThrows(ApiException.class, () ->
                svc.login(new LoginRequest("alice", "right-pass"), key));
        assertEquals(429, ex.getStatus());
        assertEquals("TOO_MANY_ATTEMPTS", ex.getCode());
    }

    @Test
    void successfulLoginClearsFailureCounter() {
        User u = new User();
        u.setUsername("bob");
        u.setPasswordHash(encoder.encode("right-pass"));
        u.setEnabled(true);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(u));
        when(jwtTokenProvider.generateToken(any())).thenReturn("tok");

        AuthService svc = svcWithLimiter();
        String key = "bob|5.6.7.8";
        for (int i = 0; i < 3; i++) {
            assertThrows(ApiException.class, () ->
                    svc.login(new LoginRequest("bob", "wrong"), key));
        }
        assertNotNull(svc.login(new LoginRequest("bob", "right-pass"), key));
        // 成功清零后：又连续 4 次失败都只报 BAD_CREDENTIALS（若未清零，累计已达 7 次必然锁定）
        for (int i = 0; i < 4; i++) {
            assertThrows(ApiException.class, () ->
                    svc.login(new LoginRequest("bob", "wrong"), key));
        }
        // 第 5 次失败触发锁定（本次仍返回 BAD_CREDENTIALS）
        assertThrows(ApiException.class, () ->
                svc.login(new LoginRequest("bob", "wrong"), key));
        // 锁定后即使密码正确也 429
        ApiException ex = assertThrows(ApiException.class, () ->
                svc.login(new LoginRequest("bob", "right-pass"), key));
        assertEquals(429, ex.getStatus());
        assertEquals("TOO_MANY_ATTEMPTS", ex.getCode());
    }

    @Test
    void registerUsernameTakenFailuresAlsoLockOut() {
        User existing = new User();
        existing.setUsername("bob");
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(existing));

        AuthService svc = svcWithLimiter();
        String key = "bob|9.9.9.9";
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class, () ->
                    svc.register(new RegisterRequest("bob", "pass123", "Bob"), key));
        }
        ApiException ex = assertThrows(ApiException.class, () ->
                svc.register(new RegisterRequest("bob", "pass123", "Bob"), key));
        assertEquals(429, ex.getStatus());
        assertEquals("TOO_MANY_ATTEMPTS", ex.getCode());
    }

    // ---- M7：首用户注册 TOCTOU ----

    @Test
    void concurrentFirstRegistrationsCreateSingleAdmin() throws Exception {
        // 第一次 count() 为 0（首个注册成功），随后的 count() 为 1（注册关闭）
        when(userRepository.count()).thenReturn(0L).thenReturn(1L);
        when(jwtTokenProvider.generateToken(any())).thenReturn("tok");

        AuthService svc = svcWithTx();
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.List<Future<ApiException>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String username = "user" + i;
                results.add(pool.submit(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        svc.register(new RegisterRequest(username, "pass123", "成员" + username));
                        return null;
                    } catch (ApiException e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            int ok = 0;
            int rejected = 0;
            for (Future<ApiException> future : results) {
                ApiException ex = future.get(10, TimeUnit.SECONDS);
                if (ex == null) {
                    ok++;
                } else {
                    assertEquals("REGISTRATION_CLOSED", ex.getCode(), "后到注册者应收到注册关闭");
                    rejected++;
                }
            }
            assertEquals(1, ok, "并发首注册只能成功一个 ADMIN");
            assertEquals(1, rejected);
            verify(userRepository).save(any(User.class));
        } finally {
            pool.shutdownNow();
        }
    }
}