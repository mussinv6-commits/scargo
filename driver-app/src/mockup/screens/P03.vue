<template>
<div class="ap"><section class="scr" id="P-03"><div class="top"><b>상차 확인</b></div><div class="meta">{{ live ? `LH#${t.assignmentId} · 센터 #${t.toCenterId}행` : (loaded && !loadErr ? '배정된 운행 없음' : '') }}</div>
<div v-if="!live" class="card" style="text-align:center;padding:22px 14px">
  <div v-if="!loaded" class="meta">불러오는 중…</div>
  <template v-else-if="loadErr"><b>운행 정보를 불러오지 못했습니다</b><div class="meta" style="margin:6px 0 12px">서버 연결을 확인한 뒤 다시 시도해 주세요.</div><button class="btn" @click="load">다시 불러오기</button></template>
  <template v-else><b>상차할 지선 운행이 없습니다</b><div class="meta" style="margin:6px 0 12px">허브에서 운행이 배정되면 여기에서 롤테이너를 스캔합니다.</div><button class="btn" @click="$router.push('/m/P-01')">지선 운행 보기</button></template>
</div>
<template v-else>
<div class="cam" @click="scan" style="cursor:pointer;height:160px">📷 롤테이너 바코드 스캔 (탭)</div>
<div class="card"><div style="display:flex;justify-content:space-between"><b>수량</b><span id="cnt" :class="'b ' + (k >= total ? 'ok' : 'warn')">{{ k }} / {{ total }}</span></div><div class="meta">운행별 예정 롤테이너 수 = 실제 상차 수</div></div>
<div id="list"><div v-for="(x, i) in ids" :key="x.id" class="chk"><span>{{ x.id }} <span class="meta">{{ x.meta }}</span></span><span v-if="x.loaded" class="b ok">상차 ✓</span><span v-else class="b warn">대기</span></div></div>
<div v-if="errShow" class="card" style="background:#fde4e4;border-color:#f5b5b5" id="err"><b style="color:#c62828">⚠ {{ /목적지/.test(errMsg) ? '목적지 불일치' : '확인이 필요합니다' }}</b><!-- 26.10.29 다른 오류도 '목적지 불일치'로 보이던 문제 --><div class="meta">{{ errMsg }}</div></div>
<template v-if="live"><label>국내 운송 씰 번호 <b style="color:#c62828">*</b></label><input v-model="seal" placeholder="예: DS-77001 (필수)"></template>
<button class="btn" id="done" :style="{ opacity: k >= total ? 1 : 0.4 }" @click="done">상차 완료 · 봉인 (LOADED)</button></template></section></div>
</template>
<script setup>
import { errText } from '../../lib/api' // 26.10.29 QA: 오류는 기사용 문구로
import { askInput } from '../../lib/ask'
// P-03 실데이터: GET /api/app/branch/current, POST /{id}/scan(type=LOAD), /{id}/confirm-load, /{id}/depart(sealNo 필수 → shipment_legs.domestic_seal_no → 웹 W-04)
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../lib/api'
const router = useRouter()
const live = ref(false), t = ref({}), seal = ref(''), errShow = ref(false), errMsg = ref('RT-G03 목적지 경기 ≠ 운행 목적지 서울 → 상차 불가')
const ids = ref([]), loaded = ref(false), loadErr = ref('')
const k = computed(() => ids.value.filter(x => x.loaded).length)
const total = computed(() => t.value.plannedUnits || ids.value.length)
async function load() {
  loadErr.value = ''
  try { const r = await api('/api/app/branch/current'); if (!r.trip?.assignmentId) { live.value = false; return } t.value = r.trip; ids.value = (r.units || []).map(u => ({ id: u.unitId, meta: `센터 #${u.destCenterId}행 · 박스 ${u.boxCount}개`, loaded: ['LOADED', 'UNLOADED'].includes(u.status) })); live.value = true } catch (e) { live.value = false; loadErr.value = errText(e) } finally { loaded.value = true }
}
async function scan() {
  if (!live.value) return
  const unitId = await askInput({ title: '롤테이너 스캔', message: '바코드를 읽지 못하면 롤테이너에 붙은 번호를 직접 입력하세요.', label: '롤테이너 바코드 (unit_id)', placeholder: '예: RT-G03', okText: '상차' }); if (!unitId) return /* 26.10.29 QA: 기본 입력창 → 앱 팝업 */
  try { await api(`/api/app/branch/${t.value.assignmentId}/scan`, { method: 'POST', body: { type: 'LOAD', unitId } }); errShow.value = false; await load() } catch (e) { errMsg.value = errText(e); errShow.value = true }
}
async function done() {
  if (k.value < total.value) return
  if (!live.value) return
  if (!seal.value.trim()) { errMsg.value = '국내 운송 씰 번호를 입력하세요.'; errShow.value = true; return }
  if (!window.confirm('상차·봉인 후 출발할까요? 출발하면 되돌릴 수 없습니다.')) return
  try { await api(`/api/app/branch/${t.value.assignmentId}/confirm-load`, { method: 'POST', body: {} }) } catch { /* 이미 LOADED */ }
  try { await api(`/api/app/branch/${t.value.assignmentId}/depart`, { method: 'POST', body: { sealNo: seal.value.trim() } }); router.push('/m/P-04') } catch (e) { errMsg.value = errText(e); errShow.value = true }
}
load()
</script>
