// src/api/categories.ts —— 分类接口封装（spec §5.3：列表/详情/增删改，重名/占用由后端 400 拒绝）。
import { http } from './http';
import type { Category, CategoryPayload } from '../types';

export const categoriesApi = {
  /** GET /api/categories → 分类列表（按 sortOrder 排序）。 */
  list() {
    return http.get<Category[]>('/categories').then((r) => r.data);
  },
  /** GET /api/categories/{id} → 分类详情。 */
  get(id: number) {
    return http.get<Category>(`/categories/${id}`).then((r) => r.data);
  },
  /** POST /api/categories → 新增，返回新分类 id（后端契约返回 Long）。 */
  create(data: CategoryPayload) {
    return http.post<number>('/categories', data).then((r) => r.data);
  },
  /** PUT /api/categories/{id} → 更新。 */
  update(id: number, data: CategoryPayload) {
    return http.put<Category>(`/categories/${id}`, data).then((r) => r.data);
  },
  /** DELETE /api/categories/{id} → 删除（有物品引用时后端 400 CATEGORY_IN_USE）。 */
  delete(id: number) {
    return http.delete<void>(`/categories/${id}`).then((r) => r.data);
  },
};