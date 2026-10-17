<!-- src/views/ItemEditView.vue —— 物品新增/编辑两用表单：
     名称/分类(Picker)/位置(LocationPicker 级联)/数量+单位/单价/购买日期/到期日期/照片(Uploader)/备注，
     提交前 van-form 校验；照片符合类型与 5MB 限制，删除同步调 DELETE /api/files/{name}。 -->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { showToast } from 'vant/es/toast';
import 'vant/es/toast/style';
import type { UploaderFileListItem, FieldRule } from 'vant';
import { apiErrorMessage } from '../api/http';
import { itemsApi } from '../api/items';
import { categoriesApi } from '../api/categories';
import { filesApi, toFileDeleteName } from '../api/files';
import type { Category, Item, ItemPayload } from '../types';

const route = useRoute();
const router = useRouter();

const itemId = computed(() => Number(route.params.id));
const isEdit = computed(() => Number.isFinite(itemId.value) && itemId.value > 0);

const submitting = ref(false);
const loadingItem = ref(isEdit.value);

const form = reactive({
  name: '',
  categoryId: null as number | null,
  categoryName: '',
  locationId: null as number | null,
  quantity: '0',
  unit: '',
  purchasePrice: '',
  purchaseDate: '',
  expiryDate: '',
  photoUrl: null as string | null,
  remark: '',
});

// --- 分类选择 ---
const categories = ref<Category[]>([]);
const showCategoryPicker = ref(false);
const categoryColumns = computed(() =>
  categories.value.map((c) => ({ text: c.name, value: c.id })),
);

function onCategoryConfirm(params: { selectedOptions: Array<{ text?: unknown; value?: unknown }> }) {
  const opt = params.selectedOptions[0];
  if (opt && typeof opt.value === 'number') {
    form.categoryId = opt.value;
    form.categoryName = String(opt.text ?? '');
  }
  showCategoryPicker.value = false;
}

// --- 日期选择 ---
const showPurchaseCalendar = ref(false);
const showExpiryCalendar = ref(false);

function onPurchaseConfirm(value: Date) {
  form.purchaseDate = dayjs(value).format('YYYY-MM-DD');
  showPurchaseCalendar.value = false;
}

function onExpiryConfirm(value: Date) {
  form.expiryDate = dayjs(value).format('YYYY-MM-DD');
  showExpiryCalendar.value = false;
}

// --- 照片上传（spec §5.6：jpg/jpeg/png/webp、≤5MB，DELETE 用相对路径） ---
const fileList = ref<UploaderFileListItem[]>([]);
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const ACCEPTED_EXTS = ['jpg', 'jpeg', 'png', 'webp'];

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
  // 注意：vant Uploader 将返回值按布尔判定，undefined 会中止上传（视为拒绝），
  // 合法文件必须返回 true 才会继续进入 max-size 校验与上传流程。
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
    form.photoUrl = res.url;
  } catch (e) {
    target.status = 'failed';
    target.message = '上传失败';
    showToast(apiErrorMessage(e, '照片上传失败'));
  }
}

/** 移除照片：同步清理已上传到服务器的文件，避免孤悬资源。 */
async function onDelete(file: UploaderFileListItem) {
  if (file.url) {
    if (form.photoUrl === file.url) form.photoUrl = null;
    try {
      await filesApi.delete(toFileDeleteName(file.url));
    } catch (e) {
      showToast(apiErrorMessage(e, '照片删除失败'));
    }
  }
}

// --- 校验规则 ---
const quantityRule: FieldRule = {
  required: true,
  message: '请填写数量',
  validator: (v: string) => (/^\d+$/.test(v.trim()) ? true : '数量必须为非负整数'),
};
const priceRule: FieldRule = {
  validator: (v: string) => {
    if (v === '') return true;
    const n = Number(v);
    return Number.isFinite(n) && n >= 0 ? true : '单价必须为非负数字';
  },
};

// --- 加载与回填 ---
onMounted(async () => {
  const cats = await categoriesApi.list().catch(() => [] as Category[]);
  categories.value = cats;
  if (!isEdit.value) {
    loadingItem.value = false;
    return;
  }
  try {
    const it: Item = await itemsApi.getItem(itemId.value);
    form.name = it.name;
    form.categoryId = it.categoryId;
    form.categoryName = cats.find((c) => c.id === it.categoryId)?.name ?? '';
    form.locationId = it.locationId;
    form.quantity = String(it.quantity);
    form.unit = it.unit ?? '';
    form.purchasePrice = it.purchasePrice != null ? String(it.purchasePrice) : '';
    form.purchaseDate = it.purchaseDate ?? '';
    form.expiryDate = it.expiryDate ?? '';
    form.photoUrl = it.photoUrl;
    form.remark = it.remark ?? '';
    if (it.photoUrl) {
      fileList.value = [{ url: it.photoUrl, status: 'done', isImage: true }];
    }
  } catch (e) {
    showToast(apiErrorMessage(e, '物品不存在'));
    void router.replace('/items');
    return;
  } finally {
    loadingItem.value = false;
  }
});

