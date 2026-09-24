package com.family.storage.item;

/**
 * 物品状态（V1 迁移 items.status：ACTIVE / ARCHIVED）。
 * Task 3 仅为分类/位置删除引用计数引入；完整物品业务在 Task 4。
 */
public enum ItemStatus {
    ACTIVE,
    ARCHIVED
}