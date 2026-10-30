// src/api/http.ts —— axios 实例：baseURL=/api；请求附加 Bearer token；401 清 token 并跳转登录页。
import axios from 'axios';
import { clearToken, restoreToken } from '../store/auth';

export const http = axios.create({ baseURL: '/api' });

// 请求拦截：携带已登录的 JWT（localStorage 持久会话或 sessionStorage 临时会话）。
http.interceptors.request.use((cfg) => {
  const t = restoreToken();
  if (t) cfg.headers.Authorization = `Bearer ${t}`;
  return cfg;
});

// 响应拦截：401 视为登录失效，清除 token 并回到登录页。
http.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401) {
      clearToken();
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(err);
  },
);

/** 从 axios 错误里提取后端统一错误体 {code,message} 的中文 message，取不到时给兜底文案。 */
export function apiErrorMessage(err: unknown, fallback = '操作失败，请稍后重试'): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const body = (err as { response?: { data?: { message?: unknown } } }).response?.data;
    if (body && typeof body.message === 'string' && body.message) return body.message;
  }
  return fallback;
}