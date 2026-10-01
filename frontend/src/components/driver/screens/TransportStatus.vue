<template>
  <div class="topbar">
    <div>
      <div class="brand">운송현황</div>
      <div class="sub">{{ todayLabel }}</div>
    </div>
    <div class="driver-chip">
      <span class="dot" :class="{ off: !dispatch }"></span>
      {{ dispatch ? '운행중' : '대기중' }} · {{ userName }} 기사님
    </div>
  </div>

  <!-- 로딩 -->
  <main v-if="loading" class="page">
    <p class="hint-text">불러오는 중...</p>
  </main>

  <!-- 등록된 차량이 없음 -->
  <main v-else-if="!vehicleNo" class="page empty-state">
    <p class="hint-text">차량을 먼저 등록해야 배차를 받을 수 있어요.</p>
    <RouterLink to="/driver/app/my-page#register" class="drv-cta">차량 등록하러 가기</RouterLink>
  </main>

  <!-- 진행 중인 배차 없음 -->
  <main v-else-if="!dispatch" class="page empty-state">
    <p class="hint-text">진행 중인 배차가 없습니다.</p>
    <RouterLink to="/driver/app/dispatch-list" class="drv-cta">배차목록 보기</RouterLink>
  </main>

  <!-- 진행 중인 배차 -->
  <template v-else>
    <main class="page">
      <div class="layout-2col">
        <div class="col-main">
          <!-- 상태 스테퍼 -->
          <div class="stepper">
            <div class="line"></div>
            <div class="line-fill" :style="{ width: lineFillPct + '%' }"></div>
            <div
              v-for="(label, idx) in statusLabels"
              :key="idx"
              class="step"
              :class="{ done: idx < currentStepIndex, active: idx === currentStepIndex }"
            >
              <div class="node"></div>
              <div class="stepper-label" v-html="label"></div>
            </div>
          </div>

          <!-- 상/하차 정보 -->
          <div class="route-card">
            <div class="leg pickup">
              <div class="marker"><div class="pin"></div><div class="stem"></div></div>
              <div class="leg-body">
                <div class="leg-tag">상차지</div>
                <div class="leg-addr">{{ dispatch.pickupAddress }}<br>{{ dispatch.pickupPlace }}</div>
                <div class="leg-meta">
                  <span v-if="dispatch.pickupTime">⏰ {{ formatTime(dispatch.pickupTime) }} 도착 예정</span>
                </div>
                <div class="leg-actions">
                  <button
                    v-if="dispatch.pickupContactPhone"
                    class="icon-btn"
                    @click="call(dispatch.pickupContactName || '상차지 담당자', dispatch.pickupContactPhone)"
                  >📞 담당자 통화</button>
                </div>
              </div>
            </div>
            <div class="leg dropoff">
              <div class="marker"><div class="pin"></div></div>
              <div class="leg-body">
                <div class="leg-tag">하차지</div>
                <div class="leg-addr">{{ dispatch.dropoffAddress }}<br>{{ dispatch.dropoffPlace }}</div>
                <div class="leg-meta">
                  <span v-if="dispatch.dropoffTime">⏰ {{ formatTime(dispatch.dropoffTime) }} 도착 예정</span>
                  <span v-if="dispatch.distanceKm">🛣️ 약 {{ dispatch.distanceKm }}km</span>
                </div>
                <div class="leg-actions">
                  <button
                    v-if="dispatch.dropoffContactPhone"
                    class="icon-btn"
                    @click="call(dispatch.dropoffContactName || '하차지 담당자', dispatch.dropoffContactPhone)"
                  >📞 담당자 통화</button>
                </div>
              </div>
            </div>
          </div>

          <!-- 요약 통계 -->
          <div class="stat-row">
            <div class="stat-box">
              <div class="stat-value">{{ dispatch.distanceKm ?? '-' }}<span class="unit">km</span></div>
              <div class="stat-label">총 운행거리</div>
            </div>
            <div class="stat-box">
              <div class="stat-value">{{ formatWonShort(dispatch.fare) }}</div>
              <div class="stat-label">운임</div>
            </div>
            <div class="stat-box">
              <div class="stat-value">#{{ dispatch.dispatchId }}</div>
              <div class="stat-label">배차번호</div>
            </div>
          </div>

          <!-- 화물 정보 -->
          <div class="section-title">화물 정보</div>
          <div class="card cargo-card">
            <div class="cargo-row"><span class="k">품목</span><span class="v">{{ dispatch.cargoItem || '-' }}</span></div>
            <div class="cargo-row"><span class="k">화주</span><span class="v">{{ shipperName || '-' }}</span></div>
            <div class="cargo-row"><span class="k">운임</span><span class="v">{{ formatWon(dispatch.fare) }}원</span></div>
          </div>
        </div>

        <!-- 우측 실행 패널 -->
        <aside class="side-panel">
          <div class="card action-card">
            <div class="section-title">빠른 실행</div>
            <button class="btn-secondary" @click="naviOpen = true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="3 11 22 2 13 21 11 13 3 11"></polygon>
              </svg>
              내비게이션 시작
            </button>
            <button class="btn-primary" :disabled="updating" @click="advanceStatus">
              {{ nextButtonLabel }}
            </button>
          </div>
        </aside>
      </div>
    </main>

    <!-- 내비 앱 선택 시트 (주소 검색 방식 - 정확한 좌표 데이터는 아직 없음) -->
    <div class="navi-sheet" :class="{ open: naviOpen }" @click.self="naviOpen = false">
      <div class="navi-panel">
        <div class="handle"></div>
        <h3>내비게이션 앱 선택</h3>
        <div class="navi-option" @click="launchNavi('tmap')">
          <div class="swatch" style="background:#1F2A44; color:#4DA6FF;">T</div> 티맵
        </div>
        <div class="navi-option" @click="launchNavi('kakao')">
          <div class="swatch" style="background:#2A2200; color:#FFE300;">K</div> 카카오내비
        </div>
        <div class="navi-option" @click="launchNavi('naver')">
          <div class="swatch" style="background:#0F2E1A; color:#2FBF71;">N</div> 네이버 지도
        </div>
        <button class="navi-cancel" @click="naviOpen = false">취소</button>
      </div>
    </div>
  </template>
