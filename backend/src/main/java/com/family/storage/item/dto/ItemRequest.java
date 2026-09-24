package com.family.storage.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 物品创建/更新请求（spec §5.4）。quantity 为负在 Service 层拒绝
 * （400 NEGATIVE_QUANTITY），不在 DTO 校验以免与错误码约定冲突；
 * status 不随本请求变更（ACTIVE/ARCHIVED 由归档动作管理）。
 */
public record ItemRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Long categoryId,
        @NotNull Long locationId,
        int quantity,
        @Size(max = 20) String unit,
        BigDecimal purchasePrice,
        LocalDate purchaseDate,
        LocalDate expiryDate,
        @Size(max = 255) String photoUrl,
        @Size(max = 500) String remark
) {
}