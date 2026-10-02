<template>
  <div class="topbar">
    <div>
      <div class="brand">운송현황</div>
      <div class="sub">{{ todayLabel }}</div>
    </div>
    <div class="driver-chip">
      <span class="dot" :class="{ off: !current }"></span>
      {{ current ? '운행중' : '대기중' }}
    </div>
  </div>

  <main v-if="loading" class="page">
    <p class="hint-text">불러오는 중...</p>
  </main>

  <main v-else-if="!truck" class="page empty-state">
    <p class="hint-text">차량을 먼저 등록하고, 소속 업체에서 기사 배정을 받아야 운송현황을 볼 수 있어요.</p>
    <RouterLink to="/driver/app/my-page#register" class="drv-cta">MY/차량으로 가기</RouterLink>
  </main>

  <main v-else-if="!current" class="page empty-state">
    <p class="hint-text">진행 중인 컨테이너 배차가 없습니다. 사업자가 차량에 컨테이너를 매핑하면 적재 위치가 여기에 표시됩니다.</p>
    <RouterLink to="/driver/app/dispatch-list" class="drv-cta">배차목록 보기</RouterLink>
  </main>

  <template v-else>
    <main class="page">
      <div class="layout-2col">
        <div class="col-main">
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

          <div class="route-card">
            <div class="leg pickup">
              <div class="marker"><div class="pin"></div><div class="stem"></div></div>
              <div class="leg-body">
                <div class="leg-tag">적재 위치</div>
                <div class="leg-addr">{{ current.locationLabel }}</div>
                <div class="leg-meta">
                  <span v-if="current.location?.locationId">위치 #{{ current.location.locationId }}</span>
                  <span v-if="current.loadedAt">⏰ {{ formatDateTime(current.loadedAt) }} 배정</span>
                  <span v-if="navi.hasCoords">📍 {{ navi.lat }}, {{ navi.lng }}</span>
                </div>
              </div>
            </div>
            <div class="leg dropoff">
              <div class="marker"><div class="pin"></div></div>
              <div class="leg-body">
                <div class="leg-tag">배정 컨테이너</div>
                <div class="leg-addr">{{ current.containerNo || '-' }}</div>
                <div class="leg-meta">
                  <span>차량 {{ truck.vehicleNo }}</span>
                  <span>진입 허가 {{ approval.label }}</span>
                </div>
              </div>
            </div>
          </div>

          <p v-if="approval.value !== 'APPROVED'" class="warn-text">
            {{ approval.value === 'REJECTED'
              ? '진입이 반려된 차량입니다. 야드에 들어가기 전에 소속 업체 또는 관리자에게 문의하세요.'
              : '진입 허가가 나기 전에는 게이트를 통과할 수 없습니다.' }}
          </p>

          <div class="stat-row">
            <div class="stat-box">
              <div class="stat-value">{{ current.containerNo || '-' }}</div>
              <div class="stat-label">컨테이너</div>
            </div>
            <div class="stat-box">
              <div class="stat-value">{{ current.location?.sector || current.locationName || '-' }}</div>
              <div class="stat-label">섹터</div>
            </div>
            <div class="stat-box">
              <div class="stat-value">{{ approval.label }}</div>
              <div class="stat-label">진입 허가</div>
            </div>
          </div>
        </div>

        <aside class="side-panel">
          <div class="card action-card">
            <div class="section-title">안내</div>
            <p class="hint-inline">입·출차는 게이트 OCR이 처리합니다. 내비게이션은 배정된 적재 위치({{ navi.name }})를 목적지로 엽니다.</p>
            <button class="btn-secondary" :disabled="!canNavi" @click="naviOpen = true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="3 11 22 2 13 21 11 13 3 11"></polygon>
              </svg>
              내비게이션
            </button>
          </div>
        </aside>
      </div>
    </main>

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
import { ref, computed, onMounted } from 'vue'
import {
  fetchDriverAssignment,
  entryApprovalMeta,
  formatDateTime,
  naviDestination,
} from '@/utils/driverAssignment.js'

