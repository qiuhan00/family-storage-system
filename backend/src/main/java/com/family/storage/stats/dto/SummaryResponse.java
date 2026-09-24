package com.family.storage.stats.dto;

import java.math.BigDecimal;

/**
 * 统计总览（spec §5.8 GET /api/statistics/summary）：
 * totalItems/totalQuantity/totalValue 仅统计 ACTIVE 物品；
 * expiringSoon 为阈值内临期数；pendingShopping 为待购清单项数。
 */
public record SummaryResponse(long totalItems, long totalQuantity, BigDecimal totalValue,
                              long expiringSoon, long pendingShopping) {
}