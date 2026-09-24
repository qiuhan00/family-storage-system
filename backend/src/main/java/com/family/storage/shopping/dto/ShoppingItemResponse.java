package com.family.storage.shopping.dto;

import java.time.LocalDateTime;

/**
 * 购物清单响应体（spec §5.5 / Task 6 契约）。status 输出枚举名（PENDING/BOUGHT/CANCELLED）。
 */
public record ShoppingItemResponse(
        Long id,
        String name,
        int quantity,
        String unit,
        Long categoryId,
        String remark,
        String status,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}