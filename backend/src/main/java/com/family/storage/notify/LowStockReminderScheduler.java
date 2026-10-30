package com.family.storage.notify;

import com.family.storage.item.Item;
import com.family.storage.item.ItemRepository;
import com.family.storage.item.ItemStatus;
import com.family.storage.notify.channel.InAppChannel;
import com.family.storage.notify.channel.NotificationChannel;
import com.family.storage.notify.channel.NotificationContext;
import com.family.storage.user.User;
import com.family.storage.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 库存不足提醒调度器：每日 09:00 扫描「已设阈值且数量低于阈值」的 ACTIVE 物品，
 * 对每个「物品 × 启用用户」经所有通道投递。
 * reminder_key = LOW_STOCK:{itemId}:{yyyy-MM-dd}，与临期提醒（EXPIRY 前缀）区分；
 * 幂等去重由 InAppChannel 的 (user_id, reminder_key) 唯一索引兜底。
 */
@Component
public class LowStockReminderScheduler {
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final List<NotificationChannel> channels;

    public LowStockReminderScheduler(ItemRepository itemRepository,
                                     UserRepository userRepository,
                                     InAppChannel inAppChannel,
                                     List<NotificationChannel> channels) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.channels = new ArrayList<>(channels);
        if (!this.channels.contains(inAppChannel)) {
            this.channels.add(inAppChannel);
        }
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void scanAndNotify() {
        LocalDate today = LocalDate.now();
        List<Item> low = itemRepository.findLowStock(ItemStatus.ACTIVE);
        List<User> users = userRepository.findByEnabledTrue();
        for (Item item : low) {
            String key = "LOW_STOCK:" + item.getId() + ":" + today;
            String unit = item.getUnit() == null ? "" : item.getUnit();
            for (User user : users) {
                NotificationContext ctx = new NotificationContext(user.getId(), "库存不足",
                        String.format("「%s」仅剩 %d%s（低于 %d）", item.getName(),
                                item.getQuantity(), unit, item.getLowStockThreshold()),
                        NotificationType.LOW_STOCK, item.getId(), key);
                for (NotificationChannel ch : channels) {
                    ch.send(ctx);
                }
            }
        }
    }
}
