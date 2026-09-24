package com.family.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.family.storage.common.ApiException;
import com.family.storage.user.Role;
import com.family.storage.user.User;
import com.family.storage.user.UserRepository;
import com.family.storage.user.UserService;
import com.family.storage.user.dto.UserRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用户管理测试（code-review M6）：停用/降级最后一个启用 ADMIN 一律拒绝（400 LAST_ADMIN）。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;

    private UserService svc() {
        return new UserService(userRepository, passwordEncoder);
    }

    private User adminUser(Long id, boolean enabled) {
        User u = new User();
        u.setId(id);
        u.setUsername("admin" + id);
        u.setDisplayName("管理员" + id);
        u.setRole(Role.ADMIN);
        u.setEnabled(enabled);
        u.setPasswordHash("hash");
        return u;
    }

    @Test
    void disablingLastAdminIsRejected() {
        User u = adminUser(1L, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.countByEnabledTrueAndRole(Role.ADMIN)).thenReturn(1L);

        ApiException ex = assertThrows(ApiException.class, () -> svc().setStatus(1L, false));
        assertEquals(400, ex.getStatus());
        assertEquals("LAST_ADMIN", ex.getCode());
        verify(userRepository, never()).save(u);
    }

    @Test
    void disablingAdminAllowedWhenAnotherEnabledAdminExists() {
        User u = adminUser(1L, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.countByEnabledTrueAndRole(Role.ADMIN)).thenReturn(2L);

        var resp = svc().setStatus(1L, false);
        assertFalse(resp.enabled());
        verify(userRepository).save(u);
    }

    @Test
    void demotingLastAdminIsRejected() {
        User u = adminUser(1L, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.countByEnabledTrueAndRole(Role.ADMIN)).thenReturn(1L);

        ApiException ex = assertThrows(ApiException.class,
                () -> svc().update(1L, new UserRequest("admin1", "管理员1", Role.MEMBER,
                        "a@b.c", null, null)));
        assertEquals("LAST_ADMIN", ex.getCode());
        verify(userRepository, never()).save(u);
    }

    @Test
    void demotingAdminAllowedWhenAnotherEnabledAdminExists() {
        User u = adminUser(1L, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.countByEnabledTrueAndRole(Role.ADMIN)).thenReturn(2L);

        var resp = svc().update(1L, new UserRequest("admin1", "管理员1", Role.MEMBER,
                "a@b.c", null, null));
        assertEquals(Role.MEMBER, resp.role());
    }

    @Test
    void disablingMemberIsAlwaysAllowed() {
        User u = new User();
        u.setId(2L);
        u.setUsername("kid");
        u.setRole(Role.MEMBER);
        u.setEnabled(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(u));

        var resp = svc().setStatus(2L, false);
        assertFalse(resp.enabled());
        verify(userRepository).save(u);
        // 不触发 last-admin 查询
        verify(userRepository, never()).countByEnabledTrueAndRole(Role.ADMIN);
    }

    @Test
    void disablingAlreadyDisabledAdminIsAllowed() {
        User u = adminUser(1L, false); // 已停用，再停无意义但不应触发 last-admin 拒绝
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        var resp = svc().setStatus(1L, false);
        assertFalse(resp.enabled());
        verify(userRepository, never()).countByEnabledTrueAndRole(Role.ADMIN);
    }
}