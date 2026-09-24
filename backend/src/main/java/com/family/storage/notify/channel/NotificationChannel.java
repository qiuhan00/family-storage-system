package com.family.storage.notify.channel;

/**
 * 通知通道抽象（spec §6）：调度器对每个「物品 × 用户」依次调用所有已注册通道。
 * 新增通道只需实现本接口并注册为 Spring Bean。
 */
public interface NotificationChannel {
    ChannelType type();

    void send(NotificationContext ctx);
}