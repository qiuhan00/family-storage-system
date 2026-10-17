<!-- src/views/LocationsView.vue —— 位置管理（spec §5.3）：树形列表（说明摘要 + 照片缩略）
     + 新增/编辑/删除。权限为登录即可（全员可维护）；图片经 POST /api/files 上传回填 imageUrl。 -->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import { showConfirmDialog } from 'vant/es/dialog';
import 'vant/es/dialog/style';
import type { UploaderFileListItem } from 'vant';
import { apiErrorMessage } from '../api/http';
import { filesApi, toFileDeleteName } from '../api/files';
import PaperIcon from '../components/PaperIcon.vue';
import { flattenLocations, locationsApi, type FlatLocation } from '../api/locations';
import type { Location, LocationPayload } from '../types';

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const ACCEPTED_EXTS = ['jpg', 'jpeg', 'png', 'webp'];
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

const tree = ref<Location[]>([]);
const loading = ref(true);
const showForm = ref(false);
const showParentPicker = ref(false);
const editingId = ref<number | null>(null);
const submitting = ref(false);
/** 保存成功后暂存提示，待弹层关闭动画结束（closed）再弹出，避免 toast 与标题重叠（DEF-LOC-01）。 */
const pendingToast = ref('');
/** 文件清理状态（code-review Y1）：删除/替换的物理清理推迟到弹层关闭时统一执行，
 *  避免「立即删文件后点取消」造成 DB 指向已删文件的坏图。 */
let originalImageUrl: string | null = null;
let sessionUploads: string[] = [];
let formSaved = false;
const hasUploading = computed(() => fileList.value.some((f) => f.status === 'uploading'));

const form = ref({
  name: '',
  parentId: null as number | null,
  sortOrder: 0,
  description: '',
  imageUrl: null as string | null,
});
const fileList = ref<UploaderFileListItem[]>([]);

const flat = computed(() => flattenLocations(tree.value));
const parentLabel = computed(() => {
  if (form.value.parentId === null) return '根节点';
  return flat.value.find((l) => l.id === form.value.parentId)?.path ?? '根节点';
});

function isImageFile(file: File): boolean {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  return ACCEPTED_TYPES.includes(file.type) && ACCEPTED_EXTS.includes(ext);
}

function beforeRead(file: File | File[]): boolean | undefined {
  const files = Array.isArray(file) ? file : [file];
  if (files.length === 0 || files.some((f) => !isImageFile(f))) {
    showToast('仅支持 jpg/jpeg/png/webp 图片');
    return false;
  }
  return true;
}

function onOversize() {
  showToast('图片不能超过 5MB');
}

async function afterRead(uploaded: UploaderFileListItem | UploaderFileListItem[]) {
  const target = Array.isArray(uploaded) ? uploaded[0] : uploaded;
  if (!target.file) return;
  target.status = 'uploading';
  target.message = '上传中…';
  try {
    const res = await filesApi.upload(target.file);
    target.status = 'done';
    target.url = res.url;
    target.message = '';
    form.value.imageUrl = res.url;
    sessionUploads.push(res.url);
  } catch (e) {
    target.status = 'failed';
    target.message = '上传失败';
    showToast(apiErrorMessage(e, '照片上传失败'));
  }
}

/** 编辑弹层内移除照片：仅改状态；物理删除延后到弹层关闭（onPopupClosed），避免取消保存后坏图。 */
function onFileDelete(file: UploaderFileListItem) {
  if (file.url && form.value.imageUrl === file.url) form.value.imageUrl = null;
}

