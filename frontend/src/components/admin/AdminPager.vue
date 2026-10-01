<template>
  <nav class="admin-pager" aria-label="페이지 이동">
    <button
      v-if="pageCount > 1"
      type="button"
      class="pager-btn"
      :disabled="modelValue <= 1"
      @click="go(modelValue - 1)"
    >
      ‹
    </button>
    <button
      v-for="p in visiblePages"
      :key="p"
      type="button"
      class="pager-btn"
      :class="{ active: modelValue === p }"
      @click="go(p)"
    >
      {{ p }}
    </button>
    <button
      v-if="pageCount > 1"
      type="button"
      class="pager-btn"
      :disabled="modelValue >= pageCount"
      @click="go(modelValue + 1)"
    >
      ›
    </button>
  </nav>
</template>

<script setup>
import { computed } from 'vue'

const WINDOW = 3

const props = defineProps({
  modelValue: { type: Number, default: 1 },
  pageCount: { type: Number, default: 1 },
})
const emit = defineEmits(['update:modelValue', 'change'])

const visiblePages = computed(() => {
  const total = Math.max(1, props.pageCount)
  const current = Math.min(Math.max(1, props.modelValue), total)
  const start = Math.floor((current - 1) / WINDOW) * WINDOW + 1
  const end = Math.min(start + WINDOW - 1, total)
  const pages = []
  for (let p = start; p <= end; p += 1) pages.push(p)
  return pages
})

function go(page) {
  const next = Math.min(Math.max(1, page), Math.max(1, props.pageCount))
  emit('update:modelValue', next)
  emit('change', next)
}
</script>
