<template>
  <div class="gate-page">
    <div class="gate-header">
      <h1>🔍 게이트 OCR 검사</h1>
      <p>
        CargoScan AI(YOLO11 탐지 + CRNN 문자 인식)가 화물차 번호판을 OCR로
        판독해서 게이트 통과 여부를 자동으로 검사합니다.
      </p>
      <div class="gate-live-scan-cta">
        <button class="gate-live-scan-btn" :disabled="liveScanBusy" @click="runLiveScan('ENTRY')">
          {{ liveScanBusy && scanPhase === 'ENTRY' ? 'ENTRY OCR 검사 중...' : '🔍 게이트인 (ENTRY OCR 검사)' }}
        </button>
        <span class="gate-live-scan-hint">{{ scanGuideText }}</span>
        <p v-if="liveScanError" class="gate-live-scan-error">⚠ {{ liveScanError }}</p>
        <div v-if="yardVehicles.length" class="gate-yard">
          <div v-for="v in yardVehicles" :key="v" class="gate-yard-row">
            <strong class="gate-yard-no">{{ v }}</strong>
            <span class="gate-yard-state">{{ yardStateText(v) }}</span>
            <button
              class="gate-live-scan-btn gate-yard-exit"
              :disabled="liveScanBusy || !canExit(v)"
              @click="runLiveScan('EXIT', v)"
            >
              {{ liveScanBusy && scanPhase === 'EXIT' && entryVehicleNo === v ? 'EXIT 검사 중...' : '🚪 게이트아웃' }}
            </button>
            <button class="gate-yard-del" title="목록에서 제거" @click="removeYard(v)">✕</button>
          </div>
        </div>
      </div>
    </div>
    <div class="gate-layout">
      <!-- 좌측: 게이트 시각화 -->
      <div class="gate-visual-card">
        <div class="gate-visual">
          <svg class="gate-svg" :class="state" viewBox="0 0 220 190" xmlns="http\://www\\\.w3.org/2000/svg">
            <defs>
              <linearGradient id="gsPostGrad" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0" stop-color="#33547a" />
                <stop offset="0.45" stop-color="#4a7bb0" />
                <stop offset="1" stop-color="#1c3654" />
              </linearGradient>
              <linearGradient id="gsBaseGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stop-color="#46586c" />
                <stop offset="1" stop-color="#1b232d" />
              </linearGradient>
              <linearGradient id="gsArmGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stop-color="#ffffff" />
                <stop offset="0.55" stop-color="#f0f1f2" />
                <stop offset="1" stop-color="#cfd3d6" />
              </linearGradient>
              <pattern id="gsStripes" patternUnits="userSpaceOnUse" width="34" height="11">
                <rect width="17" height="11" fill="var(--color-accent)" />
                <rect x="17" width="17" height="11" fill="#ffffff" />
              </pattern>
            </defs>
            <ellipse class="gs-shadow" cx="108" cy="178" rx="60" ry="7" />
            <rect class="gs-base" x="80" y="150" width="58" height="17" rx="4" fill="url(#gsBaseGrad)" />
            <rect class="gs-base-hi" x="80" y="150" width="58" height="4" rx="2" />
            <rect class="gs-post" x="97" y="38" width="19" height="114" rx="5" fill="url(#gsPostGrad)" />
            <rect class="gs-post-hi" x="99.5" y="40" width="3.5" height="110" rx="1.5" />
            <g class="gs-scan-rings">
              <circle class="gs-ring gs-ring1" cx="106.5" cy="151" r="10" />
              <circle class="gs-ring gs-ring2" cx="106.5" cy="151" r="10" />
            </g>
            <circle class="gs-light-ring" cx="130" cy="50" r="11" />
            <circle class="gs-light" cx="130" cy="50" r="6.5" />
            <g class="gs-arm-group">
              <rect class="gs-arm-base" x="106.5" y="41.5" width="98" height="12" rx="6" fill="url(#gsArmGrad)" />
              <rect class="gs-arm-stripe" x="106.5" y="41.5" width="98" height="12" rx="6" fill="url(#gsStripes)" />
              <rect class="gs-arm-sheen" x="106.5" y="43" width="98" height="2.4" rx="1.2" />
              <circle class="gs-arm-tip-ring" cx="204.5" cy="47.5" r="8" />
              <circle class="gs-arm-tip" cx="204.5" cy="47.5" r="4" />
            </g>
          </svg>
        </div>
        <div class="gate-status-line" :class="state">
          <span v-if="state === 'idle'">{{ idleStatusText }}</span>
          <span v-else-if="state === 'scanning'">번호판 OCR 검사 중...</span>
          <span v-else-if="state === 'pass'">✅ 통과</span>
          <span v-else>⛔ 인식 보류 · 수동 확인</span>
        </div>
        <button v-if="selected" class="gate-reset-btn" @click="reset">
          다시 시도
        </button>
      </div>
      <!-- 우측: 선택한 사진 + 결과 -->
      <div class="gate-result-card">
        <div v-if="!selected" class="gate-placeholder">
          🔍 {{ scanPhase === 'EXIT' ? '게이트아웃' : '게이트인' }} 버튼을 누르면<br />여기에 실시간 OCR 검사 과정이 표시됩니다.
        </div>
        <template v-else>
          <div class="gate-photo-wrap">
            <img v-if="selected.image" :src="selected.image" class="gate-photo" />
            <div v-else class="gate-photo-fallback">🚚 스캔된 사진 미리보기 없음</div>
            <div v-if="state === 'scanning'" class="scan-line"></div>
          </div>
          <div v-if="state === 'pass' || state === 'fail'" class="gate-result-info">
            <div class="plate-box" :class="state">
              {{ selected.plate || '판독불가' }}
            </div>
            <span class="gate-live-tag" :class="state">
              {{ resultTagText }}
            </span>
            <p v-if="state === 'pass'" class="gate-explain ok">{{ successExplain }}</p>
            <RouterLink
              v-if="state === 'pass' && selected?.scanType === 'ENTRY'"
              to="/admin/weighbridge"
              class="gate-next-link"
            >
              검사소 계량 화면 열기
            </RouterLink>
            <p v-else class="gate-explain warn">
              {{ liveDenyExplain }}
            </p>
          </div>
        </template>
      </div>
    </div>
    <!-- 실시간 백엔드 연동 (gate_live_demo.py -> scargo 백엔드 -> 이 화면) -->
    <div class="gate-live-section">
      <h2>🔴 실시간 게이트 OCR 검사 기록</h2>
      <p class="gate-live-sub">
        <code>gate_live_demo.py</code> / <code>gate_watch_service.py</code> / 위
        "게이트인" 버튼(<code>gate_api.py</code>) 중 무엇으로 인식하든, scargo
        백엔드(PostgreSQL)에 저장된 기록을 3초마다 불러옵니다. (목업이 아니라 실데이터 연동)
      </p>
      <div v-if="liveError" class="gate-live-empty">
        백엔드 연결 대기 중... scargo 서버가 <code>localhost:8080</code>에서
        실행 중인지 확인해 주세요.
      </div>
      <div v-else-if="liveLoading" class="gate-live-empty">불러오는 중...</div>
      <div v-else-if="liveLogs.length === 0" class="gate-live-empty">
        아직 기록이 없습니다. <code>python gate_live_demo.py</code>를 실행하면
        여기에 실시간으로 표시됩니다.
      </div>
      <table v-else class="gate-live-table">
        <thead>
          <tr>
            <th>시각</th>
            <th>게이트</th>
            <th>구분</th>
            <th>인식된 번호판</th>
            <th>신뢰도</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in liveLogs" :key="log.gateLogId">
            <td>{{ formatTime(log.passAt) }}</td>
            <td>{{ log.gateName }} · {{ log.gateType }}</td>
            <td>
              <span class="scan-type-pill" :class="(log.scanType || '').toLowerCase()">
                {{ log.scanType || '-' }}
              </span>
            </td>
            <td class="mono">{{ log.recognizedPlateNo || '(판독불가)' }}</td>
            <td>{{ log.plateConfidence != null ? log.plateConfidence + '%' : '-' }}</td>
            <td>
              <span class="pill" :class="log.actualVehicleNo ? 'pill-on' : 'pill-off'">
                {{ log.actualVehicleNo ? (log.scanType === 'EXIT' ? '출차 완료' : '입차 통과') : '대기' }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
<script setup>
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import axios from 'axios'
import { API_BASE } from '@/utils/apiBase.js'
import { GATE_SCAN_API_BASE } from '@/utils/gateApiBase.js'
const selected = ref(null)
const state = ref('idle') // idle | scanning | pass | fail
// 2026-10-02 변경: 차량 1대씩 순차 처리 → 구내 차량 목록 방식
// 게이트인은 항상 가능, 게이트아웃은 차량별로 [상하차 COMPLETED + 최신 과적 통과]일 때만 가능
const scanPhase = ref('ENTRY') // 마지막(현재) 요청 종류 표시용
const entryVehicleNo = ref('') // 마지막 EXIT 대상 차량
function loadYard() {
  try {
    const arr = JSON.parse(localStorage.getItem('gateYardVehicles') || '[]')
    // 이전 버전(단일 차량 키) 마이그레이션
    const old = localStorage.getItem('gateEntryVehicleNo')
    if (old && !arr.includes(old)) arr.push(old)
    localStorage.removeItem('gateEntryVehicleNo')
    localStorage.removeItem('gateScanPhase')
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}
const yardVehicles = ref(loadYard())
// 차량별 이번 ENTRY 시각 - 이전 방문의 상하차/과적 기록과 섞이지 않도록 이 시각 이후 기록만 사용
function loadEntryAt() {
  try {
    return JSON.parse(localStorage.getItem('gateYardEntryAt') || '{}') || {}
  } catch {
    return {}
  }
}
const yardEntryAt = ref(loadEntryAt())
const yardStatus = ref({}) // { 차량번호: { loading: 'PENDING'|..., overload: 'pass'|'fail'|'none'|'unknown' } }
function saveYard() {
  localStorage.setItem('gateYardVehicles', JSON.stringify(yardVehicles.value))
  localStorage.setItem('gateYardEntryAt', JSON.stringify(yardEntryAt.value))
}
function addYard(vno) {
  if (!vno) return
  yardEntryAt.value = { ...yardEntryAt.value, [vno]: new Date(Date.now() - 5000).toISOString() }
  if (!yardVehicles.value.includes(vno)) yardVehicles.value = [...yardVehicles.value, vno]
  saveYard()
  refreshYardStatus()
}
function removeYard(vno) {
  yardVehicles.value = yardVehicles.value.filter((v) => v !== vno)
  const next = { ...yardStatus.value }
  delete next[vno]
  yardStatus.value = next
  const at = { ...yardEntryAt.value }
  delete at[vno]
  yardEntryAt.value = at
  saveYard()
}
function canExit(vno) {
  const st = yardStatus.value[vno]
  return !!st && st.loading === 'COMPLETED' && st.overload === 'pass'
}
function yardStateText(vno) {
  const st = yardStatus.value[vno]
  if (!st) return '상태 확인 중...'
  if (st.overload === 'fail') return `⛔ 과적(검사 #${st.checkId}) - 재계량 통과 필요`
  if (st.overload === 'none') return '⏳ 검사소 대기'
  if (st.loading !== 'COMPLETED') return `⏳ 상하차 ${st.loading || '대기'}`
  if (st.overload === 'pass') return '✅ 출차 가능'
  if (st.overload === 'unknown') return '⚠ 과적 판정 조회 실패(로그인/백엔드 확인)'
  return '확인 중...'
}
async function fetchLoadingMap() {
  const map = {}
  let p = 0
  let totalPages = 1
  while (p < totalPages && p < 20) {
    const res = await axios.get(`${API_BASE}/api/loading-records`, {
      params: { page: p, size: 100 },
      withCredentials: true,
    })
    // 차량별 가장 최근 상하차 기록(recordId 최대)만 사용 - 이전 방문 기록과 섞이지 않게
    for (const r of res.data?.content ?? []) {
      if (!r.vehicleNo) continue
      const prev = map[r.vehicleNo]
      if (!prev || Number(r.recordId || 0) > prev.id) map[r.vehicleNo] = { id: Number(r.recordId || 0), status: r.status }
    }
    totalPages = Math.max(1, Number(res.data?.totalPages || 1))
    p += 1
  }
  return map
}
async function fetchOverload(vno) {
  try {
    const res = await axios.get(`${API_BASE}/api/overload-checks/vehicle`, {
      params: { vehicleNo: vno, page: 0, size: 50 },
      withCredentials: true,
    })
    const key = String(vno).replace(/\s/g, '')
    const since = yardEntryAt.value[vno] ? new Date(yardEntryAt.value[vno]).getTime() : 0
    const rows = (res.data?.content ?? []).filter(
      (r) =>
        String(r.vehicleNo || '').replace(/\s/g, '') === key &&
        (!since || !r.checkedAt || new Date(r.checkedAt).getTime() >= since),
    )
    if (!rows.length) return { overload: 'none' }
    // 가장 최근 검사 1건 기준 (checkedAt → checkId 순)
    rows.sort((x, y) => {
      const t = new Date(y.checkedAt || 0) - new Date(x.checkedAt || 0)
      return t !== 0 ? t : Number(y.checkId || 0) - Number(x.checkId || 0)
    })
    const row = rows[0]
    const passed = row.isPassed ?? row.passed
    const violation = row.isViolation ?? row.violation
    // 과적 여부(is_violation) 기준 판정 - DB 과적 컬럼과 동일하게
    const ok = violation != null ? violation === false : passed === true
    console.log('[게이트아웃 과적판정]', vno, { checkId: row.checkId, isPassed: passed, isViolation: violation })
    return { overload: ok ? 'pass' : 'fail', checkId: row.checkId }
  } catch (err) {
    console.error('과적 판정 조회 실패:', err)
    return { overload: 'unknown' }
  }
}
let yardPollId = null
async function refreshYardStatus() {
  if (!yardVehicles.value.length) return
  let loadingMap = {}
  try {
    loadingMap = await fetchLoadingMap()
  } catch (err) {
    console.error('상하차 상태 조회 실패:', err)
  }
  const next = {}
  await Promise.all(
    yardVehicles.value.map(async (vno) => {
      next[vno] = { loading: loadingMap[vno]?.status || '', ...(await fetchOverload(vno)) }
    }),
  )
  yardStatus.value = next
}
let timer = null
function reset() {
  clearTimeout(timer)
  selected.value = null
  state.value = 'idle'
  liveScanError.value = ''
}
// ---- 실시간 게이트 버튼 (gate_api.py, localhost:8001, POST /scan) ----
const liveScanBusy = ref(false)
const liveScanError = ref('')
const scanGuideText = '게이트인은 항상 가능합니다. 게이트아웃은 아래 구내 차량 중 상하차 완료 + 과적 통과 차량만 가능합니다.'
const idleStatusText = computed(() =>
  yardVehicles.value.length ? `구내 차량 ${yardVehicles.value.length}대 · 게이트인/아웃 버튼을 눌러주세요` : '게이트인 버튼을 눌러주세요',
)
const resultTagText = computed(() => {
  if (!selected.value) return ''
  if (selected.value.matchResult === 'VEHICLE_MISMATCH') return 'ENTRY 차량과 불일치'
  if (selected.value.matchResult === 'AUTHORIZED') return selected.value.scanType === 'EXIT' ? '출차 차량 일치' : '등록차량 일치'
  if (selected.value.matchResult === 'DENIED') return '미등록 차량'
  return '판독불가'
})
const successExplain = computed(() => {
  if (selected.value?.scanType === 'EXIT') {
    return `ENTRY 차량(${entryVehicleNo.value || selected.value.plate})과 EXIT OCR 번호판이 일치하여 출차를 허용합니다.`
  }
  return '등록차량(trucks) DB와 번호판이 일치하여 차단기가 열립니다. 이 차량은 검사소 계량 대기열에 자동으로 올라갑니다.'
})
const liveDenyExplain = computed(() => {
  if (selected.value && selected.value.matchResult === 'DENIED') {
    return '번호판은 인식했지만 등록차량(trucks) 목록에 없어 차단기를 열지 않습니다.'
  }
  return '번호판을 판독하지 못해 안전하게 통과를 보류하고, 사람이 다시 확인하도록 합니다.'
})
async function runLiveScan(requestPhase = 'ENTRY', vehicleNo = '') {
  if (liveScanBusy.value) return
  if (requestPhase === 'EXIT' && !canExit(vehicleNo)) return
  clearTimeout(timer)
  liveScanError.value = ''
  liveScanBusy.value = true
  state.value = 'scanning'
  scanPhase.value = requestPhase
  if (requestPhase === 'EXIT') entryVehicleNo.value = vehicleNo
  try {
    const params = { scanType: requestPhase }
    if (requestPhase === 'EXIT') params.vehicleNo = vehicleNo
    const res = await axios.post(`${GATE_SCAN_API_BASE}/scan`, null, { params })
    if (res.status === 204 || !res.data) {
      liveScanError.value = requestPhase === 'EXIT'
        ? 'ENTRY 때 저장한 동일 OCR 이미지를 찾을 수 없습니다.'
        : '감시 폴더("게이트_수신함")에 처리할 사진이 없습니다.'
      selected.value = null
      state.value = 'idle'
      return
    }
    const data = res.data
    selected.value = {
      id: `live-${data.fileName}`,
      image: data.imageUrl ? `${GATE_SCAN_API_BASE}${data.imageUrl}` : null,
      plate: data.recognizedPlate || '',
      isLive: true,
      scanType: data.scanType || requestPhase,
      requestedVehicleNo: data.requestedVehicleNo || null,
      matchResult: data.matchResult,
      matchedVehicle: data.matchedVehicle,
    }
    state.value = data.gateOpen ? 'pass' : 'fail'
    fetchLiveLogs()
    if (requestPhase === 'ENTRY' && data.gateOpen && data.matchResult === 'AUTHORIZED' && data.recognizedPlate) {
      addYard(data.recognizedPlate)
    }
    if (requestPhase === 'EXIT' && data.gateOpen && data.matchResult === 'AUTHORIZED') {
      removeYard(vehicleNo)
      timer = window.setTimeout(() => {
        selected.value = null
        state.value = 'idle'
        scanPhase.value = 'ENTRY'
      }, 2500)
    }
  } catch (err) {
    liveScanError.value =
      err?.response?.data?.detail ||
      err?.message ||
      '실시간 인식 서버(localhost:8001)에 연결할 수 없습니다.'
    selected.value = null
    state.value = 'idle'
  } finally {
    liveScanBusy.value = false
  }
}
// ---- 실시간 백엔드 연동 ----
const liveLogs = ref([])
const liveLoading = ref(true)
const liveError = ref(false)
let livePollId = null
function formatTime(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  return d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
async function fetchLiveLogs() {
  try {
    const res = await axios.get(`${API_BASE}/api/v1/gate-logs`, {
      params: { size: 8, sort: 'passAt,desc' },
      withCredentials: true,
    })
    liveLogs.value = res.data.content ?? []
    liveError.value = false
  } catch (err) {
    liveError.value = true
  } finally {
    liveLoading.value = false
  }
}
onMounted(() => {
  fetchLiveLogs()
  livePollId = setInterval(fetchLiveLogs, 3000)
  // 구내 차량별 상하차/과적 상태를 계속 확인
  refreshYardStatus()
  yardPollId = setInterval(refreshYardStatus, 3000)
})
onBeforeUnmount(() => {
  clearTimeout(timer)
  clearInterval(livePollId)
  clearInterval(yardPollId)
})
</script>
<style scoped>
.gate-page {
  max-width: 980px;
  margin: 0 auto;
  padding: 32px 20px 64px;
  color: var(--color-text);
}
.gate-header h1 {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}
.gate-header p {
  color: var(--color-subtext);
  font-size: 14px;
  line-height: 1.6;
  margin: 0 0 6px;
}
/* ---- 실시간 게이트인 버튼 ---- */
.gate-live-scan-cta {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: rgba(16, 129, 185, 0.06);
  border: 1px solid rgba(16, 129, 185, 0.18);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.gate-live-scan-btn {
  padding: 10px 18px;
  border-radius: 8px;
  border: none;
  background: var(--color-accent);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
}
.gate-live-scan-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.gate-live-scan-hint {
  font-size: 12px;
  color: var(--color-subtext);
  line-height: 1.5;
  flex: 1 1 260px;
}
.gate-yard {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.gate-yard-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
}
.gate-yard-no {
  min-width: 110px;
  font-size: 14px;
}
.gate-yard-state {
  flex: 1;
  font-size: 12.5px;
  color: var(--color-subtext);
}
.gate-yard-exit {
  padding: 6px 14px;
  font-size: 13px;
}
.gate-yard-del {
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
}
.gate-live-scan-error {
  width: 100%;
  margin: 0;
  font-size: 12.5px;
  color: #b91c1c;
}
.gate-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 20px;
  margin: 24px 0;
}
@media (max-width: 720px) {
  .gate-layout { grid-template-columns: 1fr; }
}
.gate-visual-card,
.gate-result-card {
  background: var(--color-card);
  border: 1px solid rgba(10, 37, 64, 0.1);
  border-radius: 14px;
  padding: 20px;
}
/* ---- 게이트(차단기) 시각화 ---- */
.gate-visual {
  position: relative;
  height: 150px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.gate-svg {
  width: 100%;
  max-width: 220px;
  height: 150px;
  overflow: visible;
}
.gs-shadow {
  fill: rgba(10, 37, 64, 0.16);
  filter: blur(1.5px);
}
.gs-base-hi {
  fill: rgba(255, 255, 255, 0.18);
}
.gs-post-hi {
  fill: rgba(255, 255, 255, 0.35);
}
/* 회전하는 차단봉 그룹: post 상단(106.5, 47.5)을 축으로 회전 */
.gs-arm-group {
  transform-origin: 106.5px 47.5px;
  transform: rotate(0deg);
  transition: transform 0.75s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.gate-svg.pass .gs-arm-group {
  transform: rotate(-80deg);
}
.gs-arm-base {
  filter: drop-shadow(0 2px 3px rgba(0, 0, 0, 0.2));
}
.gs-arm-stripe {
  opacity: 0.92;
}
.gs-arm-sheen {
  fill: rgba(255, 255, 255, 0.55);
}
.gs-arm-tip-ring {
  fill: var(--color-accent);
}
.gs-arm-tip {
  fill: #ffffff;
}
/* 상태등 */
.gs-light-ring {
  fill: none;
  stroke: rgba(10, 37, 64, 0.12);
  stroke-width: 2;
}
.gs-light {
  fill: #cbd5e1;
  transition: fill 0.3s ease;
}
.gate-svg.scanning .gs-light {
  fill: var(--color-accent);
  animation: gsBlink 0.6s infinite;
  filter: drop-shadow(0 0 5px var(--color-accent));
}
.gate-svg.pass .gs-light {
  fill: #10b981;
  filter: drop-shadow(0 0 7px #10b981);
}
.gate-svg.fail .gs-light {
  fill: #ef4444;
  filter: drop-shadow(0 0 7px #ef4444);
}
@keyframes gsBlink {
  50% { opacity: 0.3; }
}
/* 스캔 중 레이더 펄스 (게이트 하단 센서) */
.gs-scan-rings { opacity: 0; }
.gate-svg.scanning .gs-scan-rings { opacity: 1; }
.gs-ring {
  fill: none;
  stroke: var(--color-accent);
  stroke-width: 2;
  opacity: 0;
  transform-origin: 106.5px 151px;
}
.gate-svg.scanning .gs-ring1 {
  animation: gsPulse 1.4s ease-out infinite;
}
.gate-svg.scanning .gs-ring2 {
  animation: gsPulse 1.4s ease-out infinite 0.7s;
}
@keyframes gsPulse {
  0% { transform: scale(0.4); opacity: 0.65; }
  100% { transform: scale(2.6); opacity: 0; }
}
/* 통과 실패 시 살짝 흔들림 */
.gate-svg.fail {
  animation: gsShake 0.45s ease;
}
@keyframes gsShake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-4px); }
  40% { transform: translateX(3px); }
  60% { transform: translateX(-2px); }
  80% { transform: translateX(2px); }
}
.gate-status-line {
  text-align: center;
  margin-top: 14px;
  font-weight: 700;
  font-size: 14px;
  color: var(--color-subtext);
}
.gate-status-line.scanning { color: var(--color-accent); }
.gate-status-line.pass { color: #10b981; }
.gate-status-line.fail { color: #ef4444; }
.gate-reset-btn {
  display: block;
  width: 100%;
  margin-top: 16px;
  padding: 9px 12px;
  border-radius: 8px;
  border: 1px solid rgba(10, 37, 64, 0.15);
  background: #fff;
  color: var(--color-primary);
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
}
.gate-reset-btn:hover { background: var(--color-bg); }
/* ---- 결과 카드 ---- */
.gate-placeholder {
  height: 100%;
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--color-subtext);
  font-size: 13.5px;
  line-height: 1.7;
}
.gate-photo-wrap {
  position: relative;
  border-radius: 10px;
  overflow: hidden;
  line-height: 0;
}
.gate-photo {
  width: 100%;
  display: block;
}
.scan-line {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 3px;
  background: var(--color-accent);
  box-shadow: 0 0 12px var(--color-accent);
  animation: scan 1.3s linear;
}
@keyframes scan {
  from { top: 0; }
  to { top: 100%; }
}
.gate-result-info {
  margin-top: 16px;
  text-align: center;
}
.plate-box {
  display: inline-block;
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 1px;
  padding: 10px 22px;
  border-radius: 8px;
  border: 3px solid var(--color-primary);
  background: #fff;
}
.plate-box.fail {
  border-color: #ef4444;
  color: #ef4444;
  font-size: 18px;
}
.gate-explain {
  margin: 10px auto 0;
  max-width: 480px;
  font-size: 12.5px;
  line-height: 1.6;
}
.gate-explain.ok { color: #059669; }
.gate-next-link {
  display: inline-block;
  margin-top: 10px;
  padding: 8px 14px;
  border-radius: 8px;
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}
.gate-next-link:hover { background: var(--color-primary-hover); }
.gate-explain.warn { color: #b45309; }
.gate-live-tag {
  display: inline-block;
  margin-top: 8px;
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}
.gate-live-tag.fail {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.gate-photo-fallback {
  padding: 60px 10px;
  text-align: center;
  color: var(--color-subtext);
  font-size: 13px;
}
/* ---- 실시간 백엔드 연동 ---- */
.gate-live-section {
  margin-top: 40px;
}
.gate-live-section h2 {
  font-family: 'Barlow Condensed', sans-serif;
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 4px;
}
.gate-live-sub {
  color: var(--color-subtext);
  font-size: 12.5px;
  margin: 0 0 14px;
}
.gate-live-sub code {
  background: rgba(10, 37, 64, 0.08);
  padding: 1px 5px;
  border-radius: 4px;
}
.gate-live-empty {
  background: var(--color-card);
  border: 1px dashed rgba(10, 37, 64, 0.2);
  border-radius: 10px;
  padding: 24px;
  text-align: center;
  color: var(--color-subtext);
  font-size: 13px;
}
.gate-live-empty code {
  background: rgba(10, 37, 64, 0.08);
  padding: 1px 5px;
  border-radius: 4px;
}
.gate-live-table {
  width: 100%;
  border-collapse: collapse;
  background: var(--color-card);
  border: 1px solid rgba(10, 37, 64, 0.1);
  border-radius: 10px;
  overflow: hidden;
  font-size: 13px;
}
.gate-live-table thead th {
  text-align: center;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--color-subtext);
  padding: 10px 12px;
  background: rgba(10, 37, 64, 0.04);
  border-bottom: 1px solid rgba(10, 37, 64, 0.1);
}
.gate-live-table tbody td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(10, 37, 64, 0.06);
  text-align: center;
}
.gate-live-table tbody tr:last-child td {
  border-bottom: none;
}
.gate-live-table .mono {
  font-family: 'Barlow Condensed', sans-serif;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.scan-type-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  font-size: 10.5px;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.12);
  color: #64748b;
}
.scan-type-pill.entry { background: rgba(59, 130, 246, 0.12); color: #2563eb; }
.scan-type-pill.exit { background: rgba(16, 185, 129, 0.12); color: #059669; }
.pill {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 999px;
}
.pill-on { background: rgba(16, 185, 129, 0.12); color: #10b981; }
.pill-off { background: rgba(239, 68, 68, 0.1); color: #ef4444; }
</style>