</template>

<script setup>
import { fetchMyVehicleNo } from '@/utils/driverTruck.js'
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { authState } from '@/auth/authState.js'
import { API_BASE } from '@/utils/apiBase.js'
import { getCompanyName } from '@/utils/companyDirectory.js'

// ---- 오늘 날짜 ----
const dayNames = ['일', '월', '화', '수', '목', '금', '토']
const now = new Date()
const todayLabel = `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')} (${dayNames[now.getDay()]})`

const userName = computed(() => authState.user?.userName || '-')
const vehicleNo = ref(null)
const loading = ref(true)
const dispatch = ref(null)
const shipperName = ref(null)

// ---- 상태(스테퍼) ----
const statusSequence = ['ACCEPTED', 'LOADING', 'IN_TRANSIT', 'UNLOADING', 'COMPLETED']
const statusLabels = ['배차<br>확정', '상차<br>중', '운송<br>중', '하차<br>중', '운송<br>완료']
const nextActionLabel = {
  ACCEPTED: '상차 시작',
  LOADING: '상차 완료',
  IN_TRANSIT: '하차지 도착',
  UNLOADING: '하차 완료',
}

const currentStepIndex = computed(() => {
  if (!dispatch.value) return 0
  const idx = statusSequence.indexOf(dispatch.value.status)
  return idx === -1 ? 0 : idx
})
const lineFillPct = computed(() => (currentStepIndex.value / (statusLabels.length - 1)) * 80)
const nextButtonLabel = computed(() => nextActionLabel[dispatch.value?.status] || '상태 변경')

// ---- 데이터 로딩 ----
async function loadCurrentDispatch() {
  if (!vehicleNo.value) return
  try {
    const resp = await axios.get(`${API_BASE}/api/dispatches/vehicle/${encodeURIComponent(vehicleNo.value)}/current`)
    dispatch.value = resp.status === 204 || !resp.data ? null : resp.data
    if (dispatch.value?.shipperCompanyId) {
      shipperName.value = await getCompanyName(dispatch.value.shipperCompanyId)
    }
  } catch (err) {
    dispatch.value = null
  }
}

