<template>
<section class="scr w07" id="W-07"><div class="grid g2" v-if="role === 'carrier'"><div class="card"><h3>운송오더 등록 <span class="meta">· 운송사 담당자 (등록 주체)</span></h3><table class="form">
<tr><td>컨테이너 · 씰</td><td><select v-model="sealPick" @change="pickSeal"><option :value="null">등록된 씰 선택</option><option v-for="x in sealOpts" :key="x.sealId" :value="x">{{ x.containerNo }} · {{ x.sealNo }}</option></select><div v-if="!sealOpts.length" class="meta" style="color:#B45309">⚠ 선택할 수 있는 등록 씰이 없습니다</div></td></tr>
<tr><td>화주</td><td><input v-model="f.shipper" placeholder="화주명"></td></tr>
<tr><td>출발 터미널</td><td><select v-model="f.terminalId"><option v-for="t in opt.terminals" :key="t.id" :value="t.id">{{ t.name }}</option></select></td></tr>
<tr><td>목적지</td><td><select v-model="f.hubId"><option v-for="h in opt.hubs" :key="h.id" :value="h.id">{{ h.name }}</option></select></td></tr>
<tr><td>반납 CY (D/O)</td><td><select v-model="f.returnCyId"><option :value="null">선택</option><option v-for="c in cys" :key="c.id" :value="c.id">{{ c.name }}{{ c.isDemo ? ' (데모)' : '' }}</option></select></td></tr>
<tr><td>반납 기한 (선사별)</td><td><input type="datetime-local" v-model="f.returnDueAt"><div v-if="!f.returnDueAt" class="meta" style="color:#B45309">⚠ 비워 두면 {{ dueDays }}일 후 18:00 으로 자동 지정됩니다 (기준정보)</div></td></tr>
<tr><td>배차 기사</td><td><select v-model="drv"><option :value="null">나중에 배차</option><option v-for="d in cDrivers" :key="d.accountId + d.vehicleNo" :value="d">{{ d.userName }} · {{ d.vehicleNo }}</option></select></td></tr></table>
<br><button class="btn" @click="save">저장</button> <span v-if="msg" :class="'b ' + (ok ? 'ok' : 'crit')">{{ msg }}</span></div>
<div class="card"><h3>포장명세서 · 배분지시 CSV 업로드</h3>
<div class="frow"><label>대상 오더</label><select v-model="sel"><option v-for="o in rows" :key="o.orderId" :value="o.orderId">{{ o.orderNo }} · {{ o.containerNo }}</option></select></div>
<label class="drop" style="display:block;margin-top:10px;cursor:pointer">📄 화주 파일 <b>파일 선택</b> (CSV: box_id, item_name, quantity, weight_kg, dest_center)<input type="file" accept=".csv,text/csv" style="display:none" @change="e => (file = e.target.files[0])"><br><span class="meta">{{ file ? file.name : '선택된 파일 없음' }}</span></label>
<div style="margin-top:10px"><button class="btn y" :disabled="!file || !sel" @click="upload">물품(items) 반영</button> <button class="btn o" @click="tpl">템플릿 다운로드</button></div>
<div v-if="up" style="margin-top:12px" class="meta">업로드 결과: 저장 <b>{{ up.saved }}</b>개 · 오류 {{ (up.errors || []).length }}<div v-for="(e, i) in (up.errors || []).slice(0, 5)" :key="i" style="color:#c62828">{{ e }}</div></div>
<template v-if="bx"><table style="margin-top:8px"><tr><th>박스 ID</th><th>품목</th><th>수량</th><th>목적지 센터</th></tr><tr v-for="x in bx.boxes.slice(0, 5)" :key="x.boxId"><td>{{ x.boxId }}</td><td>{{ x.itemName }}</td><td>{{ x.quantity }}</td><td>{{ x.dest }}</td></tr><tr v-if="bx.boxes.length > 5"><td colspan="4" class="meta">… {{ bx.boxes.length - 5 }}개 더</td></tr><tr><td colspan="4" class="meta">센터별 합계 <span v-for="c in bx.byCenter" :key="c.dest">{{ c.dest }} {{ c.boxes }} · </span></td></tr></table></template></div></div>
<div class="card" :style="role === 'carrier' ? 'margin-top:14px' : ''"><h3>운송오더 목록</h3><table :style="pg.reset"><tr><th>오더</th><th>컨테이너</th><th>화주</th><th>기사·차량</th><th>박스</th><th>상태</th><th>반납 CY</th><th>반납 상태</th><th>반납 기한</th><th>반납 기사</th></tr><tr v-for="r in pg.rows" :key="r.orderId" @click="sel = r.orderId" style="cursor:pointer"><td>{{ r.orderNo }}</td><td>{{ r.containerNo }}</td><td>{{ r.shipper || '—' }}</td><td>{{ r.driverName || '미배차' }} · {{ r.vehicleNo || '—' }}</td><td>{{ r.boxes }}</td><td><span class="b gray">{{ stl(r.status) }}</span></td><td>{{ r.returnCy || '—' }}</td><td><span v-if="r.returnStatus" class="b gray">{{ stl(r.returnStatus) }}</span><template v-else>—</template></td><td><template v-if="r.returnDueAt">{{ md(r.returnDueAt) }}</template><span v-else class="b warn">기한 없음</span> <span v-if="r.dueSoon" class="b warn">임박</span></td><td><template v-if="r.returnDriverName">{{ r.returnDriverName }}</template><span v-else-if="r.returnReadyAt" class="b ok">반납 가능</span><template v-else>—</template></td></tr><tr v-if="!rows.length"><td colspan="10" class="meta">운송오더가 없습니다.</td></tr></table><Pager :p="pg"/></div>
<div class="card" v-if="cur && role !== 'CONTROL'" style="margin-top:14px"><h3>반납 정보</h3>
<div class="grid g2"><div class="acts"><div class="act"><h4><i class="bi bi-pencil-square"></i> 반납 정보 변경</h4><table class="form">
<tr><td>반납 CY</td><td><select v-model="pl.returnCyId"><option v-for="c in cys" :key="c.id" :value="c.id">{{ c.name }}{{ c.isDemo ? ' (데모)' : '' }}</option></select></td></tr>
<tr><td>반납 기한</td><td><input type="datetime-local" v-model="pl.returnDueAt"></td></tr>
<tr><td>변경 사유 (필수)</td><td><input v-model="pl.reason" placeholder="예: 선사 반납 기한 연장"></td></tr></table>
<div class="ab cen"><button class="btn" :disabled="!pl.reason.trim() || cur.returnStatus === 'EMPTY_RETURNED'" @click="savePlan">반납 정보 변경</button>
</div><div class="meta">변경하면 기사·운송사에 알림.<br>반납 운행 중 CY 를 바꾸면 기사 확인이 필요한 '반납지 변경' 예외가 생깁니다.</div></div>
<div class="act"><h4><i class="bi bi-truck"></i> 반납 지시</h4><div class="meta">허브 하역 완료 + 빈 컨테이너 검사 OK 뒤<br>(운송사 승인 담당자)</div>
<div v-if="cur.damageNoted" class="meta" style="margin:6px 0"><span class="b warn">공컨 손상 기록</span> {{ cur.damageNote }} · 반납은 그대로 진행 (기록용)</div>
<div v-if="!cur.returnReadyAt" class="meta">아직 반납 가능 상태가 아닙니다<br>(허브 확인 대기).</div>
<div v-else-if="cur.returnStatus" class="meta">반납 운행 중 · {{ cur.returnDriverName }}</div>
<div v-else><select v-model="rd"><option :value="null">기사 선택</option><option v-for="d in cDrivers" :key="d.accountId + d.vehicleNo" :value="d.accountId">{{ d.userName }} · {{ d.vehicleNo }}</option></select>
 <button class="btn y" :disabled="!rd" @click="instruct">{{ cur.returnDriverName ? '다시 지시' : '반납 지시' }}</button> <span v-if="cur.returnDriverName" class="meta">현재 {{ cur.returnDriverName }}</span></div></div>
