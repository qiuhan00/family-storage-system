package com.family.storage.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 位置创建/更新请求（spec §5.3）。parentId 为 null 表示根节点；
 * description 为位置具体说明（≤500 字），imageUrl 为 POST /api/files 回填的站内照片路径。
 */
public record LocationRequest(
        @NotBlank @Size(max = 50) String name,
        Long parentId,
        int sortOrder,
        @Size(max = 500) String description,
        @Size(max = 255)
        @Pattern(regexp = "(^$|^/(?!/)(?!.*\\.\\.)[A-Za-z0-9._/-]{1,254}$)",
                message = "imageUrl 须为 /api/files 返回的站内路径（如 /uploads/202609/uuid.jpg）")
        String imageUrl
) {
}
