package com.family.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.family.storage.auth.JwtAuthFilter;
import com.family.storage.auth.JwtTokenProvider;
import com.family.storage.user.UserRepository;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JWT 过滤器校验测试（code-review M1）：
 * token 有效但用户已停用/不存在 → 401 USER_DISABLED 并截断请求；
 * 用户启用 → 放行并写入 SecurityContext；token 无效 → 降级匿名放行。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock
    UserRepository userRepository;

    private static final AtomicBoolean CHAIN_CALLED = new AtomicBoolean(false);
    // 简单的记录是否放行到链路的 FilterChain
    private static final FilterChain CHAIN = new FilterChain() {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            CHAIN_CALLED.set(true);
        }
    };

    private MockHttpServletRequest bearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.jwt.token");
        return request;
    }

    @Test
    void disabledUserTokenRejectedWith401UserDisabled() throws ServletException, IOException {
        CHAIN_CALLED.set(false);
        when(jwtTokenProvider.parseUserId(any())).thenReturn(7L);
        when(jwtTokenProvider.parseRole(any())).thenReturn("ADMIN");
        when(userRepository.existsByIdAndEnabledTrue(7L)).thenReturn(false);

        JwtAuthFilter filter = new JwtAuthFilter(jwtTokenProvider, userRepository);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(bearerRequest(), response, CHAIN);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("USER_DISABLED"));
        assertFalse(CHAIN_CALLED.get(), "停用用户请求不应继续到达业务链路");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void enabledUserTokenPassesThrough() throws ServletException, IOException {
        CHAIN_CALLED.set(false);
        when(jwtTokenProvider.parseUserId(any())).thenReturn(7L);
        when(jwtTokenProvider.parseRole(any())).thenReturn("ADMIN");
        when(userRepository.existsByIdAndEnabledTrue(7L)).thenReturn(true);

        JwtAuthFilter filter = new JwtAuthFilter(jwtTokenProvider, userRepository);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(bearerRequest(), response, CHAIN);

        assertTrue(CHAIN_CALLED.get(), "启用用户请求应放行");
        assertEquals(200, response.getStatus());
        assertEquals(7L, (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidTokenContinuesAnonymous() throws ServletException, IOException {
        CHAIN_CALLED.set(false);
        when(jwtTokenProvider.parseUserId(any())).thenThrow(new MalformedJwtException("bad token"));

        JwtAuthFilter filter = new JwtAuthFilter(jwtTokenProvider, userRepository);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(bearerRequest(), response, CHAIN);

        assertTrue(CHAIN_CALLED.get(), "无效 token 应降级匿名放行，由安全规则统一回 401");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void noHeaderPassesThrough() throws ServletException, IOException {
        CHAIN_CALLED.set(false);
        JwtAuthFilter filter = new JwtAuthFilter(jwtTokenProvider, userRepository);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, CHAIN);
        assertTrue(CHAIN_CALLED.get());
    }
}