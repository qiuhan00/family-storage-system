package com.family.storage.item.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 物品创建/更新请求（spec §5.4）。quantity 为负在 Service 层拒绝
 * （400 NEGATIVE_QUANTITY），不在 DTO 校验以免与错误码约定冲突；
 * status 不随本请求变更（ACTIVE/ARCHIVED 由归档动作管理）。
 * purchasePrice 限定在 DECIMAL(10,2) 范围内且最多 2 位小数，
 * 越界由全局异常映射为 400 INVALID_PRICE（code-review L4 顺手项）。
 */
public record ItemRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Long categoryId,
        @NotNull Long locationId,
        int quantity,
        @Size(max = 20) String unit,
        @DecimalMin(value = "0.00", message = "价格不能为负数")
        @DecimalMax(value = "99999999.99", message = "价格超出允许范围(最大 99999999.99)")
        @Digits(integer = 8, fraction = 2, message = "价格最多 8 位整数和 2 位小数")
        BigDecimal purchasePrice,
        LocalDate purchaseDate,
        LocalDate expiryDate,
        @Size(max = 255) String photoUrl,
        @Size(max = 500) String remark,
        @Size(max = 200) String locationDetail
) {
}