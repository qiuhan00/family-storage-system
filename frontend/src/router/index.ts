// src/router/index.ts —— 路由与登录守卫：认证页公开，其余需登录。
import { createRouter, createWebHistory } from 'vue-router';
import { restoreToken } from '../store/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomeView.vue'),
      meta: { requiresAuth: true },
    },
  ],
});

router.beforeEach((to) => {
  const authed = Boolean(restoreToken());
  if (to.meta.requiresAuth && !authed) return '/login';
  if (!to.meta.requiresAuth && authed && to.path !== '/') return '/';
  return true;
});

export default router;