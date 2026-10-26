<template>
<section class="scr dash" id="W-02">
<div class="grid g4">
<div class="card kc k-navy"><div class="ic"><i class="bi bi-box-seam"></i></div><div class="tx"><div class="v">{{ d.kpi.orders ?? 0 }}</div><div class="l">진행 중 운송오더</div><div class="s">컨테이너 기준</div></div></div>
<div class="card kc k-blue"><div class="ic"><i class="bi bi-truck"></i></div><div class="tx"><div class="v">{{ d.kpi.trips ?? 0 }}</div><div class="l">진행 중 지선 운행</div><div class="s">허브 → 물류센터</div></div></div>
<div class="card kc k-red"><div class="ic"><i class="bi bi-exclamation-octagon"></i></div><div class="tx"><div class="v">{{ d.kpi.open ?? 0 }}</div><div class="l">미해결 예외</div><div class="s">긴급 {{ d.kpi.crit ?? 0 }} · 경고 {{ d.kpi.warn ?? 0 }}</div></div></div>
<div class="card kc k-amber"><div class="ic"><i class="bi bi-hourglass-split"></i></div><div class="tx"><div class="v">{{ d.kpi.returnDueSoon ?? 0 }}</div><div class="l">반납 기한 임박</div><div class="s">빈 컨테이너</div></div></div>
</div>
<h3 class="sec">거점 현황 (거점 {{ d.sites.length }}곳)</h3>
<div class="grid g4" id="sites"><div v-for="s in d.sites" :key="s.centerId" :class="'card site ' + st(s)[0]"><div style="display:flex;justify-content:space-between"><b>{{ s.t }} {{ s.name }}</b><span :class="'b ' + st(s)[0]">{{ st(s)[1] }}</span></div><div class="meta" style="margin-top:8px">미해결 예외 긴급 {{ s.crit }} · 경고 {{ s.warn }}<br>최대 지연 {{ s.maxDelay }}분</div></div></div>
<div class="bottom"><div class="card"><h3>최근 예외</h3><table :style="pgR.reset"><tr><th class="c">등급</th><th class="c">유형</th><th>거점·대상</th><th class="c">상태</th></tr><tbody id="exmini"><tr v-for="e in pgR.rows" :key="e.exceptionId"><td><span :class="'b ' + lvc(e.severity)">{{ lv[e.severity] || e.severity }}</span></td><td><span class="b gray">{{ EXL[e.type] || e.type }}</span></td><td>{{ e.centerName || '' }} {{ e.message }}</td><td><span class="b gray">{{ stl(e.status) }}</span></td></tr><tr v-if="!d.recent.length"><td colspan="4" class="meta">예외가 없습니다.</td></tr></tbody></table><Pager :p="pgR"/></div>
<div class="card"><h3>오늘의 배송 내역<template v-if="f.containerNo"> (최근 컨테이너 {{ f.containerNo }})</template></h3><table><tr><th>단계</th><th>수량</th><th>진행</th></tr>
<tr v-for="r in flow" :key="r[0]"><td>{{ r[0] }}</td><td>{{ r[1] }} / {{ r[2] }}{{ r[3] }}</td><td><div class="bar"><i :style="'width:' + (r[2] ? Math.round(r[1] * 100 / r[2]) : 0) + '%'"></i></div></td></tr></table></div></div>
<div class="legend"><span class="lt"><i class="bi bi-info-circle"></i> 거점 색 기준</span>
<span class="chip"><i style="background:#2e9e5a"></i><b>정상</b> 미해결 예외 없음 · 지연 {{ d.warnDelayMin }}분 미만</span>
<span class="chip"><i style="background:#f08c00"></i><b>주의</b> 경고 예외 1건↑ 또는 {{ d.warnDelayMin }}분↑ 지연 차량</span>
<span class="chip"><i style="background:#d32f2f"></i><b>긴급</b> 긴급 예외 1건↑</span>
<span class="meta">기준 값은 기준정보에서 변경<template v-if="role === 'carrier'"> · 자기 운송사 데이터만 집계</template></span></div>
</section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC, stl } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 통합 대시보드: GET /api/web/dashboard (권한별 범위는 서버가 결정)
const d = ref({ kpi: {}, sites: [], recent: [], flow: {}, warnDelayMin: 30 })
const f = computed(() => d.value.flow || {})
const flow = computed(() => [['컨테이너 하역', f.value.unloaded || 0, f.value.total || 0, ' 박스'], ['박스 식별·분류', f.value.scanned || 0, f.value.total || 0, ''], ['롤테이너 적재', f.value.loaded || 0, f.value.total || 0, ''], ['지선 운행', f.value.departed || 0, f.value.trips || 0, ' 출발']])
function st(s) { if (s.crit > 0) return ['crit', '긴급']; if (s.warn > 0 || s.maxDelay >= d.value.warnDelayMin) return ['warn', '주의']; return ['ok', '정상'] }
async function load() { const r = await get('/api/web/dashboard'); if (r) d.value = r }
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pgR = usePage(() => d.value.recent, 5)
</script>
