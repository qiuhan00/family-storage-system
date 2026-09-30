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

      <div class="detail-head">
        <div class="detail-head__name">{{ item.name }}</div>
        <div
          v-if="expiryLevel(item.expiryDate, remindDays) === 'red'"
          class="detail-head__expiry detail-head__expiry--red"
        >
          临期 {{ item.expiryDate }}
        </div>
        <div v-else-if="item.expiryDate" class="detail-head__expiry">到期 {{ item.expiryDate }}</div>
      </div>

      <van-cell-group inset>
        <van-cell title="数量" :value="`${item.quantity}${item.unit ?? ''}`" />
        <van-cell title="分类" :value="categoryName(item.categoryId)" />
        <van-cell title="位置" :value="locationPath(item.locationId)" />
        <van-cell
          title="单价"
          :value="item.purchasePrice != null ? `¥ ${item.purchasePrice}` : '—'"
        />
        <van-cell title="购买日期" :value="item.purchaseDate ?? '—'" />
        <van-cell title="状态" :value="statusLabel[item.status] ?? item.status" />
        <van-cell v-if="item.remark" title="备注" :value="item.remark" />
      </van-cell-group>

      <div class="detail-section">
        <h3 class="detail-section__title">库存变更</h3>
        <van-cell-group inset>
          <van-empty v-if="history.length === 0" description="暂无出入库记录" image-size="60" />
          <van-cell v-for="ch in history" v-else :key="ch.id">
            <template #title>
              <span class="history-type">{{ stockTypeLabel(ch.type) }} · {{ fmtTime(ch.createdAt) }}</span>
              <span v-if="ch.note" class="history-note">{{ ch.note }}</span>
            </template>
            <template #value>
              <span :class="ch.changeQty >= 0 ? 'history-qty--in' : 'history-qty--out'">
                {{ ch.changeQty >= 0 ? '+' : '' }}{{ ch.changeQty }}
              </span>
            </template>
          </van-cell>
        </van-cell-group>
      </div>

      <div class="detail-actions">
        <van-button type="primary" block round @click="showActionSheet = true">出入库</van-button>
        <div class="detail-actions__row">
          <van-button icon="edit" plain type="primary" block @click="goEdit">编辑</van-button>
          <van-button icon="delete-o" plain type="danger" block @click="onDelete">删除</van-button>
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
  margin: 0 16px 8px;
}

.detail-cover__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  border-radius: 8px;
  background: var(--fs-surface);
  color: var(--fs-text-3);
  font-size: 13px;
}

.detail-head {
  padding: 8px 16px 12px;
}

.detail-head__name {
  font-size: 18px;
  font-weight: 600;
  color: var(--fs-text);
}

.detail-head__expiry {
  margin-top: 6px;
  font-size: 13px;
}

.detail-head__expiry--red {
  color: var(--fs-danger);
  font-weight: 600;
}

.detail-section {
  margin-top: 12px;
}

.detail-section__title {
  margin: 0;
  padding: 0 16px 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--fs-text);
}

.history-type {
  font-size: 14px;
  color: var(--fs-text);
}

.history-note {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--fs-text-3);
}

.history-qty--in {
  color: var(--fs-success);
  font-weight: 600;
}

.history-qty--out {
  color: var(--fs-danger);
  font-weight: 600;
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
  color: var(--fs-text-3);
}
</style>