const updating = ref(false)
async function advanceStatus() {
  if (!dispatch.value || updating.value) return
  const currentIdx = statusSequence.indexOf(dispatch.value.status)
  const nextStatus = statusSequence[currentIdx + 1]
  if (!nextStatus) return

  updating.value = true
  try {
    const resp = await axios.patch(
      `${API_BASE}/api/dispatches/${dispatch.value.dispatchId}/status`,
      null,
      { params: { status: nextStatus } }
    )
    if (nextStatus === 'COMPLETED') {
      // 완료되면 "진행 중" 목록에서 빠지므로, 다시 조회해서 다음 배차가 있는지 확인
      dispatch.value = null
      await loadCurrentDispatch()
    } else {
      dispatch.value = resp.data
    }
  } catch (err) {
    alert(err.response?.data?.message || '상태 변경에 실패했습니다.')
  } finally {
    updating.value = false
  }
}

// ---- 연락처 ----
function call(name, number) {
  if (confirm(`${name} (${number})\n전화를 거시겠습니까?`)) {
    window.location.href = `tel:${number}`
  }
}

// ---- 내비게이션 (주소 텍스트 기반 검색 - 정밀 좌표 데이터는 백엔드에 없음) ----
const naviOpen = ref(false)
function launchNavi(app) {
  const address = dispatch.value?.dropoffAddress || ''
  const encoded = encodeURIComponent(address)
  let url = ''
  switch (app) {
    case 'tmap':
      url = `tmap://search?name=${encoded}`
      break
    case 'kakao':
      url = `kakaomap://search?q=${encoded}`
      break
    case 'naver':
      url = `nmap://search?query=${encoded}`
      break
  }
  const fallback = `https://map.naver.com/v5/search/${encoded}`
  const timer = setTimeout(() => { window.location.href = fallback }, 1200)
  window.addEventListener('blur', () => clearTimeout(timer), { once: true })
  window.location.href = url
  naviOpen.value = false
}

// ---- 포맷터 ----
function formatTime(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
function formatWon(n) {
  return n != null ? Number(n).toLocaleString('ko-KR') : '-'
}
function formatWonShort(n) {
  if (n == null) return '-'
  return (Number(n) / 10000).toLocaleString('ko-KR') + '만'
}

// ---- 초기 로딩 ----
onMounted(async () => {
  // 26.09.30 수정: 배정 차량을 서버에서 조회 (localStorage 값은 더 이상 저장되지 않음)
  vehicleNo.value = await fetchMyVehicleNo()
  await loadCurrentDispatch()
  loading.value = false
})
</script>

<style scoped>
.driver-chip { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-muted); }
.driver-chip .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--green); box-shadow: 0 0 0 3px var(--green-soft); }
.driver-chip .dot.off { background: var(--text-muted); box-shadow: none; }

.col-main { min-width: 0; }

/* 스테퍼 */
.stepper { display: flex; align-items: flex-start; justify-content: space-between; padding: 4px 4px 22px; position: relative; }
.step { display: flex; flex-direction: column; align-items: center; gap: 8px; flex: 1; position: relative; z-index: 1; }
.step .node { width: 22px; height: 22px; border-radius: 50%; background: var(--surface); border: 2px solid var(--border); transition: all .25s ease; }
.step.done .node { background: var(--green); border-color: var(--green); }
.step.active .node { background: var(--amber); border-color: var(--amber); box-shadow: 0 0 0 5px var(--amber-soft); }
.step .stepper-label { font-size: 13px; color: var(--text-muted); font-weight: 500; text-align: center; line-height: 1.15; white-space: nowrap; }
.step.active .stepper-label { color: var(--amber); font-weight: 600; }
.step.done .stepper-label { color: var(--text); }
.stepper .line { position: absolute; top: 15px; left: 10%; right: 10%; height: 2px; background: var(--border); z-index: 0; }
.stepper .line-fill { position: absolute; top: 15px; left: 10%; height: 2px; background: var(--green); z-index: 0; transition: width .3s ease; }

