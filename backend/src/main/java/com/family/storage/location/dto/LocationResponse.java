package com.family.storage.location.dto;

import java.util.List;

/**
 * 位置树节点响应（spec §5.3 GET 树形 parent_id）：id/name/parentId/sortOrder/
 * description/imageUrl/children。
 */
public record LocationResponse(
        Long id,
        String name,
        Long parentId,
        int sortOrder,
        String description,
        String imageUrl,
        List<LocationResponse> children
) {
}
