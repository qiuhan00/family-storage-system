package com.family.storage.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 位置创建/更新请求（spec §5.3）。parentId 为 null 表示根节点。
 */
public record LocationRequest(
        @NotBlank @Size(max = 50) String name,
        Long parentId,
        int sortOrder
) {
}