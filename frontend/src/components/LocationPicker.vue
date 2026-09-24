<!-- src/components/LocationPicker.vue —— 位置级联选择器：GET /api/locations 树 → Cascader。
     v-model 为选中 locationId；change 事件返回 { id, name }（name 为「父 / 子」路径）。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type { CascaderOption, FieldRule } from 'vant';
import { locationsApi } from '../api/locations';
import type { Location } from '../types';

const props = defineProps<{
  modelValue: number | null;
  label?: string;
  name?: string;
  placeholder?: string;
  rules?: FieldRule[];
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | null): void;
  (e: 'change', value: { id: number; name: string }): void;
}>();

const tree = ref<Location[]>([]);
const show = ref(false);
const cascaderValue = ref<number | string | undefined>(props.modelValue ?? undefined);

watch(
  () => props.modelValue,
  (v) => {
    cascaderValue.value = v ?? undefined;
  },
);

const options = computed<CascaderOption[]>(() => toOptions(tree.value));

function toOptions(nodes: Location[]): CascaderOption[] {
  return nodes.map((n) => ({
    text: n.name,
    value: n.id,
    children: n.children?.length ? toOptions(n.children) : undefined,
  }));
}

/** 由 id 反查展示路径（如「客厅 / 电视柜」）。 */
const displayText = computed(() => {
  if (props.modelValue == null) return '';
  return findPath(tree.value, props.modelValue).join(' / ');
});

function findPath(nodes: Location[], id: number): string[] {
  for (const n of nodes) {
    if (n.id === id) return [n.name];
    const sub = n.children?.length ? findPath(n.children, id) : [];
    if (sub.length) return [n.name, ...sub];
  }
  return [];
}

function onFinish(params: { value?: number | string; selectedOptions?: CascaderOption[] }) {
  const id = Number(params.value ?? cascaderValue.value);
  if (!Number.isInteger(id) || id <= 0) return;
  emit('update:modelValue', id);
  const name = (params.selectedOptions ?? []).map((o) => String(o.text ?? '')).filter(Boolean).join(' / ');
  emit('change', { id, name });
  show.value = false;
}

onMounted(async () => {
  try {
    tree.value = await locationsApi.getTree();
  } catch {
    // 树加载失败不阻塞表单：字段保持占位，重新打开弹层可再次加载。
  }
});
</script>

<template>
  <van-field
    :model-value="displayText"
    :name="name"
    :label="label ?? '位置'"
    :placeholder="placeholder ?? '请选择收纳位置'"
    :rules="rules"
    readonly
    is-link
    @click="show = true"
  />
  <van-popup v-model:show="show" position="bottom" round>
    <van-cascader
      v-model="cascaderValue"
      :options="options"
      title="选择位置"
      placeholder="请选择收纳位置"
      @close="show = false"
      @finish="onFinish"
    />
  </van-popup>
</template>