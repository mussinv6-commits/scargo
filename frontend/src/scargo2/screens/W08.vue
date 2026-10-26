<template>
<section class="scr w08" id="W-08"><div class="grid"><div class="card"><h3>지선 배차 목록</h3><table :style="pg.reset"><tr><th>운행</th><th>도착 센터</th><th>기사 · 차량</th><th>롤테이너</th><th>출고 도크</th><th>출발 예정</th><th>상태</th></tr>
<tr v-for="r in pg.rows" :key="r.tripId" @click="pick(r)" style="cursor:pointer"><td>LH#{{ r.tripId }}</td><td>{{ r.dest }}</td><td>{{ r.driverName || '미배정' }} · {{ r.vehicleNo }}</td><td>{{ r.units }}/{{ r.planned ?? 0 }}</td><td>{{ r.outDock || '—' }}</td><td>{{ md(r.plannedDepartAt) }}</td><td><span class="b gray">{{ stl(r.status) }}</span></td></tr><tr v-if="!rows.length"><td colspan="7" class="meta">지선 운행이 없습니다.</td></tr></table><Pager :p="pg"/></div>
<div class="card" v-if="role === 'carrier'"><h3>배차 확정 <span class="meta">· 운송사 담당자</span></h3><table class="form two">
<tr><td>출발 허브</td><td><select v-model="f.fromCenterId"><option v-for="h in opt.hubs" :key="h.id" :value="h.centerId">{{ h.name }}</option></select></td></tr>
<tr><td>도착 센터</td><td><select v-model="f.toCenterId"><option v-for="c in opt.centers" :key="c.id" :value="c.id">{{ c.name }}</option></select></td></tr>
<tr><td>차량 (일반화물차)</td><td><select v-model="f.vehicleNo"><option v-for="v in gVeh" :key="v.vehicleNo" :value="v.vehicleNo">{{ v.vehicleNo }} · {{ v.truckType }}</option></select></td></tr>
<tr><td>지선기사</td><td><select v-model="f.driverAccountId"><option :value="null">미배정</option><option v-for="d in lDrivers" :key="d.accountId" :value="d.accountId">{{ d.userName }}</option></select></td></tr>
<tr><td>계획 롤테이너</td><td><input type="number" min="0" v-model.number="f.plannedUnits"></td></tr>
<tr><td>출고 도크</td><td><select v-model="p.dockId"><option :value="null">나중에</option><option v-for="d in opt.outDocks" :key="d.id" :value="d.id">{{ d.name }}</option></select></td></tr>
<tr><td>출발 예정</td><td><input type="datetime-local" v-model="p.plannedDepartAt"></td></tr></table>
<div class="fbtns cen"><button class="btn y" @click="create">배차 확정</button> <button class="btn o" :disabled="!cur" @click="plan">선택 운행 도크·시각 저장{{ cur ? ' (LH#' + cur.tripId + ')' : '' }}</button> <span v-if="msg" :class="'b ' + (ok ? 'ok' : 'crit')">{{ msg }}</span></div></div></div>
<div class="card" v-if="role === 'carrier'" style="margin-top:14px"><h3>공컨 반납 지시 <span class="meta">· 허브 하역 완료 + 공컨 점검 OK = 반납 가능</span></h3><table :style="pgRd.reset"><tr><th>오더</th><th>컨테이너</th><th>반납 CY</th><th>반납 기한</th><th>반납 가능</th><th>반납 기사</th><th></th></tr>
<tr v-for="o in pgRd.rows" :key="o.orderId"><td>{{ o.orderNo }}</td><td>{{ o.containerNo }}</td><td>{{ o.returnCy || '앱에서 선택' }}</td><td>{{ md(o.returnDueAt) }} <span v-if="o.dueSoon" class="b warn">임박</span></td><td>{{ md(o.returnReadyAt) }}</td>
<td><select v-model="pickD[o.orderId]"><option :value="undefined">기사 선택</option><option v-for="d in cDrivers" :key="d.accountId + d.vehicleNo" :value="d.accountId">{{ d.userName }} · {{ d.vehicleNo }}</option></select></td>
<td><button class="btn y" :disabled="!pickD[o.orderId]" @click="instruct(o)">{{ o.returnDriverName ? '다시 지시 (현재 ' + o.returnDriverName + ')' : '반납 지시' }}</button></td></tr>
<tr v-if="!ready.length"><td colspan="7" class="meta">반납 가능한 오더가 없습니다.</td></tr></table><Pager :p="pgRd"/><span v-if="rmsg" :class="'b ' + (rok ? 'ok' : 'crit')">{{ rmsg }}</span></div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC, stl } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 지선 배차: GET /api/web/branch (권한별 범위). 운송사: POST /api/web/carrier/branch (배차 확정), PUT /api/web/carrier/branch/{id}/plan (출고 도크·출발 예정)
const rows = ref([]), opt = ref({ hubs: [], centers: [], vehicles: [], drivers: [], outDocks: [] }), cur = ref(null), msg = ref(''), ok = ref(false)
const f = reactive({ fromCenterId: null, toCenterId: null, vehicleNo: null, driverAccountId: null, plannedUnits: 0 }), p = reactive({ dockId: null, plannedDepartAt: '' })
const gVeh = computed(() => opt.value.vehicles.filter(v => v.category === 'GENERAL_TRUCK'))
const lDrivers = computed(() => opt.value.drivers.filter(d => d.userType === 'BRANCH_DRIVER'))
function pick(r) { cur.value = r }
// v11: 반납 가능 오더 (GET /api/web/orders 중 returnReadyAt 있고 반납 시작 전) → POST /api/web/carrier/orders/{id}/return-instruction
const orders = ref([]), pickD = reactive({}), rmsg = ref(''), rok = ref(false)
const ready = computed(() => orders.value.filter(o => o.returnReadyAt && !o.returnStatus && o.status !== 'CANCELED'))
const cDrivers = computed(() => opt.value.drivers.filter(d => d.userType === 'CONTAINER_DRIVER' && d.vehicleNo))
async function instruct(o) {
  const r = await req('POST', '/api/web/carrier/orders/' + o.orderId + '/return-instruction', { driverAccountId: pickD[o.orderId] })
  rok.value = r.ok; rmsg.value = r.ok ? o.containerNo + ' 반납 지시 · 기사 알림 보냄' : r.msg; load()
}
async function plan(id) {
  const r = await req('PUT', '/api/web/carrier/branch/' + (typeof id === 'number' ? id : cur.value.tripId) + '/plan', { dockId: p.dockId, plannedDepartAt: p.plannedDepartAt || null })
  ok.value = r.ok; msg.value = r.ok ? '저장됨' : r.msg; load()
}
async function create() {
  const r = await req('POST', '/api/web/carrier/branch', f); ok.value = r.ok; msg.value = r.ok ? '배차 확정 · LH#' + r.data.tripId : r.msg
  if (r.ok && (p.dockId || p.plannedDepartAt)) await plan(r.data.tripId); else load()
}
async function load() {
  if (props.role === 'carrier' && !opt.value.hubs.length) { const o = await get('/api/web/carrier/form-options'); if (o) { opt.value = o; f.fromCenterId = o.hubs[0]?.centerId; f.toCenterId = o.centers[0]?.id } }
  const l = await get('/api/web/branch'); if (Array.isArray(l)) rows.value = l
  if (props.role === 'carrier') { const o = await get('/api/web/orders'); if (Array.isArray(o)) orders.value = o }
}
load(); watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pg = usePage(rows, 10)
const pgRd = usePage(ready, 5)
</script>
