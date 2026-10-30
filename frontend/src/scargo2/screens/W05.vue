<template>
<section class="scr" id="W-05">
<!-- 26.10.28 센터 셀렉트 박스 → 탭 (다른 메뉴와 같은 모양) -->
<div v-if="role !== 'CENTER_OP' && centers.length" class="w05-ctr"><span class="lb">센터</span><div class="ctr-tabs" role="tablist"><button v-for="c in centers" :key="c.centerId" type="button" :class="{ on: cid === c.centerId }" @click="pickCenter(c.centerId)">{{ c.name }}</button></div></div>
<!-- 26.10.28 QA#17 지역센터 관제: 도착 예정 지선 / 최근 운행 진행 / 문제 / 지선 봉인 대조 / 이 센터로 올 박스를 탭으로 -->
<div class="tab-box"><div class="w-tabs" role="tablist"><button v-for="t in TABS" :key="t[0]" type="button" :class="{ on: tab === t[0] }" @click="tab = t[0]">{{ t[1] }}<span v-if="cnt(t[0])" class="n">{{ cnt(t[0]) }}</span></button></div><!-- 26.10.28 nav 태그는 sc2.css 에서 사이드바로 꾸며져서 div 로 -->
<div v-if="tab === 'inc'" class="card"><h3>도착 예정 지선 <span class="meta">· {{ cname }}</span></h3><table :style="pgInc.reset"><thead><tr><th>운행</th><th>차량</th><th>상태</th><th>출발</th><th>롤테이너</th></tr></thead><tbody><tr v-for="t in pgInc.rows" :key="t.tripId"><td>LH#{{ t.tripId }}</td><td>{{ t.vehicleNo }}</td><td><span class="b gray">{{ stl(t.status) }}</span></td><td>{{ t.departedAt ? hm(t.departedAt) : '예정 ' + hm(t.plannedDepartAt) }}</td><td>{{ t.units }}</td></tr><tr v-if="!m.incoming.length"><td colspan="5" class="meta">없음</td></tr></tbody></table><Pager :p="pgInc"/></div>
<div v-if="tab === 'latest'" class="card"><h3>최근 운행 진행<template v-if="m.latest && m.latest.tripId"><span class="meta"> · LH#{{ m.latest.tripId }}<template v-if="m.latest.vehicleNo"> · {{ m.latest.vehicleNo }}</template></span></template></h3><table><thead><tr><th>단계</th><th>진행</th><th>시각</th></tr></thead><tbody><template v-if="m.latest"><tr v-for="s in steps" :key="s[0]"><td>{{ s[0] }}</td><td><span :class="'b ' + (m.latest[s[1]] ? 'ok' : 'gray')">{{ m.latest[s[1]] ? '완료' : '대기' }}</span></td><td>{{ m.latest[s[1]] ? md(m.latest[s[1]]) : '—' }}</td></tr></template><tr v-else><td colspan="3" class="meta">없음</td></tr></tbody></table></div>
<div v-if="tab === 'prob'" class="card"><h3>문제</h3><table :style="pgProb.reset"><thead><tr><th>등급</th><th>내용</th></tr></thead><tbody><tr v-for="(p, i) in pgProb.rows" :key="i"><td><span :class="'b ' + lvc(p.severity)">{{ lv[p.severity] || p.severity }}</span></td><td class="l">{{ p.message }}</td></tr><tr v-if="!(m.problems || []).length"><td colspan="2" class="meta">미해결 문제 없음</td></tr></tbody></table><Pager :p="pgProb"/></div>
<div v-if="tab === 'seal'" class="card"><h3>지선 봉인 대조 <span class="meta">· 허브 출발 때 등록된 씰번호와 센터 도착 때 입력한 번호를 대조 (다르면 봉인 불일치 예외)</span></h3><table :style="pgSeal.reset"><thead><tr><th>운행</th><th>차량번호</th><th>등록된 씰번호</th><th>판정</th><th>확인자</th><th>확인 시각</th><th v-if="canConfirm">도착 봉인 입력</th></tr></thead><tbody><tr v-for="t in pgSeal.rows" :key="t.tripId"><td>LH#{{ t.tripId }}</td><td>{{ t.vehicleNo }}</td><td>{{ t.domesticSealNo || '—' }}</td><td><span v-if="!t.arrivalSealNo" class="b gray">미확인</span><span v-else-if="t.arrivalSealNo === t.domesticSealNo" class="b ok">일치</span><span v-else class="b crit">불일치</span><div v-if="t.arrivalSealNo && t.arrivalSealNo !== t.domesticSealNo" class="meta">현장 {{ t.arrivalSealNo }}</div><span v-if="t.arrivalChecks > 1" class="meta" style="margin-left:4px">입력 {{ t.arrivalChecks }}회</span></td><td>{{ t.arrivalCheckedBy || '—' }}</td><td>{{ t.arrivalCheckedAt ? md(t.arrivalCheckedAt) : '—' }}</td><td v-if="canConfirm"><input v-model="sin[t.tripId]" placeholder="봉인 번호" style="width:120px"> <button class="btn y" :disabled="!(sin[t.tripId] || '').trim()" @click="confirmSeal(t)">확인</button></td></tr><tr v-if="!sealTrips.length"><td :colspan="canConfirm ? 7 : 6" class="meta">출발한 지선 운행이 없습니다.</td></tr></tbody></table><Pager :p="pgSeal"/><span v-if="smsg" :class="'b ' + (sok ? 'ok' : 'crit')">{{ smsg }}</span></div>
<div v-if="tab === 'exp'" class="card"><h3>이 센터로 올 박스 (운송오더별)</h3><table :style="pgExp.reset"><thead><tr><th>오더</th><th>컨테이너</th><th>박스</th></tr></thead><tbody><tr v-for="e in pgExp.rows" :key="e.orderNo"><td>{{ e.orderNo }}</td><td>{{ e.containerNo }}</td><td>{{ e.boxes }}</td></tr><tr v-if="!m.expected.length"><td colspan="3" class="meta">없음</td></tr></tbody></table><Pager :p="pgExp"/></div>
</div>
</section>
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
function pickCenter(id) { if (cid.value === id) return; cid.value = id; load() }
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
const pgProb = usePage(() => m.value.problems || [], 10)
const TABS = [['inc', '① 도착 예정 지선'], ['latest', '② 최근 운행 진행'], ['prob', '③ 문제'], ['seal', '④ 지선 봉인 대조'], ['exp', '⑤ 이 센터로 올 박스']] // 26.10.28 운영 센터 탭과 같은 모양
const tab = ref('inc')
const cnt = (k) => ({ inc: (m.value.incoming || []).length, prob: (m.value.problems || []).length, exp: (m.value.expected || []).length }[k] || 0)
</script>
<style scoped>
/* 26.10.28 센터 선택 탭 줄 */
.w05-ctr{display:flex;align-items:center;gap:10px;margin-bottom:12px;flex-wrap:wrap}
.w05-ctr .lb{font-size:14px;font-weight:700;color:#14213d}
/* 센터 줄은 아래 내용 탭(.w-tabs)과 모양은 같게, 선택 색만 남색으로 구분 (상단 고정 탭은 아래 내용 탭) */
.ctr-tabs{display:flex;gap:6px;flex-wrap:wrap}
.ctr-tabs button{display:inline-flex;align-items:center;border:1px solid #cfd6df;background:#fff;color:#1f2937;border-radius:8px;padding:7px 14px;font-family:inherit;font-size:13.5px;font-weight:600;cursor:pointer}
.ctr-tabs button:hover:not(.on){border-color:#14213d;color:#14213d}
.ctr-tabs button.on{background:#14213d;border-color:#14213d;color:#fff;font-weight:700}
</style>
