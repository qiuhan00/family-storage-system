// src/api/notifications.ts —— 通知接口封装（spec §5.7；本期仅未读数，列表/已读由后续任务补齐）。
import { http } from './http';
import type { UnreadCountResponse } from '../types';

export const notificationsApi = {
  /** GET /api/notifications/unread-count → {count}（未读通知数）。 */
  unreadCount() {
    return http.get<UnreadCountResponse>('/notifications/unread-count').then((r) => r.data);
  },
};