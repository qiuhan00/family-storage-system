<!-- src/views/ItemDetailView.vue —— 物品详情：照片大图预览、基本信息、出入库（Stepper+ActionSheet+Dialog）、
     库存变更历史（createdAt 倒序、中文类型标签）、编辑/删除入口。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { showConfirmDialog } from 'vant/es/dialog';
import 'vant/es/dialog/style';
import { showImagePreview } from 'vant/es/image-preview';
import 'vant/es/image-preview/style';
import { apiErrorMessage } from '../api/http';
import { itemsApi } from '../api/items';
import { categoriesApi } from '../api/categories';
import { locationsApi, flattenLocations } from '../api/locations';
import { settingsApi } from '../api/settings';
import { expiryLevel } from '../utils/expiry';
import PaperIcon from '../components/PaperIcon.vue';
import type { FlatLocation } from '../api/locations';
import type { Category, Item, StockChange } from '../types';

const route = useRoute();
const router = useRouter();

const itemId = computed(() => Number(route.params.id));

/** 临期阈值（天）：GET /api/settings 的 expiry_remind_days，驱动详情角标（F2）。 */
const remindDays = ref(7);

const item = ref<Item | null>(null);
const history = ref<StockChange[]>([]);
const loading = ref(true);

const categories = ref<Category[]>([]);
const flatLocations = ref<FlatLocation[]>([]);

function categoryName(id: number): string {
  return categories.value.find((c) => c.id === id)?.name ?? '';
}

function locationPath(id: number): string {
  return flatLocations.value.find((l) => l.id === id)?.path ?? '';
}

/** 位置展示：顶层路径 + 具体位置（如「厨房 · 冰箱上层」）。 */
function locationDisplay(id: number, detail: string | null): string {
  const path = locationPath(id);
  return detail ? `${path} · ${detail}` : path;
}

const statusLabel: Record<string, string> = { ACTIVE: '在库', ARCHIVED: '已归档' };

async function loadAll() {
  loading.value = true;
  try {
    const [it, hist] = await Promise.all([
      itemsApi.getItem(itemId.value),
      itemsApi.stockHistory(itemId.value),
    ]);
    item.value = it;
    history.value = hist;
  } catch (e) {
    showToast(apiErrorMessage(e, '物品不存在'));
  } finally {
    loading.value = false;
  }
}

function previewPhoto() {
  if (item.value?.photoUrl) {
    showImagePreview([item.value.photoUrl]);
  }
}

function fmtTime(value: string): string {
  return dayjs(value).format('YYYY-MM-DD HH:mm');
}

function stockTypeLabel(type: StockChange['type']): string {
  const labels: Record<StockChange['type'], string> = {
    INITIAL: '初始',
    IN: '入库',
    OUT: '出库',
    ADJUST: '盘点',
  };
  return labels[type] ?? type;
}

function goEdit() {
  void router.push(`/items/${itemId.value}/edit`);
}

async function onDelete() {
  try {
    await showConfirmDialog({ title: '删除物品', message: '确定删除该物品？其出入库记录将一并删除。' });
  } catch {
    return;
  }
  try {
    await itemsApi.deleteItem(itemId.value);
    showToast('已删除');
    void router.replace('/items');
  } catch (e) {
    showToast(apiErrorMessage(e));
  }
}

// --- 出入库操作：ActionSheet 选类型 → Dialog 输入数量/备注 → adjustStock ---
const showActionSheet = ref(false);
const stockType = ref<'IN' | 'OUT' | 'ADJUST'>('IN');
const showStockDialog = ref(false);
const stockDelta = ref(1);
const stockNote = ref('');
const submitting = ref(false);

const stockActions = computed(() => [
  { name: '入库', value: 'IN' as const },
  {
    name: '出库',
    value: 'OUT' as const,
    disabled: (item.value?.quantity ?? 0) <= 0,
  },
  { name: '盘点调整', value: 'ADJUST' as const },
]);

const stockDialogTitle = computed(() => {
  const titles: Record<string, string> = { IN: '入库', OUT: '出库', ADJUST: '盘点调整' };
  return titles[stockType.value];
});

const stockMin = computed(() => (stockType.value === 'ADJUST' ? -999999 : 1));
const stockMax = computed(() =>
  stockType.value === 'OUT' ? (item.value?.quantity ?? 0) : 999999,
);

