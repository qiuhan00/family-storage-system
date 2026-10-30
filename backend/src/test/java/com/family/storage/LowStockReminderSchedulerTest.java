package com.family.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.family.storage.item.Item;
import com.family.storage.item.ItemRepository;
import com.family.storage.item.ItemStatus;
import com.family.storage.notify.LowStockReminderScheduler;
import com.family.storage.notify.NotificationType;
import com.family.storage.notify.channel.InAppChannel;
import com.family.storage.notify.channel.NotificationContext;
import com.family.storage.user.User;
import com.family.storage.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 低库存提醒调度编排测试：对每个「低库存物品 × 启用用户」发送一条 LOW_STOCK 通知，
 * reminder_key 以 LOW_STOCK 前缀与临期（EXPIRY）区分；幂等由 InAppChannel 唯一索引兜底。
 */
@ExtendWith(MockitoExtension.class)
class LowStockReminderSchedulerTest {
    @Mock ItemRepository itemRepository;
    @Mock UserRepository userRepository;
    @Mock InAppChannel inAppChannel;
    @Captor ArgumentCaptor<NotificationContext> ctx;

    @Test
    void notifiesEnabledUsersForLowStockItems() {
        Item item = new Item();
        item.setId(5L);
        item.setName("牛奶");
        item.setQuantity(1);
        item.setUnit("盒");
        item.setLowStockThreshold(3);
        User user = new User();
        user.setId(7L);

        when(itemRepository.findLowStock(ItemStatus.ACTIVE)).thenReturn(List.of(item));
        when(userRepository.findByEnabledTrue()).thenReturn(List.of(user));

        new LowStockReminderScheduler(itemRepository, userRepository, inAppChannel, List.of())
                .scanAndNotify();

        verify(inAppChannel).send(ctx.capture());
        NotificationContext c = ctx.getValue();
        assertEquals(7L, c.userId());
        assertEquals(5L, c.refId());
        assertEquals(NotificationType.LOW_STOCK, c.type());
        assertTrue(c.reminderKey().startsWith("LOW_STOCK:5:"));
        assertTrue(c.body().contains("牛奶"));
        assertTrue(c.body().contains("低于 3"));
    }
}
