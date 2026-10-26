<template>
<section class="scr" id="W-05">
<div v-if="role !== 'CENTER_OP'" style="margin-bottom:10px">센터 <select v-model="cid" @change="load"><option v-for="c in centers" :key="c.centerId" :value="c.centerId">{{ c.name }}</option></select></div>
<div class="panels">
<div class="card"><h3>도착 예정 지선 <span class="meta">· {{ cname }}</span></h3><table :style="pgInc.reset"><tr><th>운행</th><th>차량</th><th>상태</th><th>출발</th><th>롤테이너</th></tr><tr v-for="t in pgInc.rows" :key="t.tripId"><td>LH#{{ t.tripId }}</td><td>{{ t.vehicleNo }}</td><td><span class="b gray">{{ stl(t.status) }}</span></td><td>{{ t.departedAt ? hm(t.departedAt) : '예정 ' + hm(t.plannedDepartAt) }}</td><td>{{ t.units }}</td></tr><tr v-if="!m.incoming.length"><td colspan="5" class="meta">없음</td></tr></table><Pager :p="pgInc"/></div>
<div class="card"><h3>최근 운행 진행</h3><div v-if="m.latest" class="steps"><div v-for="s in steps" :key="s[0]" :class="'st' + (m.latest[s[1]] ? ' done' : '')"><b>{{ s[0] }}</b><span>{{ m.latest[s[1]] ? md(m.latest[s[1]]) : '—' }}</span></div></div><div v-else class="meta">없음</div></div>
<div class="card"><h3>문제</h3><div v-for="(p, i) in m.problems" :key="i" style="margin-top:6px"><span :class="'b ' + lvc(p.severity)">{{ lv[p.severity] || p.severity }}</span> {{ p.message }}</div><div v-if="!m.problems.length" class="meta">미해결 문제 없음</div></div></div>
<div class="card" style="margin-top:14px"><h3>지선 봉인 대조 <span class="meta">· 허브 출발 입력 vs 센터 도착 확인 (다르면 봉인 불일치 예외)</span></h3><table :style="pgSeal.reset"><tr><th>운행</th><th>차량</th><th>출발 봉인</th><th>도착 확인</th><th>판정</th><th>확인자</th><th>확인 시각</th><th v-if="canConfirm">도착 봉인 입력</th></tr><tr v-for="t in pgSeal.rows" :key="t.tripId"><td>LH#{{ t.tripId }}</td><td>{{ t.vehicleNo }}</td><td>{{ t.domesticSealNo || '—' }}</td><td>{{ t.arrivalSealNo || '—' }}</td><td><span v-if="!t.arrivalSealNo" class="b gray">미확인</span><span v-else-if="t.arrivalSealNo === t.domesticSealNo" class="b ok">일치</span><span v-else class="b crit">불일치</span><span v-if="t.arrivalChecks > 1" class="meta" style="margin-left:4px">입력 {{ t.arrivalChecks }}회</span></td><td>{{ t.arrivalCheckedBy || '—' }}</td><td>{{ t.arrivalCheckedAt ? md(t.arrivalCheckedAt) : '—' }}</td><td v-if="canConfirm"><input v-model="sin[t.tripId]" placeholder="봉인 번호" style="width:120px"> <button class="btn y" :disabled="!(sin[t.tripId] || '').trim()" @click="confirmSeal(t)">확인</button></td></tr><tr v-if="!sealTrips.length"><td :colspan="canConfirm ? 8 : 7" class="meta">출발한 지선 운행이 없습니다.</td></tr></table><Pager :p="pgSeal"/><span v-if="smsg" :class="'b ' + (sok ? 'ok' : 'crit')">{{ smsg }}</span></div>
<div class="card" style="margin-top:14px"><h3>이 센터로 올 박스 (운송오더별)</h3><table :style="pgExp.reset"><tr><th>오더</th><th>컨테이너</th><th>박스</th></tr><tr v-for="e in pgExp.rows" :key="e.orderNo"><td>{{ e.orderNo }}</td><td>{{ e.containerNo }}</td><td>{{ e.boxes }}</td></tr><tr v-if="!m.expected.length"><td colspan="3" class="meta">없음</td></tr></table><Pager :p="pgExp"/></div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC, stl } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 지역센터 관제: GET /api/web/centers, /api/web/centers/{id}/monitor (CENTER_OP 는 서버가 소속 센터만 허용)
const centers = ref([]), cid = ref(authState.user?.centerId || null)
const m = ref({ incoming: [], latest: null, expected: [], problems: [], sealTrips: [] })
// fix_1300: 지선 봉인 대조. CENTER_OP(도착 센터)·ADMIN 이 도착 봉인 번호 입력 → POST /api/web/centers/trips/{id}/arrival-seal
const canConfirm = computed(() => props.role === 'CENTER_OP' || props.role === 'ADMIN')
const sealTrips = computed(() => m.value.sealTrips || [])
const sin = reactive({}), smsg = ref(''), sok = ref(false)
async function confirmSeal(t) {
  const r = await req('POST', '/api/web/centers/trips/' + t.tripId + '/arrival-seal', { sealNo: sin[t.tripId].trim() })
  sok.value = r.ok && r.data.arrivalMatches; smsg.value = !r.ok ? r.msg : r.data.arrivalMatches ? 'LH#' + t.tripId + ' 봉인 일치' : 'LH#' + t.tripId + ' 봉인 불일치 · 예외 등록·관제/운송사 알림'
  if (r.ok) sin[t.tripId] = ''
  load()
}
const cname = computed(() => (centers.value.find(c => c.centerId === cid.value) || {}).name || '')
const steps = [['출발', 'departedAt'], ['도착', 'arrivedAt'], ['봉인 확인', 'inspectedAt'], ['인수', 'receivedAt'], ['하차 완료', 'unloadedAt']]
async function load() {
  if (!centers.value.length) { const c = await get('/api/web/centers'); if (Array.isArray(c)) { centers.value = c.filter(x => x.type !== 'HUB' && x.type !== 'PORT'); if (!cid.value && centers.value.length) cid.value = centers.value[0].centerId } }
  if (cid.value) { const r = await get('/api/web/centers/' + cid.value + '/monitor'); if (r) m.value = r }
}
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pgInc = usePage(() => m.value.incoming, 5)
const pgExp = usePage(() => m.value.expected, 10)
const pgSeal = usePage(() => sealTrips.value, 5)
</script>
