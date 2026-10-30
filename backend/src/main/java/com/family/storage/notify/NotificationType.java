package com.family.storage.notify;

/**
 * 通知类型（映射 notifications.type 列）：EXPIRY 临期提醒，LOW_STOCK 库存不足，OTHER 其他。
 */
public enum NotificationType {
    EXPIRY,
    LOW_STOCK,
    OTHER
}