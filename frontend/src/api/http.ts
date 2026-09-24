// src/api/http.ts —— axios 实例：baseURL=/api；请求附加 Bearer token；401 清 token 并跳转登录页。
import axios from 'axios';
import { tokenKey } from '../store/auth';

export const http = axios.create({ baseURL: '/api' });

// 请求拦截：携带已登录的 JWT。
http.interceptors.request.use((cfg) => {
  const t = localStorage.getItem(tokenKey);
  if (t) cfg.headers.Authorization = `Bearer ${t}`;
  return cfg;
});

// 响应拦截：401 视为登录失效，清除本地 token 并回到登录页。
http.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem(tokenKey);
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(err);
  },
);