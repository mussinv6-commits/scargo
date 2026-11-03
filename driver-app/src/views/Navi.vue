<template>
  <div ref="rootRef" class="screen" style="position: relative">
    <!-- fix_app_1026: 데이터 로드 실패 안내 -->
    <div v-if="!loading && (loadError || (trip.error && !trip.route))" class="content">
      <div class="card empty" style="color: #C62828">
        <b>내비 정보를 불러오지 못했습니다.</b><br />
        <span class="small">{{ loadError || trip.error }}</span>
        <div class="row" style="margin-top: 12px; gap: 8px">
          <button class="btn primary" @click="load">다시 시도</button>
          <button class="btn" @click="session.serverSheet = true">서버 주소 설정</button>
        </div>
      </div>
    </div>
    <template v-else-if="!trip.orderId && !loading">
      <TopBar title="내비게이션" />
      <div class="content">
        <div class="card empty">운행 중인 오더가 없습니다.<br /><span class="small">운송 오더에서 인수 확인 → 출발 전 점검을 마치면 여기에서 경로를 안내합니다.<!-- 26.10.29 QA: 없어진 "적재 등록" 화면을 안내하던 문구 수정 --></span></div>
      </div>
    </template>

    <template v-else>
      <div class="map-wrap">
        <MapView ref="mapRef" :path="trip.route?.path || []" :markers="markers" :me="trip.me" :follow="follow" :pad-top="170" :pad-bottom="sheetH + 20" :heading-up="headingUp" @select="m => { selected = m; follow = false }" @longpress="onLongPress" @userpan="follow = false" />
      </div>

      <!-- 상단: 다음 구간 + 경고 -->
      <div class="top-stack">
        <div v-if="!st?.arrived" class="next-card">
          <Icon :name="nextLine.icon" size="34" stroke="2.2" />
          <div class="stack" style="gap: 2px; flex: 1; min-width: 0">
            <b style="font-size: 22px">{{ nextLine.big }}</b>
            <span style="font-size: 13px; color: #D6D8DB; overflow: hidden; text-overflow: ellipsis">{{ nextLine.small }}</span>
          </div>
          <!-- 26.10.24: 카카오맵 길찾기 삭제 (승용차 경로라 화물차 높이제한 반영 안 됨) → 목적지까지 남은 거리 -->
          <div class="remain"><span>목적지까지</span><b>{{ st?.arrived ? '도착' : km(st?.remainingM ?? trip.route?.distanceM) }}</b></div>
        </div>
        <div v-for="a in topAlerts" :key="a.type + a.title" :class="['alert', a.level.toLowerCase()]">
          <Icon :name="a.type === 'HEIGHT' ? 'height' : a.type === 'CHECKPOINT' ? 'station' : a.type === 'ARRIVED' ? 'pin' : 'warn'" size="24" stroke="2" />
          <div class="stack" style="gap: 1px"><b>{{ a.title }}</b><span>{{ a.message }}</span></div>
        </div>
        <div v-if="trip.rerouting" class="alert warn"><div class="spinner"></div><b>경로 재탐색 중…</b></div>
        <div v-if="trip.error" class="error-box">{{ trip.error }}</div>
        <div v-if="goErr" class="error-box">{{ goErr }}</div>
        <!-- 26.10.28: 한국어 TTS 미설치 경고 문구 숨김 (음성은 조용히 시도) -->
      </div>

      <!-- 지도 컨트롤 -->
      <div class="map-ctrl" :class="{ row: ctrlRow }" :style="{ bottom: `${sheetH + 12}px` }">
        <button class="icon-btn ctrl" type="button" :aria-pressed="voice.enabled" :aria-label="voice.enabled ? '음성 안내 끄기' : '음성 안내 켜기'" @click="setVoice(!voice.enabled)">
          <Icon :name="voice.enabled ? 'speaker' : 'mute'" :style="{ color: voice.enabled ? 'var(--primary)' : 'var(--faint)' }" />
        </button>
        <button class="icon-btn ctrl" type="button" :aria-pressed="follow" aria-label="내 위치 따라가기" @click="follow = !follow; if (trip.me) mapRef?.focus(trip.me.lat, trip.me.lng, 13)">
          <Icon name="locate" :style="{ color: follow ? 'var(--primary)' : 'var(--ink)' }" />
        </button>
        <button class="icon-btn ctrl hu" type="button" :aria-pressed="headingUp" :aria-label="headingUp ? '북쪽 위로 보기' : '진행 방향 위로 보기'" @click="setHeadingUp(!headingUp)">
          <span :style="{ color: headingUp ? 'var(--primary)' : 'var(--ink)' }">{{ headingUp ? '진행' : '북' }}</span>
        </button>
        <button class="icon-btn ctrl" type="button" aria-label="경로 전체 보기" @click="follow = false; mapRef?.fit()"><Icon name="route" /></button>
        <button class="icon-btn ctrl" type="button" aria-label="내 차량 아이콘 바꾸기" @click="iconSheet = true"><Icon name="truck" /></button>
      </div>

      <!-- fix_app_1026c: TMAP식 '현재 위치로' (지도를 끌거나 확대·축소하면 나타남) -->
      <button v-if="!follow && trip.me" class="recenter" type="button" :style="{ bottom: `${sheetH + 14}px` }" @click="recenter"><Icon name="locate" size="18" />현재 위치로</button>

      <!-- 26.10.24: 도착 팝업 -->
      <div v-if="trip.arrivedNotice" class="overlay center" role="dialog" aria-modal="true" aria-labelledby="arrTitle" @click.self="trip.arrivedNotice = null">
        <div class="arr-pop">
          <div class="arr-ic"><Icon name="pin" size="30" /></div>
          <b id="arrTitle">목적지에 도착했습니다</b>
          <span>{{ trip.arrivedNotice.name }}</span>
          <span v-if="trip.arrivedNotice.local" class="small muted">시연 주행이 끝났습니다 (서버 기록 없음)</span>
          <span v-else class="small muted">컨테이너 인계를 진행하세요</span>
          <div class="arr-btns">
            <button v-if="!trip.arrivedNotice.local" class="btn" type="button" @click="trip.arrivedNotice = null">닫기</button>
            <button v-if="!trip.arrivedNotice.local" class="btn primary" type="button" @click="trip.arrivedNotice = null; $router.push(`/arrive/${trip.orderId}`)">인계하기</button>
            <button v-else class="btn primary" type="button" @click="trip.arrivedNotice = null">확인</button>
          </div>
        </div>
      </div>

      <!-- 26.10.12: 내 차량 아이콘 선택 -->
      <div v-if="iconSheet" class="overlay" @click.self="iconSheet = false">
        <div class="sheet">
          <h2 class="section-title">내 차량 아이콘</h2>
          <VehicleIconPicker />
          <button class="btn primary" type="button" @click="iconSheet = false">확인</button>
        </div>
      </div>

      <!-- 핀 상세 (지도에서 터미널·HUB·높이제한·검문소·영업소 선택) -->
      <section v-if="selected" class="bottom-sheet detail">
        <PlaceSheet :item="selected" :pos="trip.me" :load-height-m="order?.loadHeightM" :gross-weight-kg="order?.grossWeightKg"
                    :ahead-m="selectedAhead" :passed="selectedPassed" @close="selected = null" />
      </section>

      <!-- 하단 시트 -->
      <section v-show="!selected" ref="sheetRef" class="bottom-sheet" :class="{ peek: sheetPeek && dragH == null, dragging: dragH != null }" :style="dragH != null ? { height: dragH + 'px' } : null">
        <!-- 26.10.24: 손잡이를 위아래로 끌거나 눌러서 시트 접기/펴기 (지도 넓게 보기) -->
        <div class="grab" role="button" tabindex="0" :aria-label="sheetPeek ? '아래 정보 펼치기' : '아래 정보 접기'" :aria-expanded="!sheetPeek"
             @pointerdown="dragStart" @keydown.enter.prevent="setPeek(!sheetPeek)" @keydown.space.prevent="setPeek(!sheetPeek)">
          <div class="handle"></div>
        </div>
        <div ref="statsRef" class="grid3">
          <div class="stat"><span>도착 예정</span><b>{{ st?.arrived ? '도착' : etaText }}</b></div>
          <div class="stat"><span>남은 거리</span><b>{{ st?.arrived ? '0m' : km(st?.remainingM ?? trip.route?.distanceM) }}</b></div><!-- 26.10.29 QA: 도착했는데 남은 거리가 보이던 문제 -->
          <div class="stat"><span>남은 시간</span><b>{{ st?.arrived ? '0:00' : hm(remainS) }}</b></div>
        </div>

        <div v-if="tollWindow.length" class="stack" style="gap: 6px">
          <span class="small muted">고속도로 영업소 진행</span>
          <div class="tolls">
            <template v-for="(t, i) in tollWindow" :key="t.code">
              <span v-if="i > 0" class="seg" :class="{ on: t.passed }"></span>
              <span :class="{ on: t.passed }">{{ t.name }}</span>
            </template>
          </div>
        </div>

        <div v-if="trip.running" class="small" :style="{ color: trip.background ? 'var(--ok-ink)' : 'var(--muted)' }">
          ● {{ trip.background ? '백그라운드 위치 전송 중 (화면을 꺼도 계속)' : '위치 전송 중 (화면이 켜져 있는 동안)' }} · 경로 {{ providerLabel }} · 핀을 누르면 상세</div>
        <div v-else class="small faint">지도의 <b>T</b> 터미널 · <b>H</b> HUB · <b>↕</b> 높이제한 · <b>검</b> 검문소 핀을 누르면 상세 정보
        </div>
        <!-- fix_1600 + 26.10.24: 시연 주행 - 배속을 골라 경로를 따라 차량 이동. 지도 길게 누르기 = 내 위치 이동 -->
        <!-- 26.10.24: 출발 전 점검(차량·컨테이너)을 마쳐야 시연 주행 가능 -->
        <div v-if="demoOn && simReady && insp && !insp.ready && !trip.simulating" class="sim locked">
          <span class="small" style="font-weight: 700">시연 주행 · 출발 전 점검이 필요합니다</span>
          <div class="chk-row">
            <span :class="insp.vehicle ? 'ok' : 'no'">{{ insp.vehicle ? '✓' : '✕' }} 차량 점검표</span>
            <span :class="insp.container ? 'ok' : 'no'">{{ insp.container ? '✓' : '✕' }} 컨테이너 점검표</span>
          </div>
          <RouterLink class="btn sm primary" to="/m/C-00" style="width: 100%">출발 전 점검하러 가기</RouterLink>
        </div>
        <div v-else-if="demoOn && simReady && !insp" class="small faint">출발 전 점검 상태 확인 중…</div>
        <div v-else-if="demoOn && simReady" class="sim">
          <span class="small" style="font-weight: 700">시연 주행 · {{ trip.simSpeed }}배 <span class="muted" style="font-weight: 500">(기준 80km/h{{ trip.simulating ? (trip.simPaused ? ' · 일시정지' : ' · 주행 중') : '' }})</span></span>
          <div class="speeds" role="radiogroup" aria-label="시연 주행 배속">
            <button v-for="v in SPEEDS" :key="v" type="button" class="spd" :class="{ on: trip.simSpeed === v }" role="radio" :aria-checked="trip.simSpeed === v" @click="trip.simSpeed = v">{{ v }}배</button>
          </div>
          <div class="sim-btns">
            <button v-if="!trip.simulating || trip.simPaused" class="btn sm primary" type="button" @click="startSimulation(); follow = true"><Icon name="play" size="16" />{{ trip.simulating ? '계속' : '시작' }}</button>
            <button v-else class="btn sm" type="button" @click="pauseSimulation(true)">일시정지</button>
            <button v-if="trip.simulating" class="btn sm danger" type="button" @click="stopSimulation()"><Icon name="stop" size="16" />정지</button>
          </div>
          <span v-if="!trip.running" class="small" style="color: var(--warn-ink)">운행 시작 전이라 서버에 기록하지 않고 화면에서만 움직입니다 (음성 안내·경고는 동일)</span>
          <span class="small faint">지도를 길게 누르면(PC는 우클릭) 내 위치를 그 지점으로 옮깁니다 · 시연 중에는 실제 GPS를 쓰지 않습니다</span>
        </div>

        <div class="between" style="padding-top: 10px; border-top: 1px solid var(--sunken)">
          <div class="row" style="flex: 1; min-width: 0">
            <div style="width: 10px; height: 10px; border-radius: 5px; background: var(--accent); flex-shrink: 0"></div>
            <div class="stack" style="gap: 0; min-width: 0">
              <b style="font-size: 15px">{{ order?.dest?.name }}</b>
              <span class="small muted">{{ order?.container?.containerNo }}
                <template v-if="order?.dest?.entranceHeightLimitM"> · 진입로 높이제한 {{ order.dest.entranceHeightLimitM }}m</template>
              </span>
            </div>
          </div>
          <span v-if="order?.status === 'HANDED_OVER'" class="pill ok" style="flex-shrink: 0; font-size: 13px; padding: 5px 12px">인계 완료</span><!-- 26.10.29 QA: 인계 끝난 오더에 인계하기 · 출발 전 점검 버튼이 보이던 문제 -->
          <button v-else-if="(st?.arrived && !st?.demo) || order?.status === 'ARRIVED'" class="btn primary sm" type="button" @click="$router.push(`/arrive/${trip.orderId}`)">인계하기</button>
          <button v-else-if="order?.status === 'IN_TRANSIT'" class="btn sm" type="button" @click="manualArrive">도착</button>
          <button v-else-if="CAN_DEPART.includes(order?.status)" class="btn primary sm" type="button" :disabled="going" @click="go">{{ going ? '시작 중…' : '출발(운행 시작)' }}</button>
          <RouterLink v-else-if="order" class="btn sm" to="/m/C-00">출발 전 점검</RouterLink>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { errText } from '../lib/api' // 26.10.29 QA: 오류는 기사용 문구로
