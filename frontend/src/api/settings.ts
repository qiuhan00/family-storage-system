// src/api/settings.ts —— 应用设置接口封装（spec §5.9：GET 登录可读，PUT 仅 ADMIN）。
import { http } from './http';

/** GET /api/settings 响应：公共设置视图（临期提醒天数阈值）。 */
export interface SettingsData {
  expiry_remind_days: number;
}

export const settingsApi = {
  /** GET /api/settings → {expiry_remind_days}。 */
  get() {
    return http.get<SettingsData>('/settings').then((r) => r.data);
  },
  /** PUT /api/settings → 更新设置（body {key, value}；value 为字符串，仅白名单键 expiry.remind.days）。 */
  update(key: string, value: string) {
    return http.put<SettingsData>('/settings', { key, value }).then((r) => r.data);
  },
};
