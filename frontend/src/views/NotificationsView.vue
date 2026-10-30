<!-- src/views/NotificationsView.vue —— 通知中心：全部/未读筛选、分页加载、点击已读并跳转物品、全部已读。
     临期与库存不足用不同标签区分（类型 EXPIRY / LOW_STOCK）。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { apiErrorMessage } from '../api/http';
import { notificationsApi } from '../api/notifications';
import { useNotificationStore } from '../store/notification';
import type { Notification } from '../types';

const PAGE_SIZE = 20;

const router = useRouter();
const notifStore = useNotificationStore();

type Filter = 'all' | 'unread';
const filter = ref<Filter>('all');

const items = ref<Notification[]>([]);
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);
const page = ref(-1);

async function fetchPage(p: number) {
  loading.value = true;
  try {
    const data = await notificationsApi.list({
      unread: filter.value === 'unread' ? true : undefined,
      page: p,
      size: PAGE_SIZE,
    });
    const content = data.content ?? [];
    items.value = p === 0 ? content : [...items.value, ...content];
    page.value = p;
    finished.value = content.length === 0 || data.totalPages <= p + 1;
  } catch (e) {
    showToast(apiErrorMessage(e, '通知加载失败'));
    if (p === 0) items.value = [];
  } finally {
    loading.value = false;
  }
}

function reload() {
  finished.value = false;
  items.value = [];
  page.value = -1;
  void fetchPage(0);
}

function onLoad() {
  if (refreshing.value) return;
  void fetchPage(page.value + 1);
}

async function onRefresh() {
  refreshing.value = true;
  finished.value = false;
  try {
    await fetchPage(0);
  } finally {
    refreshing.value = false;
  }
}

function switchFilter(f: Filter) {
  if (filter.value === f) return;
  filter.value = f;
  reload();
}

function typeLabel(type: Notification['type']): string {
  if (type === 'EXPIRY') return '临期';
  if (type === 'LOW_STOCK') return '库存不足';
  return '其他';
}

function typeClass(type: Notification['type']): string {
  if (type === 'EXPIRY') return 'notif-type--expiry';
  if (type === 'LOW_STOCK') return 'notif-type--low';
  return 'notif-type--other';
}

function fmtTime(value: string): string {
  return dayjs(value).format('MM-DD HH:mm');
}

/** 点击通知：标记已读（未读时）并可跳到关联物品详情。 */
async function open(n: Notification) {
  try {
    if (!n.readFlag) {
      await notificationsApi.read(n.id);
      n.readFlag = true;
      if (filter.value === 'unread') items.value = items.value.filter((x) => x.id !== n.id);
      void notifStore.fetchUnread();
    }
  } catch {
    // 标记已读失败不阻塞跳转
  }
  if (n.refId != null) {
    void router.push(`/items/${n.refId}`);
  }
}

async function readAll() {
  try {
    await notificationsApi.readAll();
    if (filter.value === 'unread') {
      items.value = [];
    } else {
      items.value.forEach((n) => {
        n.readFlag = true;
      });
    }
    void notifStore.fetchUnread();
    showToast('已全部标记已读');
  } catch (e) {
    showToast(apiErrorMessage(e, '操作失败'));
  }
}

onMounted(reload);
</script>

<template>
  <div class="notifications-page">
    <div class="notif-toolbar">
      <div class="notif-tabs">
        <button
          type="button"
          class="notif-tab"
          :class="{ 'notif-tab--active': filter === 'all' }"
          @click="switchFilter('all')"
        >
          全部
        </button>
        <button
          type="button"
          class="notif-tab"
          :class="{ 'notif-tab--active': filter === 'unread' }"
          @click="switchFilter('unread')"
        >
          未读
        </button>
      </div>
      <van-button size="small" plain type="primary" @click="readAll">全部已读</van-button>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        :immediate-check="false"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <div v-if="items.length" class="notif-list">
          <button
            v-for="n in items"
            :key="n.id"
            type="button"
            class="notif-row paper-card"
            :class="{ 'notif-row--unread': !n.readFlag }"
            @click="open(n)"
          >
            <span class="paper-chip notif-type" :class="typeClass(n.type)">{{ typeLabel(n.type) }}</span>
            <span class="notif-row__main">
              <span class="notif-row__title">
                <span v-if="!n.readFlag" class="notif-dot" aria-label="未读"></span>{{ n.title }}
              </span>
              <span class="notif-row__body">{{ n.body }}</span>
              <span class="notif-row__time">{{ fmtTime(n.createdAt) }}</span>
            </span>
          </button>
        </div>
        <van-empty v-else-if="!loading && finished" description="暂无通知" image-size="80" />
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.notifications-page {
  padding-bottom: 12px;
}

.notif-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--paper);
}

.notif-tabs {
  display: flex;
  gap: 8px;
}

.notif-tab {
  padding: 6px 16px;
  font: inherit;
  font-size: 13px;
  color: var(--ink-2);
  background: transparent;
  border: 1px dashed var(--dashed);
  border-radius: 2px;
  cursor: pointer;
}

.notif-tab--active {
  color: var(--on-accent);
  background: var(--ink);
  border-color: var(--ink);
}

.notif-list {
  padding: 4px 12px 12px;
}

.notif-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  margin-bottom: 10px;
  padding: 12px;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.notif-row--unread {
  border-left: 3px solid var(--terracotta);
}

.notif-type {
  flex: 0 0 auto;
  margin-top: 1px;
}

.notif-type--expiry {
  color: var(--terracotta-ink);
  background: var(--terracotta-soft);
  border-color: rgba(194, 96, 60, 0.5);
}

.notif-type--low {
  color: var(--danger);
  background: var(--danger-soft);
  border-color: rgba(168, 57, 31, 0.5);
}

.notif-type--other {
  color: var(--ink-2);
}

.notif-row__main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.notif-row__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}

.notif-dot {
  width: 6px;
  height: 6px;
  background: var(--terracotta);
  border-radius: 50%;
}

.notif-row__body {
  font-size: 13px;
  color: var(--ink-2);
  word-break: break-word;
}

.notif-row__time {
  font-size: 11px;
  color: var(--ink-2);
}
</style>
