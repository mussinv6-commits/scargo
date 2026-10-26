<template>
<section class="scr" id="W-06"><div class="card"><h3>AMR 관제 (허브 내 자율이동로봇 {{ rows.length }}대)</h3><table :style="pg.reset"><tr><th>로봇</th><th>종류</th><th>상태</th><th>배터리</th><th>현재 작업</th><th>위치</th></tr>
<tr v-for="r in pg.rows" :key="r.code"><td>{{ r.code }}</td><td>{{ r.type }}</td><td><span :class="'b ' + (['ERROR','STOPPED','FAULT'].includes(r.state) ? 'crit' : r.state === 'IDLE' ? 'gray' : 'ok')">{{ stl(r.state) }}</span></td><td>{{ r.battery == null ? '—' : Math.round(r.battery) + '%' }}</td><td>{{ r.task || '—' }}</td><td>{{ r.place || '—' }}</td></tr>
<tr v-if="!rows.length"><td colspan="6" class="meta">등록된 로봇이 없습니다.</td></tr></table><Pager :p="pg"/></div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC, stl } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// AMR 관제: GET /api/web/amr/robots (HUB_OP 는 소속 허브만)
const rows = ref([])
async function load() { const r = await get('/api/web/amr/robots'); if (Array.isArray(r)) rows.value = r }
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pg = usePage(rows, 10)
</script>