const dayNames = ['일', '월', '화', '수', '목', '금', '토']
const now = new Date()
const todayLabel = `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')} (${dayNames[now.getDay()]})`

const loading = ref(true)
const truck = ref(null)
const current = ref(null)
const naviOpen = ref(false)

const approval = computed(() => entryApprovalMeta(truck.value?.entryApproval))

const statusLabels = ['차량<br>배정', '진입<br>허가', '컨테이너<br>배차', '적재<br>위치']
const currentStepIndex = computed(() => {
  if (!truck.value) return 0
  if (approval.value.value !== 'APPROVED') return 1
  if (!current.value) return 2
  return 3
})
const lineFillPct = computed(() => (currentStepIndex.value / (statusLabels.length - 1)) * 80)

const navi = computed(() => naviDestination(current.value?.location, current.value?.locationName))
const canNavi = computed(() => Boolean(navi.value.hasCoords || navi.value.name))

function webMapUrl(app, dest) {
  const { lat, lng, name, hasCoords } = dest
  const q = encodeURIComponent(name || '적재 위치')
  if (hasCoords) {
    if (app === 'kakao') return `https://map.kakao.com/link/to/${q},${lat},${lng}`
    if (app === 'tmap') return `https://www.tmap.co.kr/route?goalname=${q}&goalx=${lng}&goaly=${lat}`
    return `https://map.naver.com/p/search/${lat},${lng}`
  }
  return `https://map.naver.com/p/search/${q}`
}

function appMapUrl(app, dest) {
  const { lat, lng, name, hasCoords } = dest
  const q = encodeURIComponent(name || '적재 위치')
  if (hasCoords) {
    if (app === 'tmap') return `tmap://route?goalx=${lng}&goaly=${lat}&goalname=${q}`
    if (app === 'kakao') return `kakaomap://route?ep=${lat},${lng}&ename=${q}`
    return `nmap://place?lat=${lat}&lng=${lng}&name=${q}`
  }
  if (app === 'tmap') return `tmap://search?name=${q}`
  if (app === 'kakao') return `kakaomap://search?q=${q}`
  return `nmap://search?query=${q}`
}

function launchNavi(app) {
  const dest = navi.value
  const fallback = webMapUrl(app, dest)
  const appUrl = appMapUrl(app, dest)
  naviOpen.value = false
  const timer = setTimeout(() => {
    window.open(fallback, '_blank', 'noopener')
  }, 800)
  window.addEventListener('blur', () => clearTimeout(timer), { once: true })
  window.location.href = appUrl
}

onMounted(async () => {
  const data = await fetchDriverAssignment()
  truck.value = data.truck
  current.value = data.current
  loading.value = false
})
</script>

<style scoped>
.driver-chip { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-muted); }
.driver-chip .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--green); box-shadow: 0 0 0 3px var(--green-soft); }
.driver-chip .dot.off { background: var(--text-muted); box-shadow: none; }

.col-main { min-width: 0; }

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

.stat-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 16px; }
.stat-box { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 12px 10px; text-align: center; }
.stat-box .stat-value { font-family: 'Barlow Condensed', sans-serif; font-size: 20px; font-weight: 700; line-height: 1.15; margin-bottom: 4px; word-break: break-all; }
.stat-box .stat-label { font-size: 11.5px; color: var(--text-muted); }

.action-card { display: flex; flex-direction: column; gap: 10px; }
.hint-inline { font-size: 13px; color: var(--text-muted); line-height: 1.45; margin: 0; }
.warn-text { color: var(--amber); font-size: 13px; margin: 0 0 14px; }
.btn-secondary { width: 100%; padding: 14px; border-radius: var(--radius); border: 1px solid var(--border); background: var(--surface); color: var(--text); font-size: 15px; font-weight: 600; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 8px; }
.btn-secondary:disabled { opacity: 0.5; cursor: default; }

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
