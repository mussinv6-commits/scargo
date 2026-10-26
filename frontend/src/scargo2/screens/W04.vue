<template>
<section class="scr" id="W-04">
<div class="panels hubp">
<div class="sub2">
<div class="card hb k-blue"><h3><span class="hi"><i class="bi bi-box-arrow-in-down"></i></span>들어올 것</h3><table :style="pgIn.reset"><tr><th>컨테이너</th><th>ETA</th></tr><tr v-for="x in pgIn.rows" :key="x.orderNo"><td>{{ x.containerNo }}</td><td><span v-if="x.arrivedAt" class="b ok">도착</span><template v-else>{{ hm(x.eta) }} <span v-if="x.delayMin > 0" class="b warn">+{{ x.delayMin }}분</span></template></td></tr><tr v-if="!b.inbound.length"><td colspan="2" class="meta">없음</td></tr></table><Pager :p="pgIn"/></div>
<div class="card hb k-gold"><h3><span class="hi"><i class="bi bi-gear-wide-connected"></i></span>처리 중</h3><div class="meta">하역 {{ w.unloaded }}/{{ w.boxes }} · 스캔 {{ w.scanned }}/{{ w.boxes }}</div><div class="bar" style="margin:6px 0 10px"><i :style="'width:' + (w.boxes ? Math.round(w.scanned * 100 / w.boxes) : 0) + '%'"></i></div><div class="meta">AMR 운반 {{ w.amrRunning }}대 가동 · {{ w.amrStopped }}대 정지</div>
<h4 class="subh">센터별 롤테이너 적재 <span class="meta">· 적재 완료 / 배분된 박스</span></h4><div class="cbars"><div v-for="p in b.ports" :key="p.name" class="cbar"><span class="cn">{{ p.name }}</span><div class="bar"><i :style="'width:' + (p.total ? Math.round(p.loaded * 100 / p.total) : 0) + '%;background:' + cc(p.name)"></i></div><b>{{ p.loaded }}/{{ p.total }}</b></div><div v-if="!b.ports.length" class="meta">없음</div></div></div>
</div>
<div class="card hb k-navy"><h3><span class="hi"><i class="bi bi-door-open"></i></span>도크</h3><div class="grid g2">
<div><h4 class="subh">도크 현황</h4><table :style="pgDk.reset"><tr><th>도크</th><th>운행</th><th>상태</th></tr><tr v-for="x in pgDk.rows" :key="x.dockId"><td>{{ x.kind === 'IN' ? '입고' : '출고' }} {{ x.no }}</td><td>{{ x.trip || '—' }}</td><td><span class="b gray">{{ stl(x.status) }}</span></td></tr><tr v-if="!b.docks.length"><td colspan="3" class="meta">없음</td></tr></table><Pager :p="pgDk"/></div>
<div><h4 class="subh">씰 <span class="meta">· 정답 씰 vs 현장 입력 (기사 인수·허브 인계)</span></h4><table :style="pgSl.reset"><tr><th>컨테이너</th><th>정답 씰</th><th>현장 입력값</th><th>판정</th><th>입력자</th><th>입력 시각</th></tr><tr v-for="x in pgSl.rows" :key="x.key"><td>{{ x.containerNo }}</td><td>{{ x.expected || '—' }}</td><td>{{ x.observed || '—' }}</td><td><span :class="'b ' + x.cls" :title="x.stage || ''">{{ x.verdict }}</span><span v-if="x.openHold" class="b crit" style="margin-left:4px">개봉 보류</span></td><td>{{ x.by || '—' }}</td><td>{{ x.at ? md(x.at) : '—' }}</td></tr><tr v-if="!sealRows.length"><td colspan="6" class="meta">없음</td></tr></table><Pager :p="pgSl"/></div></div></div>
<div class="card hb k-green"><h3><span class="hi"><i class="bi bi-truck"></i></span>나갈 것</h3><div class="grid g2">
<div><h4 class="subh">지선 출발 <span class="meta">· 센터별</span></h4><table :style="pgOut.reset"><tr><th>운행</th><th>도착 센터</th><th>상태</th><th>출발 봉인</th><th>도착 확인</th></tr><tr v-for="l in pgOut.rows" :key="l.tripId"><td>{{ l.tripId }}</td><td>{{ l.dest }}</td><td><span class="b gray">{{ stl(l.status) }}</span></td><td>{{ l.sealNo || '—' }}</td><td><template v-if="bs(l).arrivalSealNo">{{ bs(l).arrivalSealNo }} <span :class="'b ' + (bs(l).arrivalMatches ? 'ok' : 'crit')">{{ bs(l).arrivalMatches ? '일치' : '불일치' }}</span></template><span v-else class="meta">미확인</span></td></tr><tr v-if="!b.outbound.length"><td colspan="5" class="meta">없음</td></tr></table><Pager :p="pgOut"/></div>
<div><h4 class="subh">빈 컨테이너 반납</h4><table><tr><th>컨테이너</th><th>상태</th></tr><tr v-for="r in b.returns" :key="r.containerNo"><td>{{ r.containerNo }}</td><td><span class="b gray">{{ stl(r.status) }}</span></td></tr><tr v-if="!b.returns.length"><td colspan="2" class="meta">없음</td></tr></table></div></div></div>
<div class="card hb k-red"><h3><span class="hi"><i class="bi bi-exclamation-triangle"></i></span>문제</h3><div v-for="(p, i) in b.problems" :key="i" style="margin-top:6px"><span :class="'b ' + lvc(p.severity)">{{ lv[p.severity] || p.severity }}</span> {{ p.message }}</div><div v-if="!b.problems.length" class="meta">미해결 문제 없음</div></div>
</div>
<div class="card" style="margin-top:14px"><h3><i class="bi bi-box-seam"></i> 빈 컨테이너 반납 준비 <span class="meta">· 하역·검사 완료 후 운송사에 반납 가능 알림</span></h3><table :style="pgRR.reset"><tr><th>오더</th><th>컨테이너</th><th>상태</th><th>하역</th><th>빈 컨테이너 검사</th><th>반납 기한</th><th>반납</th><th v-if="canOp">처리</th></tr>
<tr v-for="o in pgRR.rows" :key="o.orderId"><td>{{ o.orderNo }}</td><td>{{ o.containerNo }}</td><td><span class="b gray">{{ stl(o.status) }}</span></td>
<td><span v-if="o.unloadedAt" class="b ok">완료 {{ hm(o.unloadedAt) }}</span><span v-else class="meta">대기</span></td>
<td><span v-if="o.inspection === 'OK'" class="b ok">OK</span><span v-else-if="o.inspection === 'DAMAGED'" class="b warn">손상 기록 · {{ o.damageNote }}</span><span v-else class="meta">미점검</span></td>
<td>{{ md(o.dueAt) }}</td><td><template v-if="o.returnDriverName">지시됨 · {{ o.returnDriverName }}</template><span v-else-if="o.returnReadyAt" class="b ok">반납 가능</span><template v-else>—</template></td>
<td v-if="canOp"><button class="btn o" v-if="!o.unloadedAt" @click="act(o, 'unload-complete')">하역 완료</button>
<template v-else-if="!o.returnDriverName"><button class="btn y" @click="act(o, 'empty-inspection', { result: 'OK' })">점검 OK</button> <span style="display:inline-block"><input v-model="dm(o).loc" placeholder="손상 위치 (예: 도어)" style="width:110px"> <input v-model="dm(o).type" placeholder="손상 유형 (예: 구멍)" style="width:110px">
<label class="btn o" style="cursor:pointer">{{ dm(o).file ? '사진 ✓' : '사진 (컨테이너 번호 보이게)' }}<input type="file" accept="image/*" style="display:none" @change="e => (dm(o).file = e.target.files[0])"></label>
<button class="btn" :disabled="!dm(o).loc.trim() || !dm(o).type.trim() || !dm(o).file" @click="damaged(o)">손상 기록</button></span></template></td></tr>
<tr v-if="!(b.returnReady || []).length"><td :colspan="canOp ? 8 : 7" class="meta">도착·인계된 컨테이너가 없습니다.</td></tr></table><Pager :p="pgRR"/><span v-if="msg" :class="'b ' + (ok ? 'ok' : 'crit')">{{ msg }}</span></div>
</section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC, stl } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 허브 관제: GET /api/web/hubs/{id}/board. HUB_OP 는 서버가 소속 허브만 허용
const hubId = ref(authState.user?.hubId || 1)
const b = ref({ inbound: [], docks: [], seals: [], work: {}, ports: [], outbound: [], returns: [], problems: [] })
const w = computed(() => { const x = { boxes: 0, unloaded: 0, scanned: 0, amrRunning: 0, amrStopped: 0, ...(b.value.work || {}) }; x.boxes = Math.max(x.boxes, x.unloaded, x.scanned); return x })
// v11: POST /api/web/hubs/orders/{id}/unload-complete · /empty-inspection (HUB_OP 소속 허브, ADMIN)
const canOp = computed(() => props.role === 'HUB_OP' || props.role === 'ADMIN'), dmg = reactive({}), msg = ref(''), ok = ref(false)
const dm = (o) => (dmg[o.orderId] ||= { loc: '', type: '', file: null })
// v12: 손상은 기록·알림·경고 예외만 (반납은 막지 않음). 위치·유형·사진 필수 → multipart
function damaged(o) { const d = dm(o), fd = new FormData(); fd.append('result', 'DAMAGED'); fd.append('damageLocation', d.loc.trim()); fd.append('damageType', d.type.trim()); fd.append('photo', d.file); act(o, 'empty-inspection', { result: 'DAMAGED' }, fd) }
async function act(o, path, body, form) {
  const r = form ? await req('POST', '/api/web/hubs/orders/' + o.orderId + '/' + path, form, true) : await req('POST', '/api/web/hubs/orders/' + o.orderId + '/' + path, body || {})
  ok.value = r.ok; msg.value = r.ok ? o.containerNo + (path === 'unload-complete' ? ' 하역 완료' : ' 점검 ' + body.result) + (r.data.returnReady ? ' · 반납 가능 (운송사 알림)' : '') : r.msg
  load()
}
// fix_1300: 씰 표는 GET /api/web/seals (정답 씰·마지막 현장 입력·판정, 정답 씰 미등록 입력 포함)
const sl = ref({ containerSeals: [], unregistered: [], branchSeals: [] })
async function load() {
  const [r, s] = await Promise.all([get('/api/web/hubs/' + hubId.value + '/board'), get('/api/web/seals')])
  if (r) b.value = r
  if (s) sl.value = { containerSeals: s.containerSeals || [], unregistered: s.unregistered || [], branchSeals: s.branchSeals || [] }
}
const sealRows = computed(() => [
  ...sl.value.unregistered.filter(u => !u.hubId || u.hubId === hubId.value).map(u => ({ key: 'u' + u.inputId, containerNo: u.containerNo, expected: null, observed: u.observedSealNo, verdict: '미등록', cls: 'warn', by: u.actorName, at: u.at, stage: u.stage === 'HANDOVER' ? '허브 인계' : '기사 인수' })),
  ...sl.value.containerSeals.map(x => ({ key: 's' + x.sealId, containerNo: x.containerNo, expected: x.expectedSealNo, observed: x.lastObservedSealNo, openHold: x.openHold,
    verdict: x.lastMatches === true ? '일치' : x.lastMatches === false ? '불일치' : '미확인', cls: x.lastMatches === true ? 'ok' : x.lastMatches === false ? 'crit' : 'gray', by: x.lastBy, at: x.lastAt, stage: x.lastMemo }))
])
const bsMap = computed(() => Object.fromEntries(sl.value.branchSeals.map(x => [String(x.tripId), x])))
const bs = (l) => bsMap.value[String(l.tripId).replace(/\D/g, '')] || {}
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pgRR = usePage(() => b.value.returnReady, 10)
const pgIn = usePage(() => b.value.inbound, 5)
const pgDk = usePage(() => b.value.docks, 10)
const pgSl = usePage(() => sealRows.value, 5)
// fix_1150: 센터별 게이지 색 = 시·도 상징 전용색 (서울 단청빨강, 인천 Incheon Blue, 경기 초록), 그 밖은 브랜드 금색
const cc = (n) => (/서울/.test(n) ? '#AE1932' : /인천/.test(n) ? '#005EB8' : /경기/.test(n) ? '#00A651' : '#C9962E')
const pgOut = usePage(() => b.value.outbound, 5)
</script>
