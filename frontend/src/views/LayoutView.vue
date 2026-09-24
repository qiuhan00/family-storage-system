<!-- src/views/LayoutView.vue —— 主布局：顶部 NavBar（右侧通知角标）+ 中部 router-view + 底部 Tabbar。 -->
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import NotificationBadge from '../components/NotificationBadge.vue';

const route = useRoute();

const titles: Record<string, string> = {
  home: '首页',
  items: '物品',
  shopping: '购物',
  profile: '我的',
};

const pageTitle = computed(() => titles[String(route.name)] ?? '家庭收纳');
</script>

<template>
  <div class="layout-root">
    <van-nav-bar :title="pageTitle" fixed placeholder>
      <template #right>
        <NotificationBadge />
      </template>
    </van-nav-bar>

    <main class="layout-content">
      <router-view />
    </main>

    <van-tabbar route fixed placeholder active-color="#1989fa">
      <van-tabbar-item replace to="/" icon="wap-home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/items" icon="apps-o">物品</van-tabbar-item>
      <van-tabbar-item replace to="/shopping" icon="cart-o">购物</van-tabbar-item>
      <van-tabbar-item replace to="/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<style scoped>
.layout-root {
  min-height: 100vh;
  background: #f7f8fa;
}
.layout-content {
  /* NavBar(46px) 与 Tabbar(50px) 均已用 placeholder 占位，这里留一点底部呼吸空间 */
  min-height: calc(100vh - 46px - 50px);
}
</style>