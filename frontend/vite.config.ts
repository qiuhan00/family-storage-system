// vite.config.ts —— Vite 构建配置：Vue 插件 + Vant 按需组件，dev 代理 /api 到后端 8080。
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import { VantResolver } from '@vant/auto-import-resolver';

export default defineConfig({
  plugins: [
    vue(),
    Components({ resolvers: [VantResolver()] }),
  ],
  server: {
    port: 5173,
    // dev 代理到后端 8080：/api 接口 + /uploads 静态上传文件（生产由 nginx/后端 WebConfig 兜底）
    proxy: { '/api': 'http://localhost:8080', '/uploads': 'http://localhost:8080' },
  },
});