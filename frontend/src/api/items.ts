// src/api/items.ts —— 物品接口封装（spec §5.4；本任务仅临期列表，分页/详情由 Task 13 补齐）。
import { http } from './http';
import type { Item } from '../types';

export const itemsApi = {
  /** GET /api/items/expiring?days= → 临期物品列表。 */
  expiring(days = 7) {
    return http.get<Item[]>('/items/expiring', { params: { days } }).then((r) => r.data);
  },
};