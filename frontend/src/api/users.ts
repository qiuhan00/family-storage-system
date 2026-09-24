// src/api/users.ts —— 成员管理接口封装（spec §5.2，仅 ADMIN：分页列表/创建/更新/启停/重置密码）。
// UserResponse.enabled（R13）反映启停状态，Switch 直接读写。
import { http } from './http';
import type { PageResponse, Role, UserResponse } from '../types';

/** 成员创建/更新请求体（spec §5.2 UserRequest：password 仅创建时生效；更新忽略 username/password）。 */
export interface UserPayload {
  username: string;
  displayName: string;
  role?: Role;
  email?: string | null;
  phone?: string | null;
  /** 创建时可选：留空后端随机生成 8 位；更新时忽略。 */
  password?: string | null;
}

export const usersApi = {
  /** GET /api/users?page&size → 成员分页列表。 */
  list(page = 0, size = 20) {
    return http
      .get<PageResponse<UserResponse>>('/users', { params: { page, size } })
      .then((r) => r.data);
  },
  /** POST /api/users → 创建成员（含密码；role 缺省 MEMBER）。 */
  create(data: UserPayload) {
    return http.post<UserResponse>('/users', data).then((r) => r.data);
  },
  /** PUT /api/users/{id} → 更新昵称/角色/联系方式（用户名与密码不变）。 */
  update(id: number, data: UserPayload) {
    return http.put<UserResponse>(`/users/${id}`, data).then((r) => r.data);
  },
  /** PATCH /api/users/{id}/status → 启用/停用成员（body {enabled}）。 */
  setStatus(id: number, enabled: boolean) {
    return http.patch<UserResponse>(`/users/${id}/status`, { enabled }).then((r) => r.data);
  },
  /** POST /api/users/{id}/reset-password → 重置密码（body {newPassword}）。 */
  resetPassword(id: number, newPassword: string) {
    return http.post<void>(`/users/${id}/reset-password`, { newPassword }).then((r) => r.data);
  },
};
