<template>
<div v-if="p.pages > 1" class="pager">
  <button class="pg" :disabled="grp === 0" @click="go(grp * 3)">이전</button>
  <button v-for="n in nums" :key="n" :class="'pg' + (n === p.page ? ' on' : '')" @click="p.page = n">{{ n }}</button>
  <button class="pg" :disabled="(grp + 1) * 3 >= p.pages" @click="go((grp + 1) * 3 + 1)">다음</button>
  <span class="meta">총 {{ p.total }}건</span>
</div>
</template>
<script setup>
// fix_1434: 페이지 번호는 한 번에 최대 3개. '다음'·'이전' 으로 3개 묶음 이동 (p = usePage() 결과, reactive)
import { computed } from 'vue'
const props = defineProps({ p: { type: Object, required: true } })
const grp = computed(() => Math.floor((props.p.page - 1) / 3))
const nums = computed(() => { const s = grp.value * 3 + 1, e = Math.min(s + 2, props.p.pages); const a = []; for (let i = s; i <= e; i++) a.push(i); return a })
function go(n) { props.p.page = Math.min(Math.max(1, n), props.p.pages) }
</script>
