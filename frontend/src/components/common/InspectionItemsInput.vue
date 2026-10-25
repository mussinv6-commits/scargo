<template>
  <!-- 26.10.23 컨테이너 점검 항목 7개 입력 (기사 · 운송사 · 관리자 · 허브 화면 공용). 판정 표시는 미리보기이고 서버가 다시 계산 -->
  <div class="ci-items" :class="{ 'is-dark': dark }">
    <div v-for="item in INSPECTION_ITEMS" :key="item.key" class="ci-item">
      <span class="ci-item-label">{{ item.label }}</span>
      <div class="ci-item-btns" role="group" :aria-label="item.label">
        <button type="button" class="ci-btn ok" :class="{ on: modelValue?.[item.key] === true }" :disabled="disabled"
                @click="set(item.key, true)">양호</button>
        <button type="button" class="ci-btn bad" :class="{ on: modelValue?.[item.key] === false }" :disabled="disabled"
                @click="set(item.key, false)">불량</button>
      </div>
    </div>
    <p class="ci-judge">
      판정 <strong :class="'j-' + judge">{{ OVERALL_LABEL[judge] }}</strong>
      <span v-if="judge === 'PENDING'"> · {{ remaining }}개 항목 남음</span>
    </p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { INSPECTION_ITEMS, OVERALL_LABEL, previewJudge } from '@/utils/containerInspectionApi.js'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  disabled: { type: Boolean, default: false },
  dark: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const judge = computed(() => previewJudge(props.modelValue))
const remaining = computed(() => INSPECTION_ITEMS.filter((i) => typeof props.modelValue?.[i.key] !== 'boolean').length)

function set(key, value) {
  if (props.disabled) return
  emit('update:modelValue', { ...(props.modelValue || {}), [key]: value })
}
</script>

<style scoped>
.ci-items { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 8px; }
.ci-item { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 10px;
  border: 1px solid #e3e8ef; border-radius: 8px; background: #fff; }
.ci-item-label { font-size: 13.5px; font-weight: 600; color: #1f2937; }
.ci-item-btns { display: flex; gap: 4px; }
.ci-btn { border: 1px solid #cfd6df; background: #f8fafc; color: #4b5563; border-radius: 6px; padding: 4px 10px;
  font-size: 12.5px; cursor: pointer; }
.ci-btn:disabled { cursor: default; opacity: .75; }
.ci-btn.ok.on { background: #16a34a; border-color: #16a34a; color: #fff; }
.ci-btn.bad.on { background: #dc2626; border-color: #dc2626; color: #fff; }
.ci-judge { grid-column: 1 / -1; margin: 4px 0 0; font-size: 13.5px; color: #4b5563; }
.j-PASS { color: #16a34a; } .j-FAIL { color: #dc2626; } .j-PENDING { color: #b45309; }
.is-dark .ci-item { background: #12304f; border-color: #1d3b60; }
.is-dark .ci-item-label { color: #e6eff7; }
.is-dark .ci-btn { background: #0f2a4a; border-color: #2a4d76; color: #c9d8ea; }
.is-dark .ci-judge { color: #c9d8ea; }
</style>
