<!-- src/components/PaperIcon.vue —— 统一线性 SVG 图标（24×24 viewBox，stroke=currentColor）。
     装饰性图标默认 aria-hidden；传 label 时作为可见语义图标（role=img + aria-label）。 -->
<script setup lang="ts">
import { computed } from 'vue';

const icons = {
  home: '<path d="M3 11.5 12 4l9 7.5"/><path d="M5.5 10v9.5h13V10"/><path d="M10 19.5v-5h4v5"/>',
  items:
    '<path d="M4 8.5 12 4l8 4.5v7L12 20l-8-4.5z"/><path d="M4 8.5 12 13l8-4.5"/><path d="M12 13v7"/>',
  cart:
    '<path d="M4 5h2l2.2 9.5h9.3L20 7H7"/><circle cx="9.5" cy="18.5" r="1.2"/><circle cx="17" cy="18.5" r="1.2"/>',
  user:
    '<path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"/><path d="M4.5 20a7.5 7.5 0 0 1 15 0"/>',
  bell:
    '<path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2.5h-15z"/><path d="M10 20a2 2 0 0 0 4 0"/>',
  search: '<circle cx="11" cy="11" r="6"/><path d="M15.5 15.5 20 20"/>',
  filter: '<path d="M4 5h16"/><path d="M7 12h10"/><path d="M10 19h4"/>',
  plus: '<path d="M12 5v14"/><path d="M5 12h14"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  edit:
    '<path d="M4 20l1-4L16.5 4.5a2 2 0 0 1 3 3L8 19l-4 1z"/><path d="M14.5 6.5l3 3"/>',
  trash:
    '<path d="M4 7h16"/><path d="M9 7V4h6v3"/><path d="M6 7l1 13h10l1-13"/><path d="M10 11v5"/><path d="M14 11v5"/>',
  camera:
    '<path d="M4 8h3l1.5-2h7L17 8h3v11H4z"/><circle cx="12" cy="13" r="3.2"/>',
  location:
    '<path d="M12 21s7-6.2 7-11a7 7 0 1 0-14 0c0 4.8 7 11 7 11z"/><circle cx="12" cy="10" r="2.5"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
} as const;

export type IconName = keyof typeof icons;

const props = withDefaults(
  defineProps<{
    name: IconName;
    size?: number | string;
    label?: string;
  }>(),
  { size: 20 },
);

const paths = computed(() => icons[props.name]);
const decorative = computed(() => !props.label);
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.75"
    stroke-linecap="round"
    stroke-linejoin="round"
    :aria-hidden="decorative ? 'true' : 'false'"
    :role="decorative ? undefined : 'img'"
    :aria-label="label"
  >
    <g v-html="paths" />
  </svg>
</template>
