<template>
<div v-if="p.pages > 1" class="pager">
  <!-- 26.10.28 ‹ › 는 한 페이지씩 이동 (예전에는 3페이지 묶음으로 건너뛰어 1페이지에서 › 누르면 4페이지로 가던 문제) -->
  <button class="pg" :disabled="p.page <= 1" aria-label="이전 페이지" @click="go(p.page - 1)">‹</button>
  <button v-for="n in nums" :key="n" :class="'pg' + (n === p.page ? ' on' : '')" @click="p.page = n">{{ n }}</button>
  <button class="pg" :disabled="p.page >= p.pages" aria-label="다음 페이지" @click="go(p.page + 1)">›</button>
  <span class="meta">총 {{ p.total }}건</span>
</div>
</template>
<script setup>
// fix_1434: 페이지 번호는 한 번에 최대 3개 (p = usePage() 결과, reactive). 지금 페이지가 들어 있는 3개 묶음을 보여 준다
import { computed } from 'vue'
const props = defineProps({ p: { type: Object, required: true } })
const grp = computed(() => Math.floor((props.p.page - 1) / 3))
const nums = computed(() => { const s = grp.value * 3 + 1, e = Math.min(s + 2, props.p.pages); const a = []; for (let i = s; i <= e; i++) a.push(i); return a })
function go(n) { props.p.page = Math.min(Math.max(1, n), props.p.pages) }
</script>
