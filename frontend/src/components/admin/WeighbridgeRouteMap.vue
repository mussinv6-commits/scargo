<template>
  <!-- 26.10.02 추가: 검사소 차량 이동 경로 지도 (OpenStreetMap + Leaflet, 방식 B: 지점끼리 선으로 연결) -->
  <div class="wrm" :class="{ 'is-compact': compact }">
    <div ref="mapEl" class="wrm-map" role="img" :aria-label="ariaLabel"></div>
    <ul v-if="!compact" class="wrm-legend">
      <li><i class="dot is-origin"></i>진입 게이트</li>
      <li><i class="dot is-wb"></i>검사소</li>
      <li><i class="dot is-dest"></i>목적지</li>
      <li class="wrm-state" :class="status">{{ stateText }}</li>
    </ul>
    <p v-if="missingText" class="wrm-note">{{ missingText }}</p>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import truckImg from '@/assets/weighbridge/truck_route.png' // 26.10.02: 이동 차량 이미지 (배경 제거본, 왼쪽을 보고 있음)

const props = defineProps({
  route: { type: Object, default: null }, // /api/v1/weighbridge/route 응답
  status: { type: String, default: 'pending' }, // pending | pass | fail
  compact: { type: Boolean, default: false }, // 26.10.02: 미니 플레이어(소형 팝업)용 - 범례 숨기고 부모 높이를 꽉 채움
})
const emit = defineEmits(['phase']) // arriving(게이트→검사소 이동 중) | atScale | departing | arrived

const COLORS = { navy: '#0a2540', orange: '#ff6b00', green: '#0f9d6e', red: '#d63a3a', gray: '#94a3b8' }
const reduceMotion = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

const mapEl = ref(null)
let map = null
let layer = null
let truckMarker = null
let animFrame = null
let trail = null
let resizeObs = null

const hasCoord = (p) =>
  !!p && p.lat != null && p.lng != null && Number.isFinite(Number(p.lat)) && Number.isFinite(Number(p.lng))
const ll = (p) => [Number(p.lat), Number(p.lng)]

const stateText = computed(() => {
  if (props.status === 'pass') return '통과: 목적지로 이동'
  if (props.status === 'fail') return '과적: 검사소에서 재계량 대기'
  return '계량 대기'
})
const missingText = computed(() => {
  const r = props.route
  if (!r) return '경로 정보를 불러오는 중입니다.'
  if (!hasCoord(r.origin) && !hasCoord(r.destination))
    return '게이트 좌표가 없어 경로를 그릴 수 없습니다. 검문소 관리에서 위도·경도를 입력해 주세요.'
  if (!hasCoord(r.destination)) return '이 차량의 운행정보에 목적지 게이트가 없어 검사소까지만 표시합니다.'
  return ''
})
const ariaLabel = computed(() => {
  const r = props.route
  if (!r) return '차량 이동 경로 지도'
  return `${r.origin?.name ?? '진입 게이트'}에서 검사소를 거쳐 ${r.destination?.name ?? '목적지'}로 가는 경로, ${stateText.value}`
})

function dotIcon(color, big = false) {
  const size = big ? 18 : 14
  return L.divIcon({
    className: 'wrm-pin',
    html: `<span style="display:block;width:${size}px;height:${size}px;border-radius:50%;background:${color};border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.35)"></span>`,
    iconSize: [size + 6, size + 6],
    iconAnchor: [(size + 6) / 2, (size + 6) / 2],
    tooltipAnchor: [0, -(size / 2 + 4)],
  })
}

// 이미지 원본은 왼쪽(서쪽)을 보고 있음 → 동쪽으로 갈 때는 좌우 반전
function truckIcon() {
  const TRUCK_W = props.compact ? 72 : 96
  const TRUCK_H = Math.round((TRUCK_W * 91) / 320)
  return L.divIcon({
    className: 'wrm-pin wrm-truck',
    html: `<img src="${truckImg}" alt="" width="${TRUCK_W}" height="${TRUCK_H}" draggable="false">`,
    iconSize: [TRUCK_W, TRUCK_H],
    iconAnchor: [TRUCK_W / 2, TRUCK_H - 2], // 바퀴 아래가 경로 위에 오도록
  })
}
function faceTo(from, to) {
  const el = truckMarker?.getElement()
  if (!el || !from || !to) return
  el.classList.toggle('is-east', to[1] > from[1])
}

function initMap() {
  if (map || !mapEl.value) return
  map = L.map(mapEl.value, { zoomControl: true, attributionControl: true, scrollWheelZoom: false }).setView(
    [37.478, 126.604],
    14,
  )
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  }).addTo(map)
  layer = L.layerGroup().addTo(map)
  setTimeout(() => map && map.invalidateSize(), 150)
}

