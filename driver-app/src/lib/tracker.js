import { errText } from './api' // 26.10.29 QA: 오류는 기사용 문구로
// 운행 추적기 (앱 전체에서 하나만 동작)
// - 운행 중이면 화면을 옮겨도/화면이 꺼져도 위치를 서버로 보낸다
//   · APK: background-geolocation 플러그인 (안드로이드 상단에 "운행 중" 알림이 떠 있는 동안 계속 동작)
//   · 브라우저: 화면이 켜져 있는 동안만 동작
// - 서버 응답(남은 거리·경고·도착)으로 턴바이턴 음성 안내, 높이제한·검문소 음성 경고, 경로 이탈 재탐색
import { reactive } from 'vue'
import { Capacitor, CapacitorHttp, registerPlugin } from '@capacitor/core'
import { LocalNotifications } from '@capacitor/local-notifications'
import { rememberPosition } from './theme'
import { Api, getApiBase, getToken, isNative } from './api'
import { watchPosition } from './geo'
import { sayDistance, speak } from './voice'
import { withTurns } from './turns'
import { filterCheckpoints, filterCheckpointAlerts } from './checkpoints'

const BackgroundGeolocation = registerPlugin('BackgroundGeolocation')

export const trip = reactive({
  orderId: null,
  order: null,
  route: null,
  status: null,       // 서버 TripStatus
  me: null,           // 마지막 위치
  running: false,     // 위치 전송 중
  background: false,  // 백그라운드 위치(APK) 사용 중
  simulating: false,  // fix_1600: 시연 주행 중(일시정지 포함) → 실제 GPS 무시
  simPaused: false,
  simSpeed: 1,        // 1·2·10·30 배 (기준 80km/h)
  simAlongM: 0,
  arrivedNotice: null, // 26.10.24: 도착 팝업 { name, local }
  simLocal: false,    // 26.10.24: 운행 시작 전 시연 주행 (서버에 위치를 보내지 않고 화면에서만 이동)
  next: null,         // { text, type, distanceM, roadName }
  rerouting: false,
  error: ''
})

let stopSource = null
let stopSim = null
let lastSent = 0
let sending = false
let offRouteCount = 0
let lastReroute = 0
let spoken = new Set()
export const turnLog = [] // fix_app_1027b: 회전 음성 안내 기록 (검증·디버그용)

// ---------------------------------------------------------------- 시작 / 종료

// 26.10.27: 도착 표시는 오더가 실제로 운행 중·도착·인계 상태일 때만.
//  서버 trip-status 가 출발 전(ASSIGNED·LOADED·GATE_OUT) 오더에도 arrived=true 를 보내거나,
//  이전 운행 값이 남아 있으면 로그인하자마자 '도착'으로 보이던 문제 방지
const ARRIVED_OK = ['IN_TRANSIT', 'ARRIVED', 'HANDED_OVER']
function sanitizeStatus(st, order) {
  if (!st) return st
  const os = order?.status
  let out = st
  if (os === 'ARRIVED' || os === 'HANDED_OVER') out = { ...st, arrived: true }
  else if (!ARRIVED_OK.includes(os)) out = { ...st, arrived: false }
  if (out?.alerts) out = { ...out, alerts: filterCheckpointAlerts(out.alerts) } // 26.11.01 이 폰 검문소 허용목록
  return out
}

// 26.10.27: 같은 오더를 동시에 두 번 시작하지 않게 (로그인 직후 App 이어하기 + 내비 화면이 함께 부르면 위치 감시가 두 개 생겼음)
let starting = null
export function startTrip(orderId) {
  if (starting && starting.id === orderId) return starting.p
  const p = startTripInner(orderId).finally(() => { if (starting?.p === p) starting = null })
  starting = { id: orderId, p }
  return p
}

