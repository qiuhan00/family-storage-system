<!-- src/views/ShoppingView.vue —— 购物清单：Tab 过滤（全部/待购/已购/已取消）+ PullRefresh 下拉刷新
     + SwipeCell 行内编辑/删除（契约无 DELETE，删除=置 CANCELLED，可恢复）+ Checkbox 勾选置 BOUGHT（写 completedAt）
     + 底部弹层新增/编辑缺耗品（名称/数量/单位/分类可选/备注），表单 van-form 客户端校验。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { showConfirmDialog } from 'vant/es/dialog';
import 'vant/es/dialog/style';
import type { FieldRule } from 'vant';
import { apiErrorMessage } from '../api/http';
import { shoppingApi, type ShoppingPayload } from '../api/shopping';
import { categoriesApi } from '../api/categories';
import { toggleBought } from '../utils/shopping';
import type { Category, ShoppingItem, ShoppingStatus } from '../types';

/** Tab 取值：'all' 拉全部，其余映射为 spec §5.5 的 status 过滤参数。 */
type TabFilter = 'all' | ShoppingStatus;

const tabs: Array<{ name: TabFilter; title: string }> = [
  { name: 'all', title: '全部' },
  { name: 'PENDING', title: '待购' },
  { name: 'BOUGHT', title: '已购' },
  { name: 'CANCELLED', title: '已取消' },
];

const activeTab = ref<TabFilter>('all');

// --- 列表状态 ---
const rows = ref<ShoppingItem[]>([]);
const loading = ref(false);
const refreshing = ref(false);
const loadError = ref(false);
const loadSeq = ref(0);

async function load() {
  const seq = ++loadSeq.value;
  loading.value = true;
  loadError.value = false;
  try {
    const data = await shoppingApi.list(activeTab.value === 'all' ? null : activeTab.value);
    if (seq !== loadSeq.value) return;
    rows.value = data;
  } catch {
    if (seq !== loadSeq.value) return;
    loadError.value = true;
    if (rows.value.length === 0) showToast('购物清单加载失败，请稍后重试');
  } finally {
    if (seq === loadSeq.value) loading.value = false;
  }
}

watch(activeTab, () => void load());

async function onRefresh() {
  refreshing.value = true;
  try {
    await load();
  } finally {
    refreshing.value = false;
  }
}

function retry() {
  void load();
}

// --- 分类（新增/编辑表单的可选分类） ---
const categories = ref<Category[]>([]);
// 「不选分类」用哨兵值 0（分类 id 从 1 起；Picker 的 value 不接受 null）。
const NO_CATEGORY = 0;
const categoryColumns = computed(() => [
  { text: '不选分类', value: NO_CATEGORY },
  ...categories.value.map((c) => ({ text: c.name, value: c.id })),
]);

function categoryName(id: number | null): string {
  if (id == null) return '';
  return categories.value.find((c) => c.id === id)?.name ?? '';
}

// --- 勾选切换（PENDING ↔ BOUGHT）：乐观更新 + 失败回滚；成功后按当前 Tab 过滤行 ---
async function onToggle(row: ShoppingItem) {
  if (row.status === 'CANCELLED') return;
  toggleBought(row);
  try {
    const updated = await shoppingApi.setStatus(row.id, row.status);
    const idx = rows.value.findIndex((r) => r.id === row.id);
    if (idx >= 0) rows.value[idx] = updated;
    // 当前 Tab 与新状态不符的行移出列表（如「待购」下勾选后离开该 Tab）
    if (activeTab.value !== 'all' && updated.status !== activeTab.value) {
      rows.value = rows.value.filter((r) => r.id !== row.id);
    }
  } catch (e) {
    toggleBought(row);
    showToast(apiErrorMessage(e, '状态更新失败'));
  }
}

// --- 删除（契约无 DELETE：置 CANCELLED 软移除，可在「已取消」恢复） ---
async function onRemove(row: ShoppingItem) {
  try {
    await showConfirmDialog({
      title: '删除缺耗品',
      message: `确定删除「${row.name}」？该项将标记为已取消（可在「已取消」标签恢复）。`,
    });
  } catch {
    return; // 用户取消
  }
  try {
    const updated = await shoppingApi.setStatus(row.id, 'CANCELLED');
    if (activeTab.value === 'CANCELLED') {
      const idx = rows.value.findIndex((r) => r.id === row.id);
      if (idx >= 0) rows.value[idx] = updated;
    } else {
      rows.value = rows.value.filter((r) => r.id !== row.id);
    }
    showToast('已删除（标记为已取消）');
  } catch (e) {
    showToast(apiErrorMessage(e, '删除失败'));
  }
}