function onSubmit() {
  if (submitting.value || form.categoryId == null || form.locationId == null) return;
  submitting.value = true;
  const payload: ItemPayload = {
    name: form.name.trim(),
    categoryId: form.categoryId,
    locationId: form.locationId,
    quantity: Number(form.quantity),
    unit: form.unit.trim() || null,
    purchasePrice: form.purchasePrice === '' ? null : Number(form.purchasePrice),
    purchaseDate: form.purchaseDate || null,
    expiryDate: form.expiryDate || null,
    photoUrl: form.photoUrl,
    remark: form.remark.trim() || null,
  };
  const request = isEdit.value
    ? itemsApi.updateItem(itemId.value, payload)
    : itemsApi.createItem(payload);
  request
    .then(() => {
      showToast(isEdit.value ? '已保存' : '已新增');
      void router.replace(isEdit.value ? `/items/${itemId.value}` : '/items');
    })
    .catch((e: unknown) => {
      showToast(apiErrorMessage(e));
    })
    .finally(() => {
      submitting.value = false;
    });
}
</script>

<template>
  <div class="edit-page">
    <div v-if="loadingItem" class="edit-loading">
      <van-loading size="24">加载中…</van-loading>
    </div>

    <van-form v-else @submit="onSubmit" validate-first>
      <van-cell-group inset>
        <van-field
          v-model="form.name"
          name="name"
          label="名称"
          placeholder="物品名称"
          :maxlength="100"
          :rules="[{ required: true, message: '请填写物品名称' }]"
        />
        <van-field
          v-model="form.categoryName"
          name="categoryId"
          readonly
          is-link
          label="分类"
          placeholder="请选择分类"
          :rules="[{ required: true, message: '请选择分类' }]"
          @click="showCategoryPicker = true"
        />
        <LocationPicker
          v-model="form.locationId"
          name="locationId"
          :rules="[{ required: true, message: '请选择位置' }]"
        />
      </van-cell-group>

      <van-cell-group inset>
        <van-field
          v-model="form.quantity"
          name="quantity"
          type="digit"
          label="数量"
          placeholder="0"
          :rules="[quantityRule]"
        />
        <van-field v-model="form.unit" name="unit" label="单位" placeholder="如：件 / 瓶" :maxlength="20" />
        <van-field
          v-model="form.purchasePrice"
          name="purchasePrice"
          type="number"
          label="单价（元）"
          placeholder="可选"
          :rules="[priceRule]"
        />
        <van-field
          v-model="form.purchaseDate"
          name="purchaseDate"
          readonly
          is-link
          clearable
          label="购买日期"
          placeholder="选择日期"
          @click="showPurchaseCalendar = true"
          @clear="form.purchaseDate = ''"
        />
        <van-field
          v-model="form.expiryDate"
          name="expiryDate"
          readonly
          is-link
          clearable
          label="到期日期"
          placeholder="选择日期"
          @click="showExpiryCalendar = true"
          @clear="form.expiryDate = ''"
        />
      </van-cell-group>

      <van-cell-group inset>
        <van-cell title="照片（jpg/jpeg/png/webp，≤5MB）" :border="false" />
        <div class="uploader-wrap">
          <van-uploader
            v-model="fileList"
            :max-count="1"
            :max-size="5 * 1024 * 1024"
            accept="image/*"
            :before-read="beforeRead"
            :after-read="afterRead"
            @oversize="onOversize"
            @delete="onDelete"
          />
        </div>
      </van-cell-group>

      <van-cell-group inset>
        <van-field
          v-model="form.remark"
          name="remark"
          label="备注"
          type="textarea"
          rows="2"
          autosize
          :maxlength="500"
          show-word-limit
          placeholder="补充说明（可选）"
        />
      </van-cell-group>

      <div class="form-footer">
        <van-button round block type="primary" native-type="submit" :loading="submitting">
          {{ isEdit ? '保存修改' : '新增物品' }}
        </van-button>
      </div>
    </van-form>

    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <van-picker
        title="选择分类"
        :columns="categoryColumns"
        @confirm="onCategoryConfirm"
        @cancel="showCategoryPicker = false"
      />
    </van-popup>

    <van-calendar v-model:show="showPurchaseCalendar" title="选择购买日期" @confirm="onPurchaseConfirm" />
    <van-calendar v-model:show="showExpiryCalendar" title="选择到期日期" @confirm="onExpiryConfirm" />
  </div>
</template>

<style scoped>
.edit-page {
  padding: 12px 0 24px;
}

.edit-loading {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.uploader-wrap {
  margin: 0 14px 12px;
  padding: 10px;
  background: var(--paper);
  border: 1px dashed var(--dashed);
  border-radius: 3px;
}

.form-footer {
  padding: 16px;
}

.form-footer .van-button {
  height: 44px;
  border-radius: 2px;
}
</style>