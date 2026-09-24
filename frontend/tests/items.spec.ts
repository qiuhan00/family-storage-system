// tests/items.spec.ts —— Task 13 Step 1 TDD：buildItemParams 分页/过滤参数构造（先失败后实现）。
import { describe, it, expect } from 'vitest';
import { buildItemParams } from '../src/api/items';

describe('item query params', () => {
  it('omits empty filters', () => {
    const p = buildItemParams({ keyword: '', categoryId: null, page: 0, size: 20 });
    expect(p.keyword).toBeUndefined();
    expect(p.size).toBe(20);
  });

  it('keeps non-empty filters and maps expiring to 1', () => {
    const p = buildItemParams({
      keyword: '  牛奶  ',
      categoryId: 3,
      locationId: 7,
      status: 'ACTIVE',
      expiring: true,
      page: 1,
      size: 10,
    });
    expect(p.keyword).toBe('牛奶');
    expect(p.categoryId).toBe(3);
    expect(p.locationId).toBe(7);
    expect(p.status).toBe('ACTIVE');
    expect(p.expiring).toBe(1);
    expect(p.page).toBe(1);
    expect(p.size).toBe(10);
  });

  it('omits null keyword/status/locationId', () => {
    const p = buildItemParams({ keyword: null, categoryId: 1, locationId: null, page: 0, size: 20 });
    expect(p.keyword).toBeUndefined();
    expect(p.locationId).toBeUndefined();
    expect(p.status).toBeUndefined();
  });
});