// fix_app_1027a: 시작할 때마다 번호를 올려, 기다리는 동안 로그아웃·다시 시작되면 늦게 온 결과는 버림
let tripSeq = 0
async function startTripInner(orderId) {
  if (trip.orderId === orderId && trip.running) return
  const my = ++tripSeq
  const stale = () => my !== tripSeq || trip.orderId !== orderId
  const sameOrder = trip.orderId === orderId
  stopTrip()
  if (trip.simulating || trip.simLocal) stopSimulation(true)
  if (!sameOrder) {
    // 26.10.27: 다른 오더로 바뀌면 이전 오더의 지도·상태·도착 팝업을 먼저 지움 (불러오기에 실패해도 남지 않게)
    trip.order = null; trip.route = null; trip.status = null; trip.next = null; trip.arrivedNotice = null
    simPath = null; simCum = null; trip.simAlongM = 0; turnLog.length = 0
  }
  trip.orderId = orderId
  trip.error = ''
  spoken = new Set()
  offRouteCount = 0
  try {
    const order = await Api.order(orderId)
    const route = await Api.route(orderId)
    const status = await Api.tripStatus(orderId)
    if (stale()) return // 불러오는 사이 로그아웃·다른 오더로 바뀜
    trip.order = order
    const routed = withTurns(route); if (routed?.checkpoints) routed.checkpoints = filterCheckpoints(routed.checkpoints)
    trip.route = routed // fix_app_1027b: 회전 안내가 없으면 경로선 방위각으로 계산 · 26.11.01 이 폰 검문소 허용목록 필터
    trip.status = sanitizeStatus(status, order)
    updateNext()
  } catch (e) {
    if (stale()) return
    trip.order = null; trip.route = null; trip.status = null; trip.next = null
    trip.error = errText(e)
    return
  }
  if (trip.order.status !== 'IN_TRANSIT') return // 출발 전/도착 후에는 지도만 보여줌

  if (stale()) return
  trip.running = true
  const first = (trip.route?.instructions || []).find(i => i.type !== 'DEPART' && i.type !== 'ARRIVE') // fix_app_1026c: 지정 경로(회전 안내 없음)에서 '…앞, 도착했습니다' 문구 방지
  once('start', () => speak(`경로 안내를 시작합니다. 목적지까지 ${sayDistance(trip.status?.remainingM ?? trip.route?.distanceM ?? 0)}입니다.`
    + (first && trip.next ? ` ${sayDistance(trip.next.distanceM)} 앞, ${first.text}.` : '')))
  await startSource()
  if (stale()) stopSources() // 위치 권한을 묻는 사이 로그아웃·다시 시작된 경우
}

export function stopTrip() {
  stopSources()
  trip.running = false
  trip.simulating = false
}

// 26.10.27: 로그아웃·다른 기사 로그인 시 운행 상태 전부 비우기
//  (trip 은 앱 전체에서 하나라서, 지우지 않으면 다음에 로그인한 기사에게 이전 기사의 오더·'도착' 상태가 그대로 보였음)
export function resetTrip() {
  starting = null
  tripSeq++ // fix_app_1027a: 진행 중이던 startTrip 응답 무시
  if (stopSim) { stopSim(); stopSim = null }
  stopSources()
  Object.assign(trip, {
    orderId: null, order: null, route: null, status: null, me: null,
    running: false, background: false, simulating: false, simPaused: false, simSpeed: 1, simAlongM: 0,
    arrivedNotice: null, simLocal: false, next: null, rerouting: false, error: ''
  })
  simPath = null; simCum = null; simStopsDone = new Set()
  spoken = new Set(); turnLog.length = 0; offRouteCount = 0; lastReroute = 0; lastSent = 0; sending = false
}

function stopSources() {
  if (stopSource) { stopSource(); stopSource = null }
  if (stopSim) { stopSim(); stopSim = null }
  trip.background = false
}

