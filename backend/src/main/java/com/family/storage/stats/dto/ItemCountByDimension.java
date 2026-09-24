package com.family.storage.stats.dto;

import java.math.BigDecimal;

/**
 * 分类/位置维度聚合行（spec §5.8 GET /api/statistics/by-category、by-location）：
 * dimensionId 为 categoryId 或 locationId，dimensionName 为对应名称；
 * 物品数/总量/总值仅统计 ACTIVE 物品；无物品的维度不返回。
 */
public record ItemCountByDimension(Long dimensionId, String dimensionName, long itemCount,
                                   long totalQuantity, BigDecimal totalValue) {
}