// 화면 표시만 담당. 위치 전송·음성 안내·재탐색은 lib/tracker.js 가 앱 전체에서 처리 (다른 화면으로 가도 계속)
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { KeepAwake } from '@capacitor-community/keep-awake'
import Icon from '../components/Icon.vue'
import MapView from '../components/MapView.vue'
import PlaceSheet from '../components/PlaceSheet.vue'
import TopBar from '../components/TopBar.vue'
import VehicleIconPicker from '../components/VehicleIconPicker.vue'
import { Api } from '../lib/api'
import { checkReady } from '../lib/inspection'
import { hm, km, time } from '../lib/format'
import { session } from '../lib/session'
import { CAN_DEPART, departAndStart } from '../lib/depart'
import { jumpTo, pauseSimulation, startSimulation, startTrip, stopSimulation, trip } from '../lib/tracker'
import { filterCheckpoints } from '../lib/checkpoints'
import { setVoice, voice } from '../lib/voice'

const route = useRoute()
const router = useRouter()
const follow = ref(true)
const loading = ref(true)
const mapRef = ref(null)
if (import.meta.env.DEV) window.__scargoMap = mapRef // 개발(브라우저) 자동 검사용
const selected = ref(null)
const iconSheet = ref(false)
// 26.10.24: 하단 시트 높이만큼 지도 여백 → 내 차량이 시트에 가려지지 않게
const sheetRef = ref(null)
const sheetH = ref(290)
const statsRef = ref(null)
// 26.10.24: 시트를 펼쳐 지도 공간이 좁으면 지도 버튼을 가로 한 줄로 (위 안내 띠와 겹치지 않게)
const rootRef = ref(null)
const rootH = ref(800)
const ctrlRow = computed(() => rootH.value - sheetH.value < 420)
// 26.10.24: 시트 접기(peek: 도착 예정·남은 거리·시간만) / 펴기. 기기에 기억
// 26.10.28 QA: 화면이 짧은 휴대폰(높이 780 미만)은 처음에 시트를 접어 지도가 가려지지 않게. 사용자가 한 번 접거나 펴면 그 상태를 기억
const sheetPeek = ref((() => { try { const v = localStorage.getItem('scargo.naviSheet'); return v ? v === 'peek' : window.innerHeight < 780 } catch { return false } })())
const dragH = ref(null)
function setPeek(v) {
  sheetPeek.value = v
  try { localStorage.setItem('scargo.naviSheet', v ? 'peek' : 'full') } catch { /* 무시 */ }
}
function dragStart(e) {
  const el = sheetRef.value
  if (!el) return
  const startY = e.clientY, startH = el.offsetHeight
  const peekH = statsRef.value ? statsRef.value.offsetTop + statsRef.value.offsetHeight + 16 : 110
  const wasPeek = sheetPeek.value
  sheetPeek.value = false                      // 펼친 높이를 재기 위해 잠깐 펼침
  const fullH = Math.max(el.scrollHeight, startH)
  dragH.value = startH
  let moved = 0
  const move = (ev) => {
    const dy = startY - ev.clientY
    moved = Math.max(moved, Math.abs(dy))
    dragH.value = Math.min(fullH, Math.max(peekH, startH + dy))
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    window.removeEventListener('pointercancel', up)
    // 거의 안 움직였으면 = 누름 → 접기/펴기 전환, 아니면 가까운 쪽으로
    setPeek(moved < 6 ? !wasPeek : dragH.value < (peekH + fullH) / 2)
    dragH.value = null
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
  window.addEventListener('pointercancel', up)
}
let sheetObs = null
onMounted(() => {
  if (typeof ResizeObserver === 'undefined') return
  sheetObs = new ResizeObserver(() => {
    if (sheetRef.value?.offsetHeight) sheetH.value = sheetRef.value.offsetHeight
    if (rootRef.value?.offsetHeight) rootH.value = rootRef.value.offsetHeight
  })
  if (rootRef.value) sheetObs.observe(rootRef.value)
  const tryObs = () => sheetRef.value ? sheetObs.observe(sheetRef.value) : setTimeout(tryObs, 300)
  tryObs()
})
onBeforeUnmount(() => sheetObs?.disconnect())
const st = computed(() => trip.status)
// fix_app_1026d: 남은 시간·도착 예정은 앱에서 계산 (서버 remainingS 는 운행 전 0.0, eta 는 null 이거나 epoch 초)
//  남은 시간 = 남은 거리 ÷ 속도. 속도: 시연 주행 중 = 80km/h × 배속(실제 시계 기준), 실제 주행 = GPS 속도(5km/h 이상),
//  그 외(정지·모름) = 경로 평균속도(거리/소요시간), 그것도 없으면 60km/h. 도착 예정 = 지금 + 남은 시간 (1초마다 갱신)
const nowMs = ref(Date.now())
const clock = setInterval(() => { nowMs.value = Date.now() }, 1000)
const avgMps = computed(() => {
  const r = trip.route
  return r?.distanceM > 0 && r?.durationS > 0 ? r.distanceM / r.durationS : 60 / 3.6
})
const speedMps = computed(() => {
  if (trip.simulating && !trip.simPaused) return 80 / 3.6 * (trip.simSpeed || 1)
  const v = Number(trip.me?.speedKmh)
  if (!trip.simulating && Number.isFinite(v) && v >= 5) return v / 3.6
  return avgMps.value
})
const remainM = computed(() => {
  const v = st.value?.remainingM
  return Number.isFinite(v) ? v : (trip.route?.distanceM ?? null)
})
const remainS = computed(() => (remainM.value == null ? null : remainM.value / speedMps.value))
const etaText = computed(() => {
  if (remainS.value == null) return '-'
  const d = new Date(nowMs.value + remainS.value * 1000)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
})
// fix_app_1026c: 헤딩업(진행 방향이 위) ↔ 북쪽 위. 기기에 기억 (기본 헤딩업)
const headingUp = ref((() => { try { return localStorage.getItem('scargo.headingUp') !== 'off' } catch { return true } })())
function setHeadingUp(v) {
  headingUp.value = v
  try { localStorage.setItem('scargo.headingUp', v ? 'on' : 'off') } catch { /* 무시 */ }
  if (!v) follow.value = follow.value // 북쪽 위로 돌아가도 따라가기는 유지
}
function recenter() {
  follow.value = true
  if (trip.me) mapRef.value?.focus(trip.me.lat, trip.me.lng, 15)
  mapRef.value?.recenter()
}
// fix_1600: 시연 주행 노출 = 서버 /api/app/demo-sim (dev 기본 켜짐, 운영은 ADMIN·시연 계정) 또는 PC 테스트 dev-tools
// 26.10.24: + 내 정보 '시연 주행 버튼' 설정(기본 켜짐) → 서버에 demo-sim API 가 없어도 표시
const demoOn = computed(() => session.demoSim || session.devTools || session.simButton)
const SPEEDS = [1, 2, 5, 10, 30, 50, 100]
const insp = ref(null) // 26.10.24: 출발 전 점검 상태 { vehicle, container, ready }
checkReady().then(r => { insp.value = r })
// 26.10.24: 경로가 있으면 운행 시작 전에도 시연 주행 가능 (도착·인계 후 제외)
const simReady = computed(() => (trip.route?.path?.length || 0) > 1 && !['ARRIVED', 'HANDED_OVER'].includes(order.value?.status) && !(st.value?.arrived && trip.running))
let lastLp = 0
function onLongPress(p) {
  if (!demoOn.value || !simReady.value || !(insp.value?.ready || trip.simulating) || Date.now() - lastLp < 800) return
  lastLp = Date.now()
  if (jumpTo(p.lat, p.lng)) { follow.value = true; mapRef.value?.focus(p.lat, p.lng, 14) }
}
const order = computed(() => trip.order)

// 핀: 누르면 PlaceSheet 로 상세 정보 (kind + id 로 백엔드 /api/app/places/{kind}/{id} 조회)
const markers = computed(() => {
  const r = trip.route
  if (!r) return []
  const list = []
  r.tolls.forEach(t => list.push({ kind: 'TOLL', id: t.id, lat: t.lat, lng: t.lng, alongM: t.alongM, color: '#8A8E94', size: 12, label: `${t.name} 영업소`, name: t.name }))
  filterCheckpoints(r.checkpoints).forEach(c => list.push({ kind: 'CHECKPOINT', id: c.id, lat: c.lat, lng: c.lng, alongM: c.alongM, color: '#1C1E21', size: 22, glyph: '검', label: `과적검문소 · ${c.name}`, name: c.name }))
  r.heights.forEach(h => list.push({ kind: 'HEIGHT', id: h.id, lat: h.lat, lng: h.lng, alongM: h.alongM, color: h.hazard ? '#B8440A' : '#3F7BC4', size: h.hazard ? 26 : 18, glyph: '↕', label: `높이제한 ${h.limitHeightM}m · ${h.name}`, name: h.name }))
  const o = order.value
  if (o?.origin?.lat) list.push({ kind: 'TERMINAL', id: String(o.origin.terminalId), lat: Number(o.origin.lat), lng: Number(o.origin.lng), alongM: 0, color: '#1F4F8C', size: 26, square: true, glyph: 'T', label: o.origin.name, name: o.origin.name })
  if (o?.dest) list.push({ kind: 'HUB', id: String(o.dest.hubId), lat: Number(o.dest.lat), lng: Number(o.dest.lng), alongM: r.distanceM, color: '#B8440A', size: 28, square: true, glyph: 'H', label: o.dest.name, name: o.dest.name })
  return list
})
const selectedAhead = computed(() => selected.value?.alongM != null && st.value ? selected.value.alongM - (st.value.traveledM || 0) : null)
const selectedPassed = computed(() => selected.value?.kind === 'TOLL' && (st.value?.tolls || []).some(t => t.code === selected.value.id && t.passed))

const topAlerts = computed(() => st.value?.arrived ? [] : (st.value?.alerts || []).filter(a => a.type !== 'ARRIVED').slice(0, 2)) // 26.10.24: 도착은 팝업으로

const PROVIDERS = { fixed: '지정 경로(기본 경로 · 회전 안내는 경로선으로 계산)', tmap: 'TMAP 화물차', valhalla: 'Valhalla 화물차', osrm: 'OSRM(승용차 기준)', straight: '직선(안내 없음)' }
const providerLabel = computed(() => PROVIDERS[trip.route?.provider] || trip.route?.provider || '-')

// 상단 카드: 턴바이턴 안내가 있으면 "300m 우회전", 없으면 다음 영업소
const nextLine = computed(() => {
  const n = trip.next
  // fix_app_1026c: 도착 안내(ARRIVE)는 실제 도착 전에는 위 띠에 쓰지 않음 (LOADED 등에서 '도착했습니다'가 보이던 문제)
  if (n && n.type !== 'ARRIVE' && !/도착했습니다/.test(n.text || '')) { // fix_app_1027b: '300m 앞 좌회전' / '지금 좌회전' / '유턴하세요'
    const now = n.distanceM <= 60
    const turn = ['LEFT', 'RIGHT', 'UTURN'].includes(n.type) && n.distanceM <= 1000
    const turnWord = ({ LEFT: '좌회전', RIGHT: '우회전', UTURN: '유턴' })[n.type] // 26.10.28: 표시도 '~입니다'
    return { icon: n.type, big: now ? (turnWord ? `잠시 후 ${turnWord}입니다` : `잠시 후, ${n.text}`) : turn ? (turnWord ? `${km(n.distanceM)} 앞 ${turnWord}입니다` : `${km(n.distanceM)} 앞 ${n.text}`) : km(n.distanceM), small: now || turn ? (n.roadName || '') : n.text }
  }
  const along = st.value?.traveledM ?? 0
  const toll = (trip.route?.tolls || []).find(t => t.alongM > along)
  if (toll) return { icon: 'TOLL', big: km(toll.alongM - along), small: `${toll.name} 영업소까지` }
  return { icon: 'ARRIVE', big: km(st.value?.remainingM ?? trip.route?.distanceM), small: `${order.value?.dest?.name || '목적지 HUB'} 까지` }
})

const tollWindow = computed(() => {
  const all = st.value?.tolls?.length ? st.value.tolls
    : (trip.route?.tolls || []).map(t => ({ code: t.id, name: t.name, alongM: t.alongM, passed: false }))
  const lastPassed = all.map(t => t.passed).lastIndexOf(true)
  const start = Math.max(0, lastPassed)
  return all.slice(start, start + 4)
})


const loadError = ref('')
// fix_app_1026b: LOADED·GATE_OUT 이면 여기서 바로 출발
const going = ref(false), goErr = ref('')
async function go() {
  goErr.value = ''; going.value = true
  try { await departAndStart(order.value.orderId || trip.orderId) } catch (e) { goErr.value = errText(e) } finally { going.value = false }
}
async function load() {
  loading.value = true; loadError.value = ''
  try {
    // 26.10.29 QA: 인계가 끝난 오더는 '현재 운행'으로 다시 열지 않음 → 홈의 다음 오더
    let id = route.params.id ? Number(route.params.id) : (trip.order?.status === 'HANDED_OVER' ? null : trip.orderId)
    if (!id) {
      const home = await Api.home()
      id = home.current?.orderId || null
    }
    if (id) await startTrip(id)
  } catch (e) {
    loadError.value = errText(e)
  } finally {
    loading.value = false
  }
}
onMounted(() => {
  KeepAwake.keepAwake().catch(() => {}) // 내비 화면이 열려 있는 동안 화면 꺼짐 방지
  load()
})

onBeforeUnmount(() => {
  KeepAwake.allowSleep().catch(() => {})
  clearInterval(clock)
  trip.arrivedNotice = null // fix_app_1026d: 화면을 떠나면 도착 팝업 닫기 (돌아와도 다시 뜨지 않음)
})

async function manualArrive() {
  try {
    await Api.arrive(trip.orderId)
    router.push(`/arrive/${trip.orderId}`)
  } catch (e) {
    trip.error = errText(e)
  }
}
</script>

<style scoped>
.map-wrap { position: absolute; inset: 0; } /* 26.10.24: 하단 탭은 화면 밖(App)에 있으므로 여백 0 */
.top-stack { position: absolute; left: 0; right: 0; top: 0; display: flex; flex-direction: column; gap: 8px; z-index: 1000; } /* fix_app_1026c: 검정 띠 상단 밀착 */
.top-stack > :not(.next-card) { margin: 0 12px; }
.next-card { background: #1C1E21; color: #fff; border-radius: 0 0 16px 16px; padding: 12px 14px; display: flex; gap: 12px; align-items: center; }
.recenter { position: absolute; left: 50%; transform: translateX(-50%); z-index: 1001; display: inline-flex; align-items: center; gap: 6px; height: 40px; padding: 0 16px; border-radius: 20px; border: 0; background: #fff; color: var(--primary); font-weight: 700; font-size: 14px; box-shadow: 0 2px 8px rgba(0,0,0,.25); cursor: pointer; }
.hu span { font-size: 12px; font-weight: 800; }
.remain { display: flex; flex-direction: column; align-items: flex-end; flex-shrink: 0; padding-left: 12px; border-left: 1px solid #3A3D42; }
.remain span { font-size: 11px; color: #A9AEB5; }
.remain b { font-size: 17px; white-space: nowrap; }
.alert { border-radius: 12px; padding: 10px 12px; display: flex; gap: 10px; align-items: center; background: #fff; border: 1px solid var(--line-strong); }
.alert b { font-size: 14px; }
.alert span { font-size: 12px; color: var(--muted); }
.alert.danger { background: var(--danger-bg); border: 2px solid var(--accent); color: var(--danger-ink); }
.alert.danger span { color: #5C2A10; }
.alert.warn { background: var(--warn-bg); border-color: var(--warn-line); }
.map-ctrl { position: absolute; right: 12px; bottom: calc(var(--tabbar-h) + 340px); display: flex; flex-direction: column; gap: 8px; z-index: 1000; }
.map-ctrl.row { flex-direction: row; }
.ctrl { background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,.25); }
.bottom-sheet { position: absolute; left: 0; right: 0; bottom: 0; background: #fff; border-radius: 20px 20px 0 0; border-top: 1px solid var(--line); padding: 10px 18px 16px; display: flex; flex-direction: column; gap: 12px; z-index: 1000; box-shadow: 0 -2px 10px rgba(0,0,0,.08); }
.bottom-sheet.detail { max-height: 62%; overflow-y: auto; padding-top: 14px; }
.handle { width: 44px; height: 5px; border-radius: 3px; background: var(--line-strong); }
.grab { align-self: stretch; display: flex; justify-content: center; align-items: center; height: 24px; margin: -10px -18px -4px; cursor: grab; touch-action: none; }
.grab:focus-visible { outline: 2px solid var(--primary); outline-offset: -2px; border-radius: 12px; }
.bottom-sheet.dragging { overflow: hidden; transition: none; }
.bottom-sheet.peek > :not(.grab):not(.grid3) { display: none; }
.overlay.center { align-items: center; justify-content: center; padding: 24px; }
.arr-pop { background: var(--surface); color: var(--ink); width: 100%; max-width: 320px; border-radius: 18px; padding: 22px 20px 18px; display: flex; flex-direction: column; align-items: center; gap: 6px; text-align: center; box-shadow: 0 10px 30px rgba(0,0,0,.3); }
.arr-pop b { font-size: 18px; }
.arr-ic { width: 56px; height: 56px; border-radius: 28px; background: var(--ok-bg); color: var(--ok-ink); display: flex; align-items: center; justify-content: center; margin-bottom: 4px; }
.arr-btns { display: flex; gap: 8px; width: 100%; margin-top: 12px; }
.arr-btns .btn { flex: 1; height: 46px; }
.stat b { font-size: 20px; }
.tolls { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--muted); }
.tolls .on { color: var(--primary); font-weight: 700; }
.tolls .seg { flex: 1; height: 3px; background: var(--line-strong); min-width: 10px; }
.tolls .seg.on { background: var(--primary); }
.sim.locked { background: var(--warn-bg); border: 1px solid var(--warn-line); }
.chk-row { display: flex; gap: 14px; font-size: 13px; font-weight: 600; }
.chk-row .ok { color: var(--ok-ink); }
.chk-row .no { color: var(--danger-ink); }
.sim { display: flex; flex-direction: column; gap: 8px; background: var(--sunken); border-radius: 12px; padding: 10px 10px 10px 12px; }
.speeds { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; }
.sim-btns { display: flex; gap: 8px; }
.sim-btns .btn { flex: 1; white-space: nowrap; }
.spd { min-width: 0; height: 34px; padding: 0; letter-spacing: -0.3px; border-radius: 17px; border: 1px solid var(--line-strong); background: var(--surface); color: var(--ink); font-size: 13px; font-weight: 600; cursor: pointer; }
.spd.on { background: var(--ink); border-color: var(--ink); color: #fff; }
</style>
