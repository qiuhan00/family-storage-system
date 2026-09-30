package com.family.storage.notify;

import com.family.storage.item.Item;
import com.family.storage.item.ItemRepository;
import com.family.storage.item.ItemStatus;
import com.family.storage.notify.channel.InAppChannel;
import com.family.storage.notify.channel.NotificationChannel;
import com.family.storage.notify.channel.NotificationContext;
import com.family.storage.settings.SettingsService;
import com.family.storage.user.User;
import com.family.storage.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 临期物品提醒调度器（spec §6）：每日 09:00 扫描 ACTIVE 且 expiry_date 落在
 * [today, today+阈值] 的物品，对每个「物品 × 启用用户」经所有通知通道投递。
 * reminder_key = EXPIRY:{itemId}:{yyyy-MM-dd} 保证幂等（去重由 InAppChannel
 * 的唯一索引兜底，本类不直接触碰 NotificationRepository）。
 * 阈值自 app_settings 读取（expiry.remind.days，默认 7）。
 */
@Component
public class ExpiryReminderScheduler {
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final List<NotificationChannel> channels;

    public ExpiryReminderScheduler(ItemRepository itemRepository,
                                   UserRepository userRepository,
                                   InAppChannel inAppChannel,
                                   SettingsService settingsService,
                                   List<NotificationChannel> channels) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.channels = new ArrayList<>(channels);
        if (!this.channels.contains(inAppChannel)) {
            this.channels.add(inAppChannel);
        }
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void scanAndNotify() {
        int days = settingsService.getInt(SettingsService.EXPIRY_REMIND_DAYS,
                SettingsService.DEFAULT_EXPIRY_REMIND_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(days);
        List<Item> expiring = itemRepository
                .findByStatusAndExpiryDateBetweenOrderByExpiryDateAsc(ItemStatus.ACTIVE, today, limit);
        List<User> users = userRepository.findByEnabledTrue();
        for (Item item : expiring) {
            String key = "EXPIRY:" + item.getId() + ":" + item.getExpiryDate();
            for (User user : users) {
                NotificationContext ctx = new NotificationContext(user.getId(), "临期提醒",
                        String.format("「%s」将于 %s 到期", item.getName(), item.getExpiryDate()),
                        NotificationType.EXPIRY, item.getId(), key);
                for (NotificationChannel ch : channels) {
                    ch.send(ctx);
                }
            }
        }
    }
}