async function load() {
  loading.value = true;
  try {
    tree.value = await locationsApi.getTree();
  } catch (e) {
    showToast(apiErrorMessage(e, '位置加载失败'));
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  form.value = { name: '', parentId: null, sortOrder: 0, description: '', imageUrl: null };
  fileList.value = [];
  originalImageUrl = null;
  sessionUploads = [];
  formSaved = false;
  showForm.value = true;
}

function openEdit(loc: FlatLocation) {
  editingId.value = loc.id;
  form.value = {
    name: loc.name,
    parentId: loc.parentId,
    sortOrder: loc.sortOrder,
    description: loc.description ?? '',
    imageUrl: loc.imageUrl,
  };
  fileList.value = loc.imageUrl ? [{ url: loc.imageUrl, status: 'done', isImage: true }] : [];
  originalImageUrl = loc.imageUrl;
  sessionUploads = [];
  formSaved = false;
  showForm.value = true;
}

function pickParent(id: number | null) {
  form.value.parentId = id;
  showParentPicker.value = false;
}

function flushToast() {
  if (pendingToast.value) {
    showToast(pendingToast.value);
    pendingToast.value = '';
  }
}

/** 弹层关闭后：先弹暂存 toast，再统一清理未被保存引用的图片文件（code-review Y1）。 */
function onPopupClosed() {
  flushToast();
  const finalUrl = form.value.imageUrl;
  const stale = new Set<string>();
  if (formSaved) {
    for (const u of sessionUploads) {
      if (u !== finalUrl) stale.add(u);
    }
    if (originalImageUrl && originalImageUrl !== finalUrl) stale.add(originalImageUrl);
  } else {
    for (const u of sessionUploads) stale.add(u);
  }
  for (const u of stale) {
    // 尽力清理：失败仅产生孤悬文件，不影响数据一致性
    filesApi.delete(toFileDeleteName(u)).catch(() => undefined);
  }
  sessionUploads = [];
  formSaved = false;
  originalImageUrl = null;
}

async function submit() {
  if (submitting.value) return;
  const name = form.value.name.trim();
  if (!name) {
    showToast('请输入位置名称');
    return;
  }
  if (hasUploading.value) {
    showToast('照片上传中，请稍候');
    return;
  }
  submitting.value = true;
  try {
    const payload: LocationPayload = {
      name,
      parentId: form.value.parentId,
      sortOrder: form.value.sortOrder,
      description: form.value.description.trim() || null,
      imageUrl: form.value.imageUrl,
    };
    if (editingId.value !== null) {
      await locationsApi.update(editingId.value, payload);
    } else {
      await locationsApi.create(payload);
    }
    pendingToast.value = '已保存';
    formSaved = true;
    showForm.value = false;
    await load();
  } catch (e) {
    showToast(apiErrorMessage(e, '保存失败'));
  } finally {
    submitting.value = false;
  }
}

async function onDelete(loc: FlatLocation) {
  try {
    await showConfirmDialog({
      title: '删除位置',
      message: `确定删除「${loc.path}」？其下仍有物品或子位置时将被拒绝。`,
    });
  } catch {
    return;
  }
  try {
    await locationsApi.delete(loc.id);
    showToast('已删除');
    await load();
  } catch (e) {
    showToast(apiErrorMessage(e, '删除失败'));
  }
}

onMounted(load);
</script>

<template>
  <div class="locations-page">
    <div class="locations-toolbar">
      <span class="locations-toolbar__title">共 {{ flat.length }} 个位置</span>
      <van-button size="small" icon="plus" type="primary" @click="openCreate">新增位置</van-button>
    </div>

    <div v-if="loading" class="block-loading">
      <van-loading size="24">加载中…</van-loading>
    </div>
    <van-empty v-else-if="flat.length === 0" description="暂无位置，点击右上角新增" image-size="80" />
    <div v-else class="location-list">
      <div
        v-for="loc in flat"
        :key="loc.id"
        class="location-row"
        :style="{ paddingLeft: `${12 + loc.depth * 16}px` }"
      >
        <img v-if="loc.imageUrl" :src="loc.imageUrl" class="location-row__thumb" alt="" />
        <div v-else class="location-row__thumb location-row__thumb--empty">
          <PaperIcon name="location" :size="18" />
        </div>
        <div class="location-row__main">
          <div class="location-row__name">{{ loc.name }}</div>
          <div class="location-row__desc" :class="{ 'location-row__desc--empty': !loc.description }">
            {{ loc.description || '未填写说明' }}
          </div>
        </div>
        <div class="location-row__ops">
          <van-button size="small" plain type="primary" @click="openEdit(loc)">编辑</van-button>
          <van-button size="small" plain type="danger" @click="onDelete(loc)">删除</van-button>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹层（closed = 关闭动画结束后弹 toast 并清理未引用文件，DEF-LOC-01 + code-review Y1） -->
    <van-popup v-model:show="showForm" position="bottom" round @closed="onPopupClosed">
      <div class="form-panel">
        <div class="form-panel__title">{{ editingId === null ? '新增位置' : '编辑位置' }}</div>
        <van-form @submit="submit">
          <van-cell-group>
            <van-field
              v-model="form.name"
              name="name"
              label="名称"
              placeholder="如：厨房吊柜（必填）"
              maxlength="50"
              clearable
              :rules="[{ required: true, message: '请输入位置名称' }]"
            />
            <van-field
              :model-value="parentLabel"
              label="父级位置"
              placeholder="根节点"
              readonly
              clickable
              is-link
              @click="showParentPicker = true"
            />
            <van-field
              v-model="form.description"
              label="说明"
              type="textarea"
              rows="3"
              maxlength="500"
              show-word-limit
              placeholder="具体摆放说明，如：吊柜顶层放干货"
            />
          </van-cell-group>
          <div class="uploader-block">
            <div class="uploader-block__label">位置照片</div>
            <van-uploader
              v-model="fileList"
              max-count="1"
              :max-size="MAX_IMAGE_BYTES"
              :before-read="beforeRead"
              :after-read="afterRead"
              @oversize="onOversize"
              @delete="onFileDelete"
            />
          </div>
          <div class="form-panel__footer">
            <van-button size="small" plain type="default" @click="showForm = false">取消</van-button>
            <van-button size="small" type="primary" native-type="submit" :loading="submitting">
              保存
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 父级位置选择弹层 -->
    <van-popup v-model:show="showParentPicker" position="bottom" round>
      <div class="parent-panel">
        <div class="parent-panel__title">选择父级位置</div>
        <van-cell title="根节点" clickable @click="pickParent(null)">
          <template #right-icon>
            <PaperIcon v-if="form.parentId === null" name="check" :size="18" />
          </template>
        </van-cell>
        <van-cell
          v-for="loc in flat"
          :key="loc.id"
          :title="loc.path"
          clickable
          @click="pickParent(loc.id)"
        >
          <template #right-icon>
            <PaperIcon v-if="form.parentId === loc.id" name="check" :size="18" />
          </template>
        </van-cell>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.locations-page {
  padding-bottom: 12px;
}

.locations-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--fs-surface);
}

.locations-toolbar__title {
  font-size: 13px;
  color: var(--fs-text-3);
}

.block-loading {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.location-list {
  padding: 4px 12px 12px;
}

.location-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--fs-surface);
  box-shadow: var(--fs-shadow-sm);
}

.location-row__thumb {
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  object-fit: cover;
  background: var(--fs-surface-soft);
}

.location-row__thumb--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--fs-text-3);
}

.location-row__main {
  flex: 1;
  min-width: 0;
}

.location-row__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--fs-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.location-row__desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--fs-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.location-row__desc--empty {
  color: var(--fs-text-3);
}

.location-row__ops {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
}

.form-panel__title {
  padding: 16px 16px 4px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  color: var(--fs-text);
}

.uploader-block {
  padding: 12px 16px 4px;
}

.uploader-block__label {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--fs-text-3);
}

.form-panel__footer {
  display: flex;
  gap: 12px;
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
}

.form-panel__footer .van-button {
  flex: 1;
}

.parent-panel__title {
  padding: 16px 16px 8px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  color: var(--fs-text);
}
</style>
