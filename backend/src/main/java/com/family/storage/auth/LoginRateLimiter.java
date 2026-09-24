package com.family.storage.auth;

import com.family.storage.common.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 登录/注册轻量内存限流（code-review M3）：
 * 按「用户名|IP」维度累计失败次数，连续 5 次失败后锁定约 5 分钟，成功即清零；
 * 达到上限返回 429 {@code TOO_MANY_ATTEMPTS}，抵御暴力破解/撞库。
 * 纯 JDK 集合实现，单实例部署（nginx → 单后端，与架构一致）下足够；
 * 多实例部署时需替换为 Redis 等共享存储。
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(5);

    /** 防单 key 膨胀的软上限：超过后顺带清理过期条目。 */
    private static final int MAX_ENTRIES = 10_000;

    private final ConcurrentHashMap<String, Counter> failures = new ConcurrentHashMap<>();

    /** 若该 key 已锁定则抛 429；key 为 null（无 IP/用户名等）时不做限流。 */
    public void checkBlocked(String key) {
        if (key == null) {
            return;
        }
        purgeIfNeeded();
        Counter counter = failures.get(key);
        if (counter != null && counter.lockedUntil != null && counter.lockedUntil.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "尝试次数过多，请 5 分钟后再试");
        }
    }

    /** 记录一次失败；连续失败达到上限后锁定。 */
    public void recordFailure(String key) {
        if (key == null) {
            return;
        }
        purgeIfNeeded();
        Instant now = Instant.now();
        failures.compute(key, (k, prev) -> {
            Counter counter = (prev == null) ? new Counter() : prev;
            if (counter.lockedUntil != null && counter.lockedUntil.isAfter(now)) {
                return counter; // 已锁定，保持
            }
            counter.count++;
            if (counter.count >= MAX_FAILURES) {
                counter.lockedUntil = now.plus(LOCKOUT);
                counter.count = 0;
            }
            return counter;
        });
    }

    /** 成功后清零，使计数从零重新开始。 */
    public void clear(String key) {
        if (key == null) {
            return;
        }
        failures.remove(key);
    }

    private void purgeIfNeeded() {
        if (failures.size() < MAX_ENTRIES) {
            return;
        }
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Counter>> it = failures.entrySet().iterator();
        while (it.hasNext()) {
            Counter counter = it.next().getValue();
            if (counter.lockedUntil == null || !counter.lockedUntil.isAfter(now)) {
                it.remove();
            }
        }
    }

    static class Counter {
        int count;
        Instant lockedUntil;
    }
}