/** 已取消的行恢复为待购（completedAt 由后端清空）。 */
async function onRestore(row: ShoppingItem) {
  try {
    const updated = await shoppingApi.setStatus(row.id, 'PENDING');
    if (activeTab.value === 'PENDING') {
      const idx = rows.value.findIndex((r) => r.id === row.id);
      if (idx >= 0) rows.value[idx] = updated;
    } else {
      rows.value = rows.value.filter((r) => r.id !== row.id);
    }
    showToast('已恢复待购');
  } catch (e) {
    showToast(apiErrorMessage(e, '恢复失败'));
  }
}

// --- 新增 / 编辑表单 ---
const showForm = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);

const form = ref({ name: '', quantity: '1', unit: '', categoryId: null as number | null, remark: '' });

const showCategoryPicker = ref(false);

function onCategoryConfirm(params: { selectedOptions: Array<{ text?: unknown; value?: unknown }> }) {
  const opt = params.selectedOptions[0];
  form.value.categoryId = opt && typeof opt.value === 'number' && opt.value > NO_CATEGORY ? opt.value : null;
  showCategoryPicker.value = false;
}

const nameRule: FieldRule = { required: true, message: '请填写名称' };
const quantityRule: FieldRule = {
  required: true,
  message: '请填写数量',
  validator: (v: string) => (/^[1-9]\d*$/.test(v.trim()) ? true : '数量必须为正整数'),
};

function openCreate() {
  editingId.value = null;
  form.value = { name: '', quantity: '1', unit: '', categoryId: null, remark: '' };
  showForm.value = true;
}

function openEdit(row: ShoppingItem) {
  editingId.value = row.id;
  form.value = {
    name: row.name,
    quantity: String(row.quantity),
    unit: row.unit ?? '',
    categoryId: row.categoryId,
    remark: row.remark ?? '',
  };
  showForm.value = true;
}

function onCancelForm() {
  showForm.value = false;
}

async function onSubmit() {
  if (submitting.value) return;
  submitting.value = true;
  const payload: ShoppingPayload = {
    name: form.value.name.trim(),
    quantity: Number(form.value.quantity),
    unit: form.value.unit.trim() || null,
    categoryId: form.value.categoryId,
    remark: form.value.remark.trim() || null,
  };
  try {
    if (editingId.value != null) {
      await shoppingApi.update(editingId.value, payload);
      showToast('已保存');
    } else {
      await shoppingApi.create(payload);
      showToast('已新增');
    }
    showForm.value = false;
    await load();
  } catch (e) {
    showToast(apiErrorMessage(e));
  } finally {
    submitting.value = false;
  }
}

// --- 展示辅助 ---
const statusLabel: Record<ShoppingStatus, string> = {
  PENDING: '待购',
  BOUGHT: '已购',
  CANCELLED: '已取消',
};

function rowMeta(row: ShoppingItem): string {
  const parts = [`${row.quantity}${row.unit ?? ''}`];
  const cat = categoryName(row.categoryId);
  if (cat) parts.push(cat);
  if (row.remark) parts.push(row.remark);
  return parts.join(' · ');
}

onMounted(async () => {
  categories.value = await categoriesApi.list().catch(() => [] as Category[]);
  void load();
});
</script>

