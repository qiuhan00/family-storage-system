// src/store/notification.spec.ts
// notification store 纯逻辑单元测试：状态工厂与 Pinia store（接口层 mock，不触网）。
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { createNotifState, useNotificationStore } from './notification';
import { notificationsApi } from '../api/notifications';

vi.mock('../api/notifications', () => ({
  notificationsApi: { unreadCount: vi.fn() },
}));

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('notification store', () => {
  it('starts at zero and adopts server value', () => {
    const s = createNotifState();
    expect(s.count).toBe(0);
    s.applyCount(3);
    expect(s.count).toBe(3);
  });

  it('pinia store starts at zero', () => {
    expect(useNotificationStore().count).toBe(0);
  });

  it('fetchUnread stores unread count from api', async () => {
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue({ count: 5 });
    const store = useNotificationStore();
    await store.fetchUnread();
    expect(notificationsApi.unreadCount).toHaveBeenCalledTimes(1);
    expect(store.count).toBe(5);
  });

  it('fetchUnread fails silently keeping previous count', async () => {
    vi.mocked(notificationsApi.unreadCount).mockRejectedValue(new Error('network'));
    const store = useNotificationStore();
    store.applyCount(2);
    await store.fetchUnread();
    expect(store.count).toBe(2);
  });
});