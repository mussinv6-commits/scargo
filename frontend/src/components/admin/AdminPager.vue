<template>
  <nav v-if="pageCount > 1" class="admin-pager" aria-label="페이지 이동"><!-- 26.10.28 한 페이지뿐이면 숨김 -->
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

<style>
/* 26.10.28 관리자 화면 밖(운영센터 패널·도착 점검·사업자 화면 등)에서도 같은 모양이 나오도록 기본 스타일을 컴포넌트에 둠 (admin.css 와 같은 값) */
.admin-pager { display: flex; justify-content: center; align-items: center; gap: 6px; padding: 14px 0 10px; }
.admin-pager .pager-btn { min-width: 34px; height: 34px; padding: 0 8px; border: 1px solid #e3e8ef; border-radius: 8px; background: #fff; color: #112847; font-size: 13px; font-weight: 700; cursor: pointer; }
.admin-pager .pager-btn:hover:not(:disabled):not(.active) { border-color: #112847; }
.admin-pager .pager-btn.active { background: #112847; border-color: #112847; color: #fff; cursor: default; }
.admin-pager .pager-btn:disabled { opacity: .4; cursor: default; }
</style>
