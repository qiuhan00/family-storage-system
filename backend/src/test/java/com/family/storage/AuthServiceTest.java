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