<template>
  <div class="shopping-page">
    <div class="shopping-toolbar">
      <van-tabs v-model:active="activeTab" shrink color="var(--ink)" class="shopping-tabs">
        <van-tab v-for="t in tabs" :key="t.name" :name="t.name" :title="t.title" />
      </van-tabs>
      <van-button size="small" type="primary" class="add-btn paper-sticker-btn" @click="openCreate">
        新增
      </van-button>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <!-- 加载态 -->
      <div v-if="loading && rows.length === 0" class="state-block">
        <van-loading size="24">加载中…</van-loading>
      </div>

      <!-- 错误态 -->
      <div v-else-if="loadError && rows.length === 0" class="state-block">
        <van-empty description="加载失败，请稍后重试" image-size="80" />
        <van-button size="small" type="primary" plain @click="retry">重试</van-button>
      </div>

      <!-- 空态 -->
      <van-empty
        v-else-if="!loading && rows.length === 0"
        description="没有符合条件的缺耗品"
        image-size="80"
      />

      <!-- 列表 -->
      <div v-else class="shopping-list">
        <van-swipe-cell v-for="row in rows" :key="row.id">
          <div class="shop-row paper-card">
            <van-checkbox
              :model-value="row.status === 'BOUGHT'"
              shape="round"
              checked-color="var(--moss)"
              :disabled="row.status === 'CANCELLED'"
              aria-label="标记已购"
              @update:model-value="() => onToggle(row)"
            />
            <div class="shop-row__main" @click="openEdit(row)">
              <div
                class="shop-row__name"
                :class="{ 'shop-row__name--done': row.status === 'BOUGHT' }"
              >
                {{ row.name }}
              </div>
              <div class="shop-row__meta">{{ rowMeta(row) }}</div>
            </div>
            <span
              v-if="activeTab === 'all'"
              class="shop-row__tag paper-chip"
              :class="'shop-tag--' + row.status.toLowerCase()"
            >
              {{ statusLabel[row.status] }}
            </span>
          </div>

          <template #right>
            <van-button
              square
              type="primary"
              text="编辑"
              class="swipe-btn"
              @click="openEdit(row)"
            />
            <van-button
              v-if="row.status !== 'CANCELLED'"
              square
              type="danger"
              text="删除"
              class="swipe-btn"
              @click="onRemove(row)"
            />
            <van-button
              v-else
              square
              type="warning"
              text="恢复待购"
              class="swipe-btn"
              @click="onRestore(row)"
            />
          </template>
        </van-swipe-cell>
      </div>
    </van-pull-refresh>

    <!-- 新增 / 编辑弹层 -->
    <van-popup v-model:show="showForm" position="bottom" round>
      <div class="form-panel">
        <div class="form-panel__title">{{ editingId != null ? '编辑缺耗品' : '新增缺耗品' }}</div>
        <van-form validate-first @submit="onSubmit">
          <van-field
            v-model="form.name"
            name="name"
            label="名称"
            placeholder="缺什么买什么"
            :maxlength="100"
            :rules="[nameRule]"
          />
          <van-field
            v-model="form.quantity"
            name="quantity"
            type="digit"
            label="数量"
            placeholder="1"
            :rules="[quantityRule]"
          />
          <van-field v-model="form.unit" name="unit" label="单位" placeholder="如：件 / 瓶" :maxlength="20" />
          <van-field
            :model-value="categoryName(form.categoryId)"
            name="categoryId"
            readonly
            is-link
            label="分类"
            placeholder="可不选"
            @click="showCategoryPicker = true"
          />
          <van-field
            v-model="form.remark"
            name="remark"
            label="备注"
            type="textarea"
            rows="2"
            autosize
            :maxlength="200"
            show-word-limit
            placeholder="补充说明（可选）"
          />
          <div class="form-panel__footer">
            <van-button size="small" plain type="default" @click="onCancelForm">取消</van-button>
            <van-button size="small" type="primary" native-type="submit" :loading="submitting">
              {{ editingId != null ? '保存' : '添加' }}
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <van-picker
        title="选择分类"
        :columns="categoryColumns"
        @confirm="onCategoryConfirm"
        @cancel="showCategoryPicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped>
.shopping-page {
  padding-bottom: 12px;
}

.shopping-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--paper);
}

.shopping-tabs {
  flex: 1;
  min-width: 0;
}

.add-btn {
  flex: 0 0 auto;
}

.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 0;
}

.shopping-list {
  padding: 4px 12px 12px;
}

.shop-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  padding: 12px;
}

.shop-row__main {
  flex: 1;
  min-width: 0;
}

.shop-row__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop-row__name--done {
  color: var(--ink-2);
  text-decoration: line-through;
}

.shop-row__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop-row__tag {
  flex: 0 0 auto;
}

.shop-tag--pending {
  color: var(--terracotta-ink);
  background: var(--terracotta-soft);
  border-color: rgba(194, 96, 60, 0.5);
}

.shop-tag--bought {
  color: var(--moss-ink);
  background: var(--moss-soft);
  border-color: rgba(107, 122, 79, 0.5);
}

.shop-tag--cancelled {
  color: var(--ink-2);
  border-color: var(--dashed);
}

.swipe-btn {
  height: 100%;
}

.form-panel {
  background: var(--card);
}

.form-panel__title {
  padding: 16px 16px 4px;
  font-family: var(--fs-font-display);
  font-size: 16px;
  font-weight: 700;
  text-align: center;
  color: var(--ink);
}

.form-panel__footer {
  display: flex;
  gap: 12px;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
}

.form-panel__footer .van-button {
  flex: 1;
}
</style>
