package com.family.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.family.storage.notify.NotificationRepository;
import com.family.storage.notify.NotificationService;
import com.family.storage.notify.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 通知列表过滤编排：unread 与 type 透传到仓储查询，便于通知中心按
 * 临期 / 库存不足 分标签筛选。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository notificationRepository;

    @Test
    void listPassesUnreadAndTypeFilters() {
        when(notificationRepository.search(any(), anyBoolean(), any(), any()))
                .thenReturn(Page.empty());
        new NotificationService(notificationRepository)
                .list(1L, false, NotificationType.EXPIRY, PageRequest.of(0, 20));
        verify(notificationRepository).search(eq(1L), eq(false), eq(NotificationType.EXPIRY), any());
    }

    @Test
    void listPassesUnreadFilter() {
        when(notificationRepository.search(any(), anyBoolean(), any(), any()))
                .thenReturn(Page.empty());
        new NotificationService(notificationRepository)
                .list(2L, true, null, PageRequest.of(0, 20));
        verify(notificationRepository).search(eq(2L), eq(true), isNull(), any());
    }
}
