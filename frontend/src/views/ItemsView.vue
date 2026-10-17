<!-- src/views/ItemsView.vue —— 物品列表：关键词搜索 + 筛选 Popup（分类/位置/临期/状态）
     + List 无限滚动（immediate-check=false 手动首载，便于复用重置）+ PullRefresh 下拉刷新。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { itemsApi } from '../api/items';
import { categoriesApi } from '../api/categories';
import { locationsApi, flattenLocations } from '../api/locations';
import { settingsApi } from '../api/settings';
import { expiryLevel } from '../utils/expiry';
import PaperIcon from '../components/PaperIcon.vue';
import type { FlatLocation } from '../api/locations';
import type { Category, Item } from '../types';

const PAGE_SIZE = 20;

const router = useRouter();

/** 临期阈值（天）：GET /api/settings 的 expiry_remind_days，驱动角标与筛选文案（F2）。 */
const remindDays = ref(7);

interface FilterDraft {
  categoryId: number | '';
  locationId: number | '';
  expiring: boolean;
  status: 'ACTIVE' | 'ARCHIVED';
}

const defaultFilter: FilterDraft = { categoryId: '', locationId: '', expiring: false, status: 'ACTIVE' };

// --- 搜索 / 筛选状态 ---
const keyword = ref('');
const showFilter = ref(false);
const draft = ref<FilterDraft>({ ...defaultFilter });
const applied = ref<FilterDraft>({ ...defaultFilter });

const filterActive = computed(
  () =>
    applied.value.categoryId !== '' ||
    applied.value.locationId !== '' ||
    applied.value.expiring ||
    applied.value.status !== 'ACTIVE',
);

// --- 列表状态 ---
const items = ref<Item[]>([]);
const loading = ref(false);
const finished = ref(false);
const refreshing = ref(false);
const page = ref(-1);

// --- 名称映射（ItemResponse 只携带 id，分类/位置名称需前端解析） ---
const categories = ref<Category[]>([]);
const flatLocations = ref<FlatLocation[]>([]);

async function fetchPage(p: number) {
  loading.value = true;
  try {
    const f = applied.value;
    const data = await itemsApi.searchItems({
      keyword: keyword.value,
      categoryId: f.categoryId === '' ? null : f.categoryId,
      locationId: f.locationId === '' ? null : f.locationId,
      status: f.status,
      expiring: f.expiring,
      page: p,
      size: PAGE_SIZE,
    });
    const content = data.content ?? [];
    items.value = p === 0 ? content : [...items.value, ...content];
    page.value = p;
    finished.value = content.length === 0 || data.totalPages <= p + 1;
  } catch {
    showToast('物品加载失败，请稍后重试');
    if (p === 0) items.value = [];
  } finally {
    loading.value = false;
  }
}

/** List 滚动到底触发：加载下一页。 */
function onLoad() {
  if (refreshing.value) return;
  void fetchPage(page.value + 1);
}

