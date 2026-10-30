// src/api/notifications.ts —— 站内通知接口封装（spec §5.7：列表/未读/已读）。
import { http } from './http';
import type { Notification, NotificationType, PageResponse, UnreadCountResponse } from '../types';

export interface NotificationQuery {
  unread?: boolean;
  type?: NotificationType;
  page?: number;
  size?: number;
}

export const notificationsApi = {
  /** GET /api/notifications/unread-count → {count}（未读通知数）。 */
  unreadCount() {
    return http.get<UnreadCountResponse>('/notifications/unread-count').then((r) => r.data);
  },
  /** GET /api/notifications?unread&page&size → 分页通知（默认创建时间倒序）。 */
  list(params: NotificationQuery = {}) {
    return http
      .get<PageResponse<Notification>>('/notifications', {
        params: {
          ...(params.unread ? { unread: true } : {}),
          ...(params.type ? { type: params.type } : {}),
          page: params.page ?? 0,
          size: params.size ?? 20,
        },
      })
      .then((r) => r.data);
  },
  /** POST /api/notifications/{id}/read → 标记单条已读。 */
  read(id: number) {
    return http.post<Notification>(`/notifications/${id}/read`).then((r) => r.data);
  },
  /** POST /api/notifications/read-all → 全部已读。 */
  readAll() {
    return http.post<void>('/notifications/read-all').then((r) => r.data);
  },
};