const stockHint = computed(() => {
  const qty = `${item.value?.quantity ?? 0}${item.value?.unit ?? ''}`;
  if (stockType.value === 'IN') return `增加库存（当前 ${qty}）`;
  if (stockType.value === 'OUT') return `减少库存，不超过当前存量（当前 ${qty}）`;
  return '正数入库 / 负数出库（盘点校准）';
});

function onStockActionSelect(action: { value?: string | number }) {
  if (action.value === 'OUT' || action.value === 'ADJUST') {
    stockType.value = action.value;
  } else {
    stockType.value = 'IN';
  }
  stockDelta.value = 1;
  stockNote.value = '';
  showActionSheet.value = false;
  showStockDialog.value = true;
}

/** Dialog 确认时提交出入库；失败返回 false 保持弹窗不关闭并提示后端错误。 */
async function onStockDialogBeforeClose(action: string): Promise<boolean> {
  if (action !== 'confirm' || submitting.value) return action !== 'confirm';
  submitting.value = true;
  try {
    const type = stockType.value;
    const deltaQty = type === 'ADJUST' ? stockDelta.value : type === 'OUT' ? -stockDelta.value : stockDelta.value;
    await itemsApi.adjustStock(itemId.value, {
      deltaQty,
      type,
      note: stockNote.value.trim() || null,
    });
    showToast('已更新库存');
    await loadAll();
    return true;
  } catch (e) {
    showToast(apiErrorMessage(e));
    return false;
  } finally {
    submitting.value = false;
  }
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
  await loadAll();
});

watch(itemId, (v) => {
  if (Number.isFinite(v) && v > 0) void loadAll();
});
</script>

<template>
  <div class="detail-page">
    <div v-if="loading" class="detail-loading">
      <van-loading size="24">加载中…</van-loading>
    </div>

    <template v-else-if="item">
      <div class="detail-cover">
        <van-image
          v-if="item.photoUrl"
          :src="item.photoUrl"
          width="100%"
          height="200px"
          fit="cover"
          radius="8"
          @click="previewPhoto"
        />
        <div v-else class="detail-cover__empty">暂无照片</div>
      </div>

      <div class="detail-head paper-note">
        <div class="detail-head__name">{{ item.name }}</div>
        <div
          v-if="expiryLevel(item.expiryDate, remindDays) === 'red'"
          class="detail-head__expiry detail-head__expiry--red"
        >
          临期 {{ item.expiryDate }}
        </div>
        <div v-else-if="item.expiryDate" class="detail-head__expiry">到期 {{ item.expiryDate }}</div>
      </div>

      <div class="detail-fields paper-card">
        <div class="detail-field">
          <span class="detail-field__label">数量</span>
          <span class="detail-field__value">{{ item.quantity }}{{ item.unit ?? '' }}</span>
        </div>
        <div class="detail-field">
          <span class="detail-field__label">单价</span>
          <span class="detail-field__value">{{
            item.purchasePrice != null ? `¥ ${item.purchasePrice}` : '—'
          }}</span>
        </div>
        <div class="detail-field">
          <span class="detail-field__label">分类</span>
          <span class="detail-field__value">{{ categoryName(item.categoryId) }}</span>
        </div>
        <div class="detail-field">
          <span class="detail-field__label">位置</span>
          <span class="detail-field__value">{{ locationDisplay(item.locationId, item.locationDetail) }}</span>
        </div>
        <div class="detail-field">
          <span class="detail-field__label">购买日期</span>
          <span class="detail-field__value">{{ item.purchaseDate ?? '—' }}</span>
        </div>
        <div class="detail-field">
          <span class="detail-field__label">状态</span>
          <span class="detail-field__value">{{ statusLabel[item.status] ?? item.status }}</span>
        </div>
        <div v-if="item.remark" class="detail-field detail-field--full">
          <span class="detail-field__label">备注</span>
          <span class="detail-field__value">{{ item.remark }}</span>
        </div>
        <div v-if="item.lowStockThreshold != null" class="detail-field detail-field--full">
          <span class="detail-field__label">低库存提醒</span>
          <span class="detail-field__value">低于 {{ item.lowStockThreshold }} 时提醒</span>
        </div>
      </div>

      <div class="detail-section">
        <h3 class="detail-section__title paper-section-title">库存变更</h3>
        <van-empty v-if="history.length === 0" description="暂无出入库记录" image-size="60" />
        <div v-else class="history-list">
          <div
            v-for="ch in history"
            :key="ch.id"
            class="history-slip"
            :class="
              ch.type === 'OUT'
                ? 'history-slip--out'
                : ch.type === 'INITIAL'
                  ? 'history-slip--initial'
                  : 'history-slip--in'
            "
          >
            <div class="history-slip__main">
              <span class="history-slip__type">{{ stockTypeLabel(ch.type) }} · {{ fmtTime(ch.createdAt) }}</span>
              <span v-if="ch.note" class="history-slip__note">{{ ch.note }}</span>
            </div>
            <span class="history-slip__qty">{{ ch.changeQty >= 0 ? '+' : '' }}{{ ch.changeQty }}</span>
          </div>
        </div>
      </div>

      <div class="detail-actions">
        <van-button type="primary" block round @click="showActionSheet = true">出入库</van-button>
        <div class="detail-actions__row">
          <van-button plain type="primary" block @click="goEdit">
            <template #icon><PaperIcon name="edit" :size="16" /></template>
            编辑
          </van-button>
          <van-button plain type="danger" block @click="onDelete">
            <template #icon><PaperIcon name="trash" :size="16" /></template>
            删除
          </van-button>
        </div>
      </div>
    </template>
  </div>

  <van-action-sheet
    v-model:show="showActionSheet"
    :actions="stockActions"
    cancel-text="取消"
    description="选择出入库类型"
    @select="onStockActionSelect"
  />

  <van-dialog
    v-model:show="showStockDialog"
    :title="stockDialogTitle"
    show-cancel-button
    :before-close="onStockDialogBeforeClose"
  >
    <div class="stock-dialog">
      <div class="stock-dialog__hint">{{ stockHint }}</div>
      <van-stepper v-model="stockDelta" :min="stockMin" :max="stockMax" integer />
      <van-field v-model="stockNote" label="备注" placeholder="可选" :maxlength="200" />
    </div>
  </van-dialog>
