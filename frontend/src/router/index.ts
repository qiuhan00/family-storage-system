// src/router/index.ts —— 路由与守卫：认证页公开，其余需登录；/users、/settings 仅 ADMIN
// （meta.adminOnly：缓存 user 已是非 ADMIN 时立即跳回个人中心；刷新后 user 为空的场景由
// 视图组件内 ensureAdmin 调 /auth/me 二次校验）。主界面由 LayoutView（NavBar+Tabbar）承载。
import { createRouter, createWebHistory } from 'vue-router';
import { restoreToken, useAuthStore } from '../store/auth';

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
        {
          path: 'items/new',
          name: 'item-new',
          component: () => import('../views/ItemEditView.vue'),
          meta: { requiresAuth: true, back: true },
        },
        {
          path: 'items/:id',
          name: 'item-detail',
          component: () => import('../views/ItemDetailView.vue'),
          meta: { requiresAuth: true, back: true },
        },
        {
          path: 'items/:id/edit',
          name: 'item-edit',
          component: () => import('../views/ItemEditView.vue'),
          meta: { requiresAuth: true, back: true },
        },
        { path: 'shopping', name: 'shopping', component: () => import('../views/ShoppingView.vue') },
        { path: 'profile', name: 'profile', component: () => import('../views/ProfileView.vue') },
        {
          path: 'users',
          name: 'users',
          component: () => import('../views/UsersView.vue'),
          meta: { requiresAuth: true, adminOnly: true, back: true },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/SettingsView.vue'),
          meta: { requiresAuth: true, adminOnly: true, back: true },
        },
        {
          path: 'locations',
          name: 'locations',
          component: () => import('../views/LocationsView.vue'),
          meta: { requiresAuth: true, back: true },
        },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const authed = Boolean(restoreToken());
  if (to.meta.requiresAuth && !authed) return '/login';
  if (!to.meta.requiresAuth && authed && to.path !== '/') return '/';
  if (to.meta.adminOnly) {
    const auth = useAuthStore();
    if (auth.user && auth.user.role !== 'ADMIN') return '/profile';
  }
  return true;
});

export default router;