// src/store/notification.ts —— 通知未读状态：纯逻辑工厂 + Pinia store（60s 轮询由组件驱动）。
import { defineStore } from 'pinia';
import { notificationsApi } from '../api/notifications';

/** 未读通知状态（纯逻辑，可独立用于单元测试）。 */
export interface NotificationState {
  count: number;
  /** 应用服务器下发的未读数（负数按 0 处理）。 */
  applyCount(count: number): void;
}

/** 状态工厂：初始未读数 0，applyCount 写回 count。 */
export function createNotifState(): NotificationState {
  return {
    count: 0,
    applyCount(count: number) {
      this.count = count > 0 ? count : 0;
    },
  };
}

/**
 * 通知 store：count 为未读数；fetchUnread() 调 GET /api/notifications/unread-count，
 * 失败静默（保持上次角标，不影响页面）。轮询节奏（每 60s + 路由进入刷新）由组件控制。
 */
export const useNotificationStore = defineStore('notification', {
  state: createNotifState,
  actions: {
    async fetchUnread() {
      try {
        const { count } = await notificationsApi.unreadCount();
        this.applyCount(count);
      } catch {
        // 轮询失败静默处理：避免角标轮询打扰用户
      }
    },
  },
});