<div class="act danger"><h4><i class="bi bi-x-octagon"></i> 오더 취소</h4><div class="meta">기록·변경 이력 보존을 위해 삭제하지 않고 '취소' 상태로 바꿉니다</div>
<button class="btn o" :disabled="cur.status === 'CANCELED' || !!cur.returnStatus || ['ARRIVED','HANDED_OVER'].includes(cur.status)" @click="askCancel = true">{{ cur.status === 'CANCELED' ? '취소된 오더' : '오더 취소' }}</button></div>
<div v-if="cur.returnReceiverName" class="meta" style="margin-top:8px">반납 완료 · 인수자 {{ cur.returnReceiverName }} · EIR {{ cur.returnEirNo }}</div>
<div class="act"><h4><i class="bi bi-paperclip"></i> 반납 첨부</h4><div class="meta">선택 (CY EIR IN · 손상 확인서)</div>
<div v-for="a in atts" :key="a.attachmentId" class="meta">{{ a.kind === 'EIR_IN' ? 'EIR IN' : '손상 확인서' }} · <a :href="API_BASE + a.fileUrl" target="_blank">파일</a> · {{ a.uploadedBy }} {{ md(a.uploadedAt) }}</div>
<div v-if="role === 'carrier'"><select v-model="ak"><option value="EIR_IN">EIR IN</option><option value="DAMAGE_CONFIRM">손상 확인서</option></select>
<label class="btn o" style="cursor:pointer">{{ af ? af.name : '파일 선택' }}<input type="file" style="display:none" @change="e => (af = e.target.files[0])"></label> <button class="btn" :disabled="!af" @click="uploadAtt">첨부 올리기</button></div></div>
<span v-if="pmsg" :class="'b ' + (pok ? 'ok' : 'crit')" style="grid-column:1 / -1;justify-self:start">{{ pmsg }}</span></div>
<div><h3 class="hist">변경 이력</h3><table :style="pgH.reset"><tr><th>항목</th><th>이전</th><th>변경</th><th>사유</th><th>변경자</th><th>시각</th><th>기사 확인</th></tr>
<tr v-for="h in pgH.rows" :key="h.changeId"><td>{{ h.field === 'CY' ? '반납 CY' : '반납 기한' }}</td><td>{{ h.oldValue || '—' }}</td><td>{{ h.newValue }}</td><td>{{ h.reason }}</td><td>{{ h.changedBy || '—' }}</td><td>{{ md(h.changedAt) }}</td><td><template v-if="h.needsConfirm"><span v-if="h.driverConfirmedAt" class="b ok">확인 {{ hm(h.driverConfirmedAt) }}</span><span v-else class="b warn">대기</span></template><template v-else>—</template></td></tr>
<tr v-if="!hist.length"><td colspan="7" class="meta">변경 이력이 없습니다.</td></tr></table><Pager :p="pgH"/></div></div></div>
<!-- fix_1530: 운송사·관제가 오더별 현장 씰 입력값과 판정 확인 (GET /api/web/seals, 운송사는 자기 회사 오더만) -->
<div class="card" v-if="cur" style="margin-top:14px"><h3>씰 확인 이력 <span class="meta">· {{ cur.containerNo }}</span></h3>
<div v-if="sealRow" class="meta" style="margin-bottom:6px">등록 씰 <b>{{ sealRow.expectedSealNo }}</b> <span v-if="sealRow.openHold" class="b warn">개봉 보류</span></div>
<table v-if="sealEv.length"><tr><th>단계</th><th>현장 입력값</th><th>판정</th><th>입력자</th><th>시각</th></tr>
<tr v-for="e in sealEv" :key="e.key"><td>{{ e.stage }}</td><td>{{ e.observed || '—' }}</td><td><span :class="'b ' + (e.verdict === '일치' ? 'ok' : e.verdict === '미확인' ? 'info' : 'warn')">{{ e.verdict }}</span></td><td>{{ e.actor || '—' }}</td><td>{{ md(e.at) }}</td></tr></table>
<div v-else class="meta">아직 현장 씰 입력이 없습니다.</div></div>
<div v-if="askCancel && cur" class="mdl-bg" @click.self="askCancel = false"><div class="mdl"><div class="mh">운송오더 취소</div><div class="mb"><div><b>{{ cur.orderNo }}</b> 운송오더를 취소할까요?</div><div class="meta">기록은 지우지 않고 취소 상태로 남깁니다.</div></div>
<div class="mf"><button class="btn o" @click="askCancel = false">닫기</button><button class="btn r" @click="askCancel = false; cancelOrder()">오더 취소</button></div></div></div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC, stl } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
import { API_BASE } from '@/utils/apiBase.js'
import { toDate } from '@/utils/serverTime.js'
// 운송오더·물품: GET /api/web/orders (운송사는 자기 회사만), 등록 POST /api/web/carrier/orders, CSV POST /api/web/carrier/orders/{id}/boxes
const rows = ref([]), opt = ref({ drivers: [], terminals: [], hubs: [] }), drv = ref(null), sel = ref(null), file = ref(null), up = ref(null), bx = ref(null), msg = ref(''), ok = ref(false)
// fix_1330 씰 정책: 컨테이너·씰은 DB 에 등록된 정답 씰에서만 선택 (GET /api/web/carrier/seal-options). 직접 입력·새 씰 생성 없음
const sealOpts = ref([]), sealPick = ref(null)
function pickSeal() { f.containerNo = sealPick.value?.containerNo || ''; f.sealNo = sealPick.value?.sealNo || '' }
async function loadSeals() { const l = await get('/api/web/carrier/seal-options'); sealOpts.value = Array.isArray(l) ? l : [] }
// fix_1330: 반납 기한 기본값은 기준정보(W-11) RETURN_DUE_DEFAULT_DAYS
const dueDays = ref(7)
get('/api/web/settings').then(l => { const x = Array.isArray(l) && l.find(s => s.key === 'RETURN_DUE_DEFAULT_DAYS'); if (x) dueDays.value = x.value })
const f = reactive({ containerNo: '', shipper: '', sealNo: '', terminalId: null, hubId: null, returnCyId: null, returnDueAt: '' })
// v11: 반납 CY 목록 GET /api/web/return-cys, 반납 정보 변경 PUT /api/web/carrier/orders/{id}/return-plan, 이력 GET /api/web/orders/{id}/return-history, 반납 지시 POST .../return-instruction
const cys = ref([]), hist = ref([]), rd = ref(null), pmsg = ref(''), pok = ref(false)
const pl = reactive({ returnCyId: null, returnDueAt: '', reason: '' })
const sealOv = ref(null)
async function loadSealOv() { sealOv.value = await get('/api/web/seals') }
const sealRow = computed(() => (sealOv.value?.containerSeals || []).find(x => x.containerNo === cur.value?.containerNo) || null)
const STAGE = m => !m ? '현장 확인' : m.includes('T-02') ? '기사 인수(T-02)' : m.includes('인계') ? '허브 인계' : m.includes('도크') ? '도크' : '허브 확인(H-01)'
// fix_1630: 씰이 아니라 오더 기준 (GET /api/web/orders/{id}/seal-history) — 같은 씰의 예전 오더 이력 섞이지 않음
const sealHist = ref(null)
async function loadSealHist() { sealHist.value = sel.value ? await get('/api/web/orders/' + sel.value + '/seal-history') : null }
const sealEv = computed(() => {
  const h = sealHist.value; if (!h || h.orderId !== sel.value) return []
  const ev = (h.events || []).filter(e => e.type === 'INSPECTED' || e.type === 'LOST').map(e => ({ key: 'e' + e.eventId, stage: STAGE(e.memo), observed: e.observedSealNo, verdict: e.matches === true ? '일치' : e.matches === false ? '불일치' : '미확인', actor: e.actor, at: e.at }))
  const un = (h.unregistered || []).map(u => ({ key: 'u' + u.inputId, stage: u.stage === 'HANDOVER' ? '허브 인계' : '기사 인수(T-02)', observed: u.observedSealNo, verdict: '미등록', actor: u.actorName, at: u.at }))
  return [...ev, ...un].sort((a, b) => (toDate(b.at) || 0) - (toDate(a.at) || 0))
})
const cur = computed(() => rows.value.find(r => r.orderId === sel.value))
const p2 = (n) => String(n).padStart(2, '0')
const local = (t) => { if (!t) return ''; const d = new Date(t); return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + 'T' + p2(d.getHours()) + ':' + p2(d.getMinutes()) }
watch(cur, (c) => { if (!c) return; Object.assign(pl, { returnCyId: c.returnCyId, returnDueAt: local(c.returnDueAt), reason: '' }); rd.value = c.returnDriverId || null; pmsg.value = '' })
// v12: 반납 첨부 GET /api/web/orders/{id}/attachments, POST /api/web/carrier/orders/{id}/attachments (kind, file) — 올릴 수 있는 사람은 DB 가 판정
const atts = ref([]), ak = ref('EIR_IN'), af = ref(null)
async function uploadAtt() { const fd = new FormData(); fd.append('kind', ak.value); fd.append('file', af.value); const r = await req('POST', '/api/web/carrier/orders/' + cur.value.orderId + '/attachments', fd, true); pok.value = r.ok; pmsg.value = r.ok ? '첨부 저장' : r.msg; if (r.ok) { af.value = null; loadHist() } }
async function loadHist() { atts.value = sel.value ? (await get('/api/web/orders/' + sel.value + '/attachments')) || [] : []; hist.value = sel.value ? (await get('/api/web/orders/' + sel.value + '/return-history')) || [] : [] }
async function savePlan() {
  const c = cur.value, b = { reason: pl.reason.trim() }
  if (pl.returnCyId && pl.returnCyId !== c.returnCyId) b.returnCyId = pl.returnCyId
  if (pl.returnDueAt && pl.returnDueAt !== local(c.returnDueAt)) b.returnDueAt = pl.returnDueAt
  if (!b.returnCyId && !b.returnDueAt) { pok.value = false; pmsg.value = '바뀐 내용이 없습니다.'; return }
  const r = await req('PUT', '/api/web/carrier/orders/' + c.orderId + '/return-plan', b)
  pok.value = r.ok; pmsg.value = r.ok ? '변경됨 · 기사·운송사 알림' + (r.data.cyChangeId ? ' · 기사 확인 대기' : '') : r.msg
  if (r.ok) { pl.reason = ''; await load(); loadHist() }
}
// v12: DELETE /api/web/carrier/orders/{id} = 취소 처리 (행은 남김)
const askCancel = ref(false)
async function cancelOrder() {
  const r = await req('DELETE', '/api/web/carrier/orders/' + cur.value.orderId)
  pok.value = r.ok; pmsg.value = r.ok ? '취소됨 · 기록 보존' : r.msg; if (r.ok) load()
}
async function instruct() {
  const r = await req('POST', '/api/web/carrier/orders/' + cur.value.orderId + '/return-instruction', { driverAccountId: rd.value })
  pok.value = r.ok; pmsg.value = r.ok ? '반납 지시 · 기사 알림 보냄' : r.msg; if (r.ok) load()
}
const cDrivers = computed(() => opt.value.drivers.filter(d => d.userType === 'CONTAINER_DRIVER' && d.vehicleNo))
async function save() {
  if (!f.containerNo || !f.sealNo) { ok.value = false; msg.value = '등록된 씰을 선택하세요.'; return }
  const r = await req('POST', '/api/web/carrier/orders', { ...f, containerNo: f.containerNo.trim().toUpperCase(), driverAccountId: drv.value?.accountId, vehicleNo: drv.value?.vehicleNo })
  ok.value = r.ok; msg.value = r.ok ? '등록됨 · ' + r.data.orderNo + (r.data.returnDueDefaulted ? ' · ⚠ 반납 기한 미입력 → ' + md(r.data.returnDueAt) + ' 로 자동 지정 (아래에서 수정)' : '') : r.msg
  if (r.ok) { Object.assign(f, { containerNo: '', shipper: '', sealNo: '' }); drv.value = null; sealPick.value = null; loadSeals(); await load(); sel.value = r.data.orderId }
}
async function upload() { const fd = new FormData(); fd.append('file', file.value); const r = await req('POST', '/api/web/carrier/orders/' + sel.value + '/boxes', fd, true); up.value = r.ok ? r.data : { saved: 0, errors: [r.msg] }; load() }
function tpl() { window.open(API_BASE + '/api/web/carrier/boxes-template', '_blank') }
watch(sel, async (v) => { bx.value = v ? await get('/api/web/orders/' + v + '/boxes') : null; loadHist(); loadSealOv(); loadSealHist() })
async function load() {
  if (props.role === 'carrier' && !sealOpts.value.length) loadSeals()
  if (props.role === 'carrier' && !opt.value.terminals.length) { const o = await get('/api/web/carrier/form-options'); if (o) { opt.value = o; f.terminalId = o.terminals[0]?.id; f.hubId = o.hubs[0]?.id } }
  if (!cys.value.length) cys.value = (await get('/api/web/return-cys')) || []
  if (props.role === 'ADMIN' && !opt.value.drivers.length) { /* ADMIN 은 기사 선택지 없음 (지시는 운송사) */ }
  const o = await get('/api/web/orders'); if (Array.isArray(o)) { rows.value = o; if (!sel.value && o.length) sel.value = o[0].orderId }
}
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pg = usePage(rows, 10)
const pgH = usePage(hist, 5)
</script>
