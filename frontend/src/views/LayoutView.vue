<!-- src/views/LayoutView.vue —— 主布局：顶部 NavBar（详情/编辑页带返回箭头，右侧通知角标）+ 中部 router-view + 底部 Tabbar。 -->
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import NotificationBadge from '../components/NotificationBadge.vue';
import PaperIcon from '../components/PaperIcon.vue';

const route = useRoute();
const router = useRouter();

const titles: Record<string, string> = {
  home: '首页',
  items: '物品',
  'item-new': '新增物品',
  'item-detail': '物品详情',
  'item-edit': '编辑物品',
  shopping: '购物',
  profile: '我的',
  users: '用户管理',
  settings: '应用设置',
  locations: '位置管理',
  categories: '分类管理',
};

const pageTitle = computed(() => titles[String(route.name)] ?? '家庭收纳');
const showBack = computed(() => Boolean(route.meta.back));

function goBack() {
  if (window.history.length > 1) {
    router.back();
  } else {
    void router.replace('/');
  }
}
</script>

<template>
  <div class="layout-root paper-page">
    <van-nav-bar :title="pageTitle" fixed placeholder>
      <template #left>
        <button v-if="showBack" type="button" class="icon-btn" aria-label="返回" @click="goBack">
          <PaperIcon name="back" :size="20" />
        </button>
      </template>
      <template #right>
        <NotificationBadge />
      </template>
    </van-nav-bar>

    <main class="layout-content">
      <router-view />
    </main>

    <van-tabbar route fixed placeholder active-color="var(--ink)">
      <van-tabbar-item replace :to="{ name: 'home' }">
        <template #icon><PaperIcon name="home" :size="20" /></template>
        首页
      </van-tabbar-item>
      <van-tabbar-item replace to="/items">
        <template #icon><PaperIcon name="items" :size="20" /></template>
        物品
      </van-tabbar-item>
      <van-tabbar-item replace to="/shopping">
        <template #icon><PaperIcon name="cart" :size="20" /></template>
        购物
      </van-tabbar-item>
      <van-tabbar-item replace to="/profile">
        <template #icon><PaperIcon name="user" :size="20" /></template>
        我的
      </van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.layout-root {
  min-height: 100vh;
  background: var(--paper);
}
.layout-content {
  /* NavBar(46px) 与 Tabbar(50px) 均已用 placeholder 占位，这里留一点底部呼吸空间 */
  min-height: calc(100vh - 46px - 50px);
}
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-left: -12px;
  padding: 0;
  color: var(--ink);
  background: transparent;
  border: 0;
  cursor: pointer;
}
</style>
