// src/api/items.ts —— 物品接口封装（spec §5.4：检索/详情/CRUD/出入库/临期列表/变更历史）。
import { http } from './http';
import type { Item, ItemPayload, PageResponse, StockAdjustPayload, StockChange } from '../types';

/** GET /api/items 查询参数（分页字段 page/size；keyword/categoryId/locationId/status/expiring 过滤）。 */
export interface ItemSearchParams {
  keyword?: string | null;
  categoryId?: number | null;
  locationId?: number | null;
  status?: string | null;
  expiring?: boolean;
  page?: number | null;
  size?: number | null;
}

/** 交给 axios 的最终查询参数对象（空值已省略，expiring 映射为 1）。 */
export interface ItemQueryParams {
  keyword?: string;
  categoryId?: number;
  locationId?: number;
  status?: string;
  expiring?: 1;
  page?: number;
  size?: number;
}

/**
 * 构造 GET /api/items 查询参数：空串 / null / undefined / false 一律省略
 * （不在 URL 上出现空过滤条件），expiring 以 spec 约定的 1 表示真值。
 */
export function buildItemParams(params: ItemSearchParams): ItemQueryParams {
  const q: ItemQueryParams = {};
  const keyword = params.keyword?.trim();
  if (keyword) q.keyword = keyword;
  if (params.categoryId != null) q.categoryId = params.categoryId;
  if (params.locationId != null) q.locationId = params.locationId;
  const status = params.status?.trim();
  if (status) q.status = status;
  if (params.expiring) q.expiring = 1;
  if (params.page != null) q.page = params.page;
  if (params.size != null) q.size = params.size;
  return q;
}

export const itemsApi = {
  /** GET /api/items?keyword&categoryId&locationId&status&expiring&page&size → 分页检索。 */
  searchItems(params: ItemSearchParams) {
    return http.get<PageResponse<Item>>('/items', { params: buildItemParams(params) }).then((r) => r.data);
  },
  /** GET /api/items/expiring?days= → 临期物品列表。 */
  expiring(days = 7) {
    return http.get<Item[]>('/items/expiring', { params: { days } }).then((r) => r.data);
  },
  /** GET /api/items/{id} → 物品详情。 */
  getItem(id: number) {
    return http.get<Item>(`/items/${id}`).then((r) => r.data);
  },
  /** POST /api/items → 新增（qty>0 时后端自动写 INITIAL 出入库记录）。 */
  createItem(data: ItemPayload) {
    return http.post<Item>('/items', data).then((r) => r.data);
  },
  /** PUT /api/items/{id} → 更新。 */
  updateItem(id: number, data: ItemPayload) {
    return http.put<Item>(`/items/${id}`, data).then((r) => r.data);
  },
  /** DELETE /api/items/{id} → 删除（后端级联清理出入库记录）。 */
  deleteItem(id: number) {
    return http.delete<void>(`/items/${id}`).then((r) => r.data);
  },
  /** POST /api/items/{id}/stock → 出入库调整（deltaQty 正入负出）。 */
  adjustStock(id: number, data: StockAdjustPayload) {
    return http.post<StockChange>(`/items/${id}/stock`, data).then((r) => r.data);
  },
  /** GET /api/items/{id}/stock-changes → 库存变更历史（后端已按时间倒序）。 */
  stockHistory(id: number) {
    return http.get<StockChange[]>(`/items/${id}/stock-changes`).then((r) => r.data);
  },
};