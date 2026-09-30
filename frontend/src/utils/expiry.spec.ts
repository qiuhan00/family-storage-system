// src/utils/expiry.spec.ts —— F2 TDD：临期判定口径 = settings 的 expiry_remind_days 阈值（先失败后实现）。
import { describe, it, expect } from 'vitest';
import dayjs from 'dayjs';
import { daysUntilExpiry, expiryLevel } from './expiry';

const ymd = (offsetDays: number) => dayjs().startOf('day').add(offsetDays, 'day').format('YYYY-MM-DD');

describe('daysUntilExpiry', () => {
  it('counts calendar days from today', () => {
    expect(daysUntilExpiry(ymd(3))).toBe(3);
    expect(daysUntilExpiry(ymd(-2))).toBe(-2);
  });
});

describe('expiryLevel（阈值驱动）', () => {
  it('无到期日 → none', () => {
    expect(expiryLevel(null, 7)).toBe('none');
  });

  it('非法日期 → none（NaN 失败安全，见 code-review m4）', () => {
    expect(expiryLevel('not-a-date', 7)).toBe('none');
  });

  it('阈值内（含边界）→ red', () => {
    expect(expiryLevel(ymd(3), 7)).toBe('red');
    expect(expiryLevel(ymd(7), 7)).toBe('red');
  });

  it('已过期 → red（含在阈值判定内）', () => {
    expect(expiryLevel(ymd(-1), 7)).toBe('red');
  });

  it('超出阈值 → none（不再按硬编码 30 天标橙）', () => {
    expect(expiryLevel(ymd(10), 7)).toBe('none');
    expect(expiryLevel(ymd(31), 30)).toBe('none');
  });

  it('阈值调高后同一日期重新纳入（设 30 则 +10 天临期）', () => {
    expect(expiryLevel(ymd(10), 7)).toBe('none');
    expect(expiryLevel(ymd(10), 30)).toBe('red');
  });
});
