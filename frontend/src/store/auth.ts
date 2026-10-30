// src/store/auth.ts —— 认证状态：token 持久化工具 + Pinia auth store。
import { defineStore } from 'pinia';
import { authApi } from '../api/auth';
import type { LoginRequest, RegisterRequest, UserResponse } from '../types';

/** localStorage 中 JWT 的存储键。 */
export const tokenKey = 'fs_token';

/**
 * 持久化 token：persist=true 写 localStorage（7 天免密登录，跨浏览器重启保留）；
 * persist=false 写 sessionStorage（仅本次会话，关闭浏览器即失效）。
 * 两者互斥，切换时清掉另一个，避免读到旧 token。
 */
export const setToken = (t: string, persist = true): void => {
  if (persist) {
    localStorage.setItem(tokenKey, t);
    sessionStorage.removeItem(tokenKey);
  } else {
    sessionStorage.setItem(tokenKey, t);
    localStorage.removeItem(tokenKey);
  }
};

/** 读取持久化 token（优先 localStorage，其次 sessionStorage；未设置时返回 null）。 */
export const restoreToken = (): string | null =>
  localStorage.getItem(tokenKey) ?? sessionStorage.getItem(tokenKey);

/** 清除两种存储中的 token。 */
export const clearToken = (): void => {
  localStorage.removeItem(tokenKey);
  sessionStorage.removeItem(tokenKey);
};

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
    async login(req: LoginRequest, persist = true) {
      const res = await authApi.login(req);
      this.applyAuth(res.token, res.user, persist);
      return res;
    },
    async register(req: RegisterRequest) {
      const res = await authApi.register(req);
      this.applyAuth(res.token, res.user, true);
      return res;
    },
    logout() {
      this.token = null;
      this.user = null;
      clearToken();
    },
    applyAuth(token: string, user: UserResponse, persist = true) {
      this.token = token;
      this.user = user;
      setToken(token, persist);
    },
  },
});