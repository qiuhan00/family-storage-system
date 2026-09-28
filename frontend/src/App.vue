<!-- src/App.vue —— 根组件：ConfigProvider 下发主题变量，挂载路由出口。 -->
<script setup lang="ts">
import { themeVars } from './styles/tokens';
</script>

<template>
  <van-config-provider :theme-vars="themeVars">
    <router-view />
  </van-config-provider>
</template>
