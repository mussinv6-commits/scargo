<template>
<section class="scr" id="W-13"><div class="card"><h3>검문소 · 과적 재측정</h3><table :style="pg.reset"><tr><th>차량</th><th>측정소</th><th>1차 계량</th><th>재측정</th><th>결과</th><th>과태료</th><th>시각</th></tr>
<tr v-for="(r, i) in pg.rows" :key="i"><td>{{ r.vehicleNo }}</td><td>{{ r.station || '—' }}</td><td>{{ (r.firstKg || 0).toLocaleString() }} kg <span v-if="r.firstViolation" class="b crit">과적</span></td><td><template v-if="r.reKg != null">{{ r.reKg.toLocaleString() }} kg <span v-if="r.reViolation" class="b crit">과적</span></template><template v-else>—</template></td><td><span :class="'b ' + (r.passed ? 'ok' : 'crit')">{{ r.passed ? '통과' : '차단' }}</span></td><td>{{ r.fineManwon ? r.fineManwon + '만원' : '—' }}</td><td>{{ md(r.at) }}</td></tr>
<tr v-if="!rows.length"><td colspan="7" class="meta">기록이 없습니다.</td></tr></table><Pager :p="pg"/></div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 검문소: GET /api/web/checkpoints/rechecks
const rows = ref([])
async function load() { const l = await get('/api/web/checkpoints/rechecks'); if (Array.isArray(l)) rows.value = l }
load(); watch(() => props.tick, load)
const pg = usePage(rows, 10)
</script>
