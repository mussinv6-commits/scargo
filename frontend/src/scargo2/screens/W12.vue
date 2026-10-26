<template>
<section class="scr" id="W-12">
<div class="card" v-if="role === 'ADMIN' || role === 'CONTROL'" style="margin-bottom:14px"><h3><i class="bi bi-sign-turn-right"></i> 통과 시뮬레이션 <span class="meta">· 확정하면 저장되어 기사 앱 통행 요금에 표시</span></h3>
<table class="form two"><tr><td>차량 (운행 중)</td><td><select v-model="sim.vehicleNo"><option :value="null">선택</option><option v-for="v in so.vehicles" :key="v.vehicleNo + v.runNo" :value="v.vehicleNo">{{ v.vehicleNo }} · {{ v.runNo }} · {{ v.driverName || '기사 미배정' }}</option></select></td></tr>
<tr><td>톨게이트</td><td><select v-model="sim.tollgateId"><option v-for="g in so.tollgates" :key="g.id" :value="g.id">{{ g.name }}</option></select></td></tr>
<tr><td>차종</td><td><select v-model.number="sim.cls"><option v-for="r in so.rules" :key="r.cls" :value="r.cls">{{ r.name }} ({{ r.axles }})</option></select></td></tr>
<tr><td>주행 거리 (km)</td><td><input type="number" min="0" v-model.number="sim.km"></td></tr></table>
<div class="tollbar"><div class="fare"><span class="l">예상 통행료</span><span class="v">{{ simFare.toLocaleString() }}원</span></div><span v-if="simMsg" :class="'b ' + (simOk ? 'ok' : 'crit')">{{ simMsg }}</span><button class="btn y" :disabled="!sim.vehicleNo || busy" @click="ask = true">통과 확정</button></div></div>
<div v-if="ask" class="mdl-bg" @click.self="ask = false"><div class="mdl"><div class="mh">통과 확정</div><div class="mb"><div><b>{{ sim.vehicleNo }}</b> · {{ gateName }} · {{ sim.km }}km</div><div>통행료 <b>{{ simFare.toLocaleString() }}원</b>을 확정 저장할까요?</div></div><div class="mf"><button class="btn o" @click="ask = false">취소</button><button class="btn y" @click="confirmPass">확정</button></div></div></div><div class="grid g2"><div class="card"><h3>최근 톨게이트 통과</h3><table v-if="d.passage"><tr><td>차량</td><td><b>{{ d.passage.vehicleNo }}</b></td></tr><tr v-for="(v, k) in pinfo" :key="k"><td>{{ k }}</td><td>{{ v }}</td></tr></table><div v-else class="meta">통과 기록이 없습니다.</div></div>
<div class="card"><h3>최근 축중 계량</h3><table v-if="d.check"><tr><td>차량</td><td><b>{{ d.check.vehicleNo }}</b></td></tr><tr><td>총중량</td><td>{{ (d.check.totalKg || 0).toLocaleString() }} kg</td></tr><tr><td>축 수</td><td>{{ d.check.axles ?? '—' }}</td></tr><tr><td>판정</td><td><span :class="'b ' + (d.check.violation ? 'crit' : 'ok')">{{ d.check.violation ? '과적' : '정상' }}</span><template v-if="d.check.fineManwon"> · 과태료 {{ d.check.fineManwon }}만원</template></td></tr><tr><td>시각</td><td>{{ md(d.check.at) }} {{ d.check.station || '' }}</td></tr></table><div v-else class="meta">계량 기록이 없습니다.</div></div></div>
</section>
</template>
<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { get, req, hm, md, lv, lvc, EXL, SRC, stl } from '../api.js'
import { authState } from '@/auth/authState.js'
const props = defineProps(['tick', 'role'])
// 톨게이트: GET /api/web/toll/latest (최근 통과 + 계량)
const d = ref({ passage: null, check: null })
const pinfo = computed(() => { const p = d.value.passage || {}; return { '인식 번호판': p.plate || '—', 톨게이트: p.tollgate || '—', 요금: p.fare != null ? Number(p.fare).toLocaleString() + '원' + (p.demoFare ? ' (데모 금액)' : '') : '—', 상태: stl(p.status), 시각: md(p.at) } })
// fix_1600: 통과 시뮬레이션 (POST /api/web/toll/passages). simKey 로 같은 통과는 한 번만 저장
const so = ref({ vehicles: [], tollgates: [], rules: [] }), sim = reactive({ vehicleNo: null, tollgateId: null, cls: 5, km: 30, key: null }), ask = ref(false), busy = ref(false), simMsg = ref(''), simOk = ref(true)
const simFare = computed(() => { const r = so.value.rules.find(x => x.cls === sim.cls); return r ? Math.round((Number(r.base) + Number(r.perKm) * (sim.km || 0)) / 10) * 10 : 0 })
const gateName = computed(() => (so.value.tollgates.find(g => g.id === sim.tollgateId) || {}).name || '')
watch(() => [sim.vehicleNo, sim.tollgateId, sim.cls, sim.km], () => { sim.key = null })
async function confirmPass() {
  ask.value = false; busy.value = true
  if (!sim.key) sim.key = Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
  const r = await req('POST', '/api/web/toll/passages', { vehicleNo: sim.vehicleNo, tollgateId: sim.tollgateId, cls: sim.cls, km: sim.km, simKey: sim.key })
  busy.value = false; simOk.value = r.ok
  simMsg.value = r.ok ? (r.data.duplicate ? '이미 저장된 통과입니다' : '저장됨 · ' + Number(r.data.fare).toLocaleString() + '원' + (r.data.assignmentId ? '' : ' (운행 미연결)')) : r.msg
  if (r.ok) load()
}
get('/api/web/toll/sim-options').then(o => { if (o) { so.value = o; sim.tollgateId = o.tollgates[0]?.id ?? null } })
async function load() { const r = await get('/api/web/toll/latest'); if (r) d.value = r }
load(); watch(() => props.tick, load)
</script>