/** 搜索/筛选变化：重置并加载第一页。 */
async function reload() {
  finished.value = false;
  items.value = [];
  page.value = -1;
  await fetchPage(0);
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

function onSearch() {
  void reload();
}

function applyFilter() {
  applied.value = { ...draft.value };
  showFilter.value = false;
  void reload();
}

function resetDraft() {
  draft.value = { ...defaultFilter };
}

// --- 展示辅助 ---
function categoryName(id: number): string {
  return categories.value.find((c) => c.id === id)?.name ?? '';
}

function locationPath(id: number): string {
  return flatLocations.value.find((l) => l.id === id)?.path ?? '';
}

function goDetail(item: Item) {
  void router.push(`/items/${item.id}`);
}

function goCreate() {
  void router.push('/items/new');
}

onMounted(async () => {
  const [cats, tree, threshold] = await Promise.all([
    categoriesApi.list().catch(() => [] as Category[]),
    locationsApi.getTree().catch(() => []),
    settingsApi.get().then((s) => s.expiry_remind_days).catch(() => null),
  ]);
  categories.value = cats;
  flatLocations.value = flattenLocations(tree);
  if (threshold != null) remindDays.value = threshold;
  void reload();
});
</script>

<template>
  <div class="items-page">
    <div class="items-toolbar">
      <van-search v-model="keyword" shape="round" placeholder="搜索物品名称" @search="onSearch" @clear="onSearch" />
      <div class="toolbar-actions">
        <van-badge :dot="filterActive">
          <van-button size="small" type="primary" plain @click="showFilter = true">
            <template #icon><PaperIcon name="filter" :size="14" /></template>
            筛选
          </van-button>
        </van-badge>
        <van-button size="small" type="primary" class="paper-sticker-btn" @click="goCreate">
          <template #icon><PaperIcon name="plus" :size="14" /></template>
          新增
        </van-button>
      </div>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        :immediate-check="false"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <div v-if="items.length" class="item-list">
          <div
            v-for="(item, idx) in items"
            :key="item.id"
            class="item-card paper-card paper-drop"
            :class="idx % 2 ? 'paper-card--tilt-b' : 'paper-card--tilt-a'"
            @click="goDetail(item)"
          >
            <div class="item-card__main">
              <div class="item-card__name">{{ item.name }}</div>
              <div class="item-card__meta">
                <span v-if="categoryName(item.categoryId)" class="paper-chip">{{
                  categoryName(item.categoryId)
                }}</span>
                <span v-if="locationPath(item.locationId)" class="paper-chip paper-chip--moss">{{
                  locationPath(item.locationId)
                }}</span>
              </div>
              <div v-if="item.remark" class="item-card__remark">{{ item.remark }}</div>
            </div>
            <div class="item-card__side">
              <div class="item-card__qty">
                {{ item.quantity }}<span v-if="item.unit" class="item-card__unit">{{ item.unit }}</span>
              </div>
              <div
                v-if="expiryLevel(item.expiryDate, remindDays) === 'red'"
                class="item-card__expiry item-card__expiry--red"
              >
                临期 {{ item.expiryDate }}
              </div>
              <div v-else-if="item.expiryDate" class="item-card__expiry">到期 {{ item.expiryDate }}</div>
            </div>
          </div>
        </div>
        <van-empty v-else-if="!loading && finished" description="没有找到物品" image-size="80" />
      </van-list>
    </van-pull-refresh>

    <van-popup v-model:show="showFilter" position="bottom" round>
      <div class="filter-panel">
        <div class="filter-panel__title">筛选物品</div>
        <div class="filter-panel__body">
          <div class="filter-group">
            <div class="filter-group__label">分类</div>
            <van-radio-group v-model="draft.categoryId" class="filter-options">
              <van-radio :name="''">全部分类</van-radio>
              <van-radio v-for="c in categories" :key="c.id" :name="c.id">{{ c.name }}</van-radio>
            </van-radio-group>
          </div>

          <div class="filter-group">
            <div class="filter-group__label">位置</div>
            <van-radio-group v-model="draft.locationId" class="filter-options">
              <van-radio :name="''">全部位置</van-radio>
              <van-radio
                v-for="loc in flatLocations"
                :key="loc.id"
                :name="loc.id"
                class="filter-option--indent"
                :style="{ paddingLeft: `${16 + loc.depth * 14}px` }"
              >
                {{ loc.path }}
              </van-radio>
            </van-radio-group>
          </div>

          <van-cell :title="`仅看 ${remindDays} 天内临期`" :border="false">
            <template #right-icon>
              <van-switch v-model="draft.expiring" size="22" />
            </template>
          </van-cell>

          <div class="filter-group">
            <div class="filter-group__label">状态</div>
            <van-radio-group v-model="draft.status" class="filter-options filter-options--row">
              <van-radio name="ACTIVE">在库</van-radio>
              <van-radio name="ARCHIVED">已归档</van-radio>
            </van-radio-group>
          </div>
        </div>
        <div class="filter-panel__footer">
          <van-button size="small" plain type="default" @click="resetDraft">重置</van-button>
          <van-button size="small" type="primary" @click="applyFilter">确定</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.items-page {
  padding-bottom: 12px;
}

.items-toolbar {
  display: flex;
  align-items: center;
  padding: 8px 12px 0;
  background: var(--paper);
}

.items-toolbar .van-search {
  flex: 1;
  padding-left: 0;
}

.toolbar-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  align-items: center;
  padding-bottom: 8px;
}

.item-list {
  padding: 0 12px 12px;
}

.item-card {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  padding: 12px;
  cursor: pointer;
}

.item-card__main {
  min-width: 0;
}

.item-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}

.item-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.item-card__remark {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-card__side {
  flex: 0 0 auto;
  text-align: right;
}

.item-card__qty {
  font-size: 18px;
  font-weight: 600;
  color: var(--ink);
}

.item-card__unit {
  margin-left: 2px;
  font-size: 12px;
  font-weight: 400;
  color: var(--ink-2);
}

.item-card__expiry {
  margin-top: 4px;
  font-size: 12px;
}

.item-card__expiry--red {
  color: var(--terracotta-ink);
  font-weight: 600;
}

.filter-panel {
  background: var(--card);
}

.filter-panel__title {
  padding: 16px 16px 4px;
  font-family: var(--fs-font-display);
  font-size: 16px;
  font-weight: 700;
  text-align: center;
  color: var(--ink);
}

.filter-panel__body {
  max-height: 56vh;
  overflow-y: auto;
  padding: 4px 0 8px;
}

.filter-group {
  padding: 8px 16px;
}

.filter-group__label {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--ink-2);
}

.filter-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-options--row {
  flex-direction: row;
}

.filter-option--indent {
  padding-left: 16px;
}

.filter-panel__footer {
  display: flex;
  gap: 12px;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
}

.filter-panel__footer .van-button {
  flex: 1;
}
</style>