// mode: 'arrive' = 게이트 인부터 검사소까지 트럭이 달려오는 장면부터 시작
//       'status' = 검사소에 도착한 상태에서 판정 결과(통과/과적)만 반영
function draw(mode = 'status') {
  if (!map || !layer) return
  cancelAnimationFrame(animFrame)
  layer.clearLayers()
  truckMarker = null
  trail = null
  const r = props.route
  if (!r) return

  const bounds = []
  // 배경: 나머지 게이트
  ;(r.otherGates || []).filter(hasCoord).forEach((g) => {
    L.circleMarker(ll(g), { radius: 5, color: COLORS.gray, weight: 1, fillColor: '#fff', fillOpacity: 1 })
      .bindTooltip(g.name || g.code, { direction: 'top' })
      .addTo(layer)
    bounds.push(ll(g))
  })

  const origin = hasCoord(r.origin) ? r.origin : null
  const wb = hasCoord(r.weighbridge) ? r.weighbridge : null
  const dest = hasCoord(r.destination) ? r.destination : null
  const vias = (r.waypoints || []).filter(hasCoord)

  // 1구간: 진입 게이트 → 검사소. 'arrive' 모드면 회색 점선 위로 트럭이 지나가며 남색으로 칠해짐
  const leg1 = origin && wb ? [ll(origin), ll(wb)] : []
  const animateLeg1 = mode === 'arrive' && leg1.length === 2 && props.status === 'pending'
  if (leg1.length === 2) {
    L.polyline(
      leg1,
      animateLeg1
        ? { color: COLORS.gray, weight: 4, opacity: 0.9, dashArray: '8 8' }
        : { color: COLORS.navy, weight: 5, opacity: 0.9 },
    ).addTo(layer)
  }
  // 2구간: 검사소 → (경유지) → 목적지
  const leg2 = [wb, ...vias, dest].filter(Boolean).map(ll)
  if (leg2.length >= 2) {
    const style =
      props.status === 'pass'
        ? { color: COLORS.green, weight: 5, opacity: 0.95 }
        : props.status === 'fail'
          ? { color: COLORS.red, weight: 4, opacity: 0.8, dashArray: '8 8' }
          : { color: COLORS.gray, weight: 4, opacity: 0.9, dashArray: '8 8' }
    L.polyline(leg2, style).addTo(layer)
  }

  if (origin) {
    L.marker(ll(origin), { icon: dotIcon(COLORS.navy) })
      .bindTooltip(`진입 · ${origin.name || origin.code}`, { permanent: true, direction: 'top', className: 'wrm-tip' })
      .addTo(layer)
    bounds.push(ll(origin))
  }
  vias.forEach((v) => {
    L.circleMarker(ll(v), { radius: 4, color: COLORS.gray, fillColor: COLORS.gray, fillOpacity: 1 })
      .bindTooltip(v.name, { direction: 'top' })
      .addTo(layer)
    bounds.push(ll(v))
  })
  if (dest) {
    L.marker(ll(dest), { icon: dotIcon(props.status === 'fail' ? COLORS.gray : COLORS.green) })
      .bindTooltip(`목적지 · ${dest.name || dest.code}`, { permanent: true, direction: 'top', className: 'wrm-tip' })
      .addTo(layer)
    bounds.push(ll(dest))
  }
  if (wb) {
    const wbColor = props.status === 'fail' ? COLORS.red : COLORS.orange
    const wbLabel = props.status === 'fail' ? `${wb.name} · 과적 재계량 대기` : wb.name
    L.marker(ll(wb), { icon: dotIcon(wbColor, true), zIndexOffset: 500 })
      .bindTooltip(wbLabel, { permanent: true, direction: 'bottom', className: 'wrm-tip is-wb' })
      .addTo(layer)
    bounds.push(ll(wb))
  }

  const pad = props.compact ? [24, 24] : [40, 40]
  if (bounds.length === 1) map.setView(bounds[0], 16)
  else if (bounds.length > 1) map.fitBounds(bounds, { padding: pad, maxZoom: 17 })

  // 트럭: 게이트 인 → 검사소까지 달려옴 → (통과면) 목적지까지 이동 / (과적이면) 검사소에 멈춤
  if (animateLeg1) {
    truckMarker = L.marker(leg1[0], { icon: truckIcon(), zIndexOffset: 1000 }).addTo(layer)
    faceTo(leg1[0], leg1[1])
    emit('phase', 'arriving')
    animateTruck(leg1, COLORS.navy, 3200, () => {
      if (leg2.length >= 2) faceTo(leg2[0], leg2[1])
      emit('phase', 'atScale')
    })
  } else if (wb) {
    truckMarker = L.marker(ll(wb), { icon: truckIcon(), zIndexOffset: 1000 }).addTo(layer)
    if (leg2.length >= 2) faceTo(leg2[0], leg2[1]) // 다음 갈 방향을 보고 서 있게
    if (props.status === 'pass' && leg2.length >= 2) {
      emit('phase', 'departing')
      animateTruck(leg2, COLORS.green, 3500, () => emit('phase', 'arrived'))
    } else {
      emit('phase', 'atScale')
    }
  } else if (origin) {
    truckMarker = L.marker(ll(origin), { icon: truckIcon(), zIndexOffset: 1000 }).addTo(layer)
  }
}

