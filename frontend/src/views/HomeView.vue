// src/views/HomeView.vue —— 首页占位（物品清单等页面由后续任务实现）。
<script setup lang="ts">
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const auth = useAuthStore();

const onLogout = () => {
  auth.logout();
  showToast('已退出登录');
  router.replace('/login');
};
</script>

<template>
  <div class="home-page">
    <h1>家庭收纳物品管理系统</h1>
    <p class="home-tip">物品清单、分类、位置与购物清单功能开发中（后续任务交付）。</p>
    <p class="home-user" v-if="auth.user">当前用户：{{ auth.user.displayName }}</p>
    <van-button size="small" plain @click="onLogout">退出登录</van-button>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 20vh 24px;
  text-align: center;
  background: #f7f8fa;
}
.home-page h1 {
  margin: 0 0 12px;
  font-size: 22px;
  color: #323233;
}
.home-tip {
  font-size: 14px;
  color: #969799;
}
.home-user {
  font-size: 14px;
  color: #646566;
}
</style>