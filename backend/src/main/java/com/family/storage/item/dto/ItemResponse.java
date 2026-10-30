package com.family.storage.item.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物品响应体（spec §5.4 / Task 4 契约）。status 输出枚举名（ACTIVE/ARCHIVED）。
 */
public record ItemResponse(
        Long id,
        String name,
        Long categoryId,
        Long locationId,
        String locationDetail,
        Integer lowStockThreshold,
        int quantity,
        String unit,
        BigDecimal purchasePrice,
        LocalDate purchaseDate,
        LocalDate expiryDate,
        String photoUrl,
        String status,
        String remark,
        Long createdBy,
        LocalDateTime createdAt
) {
}