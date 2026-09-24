package com.family.storage.notify.channel;

import com.family.storage.notify.Notification;
import com.family.storage.notify.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 站内通知通道（spec §6，v1 唯一启用实现）：把通知写入 notifications 表。
 * 幂等在通道层保证：保存前以 (userId, reminderKey) 短路；并发下由
 * uk_notifications_user_reminder 唯一索引兜底（捕获 DataIntegrityViolationException 忽略）。
 */
@Component
@RequiredArgsConstructor
public class InAppChannel implements NotificationChannel {
    private final NotificationRepository notificationRepository;

    @Override
    public ChannelType type() {
        return ChannelType.IN_APP;
    }

    @Override
    public void send(NotificationContext ctx) {
        if (ctx.reminderKey() != null
                && notificationRepository.existsByUserIdAndReminderKey(ctx.userId(), ctx.reminderKey())) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(ctx.userId());
        n.setTitle(ctx.title());
        n.setBody(ctx.body());
        n.setType(ctx.type());
        n.setRefId(ctx.refId());
        n.setReminderKey(ctx.reminderKey());
        try {
            notificationRepository.saveAndFlush(n);
        } catch (DataIntegrityViolationException e) {
            // 并发下同一 (user_id, reminder_key) 已写入：视为已投递，忽略冲突
        }
    }
}