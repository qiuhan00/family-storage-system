package com.family.storage.shopping.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 购物状态流转请求体（spec §5.5 PATCH /api/shopping/{id}/status，
 * body {@code {status:"BOUGHT"}}）。Service 层负责解析 ShoppingStatus。
 */
public record ShoppingStatusRequest(@NotBlank String status) {
}