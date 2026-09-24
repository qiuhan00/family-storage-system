package com.family.storage.item.dto;

import java.time.LocalDateTime;

/**
 * 出入库记录响应体（Task 5 契约：id, itemId, changeQty, type, note, createdBy, createdAt）。
 * type 输出枚举名（INITIAL/IN/OUT/ADJUST）。
 */
public record StockChangeResponse(
        Long id,
        Long itemId,
        int changeQty,
        String type,
        String note,
        Long createdBy,
        LocalDateTime createdAt
) {
}