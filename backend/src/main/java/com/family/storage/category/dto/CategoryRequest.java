package com.family.storage.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 分类创建/更新请求（spec §5.3）。sortOrder 缺省时按 0 处理。
 */
public record CategoryRequest(
        @NotBlank @Size(max = 50) String name,
        Integer sortOrder
) {
    public int sortOrderOrDefault() {
        return sortOrder == null ? 0 : sortOrder;
    }
}