async function startSource() {
  if (isNative && Capacitor.isPluginAvailable('BackgroundGeolocation')) {
    try {
      // 안드로이드 13+ : 상단 "운행 중" 알림을 띄우려면 알림 권한 필요
      await LocalNotifications.requestPermissions().catch(() => {})
      const id = await BackgroundGeolocation.addWatcher({
        backgroundTitle: 'SCARGO 운행 중',
        backgroundMessage: '도착할 때까지 위치를 전송합니다. 인계를 마치면 자동으로 꺼집니다.',
        requestPermissions: true,
        stale: false,
        distanceFilter: 20
      }, (loc, err) => {
        if (err) {
          if (err.code === 'NOT_AUTHORIZED') trip.error = '위치 권한이 없습니다. 설정에서 "항상 허용"으로 바꿔 주세요.'
          return
        }
        onLocation({
          lat: loc.latitude,
          lng: loc.longitude,
          accuracyM: loc.accuracy,
          speedKmh: loc.speed != null ? Math.round(loc.speed * 36) / 10 : null,
          heading: loc.bearing != null ? Math.round(loc.bearing) : null
        })
      })
      trip.background = true
      stopSource = () => BackgroundGeolocation.removeWatcher({ id }).catch(() => {})
      return
    } catch (e) {
      trip.error = '백그라운드 위치를 시작하지 못해 화면이 켜진 동안만 전송합니다.'
    }
  }
  stopSource = await watchPosition((loc) => onLocation(loc))
}

// ---------------------------------------------------------------- 시연 주행 (fix_1600)
// 현재 경로(trip.route.path, 예: 기본 경로)를 따라 위치를 만들어 실제 GPS 와 같은 onLocation 으로 넣는다
//  → 서버 위치 보고(웹 관제 이동) · 남은 거리 · 검문소/높이제한/영업소/도착 음성 · 경로 이탈 재탐색이 그대로 동작
// 시연 주행 중(일시정지 포함)에는 실제 GPS 를 무시한다
const SIM_BASE_KMH = 80
const SIM_TICK_MS = 2000
let simCum = null, simPath = null

function simPrepare() {
  const p = trip.route?.path || []
  if (simPath === p && simCum) return p.length > 1
  simPath = p; simCum = [0]
  for (let i = 1; i < p.length; i++) simCum.push(simCum[i - 1] + distM(p[i - 1], p[i]))
  return p.length > 1
}
function simPointAt(along) {
  const p = simPath, c = simCum, total = c[c.length - 1]
  along = Math.max(0, Math.min(total, along))
  let i = 0; while (i < c.length - 2 && c[i + 1] < along) i++
  const a = p[i], b = p[i + 1] || a, len = (c[i + 1] - c[i]) || 1, t = Math.max(0, Math.min(1, (along - c[i]) / len))
  return { lat: a[0] + (b[0] - a[0]) * t, lng: a[1] + (b[1] - a[1]) * t, heading: Math.round(bearingDeg(a, b)), total }
}
function simEmit(pt, speedKmh) {
  return onLocation({ lat: pt.lat, lng: pt.lng, heading: pt.heading, speedKmh, accuracyM: 5, simulated: true }, true)
}
function simTick() {
  if (!trip.simulating || trip.simPaused || !simPrepare()) return
  if (!trip.running && !trip.simLocal) { stopSimulation(); return }
  const step = SIM_BASE_KMH / 3.6 * (SIM_TICK_MS / 1000) * trip.simSpeed
  // 26.10.24: 배속이 높으면 한 번에 수 km 를 건너뛰어 회전·높이제한·검문소 음성 안내가 빠짐
  //  → 안내 지점 바로 앞(회전 1km·300m·40m 전, 높이제한·검문소 1.5km 전, 영업소 1km 전)에서 한 번씩 멈춰 안내가 나오게 함
  const stop = simNextStop(trip.simAlongM, trip.simAlongM + step)
  trip.simAlongM = stop ?? trip.simAlongM + step
  const pt = simPointAt(trip.simAlongM)
  const end = trip.simAlongM >= pt.total
  if (end) trip.simAlongM = pt.total
  const sent = simEmit(pt, SIM_BASE_KMH * trip.simSpeed)
  if (end) { // 끝점 도달
    trip.simPaused = true
    // fix_app_1026d: 고배속에서는 도착 반경(지오펜스) 판정이 안 될 수 있어, 경로 끝에 닿으면 도착 처리를 보장
    Promise.resolve(sent).finally(() => ensureArrived())
  }
}

