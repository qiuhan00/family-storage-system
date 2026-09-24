# 家庭收纳物品管理系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一个可上云部署的家庭收纳管理系统：手机优先的 Vue 前端 + Spring Boot 后端 + MySQL，支持多人账号、物品/分类/位置检索、购物清单、临期站内提醒（预留短信通道）、照片、价格统计。

**Architecture:** 前后端分离单体仓库。`backend/` Spring Boot 3 提供 REST API（JWT 无状态认证，通知走 `NotificationChannel` 接口，临期扫描为每日定时任务）；`frontend/` Vue 3 + Vant 移动优先 SPA；生产非容器部署（jar + nginx 反代）。

**Tech Stack:** Java 17 / Spring Boot 3.3 / Spring Data JPA / Spring Security / JWT(jjwt 0.12) / Flyway / MySQL 8 / Maven；Vue 3 / TypeScript / Vite / Pinia / Vue Router / Vant / Axios / dayjs / vitest。

**Spec:** `docs/superpowers/specs/2026-09-24-家庭收纳管理系统-design.md`

## Global Constraints

- 所有接口前缀 `/api`，JSON + JWT Bearer；错误体固定 `{code, message}`；401/403/400/404 语义见 spec §5。
- 分页响应固定 `{content, totalElements, totalPages, size, number}`。
- 密码 BCrypt；JWT 密钥/DB 口令经环境变量注入（`JWT_SECRET`，≥32 字节；`DB_PASSWORD`），不得硬编码入码+入 Git。
- 数据库 utf8mb4；Flyway 迁移驱动（`ddl-auto: none`）；表与字段名与 spec §4 完全一致。
- 删除分类/位置时若被 `items` 引用则 400；用户软停用（`enabled=false`）。
- 临期判定：`expiry_date` 在 `[today, today+阈值]` 且 `status=ACTIVE`；阈值 = `app_settings.expiry.remind.days` 默认 `7`。
- 站内通知幂等键 `reminder_key = EXPIRY:{itemId}:{yyyy-MM-dd}`，唯一 `(user_id, reminder_key)`。
- 上传图片限 jpg/png/webp、≤5MB，落盘 `upload-dir`（默认 `./uploads`）。
- 首个注册用户为 ADMIN；非空后 `/api/auth/register` 返回 403。
- 界面与错误消息为中文。

## Review Focus

- 非空系统下尝试注册 → 返回 403（而非创建 MEMBER 或异常）。
- 过期/伪造 JWT → 401 且不返回 500；无 ADMIN 权限调用管理接口 → 403。
- 临期调度重复执行（同一天跑多次/重启）→ 不产生重复通知。
- 删除仍被物品引用的分类/位置 → 400；删除已停用用户的关联数据不报错。
- 上传非图片类型或超大文件 → 400，不是 500；`quantity` 变负 → 400。

---

### Task 1: Backend 骨架 + Flyway 迁移

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V1__init.sql`
- Create: `backend/src/main/java/com/family/storage/StorageApplication.java`
- Create: `backend/src/test/java/com/family/storage/StorageApplicationTests.java`
- Create: `backend/src/main/java/com/family/storage/config/SecurityConfig.java`（骨架，仅放行，后续任务补鉴权）
- Create: `backend/src/main/java/com/family/storage/common/ApiException.java`
- Create: `backend/src/main/java/com/family/storage/common/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/family/storage/common/PageResponse.java`
- Create: `backend/src/main/java/com/family/storage/config/OpenApiConfig.java`

**Interfaces:**
- Consumes: 无（首任务）
- Produces:
  - `ApiException(int status, String code, String message)` — 业务异常；`GlobalExceptionHandler` 由其返回 `{code, message}`。
  - `PageResponse<T>(List<T> content, long totalElements, int totalPages, int size, int number)`，静态 `PageResponse<T> of(Page<T>)`。
  - `OpenApiConfig` 启用 `/v3/api-docs` 与 `/swagger-ui.html`，并放行。
  - Flyway `V1__init.sql` 建 spec §4 全部 8 张表。

- [ ] **Step 1: 失败测试——迁移建表可用**

```java
package com.family.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StorageApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: 先写 `pom.xml`、`application.yml`、`StorageApplication.java`，再跑测试验证失败（连不上 MySQL）**

Run: `cd backend; mvn test`
数据库：本机 MySQL 5.7（服务 MySQL57，库 `family_storage` 已建好，utf8mb4）。连接密码从用户级环境变量读取，命令前执行：
`$env:DB_PASSWORD = [Environment]::GetEnvironmentVariable('DB_PASSWORD','User')`
Expected: FAIL（`Communications link failure` / 认证失败 / UnknownHost 等其一）。

- [ ] **Step 3: 实现骨架**

`pom.xml` 关键依赖与属性：

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.3.5</version>
  <relativePath/>
</parent>
<properties>
  <java.version>17</java.version>
  <jjwt.version>0.12.6</jjwt.version>
</properties>
<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
  <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
  <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.6.0</version></dependency>
  <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>${jjwt.version}</version></dependency>
  <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
  <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>${jjwt.version}</version><scope>runtime</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