// 경로를 따라 트럭을 일정 속도로 이동하면서, 지나온 길을 color 로 칠함
function animateTruck(path, color = COLORS.green, DURATION = 3500, onDone) {
  if (!truckMarker) return
  trail = L.polyline([path[0]], { color, weight: 5, opacity: 0.95 }).addTo(layer)
  if (reduceMotion) {
    truckMarker.setLatLng(path[path.length - 1])
    trail.setLatLngs(path)
    onDone?.()
    return
  }
  const seg = []
  let total = 0
  for (let i = 1; i < path.length; i++) {
    const d = Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1])
    seg.push(d)
    total += d
  }
  const start = performance.now()
  const step = (now) => {
    const p = Math.min(1, (now - start) / DURATION)
    let dist = total * p
    let i = 0
    while (i < seg.length - 1 && dist > seg[i]) {
      dist -= seg[i]
      i++
    }
    const t = seg[i] ? Math.min(1, dist / seg[i]) : 1
    const a = path[i]
    const b = path[i + 1]
    faceTo(a, b)
    const pos = [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]
    truckMarker.setLatLng(pos)
    trail?.setLatLngs([...path.slice(0, i + 1), pos])
    if (p < 1) animFrame = requestAnimationFrame(step)
    else onDone?.()
  }
  animFrame = requestAnimationFrame(step)
}

onMounted(() => {
  initMap()
  draw(props.status === 'pending' ? 'arrive' : 'status')
  // 미니 플레이어를 크게/작게 바꾸면 지도 크기 다시 계산
  if (typeof ResizeObserver !== 'undefined' && mapEl.value) {
    resizeObs = new ResizeObserver(() => map && map.invalidateSize())
    resizeObs.observe(mapEl.value)
  }
})
// 새 차량(경로)이 오면 게이트 인부터 다시 재생, 판정이 바뀌면 그 결과만 반영
watch(
  () => props.route,
  () => {
    initMap()
    draw(props.status === 'pending' ? 'arrive' : 'status')
  },
)
watch(
  () => props.status,
  () => {
    initMap()
    draw('status')
  },
)
// 처음부터 다시 보기
function replay() {
  initMap()
  draw(props.status === 'pending' ? 'arrive' : 'status')
}
defineExpose({ replay })
onBeforeUnmount(() => {
  resizeObs?.disconnect()
  cancelAnimationFrame(animFrame)
  if (map) {
    map.remove()
    map = null
  }
})
</script>

<style scoped>
.wrm {
  margin-top: 16px;
}
.wrm.is-compact {
  margin-top: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.wrm.is-compact .wrm-map {
  flex: 1;
  height: auto;
  min-height: 0;
  border: 0;
  border-radius: 0;
}
.wrm.is-compact .wrm-note {
  margin: 0;
  padding: 6px 10px;
  font-size: 11.5px;
  background: #fff;
}
.wrm-map {
  height: 280px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  z-index: 0;
}
.wrm-legend {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 16px;
  font-size: 12.5px;
  color: #64748b;
}
.wrm-legend li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.dot.is-origin {
  background: #0a2540;
}
.dot.is-wb {
  background: #ff6b00;
}
.dot.is-dest {
  background: #0f9d6e;
}
.wrm-state {
  margin-left: auto;
  font-weight: 700;
  color: #0a2540;
}
.wrm-state.pass {
  color: #0f9d6e;
}
.wrm-state.fail {
  color: #d63a3a;
}
.wrm-note {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: #64748b;
}
:deep(.wrm-pin) {
  background: transparent;
  border: 0;
}
:deep(.wrm-truck img) {
  display: block;
  width: 100%;
  height: auto;
  filter: drop-shadow(0 3px 3px rgba(0, 0, 0, 0.35));
  transition: transform 0.2s ease;
  user-select: none;
  pointer-events: none;
}
:deep(.wrm-truck.is-east img) {
  transform: scaleX(-1);
}
:deep(.wrm-tip) {
  font-family: inherit;
  font-size: 12px;
  font-weight: 600;
  color: #0a2540;
  padding: 3px 8px;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}
:deep(.wrm-tip.is-wb) {
  color: #fff;
  background: #0a2540;
  border-color: #0a2540;
}
:deep(.wrm-tip.is-wb::before) {
  border-bottom-color: #0a2540;
}
</style>
