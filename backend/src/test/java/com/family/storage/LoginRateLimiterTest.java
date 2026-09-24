package com.family.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.family.storage.auth.LoginRateLimiter;
import com.family.storage.common.ApiException;
import org.junit.jupiter.api.Test;

/**
 * 登录限流器单元测试（code-review M3）：
 * 连续 5 次失败锁定约 5 分钟，成功即清零，达到上限抛 429 TOO_MANY_ATTEMPTS。
 */
class LoginRateLimiterTest {

    @Test
    void lockoutAfterFiveFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String key = "alice|1.2.3.4";
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(key);
        }
        ApiException ex = assertThrows(ApiException.class, () -> limiter.checkBlocked(key));
        assertEquals(429, ex.getStatus());
        assertEquals("TOO_MANY_ATTEMPTS", ex.getCode());
    }

    @Test
    void fourFailuresStillAllowed() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String key = "bob|5.6.7.8";
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(key);
        }
        assertDoesNotThrow(() -> limiter.checkBlocked(key));
    }

    @Test
    void clearResetsCounterImmediately() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String key = "carol|9.9.9.9";
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(key);
        }
        assertThrows(ApiException.class, () -> limiter.checkBlocked(key));
        limiter.clear(key);
        assertDoesNotThrow(() -> limiter.checkBlocked(key));
    }

    @Test
    void differentKeysAreIsolated() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("dave|1.1.1.1");
        }
        assertThrows(ApiException.class, () -> limiter.checkBlocked("dave|1.1.1.1"));
        assertDoesNotThrow(() -> limiter.checkBlocked("dave|2.2.2.2"));
    }

    @Test
    void nullKeyIsNoOp() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        limiter.recordFailure(null);
        assertDoesNotThrow(() -> limiter.checkBlocked(null));
        limiter.clear(null);
    }
}