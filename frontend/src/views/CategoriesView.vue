<!-- src/views/CategoriesView.vue —— 分类管理：列表 + 新增/编辑（全员）+ 删除（仅 ADMIN）。
     后端 /api/categories CRUD；删除被物品/购物清单引用时后端 400 CATEGORY_IN_USE。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { showConfirmDialog } from 'vant/es/dialog';
import 'vant/es/dialog/style';
import { apiErrorMessage } from '../api/http';
import { categoriesApi } from '../api/categories';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/auth';
import PaperIcon from '../components/PaperIcon.vue';
import type { Category, CategoryPayload } from '../types';

const auth = useAuthStore();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');

const categories = ref<Category[]>([]);
const loading = ref(true);
const showForm = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);
const form = ref({ name: '', sortOrder: '0' });

async function load() {
  loading.value = true;
  try {
    categories.value = await categoriesApi.list();
  } catch (e) {
    showToast(apiErrorMessage(e, '分类加载失败'));
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  form.value = { name: '', sortOrder: '0' };
  showForm.value = true;
}

function openEdit(c: Category) {
  editingId.value = c.id;
  form.value = { name: c.name, sortOrder: String(c.sortOrder) };
  showForm.value = true;
}

async function submit() {
  if (submitting.value) return;
  const name = form.value.name.trim();
  if (!name) {
    showToast('请输入分类名称');
    return;
  }
  submitting.value = true;
  const payload: CategoryPayload = { name, sortOrder: Number(form.value.sortOrder) || 0 };
  try {
    if (editingId.value !== null) {
      await categoriesApi.update(editingId.value, payload);
      showToast('已保存');
    } else {
      await categoriesApi.create(payload);
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

async function onDelete(c: Category) {
  try {
    await showConfirmDialog({
      title: '删除分类',
      message: `确定删除「${c.name}」？被物品或购物清单引用时将无法删除。`,
    });
  } catch {
    return;
  }
  try {
    await categoriesApi.delete(c.id);
    showToast('已删除');
    await load();
  } catch (e) {
    showToast(apiErrorMessage(e, '删除失败'));
  }
}

onMounted(async () => {
  // 刷新后 store 中 user 为空：先回填，用于判断是否显示删除按钮（后端仍有 403 兜底）。
  if (!auth.user && auth.token) {
    try {
      auth.user = await authApi.me();
    } catch {
      // 401 由 http 拦截器统一处理
    }
  }
  void load();
});
</script>

<template>
  <div class="categories-page">
    <div class="categories-toolbar">
      <span class="categories-toolbar__title">共 {{ categories.length }} 个分类</span>
      <van-button size="small" type="primary" class="paper-sticker-btn" @click="openCreate">
        <template #icon><PaperIcon name="plus" :size="14" /></template>
        新增分类
      </van-button>
    </div>

    <div v-if="loading" class="block-loading">
      <van-loading size="24">加载中…</van-loading>
    </div>
    <van-empty v-else-if="categories.length === 0" description="暂无分类，点击右上角新增" image-size="80" />
    <div v-else class="category-list">
      <div v-for="c in categories" :key="c.id" class="category-row paper-card">
        <div class="category-row__main">
          <div class="category-row__name">{{ c.name }}</div>
          <div class="category-row__meta">排序 {{ c.sortOrder }}</div>
        </div>
        <div class="category-row__ops">
          <van-button size="small" plain type="primary" @click="openEdit(c)">编辑</van-button>
          <van-button v-if="isAdmin" size="small" plain type="danger" @click="onDelete(c)">删除</van-button>
        </div>
      </div>
    </div>

    <van-popup v-model:show="showForm" position="bottom" round>
      <div class="form-panel">
        <div class="form-panel__title">{{ editingId === null ? '新增分类' : '编辑分类' }}</div>
        <van-form validate-first @submit="submit">
          <van-field
            v-model="form.name"
            name="name"
            label="名称"
            placeholder="如 食品 / 日用品 / 清洁用品"
            :maxlength="50"
            :rules="[{ required: true, message: '请输入分类名称' }]"
          />
          <van-field
            v-model="form.sortOrder"
            name="sortOrder"
            type="digit"
            label="排序"
            placeholder="数字越小越靠前"
          />
          <div class="form-panel__footer">
            <van-button size="small" plain type="default" @click="showForm = false">取消</van-button>
            <van-button size="small" type="primary" native-type="submit" :loading="submitting">
              {{ editingId === null ? '添加' : '保存' }}
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.categories-page {
  padding-bottom: 12px;
}

.categories-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--paper);
}

.categories-toolbar__title {
  font-size: 13px;
  color: var(--ink-2);
}

.block-loading {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.category-list {
  padding: 4px 12px 12px;
}

.category-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  padding: 12px;
}

.category-row__main {
  min-width: 0;
}

.category-row__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-row__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-2);
}

.category-row__ops {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
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
