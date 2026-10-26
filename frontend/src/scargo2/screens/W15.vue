<template>
<section class="scr" id="W-15"><div class="card"><h3>점검표 (운행 전 점검) <span style="margin-left:auto;font-size:14px;font-weight:600;color:#4a5570">구분: <select v-model="tab"><option value="v">차량 점검표</option><option value="c">컨테이너 점검표</option></select></span></h3><template v-if="tab === 'v'"><table :style="pg.reset"><tr><th>날짜</th><th>차량</th><th>점검자</th><th>종합</th><th>브레이크</th><th>타이어</th><th>등화</th><th>적재 고정</th><th>메모</th></tr>
<tr v-for="(r, i) in pg.rows" :key="i"><td>{{ ymd(r.date) }}</td><td>{{ r.vehicleNo }}</td><td>{{ r.inspector || '—' }}</td><td><span :class="'b ' + (['OK','PASS'].includes(r.overall) ? 'ok' : r.overall ? 'crit' : 'gray')">{{ stl(r.overall) }}</span></td><td>{{ stl(r.brake) }}</td><td>{{ stl(r.tire) }}</td><td>{{ stl(r.light) }}</td><td>{{ stl(r.securement) }}</td><td class="l">{{ r.memo || '' }}</td></tr>
<tr v-if="!rows.length"><td colspan="9" class="meta">점검 기록이 없습니다. (기사 앱 운행 전 점검에서 등록)</td></tr></table><Pager :p="pg"/></template>
<template v-else><table :style="pgC.reset"><tr><th class="c">점검 시각</th><th>컨테이너</th><th>오더</th><th>점검자</th><th class="c">종합</th><th>불량 항목</th><th>메모</th></tr>
<tr v-for="r in pgC.rows" :key="r.id"><td class="c">{{ md(r.at) }}</td><td>{{ r.containerNo || '—' }}</td><td>{{ r.orderNo || '—' }}</td><td>{{ r.inspector || '—' }}</td><td><span :class="'b ' + (r.overall === 'PASS' ? 'ok' : 'crit')">{{ r.overall === 'PASS' ? '양호' : '불량' }}</span></td><td class="l">{{ bad(r.items) }}</td><td class="l">{{ r.memo || '' }}</td></tr>
<tr v-if="!crows.length"><td colspan="7" class="meta">컨테이너 점검 기록이 없습니다. (기사 앱 출발 전 점검에서 등록)</td></tr></table><Pager :p="pgC"/></template></div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC, stl } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 차량 점검표: GET /api/web/checklists (운송사는 자기 차량만)
const rows = ref([])
// v13: 서버가 날짜를 숫자(epoch ms)로 줄 때도 YYYY-MM-DD 로 표시
const ymd = (v) => { if (v == null || v === '') return '—'; if (typeof v === 'number') { const d = new Date(v); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') } return String(v).slice(0, 10) }
// fix_1543: 컨테이너 점검표 탭
const tab = ref('v'), crows = ref([])
const CL = { numberSeal: '번호·씰 대조', sealIntact: '씰 상태', doors: '문·잠금장치', frontWall: '전면벽', sideWalls: '좌우 측벽', roof: '천장·지붕', floor: '바닥', undercarriage: '하부 구조', leakOdor: '누수·오염·악취', twistLock: '샤시 트위스트록', placard: '위험물 표지' }
const bad = (s) => { try { const o = JSON.parse(s || '{}'); const b = Object.keys(o).filter(k => !o[k]).map(k => CL[k] || k); return b.length ? b.join(', ') : '없음' } catch { return '—' } }
const pgC = usePage(crows, 10)
async function load() { const l = await get('/api/web/checklists'); if (Array.isArray(l)) rows.value = l; const c = await get('/api/web/checklists/containers'); if (Array.isArray(c)) crows.value = c }
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pg = usePage(rows, 10)
</script>
