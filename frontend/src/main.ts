// src/main.ts —— 前端入口：挂载 App、Pinia、路由。
import '@fontsource/noto-serif-sc/700.css';
import './styles/global.css';
import './styles/paper.css';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { useAuthStore } from './store/auth';

const app = createApp(App);
app.use(createPinia());
app.use(router);

// 启动时从 localStorage 恢复登录态，供路由守卫/后续页面使用。
useAuthStore().init();

app.mount('#app');
