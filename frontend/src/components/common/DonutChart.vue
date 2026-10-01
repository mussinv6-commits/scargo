<template>
  <div class="donut-wrap">
    <div class="donut-visual">
      <svg :viewBox="`0 0 ${size} ${size}`" :width="size" :height="size" role="img" :aria-label="title">
        <circle
          class="donut-track"
          :cx="center"
          :cy="center"
          :r="radius"
          fill="none"
          :stroke-width="stroke"
        />
        <circle
          v-for="(seg, i) in arcs"
          :key="i"
          :cx="center"
          :cy="center"
          :r="radius"
          fill="none"
          :stroke="seg.color"
          :stroke-width="stroke"
          stroke-linecap="butt"
          :stroke-dasharray="seg.dashArray"
          :stroke-dashoffset="seg.dashOffset"
          transform="rotate(-90)"
          :style="{ transformOrigin: `${center}px ${center}px` }"
        />
      </svg>
      <div class="donut-center">
        <span>{{ centerLabel || unit }}</span>
        <strong>{{ total }}</strong>
      </div>
    </div>
    <ul class="donut-legend">
      <li v-for="(s, i) in items" :key="i">
        <i :style="{ background: s.color }"></i>
        <span>{{ s.label }}</span>
        <b>{{ s.value }}{{ unit }}</b>
        <em v-if="showPercent">{{ pct(s.value) }}%</em>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  items: { type: Array, default: () => [] }, // { label, value, color }
  unit: { type: String, default: '건' },
  centerLabel: { type: String, default: '' },
  showPercent: { type: Boolean, default: false },
  size: { type: Number, default: 168 },
  stroke: { type: Number, default: 22 },
})

const center = computed(() => props.size / 2)
const radius = computed(() => (props.size - props.stroke) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const total = computed(() => props.items.reduce((sum, s) => sum + Number(s.value || 0), 0))

const arcs = computed(() => {
  const c = circumference.value
  const tot = total.value
  if (!tot) return []
  let acc = 0
  return props.items.map((s) => {
    const val = Number(s.value || 0)
    const len = (val / tot) * c
    const dashArray = `${len} ${c - len}`
    const dashOffset = -acc
    acc += len
    return { color: s.color, dashArray, dashOffset }
  })
})

function pct(value) {
  if (!total.value) return 0
  return Math.round((Number(value || 0) / total.value) * 100)
}
</script>

<style scoped>
.donut-wrap {
  display: flex;
  align-items: center;
  gap: 18px;
  text-align: left;
}
.donut-visual { position: relative; flex-shrink: 0; }
.donut-track { stroke: #e8edf3; }
.donut-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.donut-center span {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--color-subtext, #64748b);
  margin-bottom: 2px;
}
.donut-center strong {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  color: var(--color-text, #0a2540);
}
.donut-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  min-width: 0;
}
.donut-legend li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: 5px 0;
  color: var(--color-text, #0a2540);
}
.donut-legend i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.donut-legend span { flex: 1; text-align: left; color: var(--color-subtext, #64748b); }
.donut-legend b { font-variant-numeric: tabular-nums; min-width: 18px; text-align: right; }
.donut-legend em {
  font-style: normal;
  font-size: 12px;
  color: var(--color-subtext, #64748b);
  min-width: 36px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
@media (max-width: 560px) {
  .donut-wrap { flex-direction: column; }
}
</style>
