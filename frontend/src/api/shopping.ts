// src/api/shopping.ts —— 购物清单接口封装（spec §5.5：列表/新增/更新/状态流转）。
// status 查询参数可选：缺省或空则后端返回全部（按创建倒序）；BOUGHT/CANCELLED 由后端写 completed_at。
import { http } from './http';
import type { ShoppingItem, ShoppingStatus } from '../types';

/** 购物项创建/更新请求体（spec §5.5 ShoppingRequest；status 不随本请求变更）。 */
export interface ShoppingPayload {
  name: string;
  quantity: number;
  unit: string | null;
  categoryId: number | null;
  remark: string | null;
}

export const shoppingApi = {
  /** GET /api/shopping?status= → 清单；status 为空返回全部。 */
  list(status?: ShoppingStatus | null) {
    return http
      .get<ShoppingItem[]>('/shopping', { params: status ? { status } : {} })
      .then((r) => r.data);
  },
  /** POST /api/shopping → 新增缺耗品（quantity 必须为正整数，后端 400 校验）。 */
  create(data: ShoppingPayload) {
    return http.post<ShoppingItem>('/shopping', data).then((r) => r.data);
  },
  /** PUT /api/shopping/{id} → 更新。 */
  update(id: number, data: ShoppingPayload) {
    return http.put<ShoppingItem>(`/shopping/${id}`, data).then((r) => r.data);
  },
  /** PATCH /api/shopping/{id}/status → 流转状态（BOUGHT/CANCELLED 写 completedAt，回 PENDING 清空）。 */
  setStatus(id: number, status: ShoppingStatus) {
    return http.patch<ShoppingItem>(`/shopping/${id}/status`, { status }).then((r) => r.data);
  },
};
