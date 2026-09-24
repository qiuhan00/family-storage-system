// src/utils/shopping.ts —— 购物清单行纯逻辑：行工厂 + 状态切换（供 ShoppingView 与单测复用）。
import type { ShoppingItem, ShoppingStatus } from '../types';

/** 以部分字段构造一条购物清单行（测试与占位场景使用，服务端字段缺省时给安全默认值）。 */
export function createShoppingRow(
  draft: Partial<ShoppingItem> & { status: ShoppingStatus },
): ShoppingItem {
  return {
    id: draft.id ?? 0,
    name: draft.name ?? '',
    quantity: draft.quantity ?? 1,
    unit: draft.unit ?? '',
    categoryId: draft.categoryId ?? null,
    remark: draft.remark ?? null,
    status: draft.status,
    createdBy: draft.createdBy ?? 0,
    createdAt: draft.createdAt ?? new Date().toISOString(),
    completedAt: draft.completedAt ?? null,
  };
}

/**
 * 勾选/取消勾选切换（PENDING ↔ BOUGHT，对齐 spec §5.5 的 completedAt 语义：
 * 置 BOUGHT 写完成时间，回到 PENDING 清空）。CANCELLED 行不参与勾选，保持原状。
 * 就地修改入参并返回同一对象，便于视图做乐观更新与回滚。
 */
export function toggleBought(row: ShoppingItem): ShoppingItem {
  if (row.status === 'PENDING') {
    row.status = 'BOUGHT';
    row.completedAt = new Date().toISOString();
  } else if (row.status === 'BOUGHT') {
    row.status = 'PENDING';
    row.completedAt = null;
  }
  return row;
}
