package com.family.storage.shopping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 购物清单创建/更新请求（spec §5.5）。quantity 必须为正在 Service 层拒绝
 * （400 INVALID_QUANTITY），不在 DTO 校验以免与错误码约定冲突；
 * status 不随本请求变更（由 PATCH /{id}/status 管理）。
 */
public record ShoppingRequest(
        @NotBlank @Size(max = 100) String name,
        int quantity,
        @Size(max = 20) String unit,
        Long categoryId,
        @Size(max = 200) String remark
) {
}