/* 경로 카드 */
.route-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); overflow: hidden; margin-bottom: 14px; }
.leg { display: flex; gap: 14px; padding: 18px; position: relative; }
.leg + .leg { border-top: 1px dashed var(--border); }
.leg .marker { display: flex; flex-direction: column; align-items: center; padding-top: 3px; }
.leg .marker .pin { width: 12px; height: 12px; border-radius: 3px; }
.leg.pickup .marker .pin { background: var(--amber); }
.leg.dropoff .marker .pin { background: var(--green); }
.leg .marker .stem { width: 2px; flex: 1; background: var(--border); margin-top: 6px; }
.leg-body { flex: 1; min-width: 0; }
.leg-tag { font-size: 11px; font-weight: 700; letter-spacing: 0.04em; color: var(--amber); margin-bottom: 6px; }
.leg.dropoff .leg-tag { color: var(--green); }
.leg-addr { font-size: 16px; font-weight: 600; line-height: 1.35; margin-bottom: 6px; }
.leg-meta { display: flex; gap: 14px; font-size: 13px; color: var(--text-muted); flex-wrap: wrap; }
.leg-actions { display: flex; gap: 8px; margin-top: 10px; }
.icon-btn { display: flex; align-items: center; justify-content: center; gap: 6px; padding: 8px 12px; border-radius: 8px; background: var(--surface-alt); border: 1px solid var(--border); color: var(--text); font-size: 13px; font-weight: 500; cursor: pointer; }
.icon-btn:active { transform: scale(0.97); }

/* 요약 통계 */
.stat-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 16px; }
.stat-box { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 12px 10px; text-align: center; }
.stat-box .stat-value { font-family: 'Barlow Condensed', sans-serif; font-size: 22px; font-weight: 700; line-height: 1; margin-bottom: 4px; }
.stat-box .stat-value .unit { font-size: 14px; }
.stat-box .stat-label { font-size: 11.5px; color: var(--text-muted); }

/* 화물 카드 */
.cargo-card { margin-bottom: 20px; }
.cargo-row { display: flex; justify-content: space-between; padding: 8px 0; font-size: 14px; }
.cargo-row + .cargo-row { border-top: 1px solid var(--border); }
.cargo-row .k { color: var(--text-muted); }
.cargo-row .v { font-weight: 500; }

/* 우측 실행 패널 */
.action-card { display: flex; flex-direction: column; gap: 10px; }
.btn-primary { width: 100%; padding: 16px; border-radius: var(--radius); border: none; background: var(--amber); color: #fff; font-family: 'Barlow Condensed', sans-serif; font-size: 19px; font-weight: 700; cursor: pointer; }
.btn-primary:active { transform: scale(0.98); }
.btn-primary:disabled { opacity: 0.6; cursor: default; }
.btn-secondary { width: 100%; padding: 14px; border-radius: var(--radius); border: 1px solid var(--border); background: var(--surface); color: var(--text); font-size: 15px; font-weight: 600; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 8px; }

/* 내비 시트 */
.navi-sheet { position: fixed; inset: 0; background: rgba(0,0,0,0.55); display: none; align-items: flex-end; justify-content: center; z-index: 20; }
.navi-sheet.open { display: flex; }
.navi-panel { width: 100%; max-width: 480px; background: var(--surface); border-radius: 16px 16px 0 0; padding: 10px 16px calc(20px + env(safe-area-inset-bottom)); }
.navi-panel .handle { width: 36px; height: 4px; background: var(--border); border-radius: 2px; margin: 6px auto 16px; }
.navi-panel h3 { font-size: 15px; font-weight: 600; margin-bottom: 12px; color: var(--text-muted); }
.navi-option { display: flex; align-items: center; gap: 12px; padding: 14px 8px; font-size: 16px; font-weight: 600; cursor: pointer; border-bottom: 1px solid var(--border); }
.navi-option:last-of-type { border-bottom: none; }
.navi-option .swatch { width: 34px; height: 34px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-family: 'Barlow Condensed', sans-serif; font-size: 13px; font-weight: 700; }
.navi-cancel { width: 100%; margin-top: 14px; padding: 13px; border-radius: 8px; border: none; background: var(--surface-alt); color: var(--text-muted); font-weight: 600; font-size: 14px; }

.hint-text { color: var(--text-muted); font-size: 13px; }
</style>