// fix_app_1026d: 경로 끝 도달 → 서버가 아직 도착이 아니라면 도착 처리(POST /arrive) 후 도착 팝업
async function ensureArrived() {
  if (trip.status?.arrived) return
  if (!trip.simLocal && trip.running && trip.orderId) {
    try { await Api.arrive(trip.orderId) } catch (e) { if (e.status !== 409) { trip.error = errText(e); return } }
  }
  trip.status = { ...(trip.status || {}), arrived: true, remainingM: 0, remainingS: 0 }
  afterStatus(trip.me)
}

let simStopsDone = new Set()
// 경로선 길이와 서버 경로 거리(distanceM)가 조금 다를 수 있어 비율로 맞춤
function simScale() {
  const tot = simCum?.[simCum.length - 1] || 1
  return (trip.route?.distanceM || tot) / tot
}
/** 시연 주행 중 진행 거리 (서버 경로 거리 기준) */
export function simAlongRoute() { return simCum ? trip.simAlongM * simScale() : 0 }
function simNextStop(fromP, toP) {
  // fix_app_1027b: 1·2배도 '지금' 구간(15~60m)을 한 번에 넘을 수 있어 모든 배속에서 안내 지점 앞에 멈춤
  const k = simScale(), from = fromP * k, to = toP * k
  const r = trip.route || {}
  const stops = []
  // 26.10.29 QA: 고배속(30배 이상)에서는 회전마다 3번씩 멈추면 허브 근처(회전이 많은 구간)에서 몇 분씩 걸려 멈춘 것처럼 보임
  //  → 30배 이상은 300m 앞 한 번만 멈춤 (1·2·5·10배는 1km · 300m · 40m 그대로)
  const fast = (trip.simSpeed || 1) >= 30
  for (const i of r.instructions || []) if (i.type !== 'DEPART' && i.type !== 'ARRIVE') { if (fast) stops.push(i.alongM - 290); else stops.push(i.alongM - 990, i.alongM - 290, i.alongM - 40) } // 26.10.28: '잠시 후' 안내용 40m 전 멈춤
  for (const h of r.heights || []) if (h.hazard) stops.push(h.alongM - 1500)
  for (const c of r.checkpoints || []) stops.push(c.alongM - 1500)
  for (const t of r.tolls || []) stops.push(t.alongM - 990) // fix_app_1026c: 1km 앞에서 '1킬로미터 앞' 안내
  const next = stops.filter(a => a > from + 1 && a < to && !simStopsDone.has(Math.round(a))).sort((a, b) => a - b)[0]
  if (next == null) return null
  simStopsDone.add(Math.round(next))
  return next / k
}

