package com.family.storage.auth;

import com.family.storage.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 生成与解析（jjwt 0.12.x）。subject=userId，claim=role。
 * 无效/过期 token 的解析统一抛 {@link io.jsonwebtoken.JwtException}，
 * 由 {@link JwtAuthFilter} 捕获后降级为匿名 → 401。
 */
@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long expireMillis;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.expire-hours}") long hours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = hours * 3600_000L;
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireMillis))
                .signWith(key)
                .compact();
    }

    public long parseUserId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    public String parseRole(String token) {
        return parse(token).get("role", String.class);
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}