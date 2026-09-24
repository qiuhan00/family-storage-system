// src/api/locations.ts —— 位置接口封装（spec §5.3：树形查询/增删改）+ 树展平纯工具。
import { http } from './http';
import type { Location, LocationPayload } from '../types';

/** 位置树展平节点：path 为「父名 / 子名」全路径，depth 从 0 起（用于缩进展示）。 */
export interface FlatLocation {
  id: number;
  name: string;
  path: string;
  depth: number;
  parentId: number | null;
}

/** 将位置树按深度优先展平为带全路径的列表（筛选列表与选择器共用）。 */
export function flattenLocations(nodes: Location[], prefix = '', depth = 0): FlatLocation[] {
  const out: FlatLocation[] = [];
  for (const node of nodes) {
    const path = prefix ? `${prefix} / ${node.name}` : node.name;
    out.push({ id: node.id, name: node.name, path, depth, parentId: node.parentId });
    if (node.children?.length) out.push(...flattenLocations(node.children, path, depth + 1));
  }
  return out;
}

export const locationsApi = {
  /** GET /api/locations → 位置树（children 递归）。 */
  getTree() {
    return http.get<Location[]>('/locations').then((r) => r.data);
  },
  /** GET /api/locations/{id} → 单个位置节点。 */
  get(id: number) {
    return http.get<Location>(`/locations/${id}`).then((r) => r.data);
  },
  /** POST /api/locations → 新增（parentId 为 null 表示根节点）。 */
  create(data: LocationPayload) {
    return http.post<Location>('/locations', data).then((r) => r.data);
  },
  /** PUT /api/locations/{id} → 更新（成环由后端 400 LOCATION_CYCLE 拒绝）。 */
  update(id: number, data: LocationPayload) {
    return http.put<Location>(`/locations/${id}`, data).then((r) => r.data);
  },
  /** DELETE /api/locations/{id} → 删除（有子级/物品引用时后端拒绝）。 */
  delete(id: number) {
    return http.delete<void>(`/locations/${id}`).then((r) => r.data);
  },
};