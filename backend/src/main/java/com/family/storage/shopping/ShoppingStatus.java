package com.family.storage.shopping;

/**
 * 购物清单状态（spec §4 shopping_items.status）。
 * PENDING 待购；BOUGHT 已购 / CANCELLED 已取消（两者写入 completed_at）。
 */
public enum ShoppingStatus {
    PENDING,
    BOUGHT,
    CANCELLED
}