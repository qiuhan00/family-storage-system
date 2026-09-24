package com.family.storage.item;

/**
 * 出入库类型（spec §4 stock_changes.type）：INITIAL 新增物品初始库存、
 * IN 入库、OUT 出库、ADJUST 手动校准。
 * INITIAL 仅由 create 写初始记录时使用，不允许通过出入库接口提交。
 */
public enum StockType {
    INITIAL,
    IN,
    OUT,
    ADJUST
}