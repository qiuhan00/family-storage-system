package com.family.storage.notify.channel;

import com.family.storage.notify.NotificationType;

/**
 * 通知上下文：一次通知发送所需的最小信息集合。
 * reminderKey 为可空幂等键（如 EXPIRY:{itemId}:{yyyy-MM-dd}），
 * 通道可据此避免重复投递。
 *
 * @param userId      接收用户
 * @param title       标题
 * @param body        正文
 * @param type        通知类型
 * @param refId       关联业务 id（如物品 id，可空）
 * @param reminderKey 幂等键（可空）
 */
public record NotificationContext(Long userId, String title, String body,
                                  NotificationType type, Long refId, String reminderKey) {
}