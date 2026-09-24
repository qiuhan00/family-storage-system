// src/router/index.ts —— 路由与登录守卫：认证页公开，其余需登录；主界面由 LayoutView（NavBar+Tabbar）承载。
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
      component: () => import('../views/LayoutView.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'home', component: () => import('../views/HomeView.vue') },
        { path: 'items', name: 'items', component: () => import('../views/ItemsView.vue') },
        { path: 'shopping', name: 'shopping', component: () => import('../views/ShoppingView.vue') },
        { path: 'profile', name: 'profile', component: () => import('../views/ProfileView.vue') },
      ],
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