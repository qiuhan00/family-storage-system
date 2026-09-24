// src/api/auth.ts —— 认证接口封装（spec §5.1：login/register/me/password）。
import { http } from './http';
import type {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  RegisterRequest,
  UserResponse,
} from '../types';

export const authApi = {
  /** POST /api/auth/login → {token, user}。 */
  login(data: LoginRequest) {
    return http.post<AuthResponse>('/auth/login', data).then((r) => r.data);
  },
  /** POST /api/auth/register → {token, user}；系统非空时返回 403。 */
  register(data: RegisterRequest) {
    return http.post<AuthResponse>('/auth/register', data).then((r) => r.data);
  },
  /** GET /api/auth/me → 当前用户信息。 */
  me() {
    return http.get<UserResponse>('/auth/me').then((r) => r.data);
  },
  /** PUT /api/auth/password → 修改本人密码。 */
  changePassword(data: ChangePasswordRequest) {
    return http.put<void>('/auth/password', data).then((r) => r.data);
  },
};