/** 시연 주행 시작(또는 이어서). 시작 위치 = 서버가 계산한 진행 거리 */
export function startSimulation() {
  if (!trip.route || !simPrepare()) return false
  trip.simLocal = !trip.running // 운행 시작 전이면 화면에서만 이동 (서버 기록 없음)
  if (!trip.simulating && trip.simLocal) speak(`시연 주행을 시작합니다. 목적지까지 ${sayDistance(trip.route.distanceM || 0)}입니다.`)
  // 경로 위에 있을 때만 현재 진행 지점부터, 실제 GPS 가 경로 밖(예: 사무실 PC)이면 출발점부터
  if (!trip.simulating) {
    simStopsDone = new Set()
    spoken = new Set([...spoken].filter(k => k === 'start')) // 시연을 다시 시작하면 음성 안내도 처음부터
  }
  if (!trip.simulating) trip.simAlongM = (trip.status && !trip.status.offRoute && !trip.status.arrived) ? (trip.status.traveledM || 0) : 0
  trip.simulating = true; trip.simPaused = false
  if (!stopSim) { const t = setInterval(simTick, SIM_TICK_MS); stopSim = () => clearInterval(t) }
  return true
}
export function pauseSimulation(p = true) { if (trip.simulating) trip.simPaused = p }
export function stopSimulation(keepStatus = false) {
  if (stopSim) { stopSim(); stopSim = null }
  const wasLocal = trip.simLocal
  trip.simulating = false; trip.simPaused = false; trip.simLocal = false
  if (wasLocal && trip.orderId && !keepStatus) Api.tripStatus(trip.orderId).then(st => { trip.status = sanitizeStatus(st, trip.order); updateNext() }).catch(() => {}) // 화면 계산값 → 서버 값으로 되돌림
}
/** 지도 길게 누르기: 내 위치를 그 지점으로 (시연 전용). 경로 위 가장 가까운 지점부터 시연 주행을 이어간다 */
export function jumpTo(lat, lng) {
  if (!trip.route) return false
  if (!trip.simulating) trip.simLocal = !trip.running
  const wasRunning = trip.simulating && !trip.simPaused
  let bd = Infinity
  if (simPrepare()) {
    let best = 0
    for (let i = 0; i < simPath.length; i++) { const d = distM([lat, lng], simPath[i]); if (d < bd) { bd = d; best = i } }
    trip.simAlongM = simCum[best]
  }
  if (!trip.simulating) { trip.simulating = true; trip.simPaused = true } // 실제 GPS 무시 (재생을 누르면 여기서부터)
  if (!stopSim) { const t = setInterval(simTick, SIM_TICK_MS); stopSim = () => clearInterval(t) }
  onLocation({ lat, lng, heading: null, speedKmh: 0, accuracyM: 5, simulated: true }, true)
  trip.simPaused = !wasRunning
  // 경로에서 300m 넘게 떨어진 곳이면 바로 재탐색 (서버는 60초 안 재요청이면 기존 경로를 돌려줌 → 가장 가까운 경로 지점부터 이어감)
  if (bd > 300 && !trip.rerouting && !trip.simLocal) reroute({ lat, lng })
  return true
}
// 이전 버전 호환 (PC 테스트 버튼)
export function toggleSimulation(on) { on ? startSimulation() : stopSimulation() }

function distM(a, b) {
  const R = 6371000, r = Math.PI / 180
  const dLat = (b[0] - a[0]) * r, dLng = (b[1] - a[1]) * r
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * r) * Math.cos(b[0] * r) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(x))
}
function bearingDeg(a, b) {
  const r = Math.PI / 180, y = Math.sin((b[1] - a[1]) * r) * Math.cos(b[0] * r)
  const x = Math.cos(a[0] * r) * Math.sin(b[0] * r) - Math.sin(a[0] * r) * Math.cos(b[0] * r) * Math.cos((b[1] - a[1]) * r)
  return (Math.atan2(y, x) / r + 360) % 360
}

// ---------------------------------------------------------------- 위치 → 서버

// 26.10.24: 운행 시작 전 시연 주행용 - 서버 TripStatus 와 같은 모양으로 화면에서 계산
function localStatus() {
  const r = trip.route || {}
  const along = simAlongRoute(), total = r.distanceM || 0
  const rem = Math.max(0, total - along)
  const alerts = []
  for (const h of r.heights || []) { const a = h.alongM - along; if (h.hazard && a >= 0 && a <= 5000) alerts.push({ type: 'HEIGHT', level: 'DANGER', title: `전방 ${(a / 1000).toFixed(1)}km 높이제한 ${h.limitHeightM}m`, message: h.name, distanceAheadM: a }) }
  for (const c of r.checkpoints || []) { const a = c.alongM - along; if (a >= 0 && a <= 3000) alerts.push({ type: 'CHECKPOINT', level: 'WARN', title: `전방 ${(a / 1000).toFixed(1)}km 과적검문소입니다`, message: c.name, distanceAheadM: a }) }
  trip.status = {
    ...(trip.status || {}), traveledM: along, remainingM: rem,
    remainingS: r.distanceM ? (r.durationS || 0) * rem / r.distanceM : 0,
    eta: new Date(Date.now() + (r.distanceM ? (r.durationS || 0) * 1000 * rem / r.distanceM : 0)).toISOString(),
    demo: true, // 화면 계산값 표시 (인계 버튼 등은 서버 값 기준)
    offRoute: false, arrived: total > 0 && rem <= 30, alerts, tolls: (r.tolls || []).map(t => ({ code: t.id, name: t.name, passed: t.alongM <= along }))
  }
}

