// src/utils/expiry.ts —— 临期判定（唯一口径 = app_settings 的 expiry.remind.days 阈值，
// 经 GET /api/settings 暴露；与后端统计/调度/search 窗口同一阈值，见 F2/code-review L6）。
import dayjs from 'dayjs';

/** 角标档位：阈值内（含已过期）红色「临期」；窗口外为普通「到期」（模板 v-else 分支）。 */
export type ExpiryLevel = 'red' | 'none';

/** 距到期日的自然日差（今天为 0，已过期为负）。 */
export function daysUntilExpiry(date: string): number {
  return dayjs(date).startOf('day').diff(dayjs().startOf('day'), 'day');
}

/** 临期高亮：无到期日 → none；到期 ≤ 阈值（含已过期）→ red；超出阈值 → none。 */
export function expiryLevel(date: string | null, remindDays: number): ExpiryLevel {
  if (!date) return 'none';
  return daysUntilExpiry(date) <= remindDays ? 'red' : 'none';
}
