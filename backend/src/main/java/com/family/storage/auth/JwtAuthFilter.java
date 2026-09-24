package com.family.storage.auth;

import com.family.storage.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器：解析 Bearer token 写入 SecurityContext（principal=userId，authority=ROLE_角色）。
 * 自 code-review M1 起，token 有效还会校验对应用户仍存在且启用（enabled=true）：
 * 管理员停用/删除成员后，旧 token 立即失效（401 USER_DISABLED），不再有最长 168h 的越权窗口。
 * 校验为 users 表主键查询（一次查询/请求，索引命中），家庭规模下开销可忽略。
 * token 无效/过期则静默放行降级为匿名，由 {@link AuthenticationEntryPoint401} 统一回 401。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String DISABLED_BODY =
            "{\"code\":\"USER_DISABLED\",\"message\":\"账号已停用或不存在\"}";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                long userId = jwtTokenProvider.parseUserId(token);
                String role = jwtTokenProvider.parseRole(token);
                if (!userRepository.existsByIdAndEnabledTrue(userId)) {
                    writeDisabled(response);
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // 无效 token：继续匿名，安全规则将返回 401
            }
        }
        chain.doFilter(request, response);
    }

    private void writeDisabled(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(DISABLED_BODY);
    }
}