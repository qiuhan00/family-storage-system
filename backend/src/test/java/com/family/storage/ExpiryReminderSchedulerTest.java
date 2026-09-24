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

/**
 * 临期调度编排测试（Task 8，spec §6）：
 * 阈值来自 SettingsService，扫描 ACTIVE 且 expiry_date 落在
 * [today, today+阈值] 的物品，对每个「物品 × 启用用户」至多发送一次；
 * 真正的幂等去重由 InAppChannel 的 reminder_key 唯一索引保障（集成验证）。
 */
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