</template>

<style scoped>
.detail-page {
  padding: 12px 0 24px;
}

.detail-loading {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.detail-cover {
  margin: 0 16px 10px;
}

.detail-cover :deep(.van-image) {
  overflow: hidden;
  border-radius: 3px;
}

.detail-cover__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--ink-2);
  font-size: 13px;
  background: var(--card);
  border: 1px dashed var(--dashed);
  border-radius: 3px;
}

.detail-head {
  margin: 0 16px 12px;
  padding: 16px;
  transform: rotate(-0.4deg);
}

.detail-head__name {
  font-family: var(--fs-font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
}

.detail-head__expiry {
  display: inline-block;
  margin-top: 8px;
  padding: 1px 8px;
  font-size: 11px;
  color: var(--ink-2);
  border: 1px solid var(--dashed);
  border-radius: 10px;
  transform: rotate(2deg);
}

.detail-head__expiry--red {
  color: var(--terracotta-ink);
  border-color: rgba(194, 96, 60, 0.5);
  font-weight: 600;
}

.detail-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 0 16px 12px;
  padding: 14px;
}

.detail-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.detail-field--full {
  grid-column: 1 / -1;
}

.detail-field__label {
  font-size: 10px;
  color: var(--ink-2);
}

.detail-field__value {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  word-break: break-word;
}

.detail-section {
  margin-top: 12px;
}

.detail-section__title {
  margin: 0;
  padding: 0 16px 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink);
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 16px;
}

.history-slip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  font-size: 12px;
  background: var(--card);
  border-left: 3px solid var(--dashed);
  box-shadow: 1px 1px 0 rgba(43, 38, 34, 0.07);
}

.history-slip--in {
  border-left-color: var(--moss);
}

.history-slip--initial {
  border-left-color: var(--amber);
}

.history-slip--out {
  border-left-color: var(--danger);
}

.history-slip__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.history-slip__type {
  color: var(--ink);
}

.history-slip__note {
  font-size: 11px;
  color: var(--ink-2);
}

.history-slip__qty {
  font-family: var(--fs-font-display);
  font-weight: 700;
  color: var(--ink);
}

.history-slip--in .history-slip__qty {
  color: var(--moss);
}

.history-slip--out .history-slip__qty {
  color: var(--danger);
}

.detail-actions {
  padding: 16px;
}

.detail-actions__row {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}

.stock-dialog {
  padding: 8px 16px 4px;
}

.stock-dialog__hint {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--ink-2);
}
</style>