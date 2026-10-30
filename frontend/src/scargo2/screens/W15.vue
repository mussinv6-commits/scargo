<template>
<section class="scr" id="W-15">
<!-- 26.10.28 구분 셀렉트 박스 → 탭 (다른 메뉴와 같은 모양: 흰 상자 안에 탭 + 내용) -->
<div class="tab-box"><div class="w-tabs" role="tablist"><button v-for="t in TABS" :key="t[0]" type="button" :class="{ on: tab === t[0] }" @click="tab = t[0]">{{ t[1] }}<span v-if="(t[0] === 'v' ? rows : crows).length" class="n">{{ (t[0] === 'v' ? rows : crows).length }}</span></button></div>
<div class="card"><h3>{{ tab === 'v' ? '차량 점검표 (운행 전 점검)' : '컨테이너 점검표 (출발 전 점검)' }}</h3><template v-if="tab === 'v'"><table :style="pg.reset"><thead><tr><th>날짜</th><th>차량</th><th>점검자</th><th>종합</th><th>브레이크</th><th>타이어</th><th>등화</th><th>적재 고정</th><th>메모</th></tr></thead>
<tbody><tr v-for="(r, i) in pg.rows" :key="i"><td>{{ ymd(r.date) }}</td><td>{{ r.vehicleNo }}</td><td>{{ r.inspector || '—' }}</td><td><span :class="'b ' + (['OK','PASS'].includes(r.overall) ? 'ok' : r.overall ? 'crit' : 'gray')">{{ stl(r.overall) }}</span></td><td>{{ stl(r.brake) }}</td><td>{{ stl(r.tire) }}</td><td>{{ stl(r.light) }}</td><td>{{ stl(r.securement) }}</td><td class="l memo">{{ memo(r.memo) }}</td></tr>
<tr v-if="!rows.length"><td colspan="9" class="meta">점검 기록이 없습니다. (기사 앱 운행 전 점검에서 등록)</td></tr></tbody></table><Pager :p="pg"/></template>
<template v-else><table :style="pgC.reset"><thead><tr><th class="c">점검 시각</th><th>컨테이너</th><th>씰 번호 (앱 입력)</th><th class="c">씰 대조</th><th>오더</th><th>점검자</th><th class="c">종합</th><th>불량 항목</th><th>메모</th></tr></thead>
<tbody><tr v-for="r in pgC.rows" :key="r.id"><td class="c">{{ md(r.at) }}</td><td class="code-cell">{{ r.containerNo || '—' }}</td><td class="code-cell" :title="r.sealAt ? SST[r.sealStage] + ' · ' + md(r.sealAt) : ''">{{ r.sealNo || '—' }}<div v-if="r.sealMatch === 'MISMATCH' && r.registeredSealNo" class="meta">등록 씰 {{ r.registeredSealNo }}</div><div v-else-if="r.sealNo" class="meta">{{ SST[r.sealStage] || '' }}</div></td><td class="c"><span :class="'b ' + SM[r.sealMatch || 'NONE'][1]">{{ SM[r.sealMatch || 'NONE'][0] }}</span></td><td>{{ r.orderNo || '—' }}</td><td>{{ r.inspector || '—' }}</td><td><span :class="'b ' + (r.overall === 'PASS' ? 'ok' : 'crit')">{{ r.overall === 'PASS' ? '양호' : '불량' }}</span></td><td class="l">{{ bad(r.items) }}</td><td class="l memo">{{ memo(r.memo) }}</td></tr>
<tr v-if="!crows.length"><td colspan="9" class="meta">컨테이너 점검 기록이 없습니다. (기사 앱 출발 전 점검에서 등록)</td></tr></tbody></table><Pager :p="pgC"/></template></div></div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC, stl } from '../api.js'
import { authState } from '@/auth/authState.js'
import { useRoute, useRouter } from 'vue-router'
const props = defineProps(['tick', 'role'])
// 차량 점검표: GET /api/web/checklists (운송사는 자기 차량만)
const rows = ref([])
// v13: 서버가 날짜를 숫자(epoch ms)로 줄 때도 YYYY-MM-DD 로 표시
const ymd = (v) => { if (v == null || v === '') return '—'; if (typeof v === 'number') { const d = new Date(v); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') } return String(v).slice(0, 10) }
// fix_1543: 컨테이너 점검표 탭
// 26.10.28 QA#11 구분을 주소(?tab=c)에 남겨 상단 메뉴명이 '차량 점검표' / '컨테이너 점검표'로 바뀌게 (ScLayout)
const route = useRoute(), router = useRouter()
// 26.10.28 메모에 앱이 붙이는 '[앱 추가항목]' 표시는 지우고 내용만 보여 준다
const memo = (v) => String(v || '').replace(/\[\s*앱\s*추가\s*항목\s*\]\s*:?\s*/g, '').trim()
const TABS = [['v', '차량 점검표'], ['c', '컨테이너 점검표']]
const tab = ref(route.query.tab === 'c' ? 'c' : 'v'), crows = ref([])
watch(tab, (t) => router.replace({ query: { ...route.query, tab: t } }))
const CL = { numberSeal: '번호·씰 대조', sealIntact: '씰 상태', doors: '문·잠금장치', frontWall: '전면벽', sideWalls: '좌우 측벽', roof: '천장·지붕', floor: '바닥', undercarriage: '하부 구조', leakOdor: '누수·오염·악취', twistLock: '샤시 트위스트록', placard: '위험물 표지' }
// 26.10.26: 앱 저장 시 함께 기록되는 웹 7-point 키(front_wall 등)도 같은 이름으로 묶어 중복 없이 표시
Object.assign(CL, { front_wall: '전면벽', left_side: '좌우 측벽', right_side: '좌우 측벽' })
const bad = (s) => { try { const o = JSON.parse(s || '{}'); const b = [...new Set(Object.keys(o).filter(k => o[k] === false).map(k => CL[k] || k))]; return b.length ? b.join(', ') : '없음' } catch { return '—' } }
// 26.10.26: 기사가 앱(T-02 인수 · 허브 인계)에서 입력한 씰을 컨테이너 번호와 짝지어 표시
const SM = { MATCH: ['일치', 'ok'], MISMATCH: ['불일치', 'crit'], UNREGISTERED: ['씰 미등록', 'warn'], NONE: ['미입력', 'gray'] }
const SST = { PICKUP: '인수(T-02) 입력', HANDOVER: '허브 인계 입력' }
const pgC = usePage(crows, 10)
async function load() { const l = await get('/api/web/checklists'); if (Array.isArray(l)) rows.value = l; const c = await get('/api/web/checklists/containers'); if (Array.isArray(c)) crows.value = c }
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pg = usePage(rows, 10)
</script>
<style scoped>
.code-cell{font-family:Consolas,monospace}
/* 26.10.28 메모 · 불량 항목은 좌측 정렬 */
.sc2 td.l,.sc2 td.memo{text-align:left !important}
.code-cell .meta{font-family:inherit;font-size:11px;color:#6b7590}
</style>
