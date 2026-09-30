<!-- src/views/HomeView.vue —— 首页：统计总览 + 临期物品 + 分类/位置维度折叠统计。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { statisticsApi } from '../api/statistics';
import { itemsApi } from '../api/items';
import { settingsApi } from '../api/settings';
import type { Item } from '../types';
import type { ItemCountByDimension, Summary } from '../types';

const router = useRouter();

/** 临期阈值（天）：来自 GET /api/settings 的 expiry_remind_days，失败退默认 7——与统计卡同一口径。 */
const remindDays = ref(7);

const summary = ref<Summary | null>(null);
const expiringItems = ref<Item[]>([]);
const byCategory = ref<ItemCountByDimension[]>([]);
const byLocation = ref<ItemCountByDimension[]>([]);
const loadingSummary = ref(true);
const loadingExpiring = ref(true);
const loadingDims = ref(true);
const activeDim = ref<string[]>(['category']);

const statCards = computed(() => {
  const s = summary.value;
  if (!s) return [];
  return [
    { label: '物品总数', value: String(s.totalItems), suffix: '件' },
    { label: '在库总量', value: String(s.totalQuantity), suffix: '' },
    { label: '总价值', value: fmtMoney(s.totalValue), suffix: '元' },
    { label: '临期提醒', value: String(s.expiringSoon), suffix: '项' },
    { label: '待购清单', value: String(s.pendingShopping), suffix: '项' },
  ];
});

function fmtMoney(n: number): string {
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

async function loadSummary() {
  loadingSummary.value = true;
  try {
    summary.value = await statisticsApi.summary();
  } catch {
    showToast('统计加载失败，请稍后重试');
  } finally {
    loadingSummary.value = false;
  }
}

async function loadExpiring() {
  loadingExpiring.value = true;
  try {
    const s = await settingsApi.get().catch(() => null);
    if (s) remindDays.value = s.expiry_remind_days;
    expiringItems.value = await itemsApi.expiring(remindDays.value);
  } catch {
    showToast('临期物品加载失败，请稍后重试');
  } finally {
    loadingExpiring.value = false;
  }
}

async function loadDimensions() {
  loadingDims.value = true;
  try {
    const [category, location] = await Promise.all([
      statisticsApi.byCategory(),
      statisticsApi.byLocation(),
    ]);
    byCategory.value = category;
    byLocation.value = location;
  } catch {
    showToast('维度统计加载失败，请稍后重试');
  } finally {
    loadingDims.value = false;
  }
}

/** 临期物品点击：跳转物品详情页。 */
function onItemTap(item: Item) {
  void router.push(`/items/${item.id}`);
}

onMounted(() => {
  void loadSummary();
  void loadExpiring();
  void loadDimensions();
});
</script>

<template>
  <div class="home-page">
    <!-- 统计总览 -->
    <section class="home-section">
      <h2 class="section-title">统计总览</h2>
      <div v-if="loadingSummary" class="block-loading">
        <van-loading size="24">加载中…</van-loading>
      </div>
      <div v-else-if="summary" class="stat-grid">
        <div
          v-for="card in statCards"
          :key="card.label"
          class="stat-card"
          :class="{ 'stat-card--warning': card.label === '临期提醒' && summary!.expiringSoon > 0 }"
        >
          <div class="stat-card__value">{{ card.value }}<span class="stat-card__suffix">{{ card.suffix }}</span></div>
          <div class="stat-card__label">{{ card.label }}</div>
        </div>
      </div>
      <van-empty v-else description="暂无统计数据" image-size="60" />
    </section>

    <!-- 临期物品 -->
    <section class="home-section">
      <h2 class="section-title">临期提醒（{{ remindDays }} 天内）</h2>
      <div v-if="loadingExpiring" class="block-loading">
        <van-loading size="24">加载中…</van-loading>
      </div>
      <van-empty v-else-if="expiringItems.length === 0" description="暂无临期物品" image-size="60" />
      <van-cell-group v-else inset>
        <van-cell
          v-for="item in expiringItems"
          :key="item.id"
          :title="item.name"
          :label="`临期 ${item.expiryDate} · 余量 ${item.quantity}${item.unit}`"
          is-link
          @click="onItemTap(item)"
        />
      </van-cell-group>
    </section>

    <!-- 分类 / 位置维度 -->
    <section class="home-section">
      <h2 class="section-title">维度统计</h2>
      <div v-if="loadingDims" class="block-loading">
        <van-loading size="24">加载中…</van-loading>
      </div>
      <van-collapse v-else v-model="activeDim" class="dim-collapse">
        <van-collapse-item title="按分类统计" name="category">
          <van-cell-group inset>
            <van-cell
              v-for="row in byCategory"
              :key="row.dimensionId"
              :title="row.dimensionName"
              :label="`${row.itemCount} 件 · 总量 ${row.totalQuantity} · 价值 ${fmtMoney(row.totalValue)} 元`"
            />
            <van-empty v-if="byCategory.length === 0" description="暂无分类数据" image-size="60" />
          </van-cell-group>
        </van-collapse-item>
        <van-collapse-item title="按位置统计" name="location">
          <van-cell-group inset>
            <van-cell
              v-for="row in byLocation"
              :key="row.dimensionId"
              :title="row.dimensionName"
              :label="`${row.itemCount} 件 · 总量 ${row.totalQuantity} · 价值 ${fmtMoney(row.totalValue)} 元`"
            />
            <van-empty v-if="byLocation.length === 0" description="暂无位置数据" image-size="60" />
          </van-cell-group>
        </van-collapse-item>
      </van-collapse>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  padding: 12px 0 24px;
}

.home-section {
  margin-bottom: 8px;
  padding: 4px 0;
  background: var(--fs-surface);
}

.section-title {
  margin: 0;
  padding: 12px 16px 4px;
  font-size: 16px;
  font-weight: 600;
  color: var(--fs-text);
}

.block-loading {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  padding: 8px 16px 16px;
}

.stat-card {
  border: 1px solid var(--fs-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--fs-surface-soft);
}

.stat-card--warning {
  border-color: var(--fs-warning);
  background: var(--fs-warning-soft);
}

.stat-card__value {
  font-size: 22px;
  font-weight: 600;
  color: var(--fs-text);
}

.stat-card__suffix {
  margin-left: 2px;
  font-size: 12px;
  font-weight: 400;
  color: var(--fs-text-3);
}

.stat-card__label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--fs-text-3);
}

.dim-collapse {
  border-radius: 8px;
  overflow: hidden;
}
</style>