async function onLocation(loc, force = false) {
  if (trip.simulating && !loc.simulated) return // 시뮬레이션 중에는 실제 GPS 무시
  trip.me = loc
  if (!loc.simulated) rememberPosition(loc.lat, loc.lng) // 26.10.24: 자동 다크 모드의 일출·일몰 계산 위치
  if (loc.simulated && trip.simLocal) { localStatus(); afterStatus(loc); return } // 26.10.24: 운행 전 시연 - 서버 대신 화면에서 계산
  const now = Date.now()
  if (!force && (sending || now - lastSent < 4000)) return
  lastSent = now
  sending = true
  try {
    const body = { ...loc, recordedAt: new Date().toISOString() }
    delete body.simulated
    trip.status = await post(`/api/app/orders/${trip.orderId}/locations`, body)
    trip.error = ''
    afterStatus(loc)
  } catch (e) {
    trip.error = errText(e)
  } finally {
    sending = false
  }
}

// APK 백그라운드에서는 WebView 의 fetch 가 느려질 수 있어 네이티브 HTTP 로 보낸다
async function post(path, data) {
  if (!isNative) return Api.sendLocation(trip.orderId, data)
  const res = await CapacitorHttp.post({
    url: getApiBase() + path,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` },
    data
  })
  if (res.status >= 400) throw new Error(res.data?.message || `위치 전송 실패 (${res.status})`)
  return res.data
}

function afterStatus(loc) {
  const st = trip.status
  updateNext()

  // 1) 도착
  if (st.arrived && trip.simLocal) { // 운행 전 시연: 안내만 하고 오더 상태는 그대로
    once('arrived', () => {
      speak(`목적지 ${trip.order?.dest?.name || ''}에 도착했습니다. 시연 주행을 마칩니다.`, { urgent: true })
      trip.arrivedNotice = { name: trip.order?.dest?.name || '목적지', local: true }
    })
    stopSimulation(true) // 도착 화면(남은 0km)을 그대로 둠. 다시 시작하면 처음부터
    return
  }
  if (st.arrived) {
    if (trip.order) trip.order = { ...trip.order, status: 'ARRIVED' }
    once('arrived', () => {
      speak(`목적지 ${trip.order?.dest?.name || ''}에 도착했습니다. 인계를 진행하세요.`, { urgent: true })
      trip.arrivedNotice = { name: trip.order?.dest?.name || '목적지', local: false }
    })
    stopSources()
    trip.running = false
    trip.simulating = false
    return
  }

  // 2) 높이제한 (내 차량보다 낮은 곳) · 과적검문소 - 2km 앞에서 한 번
  const along = curAlong()
  for (const h of trip.route?.heights || []) {
    const ahead = h.alongM - along
    if (h.hazard && ahead > 0 && ahead <= 2000) {
      once('h' + h.id, () => speak(`위험. ${sayDistance(ahead)} 앞, 높이제한 ${Number(h.limitHeightM)}미터 구간입니다. 내 차량이 더 높습니다. 우회하세요.`, { urgent: true }))
    }
  }
  for (const c of trip.route?.checkpoints || []) {
    const ahead = c.alongM - along
    if (ahead > 0 && ahead <= 2000) once('c' + c.id, () => speak(`전방 ${sayDistance(ahead)} 과적검문소입니다.`))
  }
  // 26.10.24: 고속도로 영업소(톨게이트) 1km 앞 안내
  for (const t of trip.route?.tolls || []) {
    const ahead = t.alongM - along
    if (ahead > 0 && ahead <= 1000) once('toll' + t.id, () => speak(`${sayDistance(ahead)} 앞, ${t.name} 영업소입니다. 하이패스 차로 또는 통행권을 확인하세요.`))
  }

  // 3) 턴바이턴 (fix_app_1027b: 회전마다 1km·300m·잠시후 3단계, 단계마다 한 번만)
  //  위치가 띄엄띄엄 들어와(고배속) 여러 단계를 한 번에 지나면 가장 가까운 단계 하나만 말하고 먼 단계는 말한 것으로 처리
  const n = trip.next
  if (n && n.type !== 'ARRIVE') {
    const key = `${n.index}@${Math.round(n.alongM)}`
    const stage = n.distanceM <= 60 ? 'now' : n.distanceM <= 300 ? '300' : n.distanceM <= 1000 ? '1k' : null
    if (stage) {
      const order = ['1k', '300', 'now']
      const text = n.text.replace(/\.$/, '')
      // 26.10.28: 모든 단계 '~입니다' — '1킬로미터 앞 우회전입니다' / '300미터 앞 좌회전입니다' / '잠시 후 유턴입니다'
      const turnWord = ({ LEFT: '좌회전', RIGHT: '우회전', UTURN: '유턴' })[n.type]
      const say = stage === 'now'
        ? (turnWord ? `잠시 후 ${turnWord}입니다.` : `잠시 후, ${text}입니다.`)
        : (turnWord ? `${sayDistance(n.distanceM)} 앞 ${turnWord}입니다.` : `${sayDistance(n.distanceM)} 앞, ${text}입니다.`)
      if (!order.slice(order.indexOf(stage)).some(x => spoken.has(`t${key}:${x}`))) { // 더 가까운 단계를 이미 말했으면 다시 안 함
        order.slice(0, order.indexOf(stage)).forEach(x => spoken.add(`t${key}:${x}`))
        once(`t${key}:${stage}`, () => {
          turnLog.push({ t: new Date().toISOString(), index: n.index, type: n.type, stage, distanceM: Math.round(n.distanceM), along: Math.round(along), speed: trip.simSpeed, text: say })
          if (turnLog.length > 500) turnLog.shift()
          speak(say, { urgent: stage === 'now' })
        })
      }
    }
  }

  // 4) 경로 이탈 → 두 번 연속이면 재탐색 (60초에 한 번)
  offRouteCount = st.offRoute ? offRouteCount + 1 : 0
  if (offRouteCount >= 2 && !trip.rerouting && Date.now() - lastReroute > 60000) reroute(loc)
}

async function reroute(loc) {
  trip.rerouting = true
  lastReroute = Date.now()
  speak('경로를 벗어났습니다. 경로를 다시 탐색합니다.', { urgent: true })
  try {
    const rr = withTurns(await Api.reroute(trip.orderId, { lat: loc.lat, lng: loc.lng })); if (rr?.checkpoints) rr.checkpoints = filterCheckpoints(rr.checkpoints); trip.route = rr
    if (trip.simulating) { simPath = null; trip.simAlongM = 0; simStopsDone = new Set() } // fix_1600: 새 경로 처음(=현재 위치)부터 시연 주행
    offRouteCount = 0
    spoken = new Set([...spoken].filter(k => !k.startsWith('t'))) // 턴 안내는 새 경로 기준으로 다시
    updateNext()
  } catch (e) {
    trip.error = errText(e)
  } finally {
    trip.rerouting = false
  }
}

// 다음 안내 지점 (서버가 계산한 traveledM 기준)
// 시연 주행 중에는 시연 위치(정확), 아니면 서버가 계산한 진행 거리
function curAlong() { return trip.simulating && simCum ? simAlongRoute() : (trip.status?.traveledM || 0) }

function updateNext() {
  const list = trip.route?.instructions || []
  const along = curAlong()
  const idx = list.findIndex(i => i.type !== 'DEPART' && i.alongM > along + 15)
  trip.next = idx < 0 ? null : { ...list[idx], index: idx, distanceM: list[idx].alongM - along }
}

function once(key, fn) {
  if (spoken.has(key)) return
  spoken.add(key)
  fn()
}
