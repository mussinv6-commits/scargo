<template>
<section class="scr" id="W-03">
<div class="net">
<div class="card full"><h3><i class="bi bi-map"></i> 네트워크 관제 <span class="meta">· 인천신항 → 곤지암 허브 → 물류센터</span></h3>
<div ref="el" class="nmap"></div>
<div class="legend" style="margin-top:10px"><span class="chip"><i style="background:#102644"></i><b>항만</b></span><span class="chip"><i style="background:#EECC83"></i><b>허브</b></span><span class="chip"><i style="background:#C9962E;height:5px"></i>지선 (허브 → 센터)</span><span class="chip"><i style="background:#2e9e5a"></i>정상</span><span class="chip"><i style="background:#f08c00"></i>주의</span><span class="chip"><i style="background:#d32f2f"></i>긴급</span><span class="chip"><i style="background:#2851c6;border-radius:50%"></i>차량 (출발 전은 출발지, 운행 중은 구간 중간)</span></div>
</div>
<div class="card eq"><h3><i class="bi bi-geo-alt"></i> 거점 <span class="meta">{{ d.nodes.length }}곳</span></h3>
<table :style="pgN.reset"><tr><th>거점</th><th>구분</th><th>상태</th></tr><tr v-for="n in pgN.rows" :key="n.centerId" style="cursor:pointer" @click="focus(n)"><td>{{ n.name }}</td><td>{{ TY[n.type] || n.type }}</td><td><span :class="'b ' + (n.level || 'ok')">{{ LV[n.level] || '정상' }}</span></td></tr><tr v-if="!d.nodes.length"><td colspan="3" class="meta">거점이 없습니다.</td></tr></table><Pager :p="pgN"/>
</div>
<div class="card eq"><h3><i class="bi bi-truck"></i> 운행 차량 <span class="meta">{{ d.vehicles.length }}대</span></h3>
<table :style="pgV.reset"><tr><th>차량</th><th>구간</th><th>상태</th><th>지연</th></tr>
<tr v-for="(v, i) in pgV.rows" :key="i"><td>{{ v.vehicleNo }}<div class="meta">{{ v.kind }}</div></td><td style="font-size:13px">{{ v.segment }}</td><td><span class="b gray">{{ stl(v.status) }}</span><div class="meta">ETA {{ hm(v.eta) }}</div></td><td><span :class="'b ' + (v.delayMin >= 30 ? 'warn' : 'ok')">{{ v.delayMin || 0 }}분</span></td></tr>
<tr v-if="!d.vehicles.length"><td colspan="4" class="meta">운행 중인 차량이 없습니다.</td></tr></table><Pager :p="pgV"/></div>
</div></section>
</template>
<script setup>
import Pager from '../comp/Pager.vue'
import { usePage } from '../comp/pager.js'
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { get, hm, stl } from '../api.js'
const props = defineProps(['tick', 'role'])
// 네트워크 관제: GET /api/web/network → Leaflet + OpenStreetMap. DB 에 좌표가 없어 거점 이름으로 대표 좌표를 둔다 (lat/lng 가 오면 그 값 우선)
const d = ref({ nodes: [], vehicles: [] })
const TY = { HUB: '허브', PORT: '항만', DISTRIBUTION_CENTER: '물류센터' }, LV = { ok: '정상', warn: '주의', crit: '긴급' }
const COORD = [['곤지암', 37.351, 127.346], ['인천항', 37.338, 126.623], ['인천신항', 37.338, 126.623], ['인천', 37.476, 126.708], ['경기', 37.263, 127.029], ['서울', 37.536, 126.988], ['부산', 35.104, 129.04], ['평택', 36.97, 126.83]]
const ll = (n) => (n && n.lat && n.lng ? [n.lat, n.lng] : (COORD.find(c => String(n?.name || n).includes(c[0])) || [0, 37.4, 127]).slice(1))
const col = (l) => (l === 'crit' ? '#d32f2f' : l === 'warn' ? '#f08c00' : '#2e9e5a')
const el = ref(null); let map = null, layer = null
function draw() {
  if (!map) return
  layer.clearLayers()
  const hub = d.value.nodes.find(n => n.type === 'HUB'), port = d.value.nodes.find(n => n.type === 'PORT') || { name: '인천신항' }
  const pts = []
  d.value.nodes.filter(n => n.type !== 'HUB' && n.type !== 'PORT').forEach(c => hub && L.polyline([ll(hub), ll(c)], { color: '#C9962E', weight: 5, opacity: 0.95 }).addTo(layer))
  if (hub) L.polyline([ll(port), ll(hub)], { color: '#102644', weight: 4, dashArray: '8 6' }).addTo(layer)
  for (const n of d.value.nodes) {
    const p = ll(n); pts.push(p)
    const fill = n.type === 'PORT' ? '#102644' : n.type === 'HUB' ? '#EECC83' : col(n.level)
    L.circleMarker(p, { radius: n.type === 'HUB' ? 13 : 10, color: n.type === 'HUB' ? col(n.level) : '#fff', weight: 3, fillColor: fill, fillOpacity: 1 })
      .bindTooltip(`${n.name} · ${LV[n.level] || '정상'}`, { permanent: true, direction: 'top', offset: [0, -10] }).addTo(layer)
  }
  d.value.vehicles.forEach((v, i) => {
    const [a, b] = String(v.segment || '').split('→').map(s => s.trim())
    const pa = ll(a), pb = ll(b || a), t = v.status === 'IN_TRANSIT' ? 0.5 : 0.08, j = (i % 5 - 2) * 0.006
    L.circleMarker([pa[0] + (pb[0] - pa[0]) * t + j, pa[1] + (pb[1] - pa[1]) * t + j], { radius: 6, color: '#fff', weight: 2, fillColor: v.delayMin >= 30 ? '#f08c00' : '#2851c6', fillOpacity: 1 })
      .bindTooltip(`${v.vehicleNo} · ${v.kind}<br>${v.segment} · ${stl(v.status)}`).addTo(layer)
  })
  if (pts.length && !map._fitted) { map.fitBounds(pts, { padding: [50, 50] }); map._fitted = true }
}
function focus(n) { map && map.setView(ll(n), 11) }
async function load() { const r = await get('/api/web/network'); if (r) { d.value = r; draw() } }
onMounted(() => {
  map = L.map(el.value, { zoomControl: true, attributionControl: true }).setView([37.42, 127.0], 9)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: '&copy; OpenStreetMap' }).addTo(map)
  layer = L.layerGroup().addTo(map); load()
})
onBeforeUnmount(() => { map && map.remove(); map = null })
watch(() => props.tick, load)
// fix_1434: 표 페이지 나누기
const pgN = usePage(() => d.value.nodes, 5)
const pgV = usePage(() => d.value.vehicles, 5)
</script>
