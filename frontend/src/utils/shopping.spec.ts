// src/utils/shopping.spec.ts
// 购物清单行纯逻辑单元测试：状态切换（PENDING→BOUGHT 写 completedAt，反向清空）。
import { describe, it, expect } from 'vitest';
import { createShoppingRow, toggleBought } from './shopping';

describe('shopping row', () => {
  it('toggling marks bouoght and sets completed time', () => {
    const row = createShoppingRow({ status: 'PENDING' });
    toggleBought(row);
    expect(row.status).toBe('BOUGHT');
    expect(row.completedAt).toBeTruthy();
  });

  it('toggling a bought row returns to pending and clears completed time', () => {
    const row = createShoppingRow({ status: 'PENDING' });
    toggleBought(row);
    toggleBought(row);
    expect(row.status).toBe('PENDING');
    expect(row.completedAt).toBeNull();
  });

  it('cancelled row is untouched by toggle', () => {
    const row = createShoppingRow({ status: 'CANCELLED' });
    toggleBought(row);
    expect(row.status).toBe('CANCELLED');
    expect(row.completedAt).toBeNull();
  });
});