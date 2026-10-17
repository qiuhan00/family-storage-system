<!-- src/components/NotificationBadge.vue —— NavBar 右侧未读通知角标：每 60s 轮询 + 路由变更刷新。 -->
<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useNotificationStore } from '../store/notification';
import PaperIcon from './PaperIcon.vue';

const POLL_INTERVAL_MS = 60_000;

const notif = useNotificationStore();
const route = useRoute();
let timer: number | undefined;

/** 拉取一次未读数（失败由 store 静默兜底）。 */
function refresh() {
  void notif.fetchUnread();
}

onMounted(() => {
  refresh();
  timer = window.setInterval(refresh, POLL_INTERVAL_MS);
});

onUnmounted(() => {
  if (timer !== undefined) window.clearInterval(timer);
});

// 路由进入（首页/物品/购物/我的 之间切换）时立即刷新一次未读数。
watch(() => route.fullPath, refresh);
</script>

<template>
  <van-badge :content="notif.count" :max="99" :show-zero="false" class="notif-badge">
    <PaperIcon name="bell" :size="20" label="通知" />
  </van-badge>
</template>

<style scoped>
.notif-badge {
  cursor: pointer;
}
</style>