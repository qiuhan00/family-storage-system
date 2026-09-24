// src/store/auth.ts —— 认证状态：token 持久化工具 + Pinia auth store。
import { defineStore } from 'pinia';
import { authApi } from '../api/auth';
import type { LoginRequest, RegisterRequest, UserResponse } from '../types';

/** localStorage 中 JWT 的存储键。 */
export const tokenKey = 'fs_token';

/** 持久化 token。 */
export const setToken = (t: string): void => localStorage.setItem(tokenKey, t);

/** 读取持久化 token（未设置时返回 null）。 */
export const restoreToken = (): string | null => localStorage.getItem(tokenKey);

/** 清除持久化 token。 */
export const clearToken = (): void => localStorage.removeItem(tokenKey);

interface AuthState {
  token: string | null;
  user: UserResponse | null;
}

/**
 * 认证 store：登录/注册成功后保存 token+user 并写 localStorage；
 * init() 从 localStorage 恢复；logout() 清空两者。
 */
export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: null,
    user: null,
  }),
  actions: {
    /** 从 localStorage 恢复已持久化的 token。 */
    init() {
      const t = restoreToken();
      if (t) this.token = t;
    },
    async login(req: LoginRequest) {
      const res = await authApi.login(req);
      this.applyAuth(res.token, res.user);
      return res;
    },
    async register(req: RegisterRequest) {
      const res = await authApi.register(req);
      this.applyAuth(res.token, res.user);
      return res;
    },
    logout() {
      this.token = null;
      this.user = null;
      clearToken();
    },
    applyAuth(token: string, user: UserResponse) {
      this.token = token;
      this.user = user;
      setToken(token);
    },
  },
});