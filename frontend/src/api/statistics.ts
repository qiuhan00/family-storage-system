// src/api/statistics.ts —— 统计接口封装（spec §5.8：summary / by-category / by-location）。
import { http } from './http';
import type { ItemCountByDimension, Summary } from '../types';

export const statisticsApi = {
  /** GET /api/statistics/summary → 物品总数/在库总量/总价值/临期提醒/待购清单。 */
  summary() {
    return http.get<Summary>('/statistics/summary').then((r) => r.data);
  },
  /** GET /api/statistics/by-category → 分类维度聚合。 */
  byCategory() {
    return http.get<ItemCountByDimension[]>('/statistics/by-category').then((r) => r.data);
  },
  /** GET /api/statistics/by-location → 位置维度聚合。 */
  byLocation() {
    return http.get<ItemCountByDimension[]>('/statistics/by-location').then((r) => r.data);
  },
};