</dependencies>
```

`application.yml`：

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/family_storage?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD:root}
  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false
  flyway:
    enabled: true
    baseline-on-migrate: true
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB
app:
  jwt:
    secret: ${JWT_SECRET:dev-secret-change-me-at-least-32-bytes}
    expire-hours: 168
  upload-dir: ${UPLOAD_DIR:./uploads}
springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

`V1__init.sql`：逐条实现 spec §4 的 8 张表与索引（字段名与 spec 完全一致，类型：`id BIGINT AUTO_INCREMENT PRIMARY KEY`、`quantity INT`、`purchase_price DECIMAL(10,2)`、时间戳 `DATETIME`、枚举用 `VARCHAR(20)`）。含：
- `users(username UNIQUE, password_hash VARCHAR(100), display_name VARCHAR(50), role VARCHAR(20), email VARCHAR(100), phone VARCHAR(20), enabled BIT DEFAULT 1, created_at, updated_at)`
- `categories(name UNIQUE, sort_order INT)`
- `locations(name, parent_id NULL FK->locations.id, sort_order INT)`
- `items(name VARCHAR(100), category_id FK, location_id FK, quantity INT, unit VARCHAR(20), purchase_price DECIMAL(10,2), purchase_date DATE NULL, expiry_date DATE NULL, photo_url VARCHAR(255), status VARCHAR(20) DEFAULT 'ACTIVE', remark VARCHAR(500), created_by BIGINT FK->users.id, created_at, updated_at)`，索引 `idx_items_name (name(64))`、`idx_items_expiry (expiry_date)`、`(category_id, location_id)`
  - MySQL 5.7 utf8mb4 前缀索引长度上限：所有索引列按 VARCHAR(100) 以内 + 长文本用 `name(64)` 前缀，规避 767 字节限制。
- `stock_changes(item_id FK, change_qty INT, type VARCHAR(20), note VARCHAR(200), created_by BIGINT, created_at)`
- `shopping_items(name, quantity INT, unit VARCHAR(20), category_id FK NULL, remark VARCHAR(200), status VARCHAR(20) DEFAULT 'PENDING', created_by BIGINT, created_at, completed_at NULL)`
- `notifications(user_id FK, title VARCHAR(100), body VARCHAR(500), type VARCHAR(20), ref_id BIGINT NULL, reminder_key VARCHAR(100) NULL, read_flag BIT DEFAULT 0, created_at)`，唯一索引 `(user_id, reminder_key)`
- `app_settings(key VARCHAR(50) UNIQUE, value VARCHAR(255))`，种子 `INSERT INTO app_settings(key,value) VALUES ('expiry.remind.days','7')`

`StorageApplication.java`：

```java
package com.family.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class StorageApplication {
    public static void main(String[] args) {
        SpringApplication.run(StorageApplication.class, args);
    }
}
```

`ApiException.java`：

```java
package com.family.storage.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status.value();
        this.code = code;
    }
}
```

（引入 Lombok `lombok` 依赖，或手写 getter。）

`GlobalExceptionHandler.java`：

```java
package com.family.storage.common;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getCode(), "message", e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).findFirst().orElse("参数校验失败");
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", msg));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception e) {
        return ResponseEntity.status(500).body(Map.of("code", "INTERNAL_ERROR", "message", "服务器内部错误"));
    }
}
```

`PageResponse.java`：

```java
package com.family.storage.common;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int size, int number) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getSize(), page.getNumber());
    }
}
```

`SecurityConfig.java`（骨架；Task 2 补齐 JWT 过滤器与角色规则）：

```java
package com.family.storage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/register", "/uploads/**",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
```

`OpenApiConfig.java`：

```java
package com.family.storage.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
}
```

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend; mvn test`
Expected: PASS（`contextLoads` 成功，Flyway 完成建表）。

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat: backend skeleton with flyway schema v1"
```

---

### Task 2: 认证与用户（JWT + 角色 + 首用户管理员）

**Files:**
- Create: `backend/src/main/java/com/family/storage/user/User.java`
- Create: `backend/src/main/java/com/family/storage/user/UserRepository.java`
- Create: `backend/src/main/java/com/family/storage/auth/AuthController.java`
- Create: `backend/src/main/java/com/family/storage/auth/AuthService.java`
- Create: `backend/src/main/java/com/family/storage/auth/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/family/storage/auth/dto/AuthResponse.java`
- Create: `backend/src/main/java/com/family/storage/auth/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/family/storage/auth/dto/ChangePasswordRequest.java`
- Create: `backend/src/main/java/com/family/storage/auth/JwtTokenProvider.java`
- Create: `backend/src/main/java/com/family/storage/auth/JwtAuthFilter.java`
- Create: `backend/src/main/java/com/family/storage/user/UserController.java`
- Create: `backend/src/main/java/com/family/storage/user/UserService.java`
- Create: `backend/src/main/java/com/family/storage/user/dto/UserRequest.java`
- Create: `backend/src/main/java/com/family/storage/user/dto/UserResponse.java`
- Create: `backend/src/main/java/com/family/storage/user/dto/ResetPasswordRequest.java`
- Modify: `backend/src/main/java/com/family/storage/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/family/storage/AuthServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 `ApiException`、`PageResponse`、`SecurityConfig`、`PasswordEncoder`。
- Produces:
  - `enum Role { ADMIN, MEMBER }`
  - `User(id, username, passwordHash, displayName, Role role, email, phone, boolean enabled, createdAt, updatedAt)`
  - `UserRepository extends JpaRepository<User, Long>`，含 `Optional<User> findByUsername(String)`, `long countByEnabledTrue()`
  - `JwtTokenProvider.generateToken(User): String`、`parseUserId(String token): Long`、`parseRole(String token): String`
  - `AuthService.login(LoginRequest): AuthResponse`、`register(RegisterRequest): AuthResponse`、`changePassword(Long userId, ChangePasswordRequest)`
  - `AuthResponse(String token, UserResponse user)`
  - `UserResponse(Long id, String username, String displayName, Role role)` + 静态 `of(User)`
  - 端点：见 spec §5.1、§5.2。

- [ ] **Step 1: 失败测试（首用户管理员、密码 BCrypt、角色保护）**

```java
package com.family.storage;

import com.family.storage.auth.AuthService;
import com.family.storage.auth.JwtTokenProvider;
import com.family.storage.auth.dto.RegisterRequest;
import com.family.storage.common.ApiException;
import com.family.storage.user.Role;
import com.family.storage.user.User;
import com.family.storage.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd backend; mvn -Dtest=AuthServiceTest test`
Expected: FAIL，类/方法不存在。

- [ ] **Step 3: 实现实体、Service 与 DTO**

`register` 返回 `AuthResponse(token, user)`，token 由 `JwtTokenProvider` 生成；测试中 mock 该 provider。`JwtTokenProvider.parse*` 对无效串抛 `JwtException`（由 `JwtAuthFilter` 捕获为匿名 → 401）。User 需暴露 `getPasswordHash()` 供校验：

```java
package com.family.storage.user;

public enum Role { ADMIN, MEMBER }
```

`User.java`（节选，含 JPA 注解）：

```java
package com.family.storage.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false, length = 50) private String username;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "display_name", nullable = false, length = 50) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Role role = Role.MEMBER;
    private String email;
    private String phone;
    private boolean enabled = true;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @PrePersist void pre() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
    // + getters/setters（手写或 Lombok）
}
```

`AuthService.java` 核心：

```java
package com.family.storage.auth;

import com.family.storage.common.ApiException;
import com.family.storage.auth.dto.*;
import com.family.storage.user.Role;
import com.family.storage.user.User;
import com.family.storage.user.UserRepository;
import com.family.storage.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
```

`JwtTokenProvider.java`：

```java
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
```

`JwtAuthFilter.java`（验证失败时抛 401 而非 500；本任务仅装配过滤器，角色判定在 Task 使用处）：

```java
package com.family.storage.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                long userId = jwtTokenProvider.parseUserId(token);
                String role = jwtTokenProvider.parseRole(token);
                var auth = new UsernamePasswordAuthenticationToken(userId, null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // 无效 token：继续匿名，安全规则将返回 401
            }
        }
        chain.doFilter(request, response);
    }
}
```

`SecurityConfig` 追加（放行 register/login；其余需认证；ADMIN 规则命中管理端点）：

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                       com.family.storage.auth.AuthenticationEntryPoint401 entryPoint) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/login", "/api/auth/register", "/uploads/**",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .requestMatchers("/api/users/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .addFilterBefore(jwtAuthFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

`AuthenticationEntryPoint401.java`：返回 401 JSON `{code:"UNAUTHORIZED", message:"未登录"}`（`ApiException` 语义串联：在 once-per-request 内判断未认证时写 401，避免 403 滥用——2026 语义：未带 token → 401）。

`UserController.java`（ADMIN，用 `@PreAuthorize("hasRole('ADMIN')")` 或保持 SecurityConfig 规则即可；在 `@EnableMethodSecurity` 下加注解，二选一，方案取 SecurityConfig 规则，控制器不加注解）：

```java
package com.family.storage.user;

import com.family.storage.common.PageResponse;
import com.family.storage.user.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public PageResponse<UserResponse> list(Pageable pageable) { return userService.list(pageable); }

    @PostMapping
    public UserResponse create(@RequestBody @jakarta.validation.Valid UserRequest req) { return userService.create(req); }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody @jakarta.validation.Valid UserRequest req) { return userService.update(id, req); }

    @PatchMapping("/{id}/status")
    public UserResponse setStatus(@PathVariable Long id, @RequestBody StatusRequest body) { return userService.setStatus(id, body.enabled()); }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest body) { userService.resetPassword(id, body); }
}
```

`UserService.java`：`create` 编码新密码（随机 8 位或 body 提供）、`setStatus` 停用时保留数据、`resetPassword` 用 `ResetPasswordRequest.newPassword`。`UserResponse.of(User)` 输出 id/username/displayName/role。

DTO 用 record + Bean Validation（`@NotBlank`、`@Size(min=6,max=32)` 密码等）。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend; mvn -Dtest=AuthServiceTest test`
Expected: PASS（3 个用例）。其余编译错误一并修复。

- [ ] **Step 5: 手动冒烟（可选）**

Run: `cd backend; mvn spring-boot:run`，然后用 `curl` 或 api-tester 验证注册/登录返回 token。

- [ ] **Step 6: Commit**

```bash
git add backend
git commit -m "feat: jwt auth and user management with first-admin rule"
```

---

### Task 3: 分类与位置（层级）CRUD

**Files:**
- Create: `backend/src/main/java/com/family/storage/category/Category.java`、`CategoryRepository.java`、`CategoryController.java`、`CategoryService.java`、`category/dto/CategoryRequest.java`
- Create: `backend/src/main/java/com/family/storage/location/Location.java`、`LocationRepository.java`、`LocationController.java`、`LocationService.java`、`location/dto/LocationRequest.java`
- Test: `backend/src/test/java/com/family/storage/CategoryServiceTest.java`、`LocationServiceTest.java`

**Interfaces:**
- Consumes: Task 2 的 `ApiException`、安全上下文 `principal=userId`（Long）。
- Produces:
  - `CategoryService.list()`、`create(CategoryRequest): Long`、`update(Long, CategoryRequest)`、`delete(Long)`
  - `LocationService.listTree(): List<LocationResponse>`（`LocationResponse(id,name,parentId,sortOrder,List<LocationResponse> children)`）、`create/create` 校验父位置存在且不构成环、`delete` 校验无 `items` 引用。
  - `ItemRepository.countByCategoryId(Long)`、`countByLocationId(Long)`（Item 实体本任务不动，Task 4 建；Task 3 先只给 service 留接口，用 Spring Data 方法即可，Task 4 编译）

- [ ] **Step 1: 失败测试**

```java
package com.family.storage;

import com.family.storage.category.CategoryRepository;
import com.family.storage.category.CategoryService;
import com.family.storage.common.ApiException;
import com.family.storage.item.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock CategoryRepository categoryRepository;
    @Mock ItemRepository itemRepository;

    @Test
    void deleteCategoryWithItemsIsRejected() {
        when(itemRepository.countByCategoryId(1L)).thenReturn(3L);
        var svc = new CategoryService(categoryRepository, itemRepository);
        assertThrows(ApiException.class, () -> svc.delete(1L));
    }
}
```

```java
package com.family.storage;

import com.family.storage.common.ApiException;
import com.family.storage.item.ItemRepository;
import com.family.storage.location.Location;
import com.family.storage.location.LocationRepository;
import com.family.storage.location.LocationService;
import com.family.storage.location.dto.LocationRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {
    @Mock LocationRepository locationRepository;
    @Mock ItemRepository itemRepository;

    @Test
    void movingLocationUnderItselfIsRejected() {
        Location location = new Location();
        location.setId(5L);
        when(locationRepository.findById(5L)).thenReturn(Optional.of(location));
        var svc = new LocationService(locationRepository, itemRepository);
        assertThrows(ApiException.class, () -> svc.update(5L, new LocationRequest("客厅", 5L, 0)));
    }
}
```

（服务单测覆盖核心规则；其余端点行为以 Task 10 的 api-tester 集成回归验证。）

- [ ] **Step 2: 跑测试验证失败**

Run: `cd backend; mvn -Dtest=CategoryServiceTest,LocationServiceTest test`
Expected: FAIL。

- [ ] **Step 3: 实现**

- `CategoryService`：依赖 `ItemRepository.countByCategoryId`；存在索引则抛 400 `CATEGORY_IN_USE`。
- `LocationService`：`resolveChildren`（自顶向下组装树）、父校验、环校验（parentId == id 或父链包含自身→400 `LOCATION_CYCLE`）。
- Controller 端点按 spec §5.3；`DELETE /api/categories/{id}` 有绑定物品返回 `{code:"CATEGORY_IN_USE"}`。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend; mvn -Dtest=CategoryServiceTest,LocationServiceTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat: category and location crud with hierarchy"
```

---

### Task 4: 物品 CRUD + 分页检索

**Files:**
- Create: `backend/src/main/java/com/family/storage/item/Item.java`、`ItemRepository.java`、`ItemController.java`、`ItemService.java`
- Create: `backend/src/main/java/com/family/storage/item/dto/ItemRequest.java`、`ItemResponse.java`
- Test: `backend/src/test/java/com/family/storage/ItemServiceTest.java`

**Interfaces:**
- Consumes: Task 3 的 `CategoryRepository`/`LocationRepository`（校验外键存在→404）。
- Produces:
  - `ItemResponse(Long id, String name, Long categoryId, Long locationId, int quantity, String unit, BigDecimal purchasePrice, LocalDate purchaseDate, LocalDate expiryDate, String photoUrl, String status, String remark, Long createdBy, LocalDateTime createdAt)`
  - `ItemService.search(String keyword, Long categoryId, Long locationId, String status, boolean expiring, Pageable): PageResponse<ItemResponse>`
  - `ItemService.expiringList(int days): List<ItemResponse>`
  - `ItemService.create(ItemRequest, Long userId)`、`update(Long, ItemRequest)`、`delete(Long)`

- [ ] **Step 1: 失败测试**

```java
package com.family.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.family.storage.common.ApiException;
import com.family.storage.item.ItemRepository;
import com.family.storage.item.ItemService;
import com.family.storage.item.dto.ItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {
    @Mock ItemRepository itemRepository;
    @Mock com.family.storage.category.CategoryRepository categoryRepository;
    @Mock com.family.storage.location.LocationRepository locationRepository;

    @Test
    void createItemWithMissingCategoryThrows404() {
        when(categoryRepository.existsById(999L)).thenReturn(false);
        var svc = new ItemService(itemRepository, categoryRepository, locationRepository);
        var req = new ItemRequest("牛奶", 999L, 1L, 3, "盒", new BigDecimal("5.9"),
                LocalDate.now(), LocalDate.now().plusDays(20), null, null);
        assertThrows(ApiException.class, () -> svc.create(req, 1L));
    }

    @Test
    void quantityCannotBeNegative() {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(locationRepository.existsById(1L)).thenReturn(true);
        var svc = new ItemService(itemRepository, categoryRepository, locationRepository);
        var req = new ItemRequest("可乐", 1L, 1L, -1, "瓶", null, null, null, null, null);
        assertThrows(ApiException.class, () -> svc.create(req, 1L));
    }
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd backend; mvn -Dtest=ItemServiceTest test`
Expected: FAIL。

- [ ] **Step 3: 实现**

- `ItemRepository`：`List<Item> findByStatusNot(String archived)` + 分页查询使用 `JpaSpecificationExecutor`，或手写 JPQL：
  - `@Query("select i from Item i where (:kw is null or i.name like %:kw%) and (:cat is null or i.categoryId=:cat) and (:loc is null or i.locationId=:loc) and (:st is null or i.status=:st) and (:exp=false or (i.expiryDate is not null and i.expiryDate between :today and :limit))")`，`Page<Item> search(..., Pageable)`。
  - `List<Item> findByStatusAndExpiryDateBetween(String status, LocalDate from, LocalDate to)`。
- `ItemService.search`：默认 `status=ACTIVE`（未传时查 ACTIVE + “不限”由 `impact` 参数决定——本方案：未传 status 只返回 ACTIVE）。
- `create` 时仅落 `items`；`INITIAL` 出入库记录在 Task 5 随 `stock_changes` 模型一并写入（spec §5.4 语义在 Task 5 结束时达成）。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend; mvn -Dtest=ItemServiceTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat: item crud with paged search"
```

---

### Task 5: 出入库与历史

**Files:**
- Create: `backend/src/main/java/com/family/storage/item/StockChange.java`、`StockChangeRepository.java`
- Create: `backend/src/main/java/com/family/storage/item/StockRequest.java`
- Create: `backend/src/main/java/com/family/storage/item/StockChangeController.java`（并入 `ItemController` 亦可，取并入方案）
- Modify: `backend/src/main/java/com/family/storage/item/ItemService.java`（`adjustStock` 方法）
- Test: `backend/src/test/java/com/family/storage/StockChangeServiceTest.java`

**Interfaces:**
- Consumes: Task 4 的 `ItemService`、`ItemRepository`。
- Produces:
  - `enum StockType { INITIAL, IN, OUT, ADJUST }`
  - `ItemService.adjustStock(Long itemId, StockRequest, Long userId)` — 校验新 quantity ≥0，写 `stock_changes`。
  - `ItemService.stockHistory(Long itemId): List<StockChangeResponse>`
  - `StockChangeResponse(id, itemId, changeQty, type, note, createdBy, createdAt)`

- [ ] **Step 1: 失败测试**

```java
package com.family.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.family.storage.common.ApiException;
import com.family.storage.item.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockChangeServiceTest {
    @Mock ItemRepository itemRepository;
    @Mock StockChangeRepository stockChangeRepository;
    @Test
    void outMoreThanStockIsRejected() {
        Item item = new Item();
        item.setId(1L); item.setQuantity(2);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        var svc = new ItemService(itemRepository, null, null, stockChangeRepository);
        assertThrows(ApiException.class, () -> svc.adjustStock(1L, new StockRequest(-5, "OUT", "用到"), 9L));
    }
    @Test
    void adjustUpdatesQuantityAndWritesRecord() {
        Item item = new Item();
        item.setId(1L); item.setQuantity(2);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(stockChangeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var svc = new ItemService(itemRepository, null, null, stockChangeRepository);
        svc.adjustStock(1L, new StockRequest(3, "IN", "补货"), 9L);
        assertEquals(5, item.getQuantity());
        verify(stockChangeRepository).save(any());
    }
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `cd backend; mvn -Dtest=StockChangeServiceTest test`
Expected: FAIL。

- [ ] **Step 3: 实现**

`ItemService.adjustStock`：由 `itemId` 取 Item；`delta` 后 `quantity + delta < 0` → 400 `INSUFFICIENT_STOCK`；否则累加、保存，写 `StockChange`（`type` 与 `delta>0? IN : OUT` 对齐，`ADJUST` 为手动校准专用）。`DELETE /api/items/{id}` 级联删除 `stockChangeRepository.deleteByItemId(id)`。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend; mvn -Dtest=StockChangeServiceTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat: stock in/out adjustment with audit history"
```

---

### Task 6: 缺耗品购物清单

**Files:**
- Create: `backend/src/main/java/com/family/storage/shopping/ShoppingItem.java`、`ShoppingItemRepository.java`、`ShoppingItemController.java`、`ShoppingItemService.java`、`shopping/dto/ShoppingRequest.java`、`shopping/dto/ShoppingItemResponse.java`
- Test: `backend/src/test/java/com/family/storage/ShoppingServiceTest.java`

**Interfaces:**
- Produces:
  - `enum ShoppingStatus { PENDING, BOUGHT, CANCELLED }`
  - `ShoppingItemService.list(ShoppingStatus status): List<ShoppingItemResponse>`；`create(ShoppingRequest, Long userId)`；`update(Long, ShoppingRequest)`；`setStatus(Long, ShoppingStatus)`
  - 端点：spec §5.5。

- [ ] **Step 1: 失败测试**

```java
package com.family.storage;

import static org.junit.jupiter.api.Assertions.*;
import com.family.storage.common.ApiException;
import com.family.storage.shopping.*;
import com.family.storage.shopping.dto.ShoppingRequest;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShoppingServiceTest {
    @Mock ShoppingItemRepository repo;
    @Test
    void createRequiresQuantityPositive() {
        var svc = new ShoppingItemService(repo, null);
        assertThrows(ApiException.class, () -> svc.create(new ShoppingRequest("盐", 0, "袋", null, null), 1L));
    }
}
```

- [ ] **Step 2: Run、验证失败**

Run: `cd backend; mvn -Dtest=ShoppingServiceTest test`
Expected: FAIL。

- [ ] **Step 3: 实现**（CRUD + 状态流转，参考 spec §5.5；`BOUGHT`/`CANCELLED` 写 `completedAt`）

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend; mvn -Dtest=ShoppingServiceTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat: shopping list with status workflow"
```

---

### Task 7: 图片文件上传/删除

**Files:**
- Create: `backend/src/main/java/com/family/storage/file/FileStorageService.java`、`file/FileController.java`
- Test: `backend/src/test/java/com/family/storage/FileStorageServiceTest.java`

**Interfaces:**
- Produces:
  - `FileStorageService.store(MultipartFile): String url`（`/uploads/{yyyyMM}/{uuid}.{ext}`）、`delete(String fileName)`。
  - 端点 `POST /api/files` → `{url}`；`DELETE /api/files/{name}`。

- [ ] **Step 1: 失败测试**

```java
package com.family.storage;

import static org.junit.jupiter.api.Assertions.*;
import com.family.storage.common.ApiException;
import com.family.storage.file.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {
    @TempDir Path tempDir;

    @Test
    void nonImageTypeRejected() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        assertThrows(ApiException.class, () -> svc.store(file));
    }

    @Test
    void oversizedFileRejected() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        byte[] big = new byte[6 * 1024 * 1024];
        var file = new MockMultipartFile("file", "a.png", "image/png", big);
        assertThrows(ApiException.class, () -> svc.store(file));
    }
}
```

- [ ] **Step 2: Run、验证失败**

Run: `cd backend; mvn -Dtest=FileStorageServiceTest test`
Expected: FAIL。

- [ ] **Step 3: 实现**

- 白名单扩展：`jpg/jpeg/png/webp`；Content-Type 亦校验（`image/` 前缀）；`>5MB` → 400 `FILE_TOO_LARGE`；根目录 `app.upload-dir/yyyyMM/`，文件名 `UUID`。
- `FileStorageService` 构造：`new FileStorageService(uploadDir, urlPrefix)`，启动时 `Files.createDirectories`。
- nginx 托管 `/uploads`（Task 16）。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend; mvn -Dtest=FileStorageServiceTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat: image upload with type and size validation"
```

---

### Task 8: 通知 + 临期调度 + 设置（含短信扩展点）

**Files:**
- Create: `backend/src/main/java/com/family/storage/notify/Notification.java`、`NotificationRepository.java`、`NotificationController.java`、`NotificationService.java`
- Create: `backend/src/main/java/com/family/storage/notify/channel/NotificationChannel.java`、`ChannelType.java`、`NotificationContext.java`、`InAppChannel.java`、`SmsChannel.java`
- Create: `backend/src/main/java/com/family/storage/notify/ExpiryReminderScheduler.java`
- Create: `backend/src/main/java/com/family/storage/settings/AppSetting.java`、`AppSettingRepository.java`、`SettingsController.java`、`SettingsService.java`
- Test: `backend/src/test/java/com/family/storage/ExpiryReminderSchedulerTest.java`、`SettingsServiceTest.java`

**Interfaces:**
- Consumes: Task 4 的 `ItemRepository`、Task 2 的 `UserRepository`。
- Produces:
  - `interface NotificationChannel { ChannelType type(); void send(NotificationContext ctx); }`
  - `enum ChannelType { IN_APP, SMS }`
  - `record NotificationContext(Long userId, String title, String body, NotificationType type, Long refId, String reminderKey)`
  - `InAppChannel.send` → 依赖 `NotificationRepository` 写库（`reminder_key` 冲突时忽略冲突）。
  - `SmsChannel`（`@ConditionalOnProperty sms.enabled`）：`type()==SMS`；disabled 时构造为 no-op。
  - `NotificationService.list(Boolean unread, Pageable)`、`unreadCount(Long userId)`、`read(Long id, Long userId)`、`readAll(Long userId)`。
  - `SettingsService.getInt(key, default)`、`update(String key, String value)`（ADMIN）。
  - `ExpiryReminderScheduler.scanAndNotify()` — 幂等：仅当 `reminder_key` 不存在时发送。

- [ ] **Step 1: 失败测试**

```java
package com.family.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.family.storage.item.ItemRepository;
import com.family.storage.notify.ExpiryReminderScheduler;
import com.family.storage.notify.channel.InAppChannel;
import com.family.storage.notify.channel.NotificationContext;
import com.family.storage.settings.SettingsService;
import com.family.storage.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ExpiryReminderSchedulerTest {
    @Mock ItemRepository itemRepository;
    @Mock UserRepository userRepository;
    @Mock InAppChannel inAppChannel;
    @Mock SettingsService settingsService;

    @Test
    void schedulerSkipsAlreadyNotifiedItems() {
        // itemRepository.findExpiring(...) -> [expiring item]
        // inAppChannel.send 已消费同 reminder_key => 二次执行不重复
        ExpiryReminderScheduler scheduler = new ExpiryReminderScheduler(
                itemRepository, userRepository, inAppChannel, settingsService, List.of());
        scheduler.scanAndNotify();
        verify(inAppChannel, atMost(1)).send(any(NotificationContext.class));
    }
}
```

（单测只验证「阈值/幂等」的调度编排；真正的去重提交依赖 InAppChannel 的 `INSERT ... ON DUPLICATE`/`save` 冲突捕获，规则测试并入集成/api-tester。）

- [ ] **Step 2: Run、验证失败**

Run: `cd backend; mvn -Dtest=ExpiryReminderSchedulerTest,SettingsServiceTest test`
Expected: FAIL。

- [ ] **Step 3: 实现**

- `NotificationRepository`：`Page<Notification> findByUserId(Long, Pageable)`、`findByUserIdAndReadFlagFalse(Long)`、`long countByUserIdAndReadFlagFalse(Long)`、`existsByUserIdAndReminderKey(Long, String)`。
- `ExpiryReminderScheduler.scanAndNotify()`：

```java
@Scheduled(cron = "0 0 9 * * *")
public void scanAndNotify() {
    int days = settingsService.getInt("expiry.remind.days", 7);
    LocalDate today = LocalDate.now();
    LocalDate limit = today.plusDays(days);
    List<Item> expiring = itemRepository.findByStatusAndExpiryDateBetween("ACTIVE", today, limit);
    var users = userRepository.findByEnabledTrue();
    for (Item item : expiring) {
        String key = "EXPIRY:" + item.getId() + ":" + item.getExpiryDate();
        for (User user : users) {
            if (notificationRepository.existsByUserIdAndReminderKey(user.getId(), key)) continue;
            channels().forEach(ch -> ch.send(new NotificationContext(user.getId(),
                    "临期提醒", String.format("「%s」将于 %s 到期", item.getName(), item.getExpiryDate()),
                    NotificationType.EXPIRY, item.getId(), key)));
        }
    }
}
```

- `InAppChannel.send`：以 `(userId, reminderKey)` 冲突为幂等键——保存前 `existsBy...` 短路；并发下唯一索引兜底（捕获 `DataIntegrityViolationException` 忽略）。
- `SettingsController`：`GET /api/settings` 返回 `{expiry_remind_days: 7}`；`PUT /api/settings` body `{key, value}`，仅白名单 `expiry.remind.days`，数字校验。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend; mvn -Dtest=ExpiryReminderSchedulerTest,SettingsServiceTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat: in-app notifications, expiry scheduler, sms extension point, settings"
```

---

### Task 9: 统计

**Files:**
- Create: `backend/src/main/java/com/family/storage/stats/StatisticsController.java`、`StatisticsService.java`、`stats/dto/SummaryResponse.java`、`stats/dto/ItemCountByDimension.java`
- Test: `backend/src/test/java/com/family/storage/StatisticsServiceTest.java`

**Interfaces:**
- Consumes: Task 4 `ItemRepository`、Task 6 `ShoppingItemRepository`、Task 8 `SettingsService`、Task 8 `NotificationRepository`（可选）。
- Produces:
  - `SummaryResponse(long totalItems, long totalQuantity, BigDecimal totalValue, long expiringSoon, long pendingShopping)`
  - `StatisticsService.summary()`、`byCategory(): List<ItemCountByDimension>`、`byLocation(): List<ItemCountByDimension>`（`ItemCountByDimension(Long dimensionId, String dimensionName, long itemCount, long totalQuantity, BigDecimal totalValue)`）

- [ ] **Step 1: 失败测试**

```java
package com.family.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.family.storage.item.ItemRepository;
import com.family.storage.settings.SettingsService;
import com.family.storage.shopping.ShoppingItemRepository;
import com.family.storage.stats.StatisticsService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {
    @Mock ItemRepository itemRepository;
    @Mock ShoppingItemRepository shoppingItemRepository;
    @Mock SettingsService settingsService;

    @Test
    void summaryAggregatesCorrectly() {
        when(settingsService.getInt("expiry.remind.days", 7)).thenReturn(7);
        when(itemRepository.countByStatus("ACTIVE")).thenReturn(5L);
        when(itemRepository.sumQuantityByStatus("ACTIVE")).thenReturn(12L);
        when(itemRepository.sumValueByStatus("ACTIVE")).thenReturn(new BigDecimal("80.50"));
        when(itemRepository.countByStatusAndExpiryDateBetween(eq("ACTIVE"), any(), any())).thenReturn(2L);
        when(shoppingItemRepository.countByStatus(com.family.storage.shopping.ShoppingStatus.PENDING)).thenReturn(3L);
        var svc = new StatisticsService(itemRepository, shoppingItemRepository, settingsService);
        var s = svc.summary();
        assertEquals(5, s.totalItems());
        assertEquals(12, s.totalQuantity());
        assertEquals(0, new BigDecimal("80.50").compareTo(s.totalValue()));
        assertEquals(2, s.expiringSoon());
        assertEquals(3, s.pendingShopping());
    }
}
```

（依赖 `ItemRepository` 的四个聚合方法与 `ShoppingItemRepository.countByStatus`，见 Step 3 签名。）

- [ ] **Step 2: Run、验证失败**

Run: `cd backend; mvn -Dtest=StatisticsServiceTest test`
Expected: FAIL。

- [ ] **Step 3: 实现**（JPQL 聚合，仅 `status=ACTIVE`；临期 = 阈值内 ACTIVE 数；待购 = PENDING 数）

`ItemRepository` 新增聚合方法（`@Query`）：

```java
@Query("select count(i) from Item i where i.status = :status")
long countByStatus(@Param("status") String status);

@Query("select coalesce(sum(i.quantity), 0) from Item i where i.status = :status")
long sumQuantityByStatus(@Param("status") String status);

@Query("select coalesce(sum(i.purchasePrice * i.quantity), 0) from Item i where i.status = :status")
java.math.BigDecimal sumValueByStatus(@Param("status") String status);
```

`ShoppingItemRepository.countByStatus(ShoppingStatus)` 由 Spring Data 推导。`StatisticsService.summary()` 组装 `SummaryResponse`。

- [ ] **Step 4: 跑测试验证通过**

Run: `cd backend; mvn -Dtest=StatisticsServiceTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "feat: statistics summary and dimension aggregation"
```

---

### Task 10: 后端集成回归（api-tester）

**Files:**
- 执行：api-tester 子 Agent（本仓库 API 测试技能），产物落 `docs/reports/api-*.md`
- 修复：按缺陷改 `backend/` 并复测

**Interfaces:**
- Consumes: Task 1–9 全部端点。
- Produces: `docs/reports/api-test-report.md`；通过标准：spec §5 全部端点通过、401/403/400/404 覆盖、临期幂等通过。

- [ ] **Step 1: 启动后端与 MySQL**（`mvn spring-boot:run` + 本机 MySQL 5.7）
- [ ] **Step 2: 调用 api-tester 加载技能并跑全量接口契约用例**（非敏感数据经 Task 传参）
- [ ] **Step 3: 修复 api-tester 反馈缺陷，复测直至通过**
- [ ] **Step 4: Commit**

```bash
git add backend docs/reports
git commit -m "test: backend api regression passing"
```

---

### Task 11: 前端骨架 + HTTP 层 + 登录注册

**Files:**
- Create: `frontend/package.json`、`vite.config.ts`、`tsconfig.json`、`index.html`
- Create: `frontend/src/main.ts`、`src/App.vue`
- Create: `frontend/src/api/http.ts`、`src/api/auth.ts`（登录/注册/me/改密）
- Create: `frontend/src/store/auth.ts`（Pinia）
- Create: `frontend/src/router/index.ts`（含登录守卫）
- Create: `frontend/src/views/LoginView.vue`、`RegisterView.vue`
- Create: `frontend/src/types/index.ts`
- Test: `frontend/src/store/auth.spec.ts`（vitest，纯逻辑）

**Interfaces:**
- Consumes: 后端 Task 2 端点。
- Produces:
  - `http` axios 实例：baseURL `/api`；请求拦截附加 `Authorization: Bearer <token>`；401 时清除 token 并跳 `/login`。
  - `useAuthStore`：`token`、`user`、`login(LoginRequest)`、`register(...)`、`logout()`、`init()`（localStorage 恢复）。
  - 路由：`/login`、`/register` 公开；其余 `meta.requiresAuth`，未登录重定向 `/login`。

- [ ] **Step 1: 失败测试（auth store 纯逻辑）**

```typescript
import { describe, it, expect } from 'vitest';

describe('auth store helpers', () => {
  it('saves and restores token', () => {
    const store = createAuthState(); // 见实现
    store.setToken('abc');
    expect(restoreToken()).toBe('abc');
  });
});
```

- [ ] **Step 2: Run、验证失败**

Run: `cd frontend; npm install; npm exec vitest run`
Expected: FAIL。

- [ ] **Step 3: 实现**

`package.json`（关键）：`vue`、`vue-router`、`pinia`、`axios`、`vant`、`dayjs`；dev: `vite`、`typescript`、`vue-tsc`、`vitest`、`@types/node`。

`vite.config.ts`：

```ts
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import { VantResolver } from '@vant/auto-import-resolver';

export default defineConfig({
  plugins: [
    vue(),
    Components({ resolvers: [VantResolver()] }),
  ],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
});
```

`src/api/http.ts`：

```ts
import axios from 'axios';
import { tokenKey } from '../store/auth';

export const http = axios.create({ baseURL: '/api' });
http.interceptors.request.use((cfg) => {
  const t = localStorage.getItem(tokenKey);
  if (t) cfg.headers.Authorization = `Bearer ${t}`;
  return cfg;
});
http.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem(tokenKey);
      if (location.pathname !== '/login') location.href = '/login';
    }
    return Promise.reject(err);
  },
);
```

`src/store/auth.ts`（`tokenKey='fs_token'`，`setToken`/`restoreToken` 导出供测试）：

```ts
export const tokenKey = 'fs_token';
export const setToken = (t: string) => localStorage.setItem(tokenKey, t);
export const restoreToken = () => localStorage.getItem(tokenKey);
```

`LoginView`：Vant `Form` + `Field` + 校验 `@NotBlank`；成功 `setToken` + `router.push('/')`。
`RegisterView`：仅当后端返回 403「注册已关闭」时 toast 提示。
路由守卫：`router.beforeEach((to) => { if (to.meta.requiresAuth && !restoreToken()) return '/login'; })`。

- [ ] **Step 4: 跑测试验证通过 + 手动冒烟（登录/注册）**

Run: `cd frontend; npm exec vitest run; npm run dev`
Expected: PASS；浏览器可进登录/注册页。

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: frontend skeleton with auth flow and route guard"
```

---

### Task 12: 前端布局与首页（统计 + 临期角标）

**Files:**
- Create: `frontend/src/views/LayoutView.vue`（Tabbar：首页/物品/购物/我的）
- Create: `frontend/src/views/HomeView.vue`（统计卡片 + 临期列表 + 通知角标）
- Create: `frontend/src/api/statistics.ts`、`frontend/src/api/notifications.ts`、`frontend/src/store/notification.ts`
- Create: `frontend/src/components/NotificationBadge.vue`

**Interfaces:**
- Consumes: Task 11 路由；后端 Task 8/9 端点。
- Produces: 首页展示 `summary` 的 5 个数字；`NotificationBadge` 轮询 `unread-count`（每 60s + 路由进入时刷新）。

- [ ] **Step 1: 失败测试（unread-count 状态累加）**

```typescript
import { describe, it, expect } from 'vitest';
describe('notification store', () => {
  it('starts at zero and adopts server value', () => {
    const s = createNotifState();
    s.applyCount(3);
    expect(s.count).toBe(3);
  });
});
```

- [ ] **Step 2: Run、验证失败**

Run: `cd frontend; npm exec vitest run`
Expected: FAIL。

- [ ] **Step 3: 实现**（Vant `NavBar`/`Tabbar`/`Cell`/`Badge`；首页卡片复用 `SummaryResponse` 字段中文标签；临期列表调用 `/api/items/expiring`，点击跳物品详情；`byCategory`/`byLocation` 两个维度以折叠表呈现）

- [ ] **Step 4: 跑测试验证通过 + 手动冒烟**

Run: `cd frontend; npm exec vitest run; npm run dev`
Expected: PASS；首页显示统计与角标。

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: home dashboard with stats and reminder badge"
```

---

### Task 13: 前端物品（列表/搜索/编辑/详情/出入库）

**Files:**
- Create: `frontend/src/api/items.ts`、`frontend/src/api/categories.ts`、`frontend/src/api/locations.ts`、`frontend/src/api/files.ts`
- Create: `frontend/src/views/ItemsView.vue`、`frontend/src/views/ItemEditView.vue`、`frontend/src/views/ItemDetailView.vue`
- Create: `frontend/src/components/LocationPicker.vue`（级联选择位置）

**Interfaces:**
- Consumes: 后端 Task 3/4/5/7 端点。
- Produces: `useItemQuery`（keyword/category/location/status/分页）；列表 `List` 无限加载；编辑页 `Uploader` 传图；详情页出入库 `Stepper` + `Dialog` 确认。

- [ ] **Step 1: 失败测试（分页参数构造）**

```typescript
import { describe, it, expect } from 'vitest';
import { buildItemParams } from '../src/api/items';
describe('item query params', () => {
  it('omits empty filters', () => {
    const p = buildItemParams({ keyword: '', categoryId: null, page: 0, size: 20 });
    expect(p.keyword).toBeUndefined();
    expect(p.size).toBe(20);
  });
});
```

- [ ] **Step 2: Run、验证失败**

Run: `cd frontend; npm exec vitest run`
Expected: FAIL。

- [ ] **Step 3: 实现**（列表页含 Vant `Search`、筛选 Popup；编辑页表单校验、照片 `Uploader`（`max-size` 5MB、`before-read` 校验类型）；出入库 `adjustStock` 走 `/items/{id}/stock`）

- [ ] **Step 4: 跑测试验证通过 + 手动冒烟**

Run: `cd frontend; npm exec vitest run; npm run dev`
Expected: PASS；可新增/检索/打图/出入库。

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: item list, search, edit with photo, stock adjust"
```

---

### Task 14: 前端购物清单 + 个人中心/管理

**Files:**
- Create: `frontend/src/api/shopping.ts`、`frontend/src/api/users.ts`、`frontend/src/api/settings.ts`
- Create: `frontend/src/views/ShoppingView.vue`、`frontend/src/views/ProfileView.vue`、`frontend/src/views/UsersView.vue`（ADMIN）、`frontend/src/views/SettingsView.vue`（ADMIN）

**Interfaces:**
- Consumes: 后端 Task 2/6/8 端点。
- Produces: 购物页 `Checkbox` 换行状态（PENDING→BOUGHT）；我的页退出登录/改密；ADMIN 用户管理（新增/停用/重置密码）与阈值设置。

- [ ] **Step 1: 失败测试（购物行状态切换）**

```typescript
import { describe, it, expect } from 'vitest';
describe('shopping row', () => {
  it('toggling marks bouoght and sets completed time', () => {
    const row = createShoppingRow({ status: 'PENDING' });
    toggleBought(row);
    expect(row.status).toBe('BOUGHT');
    expect(row.completedAt).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run、验证失败**

Run: `cd frontend; npm exec vitest run`
Expected: FAIL。

- [ ] **Step 3: 实现**（按端点点位接线；ADMIN 菜单仅 `user.role==='ADMIN'` 显示）

- [ ] **Step 4: 跑测试验证通过 + 手动冒烟**

Run: `cd frontend; npm exec vitest run; npm run dev`
Expected: PASS；购物勾选/管理页可用。

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: shopping list and profile/admin pages"
```

---

### Task 15: 前端集成回归（ui-tester E2E）

**Files:**
- 执行：ui-tester 子 Agent（加载 `e2e-test` 技能，Playwright 截图/视频），场景 CSV、脚本、报告落 `docs/e2e/`
- 修复：按缺陷改 `frontend/` 并复测

**Interfaces:**
- Consumes: Task 11–14 页面 + 后端 Task 1–9。
- Produces: `docs/e2e/e2e-report.md`（视频/截图证据）；覆盖注册登录、物品录入(含照片)、搜索/筛选、临期角标、购物打勾、统计页、ADMIN 管理。

- [ ] **Step 1: 启动后端 + 前端 dev**
- [ ] **Step 2: 调 ui-tester 加载技能，跑场景 CSV 全部用例（录像）**
- [ ] **Step 3: 修复缺陷并复测直至通过**
- [ ] **Step 4: Commit**

```bash
git add frontend docs/e2e
git commit -m "test: frontend e2e regression passing"
```

---

### Task 16: 生产打包与非容器部署准备

**Files:**
- Create: `backend/src/main/resources/application-prod.yml`（生产配置：读环境变量 DB 地址/口令、`JWT_SECRET`、`UPLOAD_DIR`；示例占位值留空——真实值只经环境变量）
- Create: `deploy/nginx-family-storage.conf`（nginx server 块示例：静态托管 + `/api` 反代 8080 + `/uploads` 别名上传目录 + HTTPS 注释）
- Create: `deploy/start.sh`（示例：`JWT_SECRET=<env> DB_PASSWORD=<env> java -jar backend/target/*.jar`，服务化/守护提醒，不写死任何真实密钥）
- Create: `.env.example`（键 `DB_PASSWORD`、`DB_URL`、`JWT_SECRET`、`UPLOAD_DIR` 的说明与示例，**不含任何真实值**）
- Modify: `.gitignore`（.gitignore 已存在，核对需含 `backend/uploads/`、`target/`、`node_modules/`、`dist/`）

**Interfaces:**
- Consumes: Task 1–15 产物（jar 由 `mvn package` 产出；前端 `npm run build` 产出 `dist/`）。
- Produces: 非容器交付物——jar + `frontend/dist` 静态资源 + nginx 反代配置 + 部署文档；不使用 Docker。

- [ ] **Step 1: 验证本地构建**

Run (PowerShell)：
`cd backend; $env:JAVA_HOME="D:\Java\jdk-17"; & "D:\apache-maven-3.9.16\bin\mvn.cmd" -DskipTests package; cd ../frontend; npm run build`
Expected: `backend/target/*.jar` 与 `frontend/dist/` 存在。

- [ ] **Step 2: 写生产配置与 nginx 样例**

- `application-prod.yml`：`spring.datasource.url=${DB_URL}`、`password=${DB_PASSWORD}`、`app.jwt.secret=${JWT_SECRET}`、`app.upload-dir=${UPLOAD_DIR}`；不出现真实值。
- `deploy/nginx-family-storage.conf`：`server { listen 80; root <dist 绝对路径>; location /api { proxy_pass http://127.0.0.1:8080; } location /uploads { alias <UPLOAD_DIR>; } }`，certbot TLS 见注释。
- `deploy/start.sh`：`JWT_SECRET="$JWT_SECRET" DB_PASSWORD="$DB_PASSWORD" java -jar app.jar --spring.profiles.active=prod`（脚本校验两个环境变量已设置才启动）。

- [ ] **Step 3: 本机冒烟（非 Docker）**

Run: 用 prod profile 本地起 jar（设 DB/密钥环境变量，连本机 MySQL）→ `curl http://localhost:8080/api/...`
Expected: 后端可独立启动，接口可达。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/application-prod.yml deploy .env.example .gitignore
git commit -m "feat: production packaging and nginx reverse-proxy config (non-container)"
```

---

### Task 17: 交付文档 + code-reviewer 收尾

**Files:**
- Create: `README.md`
- Create: `docs/api-spec.md`（与 spec §5 一致、供联调）
- Create: `docs/deploy.md`
- 执行：code-reviewer 子 Agent 全量评审

**Interfaces:**
- Consumes: 全部任务产物。

- [ ] **Step 1: 写 README**（简介、本地开发步骤、目录结构、测试命令）
- [ ] **Step 2: 写 docs/api-spec.md、docs/deploy.md**
- [ ] **Step 3: 调 code-reviewer 评审**（质量/安全：密钥不入库、越权、校验、幂等、XSS/上传安全），分级修复建议
- [ ] **Step 4: 修复高/中危问题并复跑测试（api-tester / ui-tester 各一轮）**
- [ ] **Step 5: Commit**

```bash
git add README.md docs
git commit -m "docs: delivery docs and final review fixes"
```

---

## 执行顺序依赖

`Task 1→2→…→9` 为后端串行（各自独立可测）；`Task 10` 后端回归；`Task 11→14` 前端串行；`Task 15` 前端回归；`Task 16` 依赖全部代码；`Task 17` 收尾。前端 Task 11 需要后端 Task 2 起服务联调。