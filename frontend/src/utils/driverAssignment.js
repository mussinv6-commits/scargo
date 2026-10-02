// 기사 배차 = 사업자 차량-컨테이너 매핑 → loading_records
// (/api/dispatches 는 합본 백엔드에 없음)
import axios from 'axios'
import { API_BASE } from './apiBase'
import { fetchMyTruck } from './driverTruck'

function locationLabel(location, fallbackName) {
  const sector = location?.sector || fallbackName
  const yard = location?.yardName || (location?.yardId != null ? `야드 #${location.yardId}` : '')
  const parts = [yard, sector].filter(Boolean)
  return parts.length ? parts.join(' · ') : '적재 위치 미지정'
}

async function fetchYardsById() {
  try {
    const resp = await axios.get(`${API_BASE}/api/yards`, { withCredentials: true })
    const list = Array.isArray(resp.data) ? resp.data : []
    return Object.fromEntries(list.map((y) => [y.yardId, y]))
  } catch {
    return {}
  }
}

function toCoord(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

async function fetchLocation(locationId, yardsById) {
  if (!locationId) return null
  try {
    const resp = await axios.get(`${API_BASE}/api/loading-locations/${locationId}`, { withCredentials: true })
    const loc = resp.data || null
    if (!loc) return null
    const yard = yardsById[loc.yardId] || {}
    return {
      ...loc,
      yardName: yard.yardName || yard.name || (loc.yardId != null ? `야드 #${loc.yardId}` : null),
      yardLatitude: toCoord(yard.latitude),
      yardLongitude: toCoord(yard.longitude),
    }
  } catch {
    return null
  }
}

export function naviDestination(location, fallbackName) {
  const name = locationLabel(location, fallbackName)
  const lat = toCoord(location?.latitude) ?? toCoord(location?.yardLatitude)
  const lng = toCoord(location?.longitude) ?? toCoord(location?.yardLongitude)
  const hasCoords = lat != null && lng != null
  return { name, lat: hasCoords ? lat : null, lng: hasCoords ? lng : null, hasCoords }
}

function enrich(record, location) {
  return {
    ...record,
    location,
    locationLabel: locationLabel(location, record.locationName),
  }
}

export async function fetchDriverAssignment() {
  const truck = await fetchMyTruck()
  if (!truck?.vehicleNo) {
    return { truck: null, current: null, history: [] }
  }

  let rows = []
  try {
    const resp = await axios.get(
      `${API_BASE}/api/loading-records/truck/${encodeURIComponent(truck.vehicleNo)}`,
      { params: { page: 0, size: 50 }, withCredentials: true }
    )
    rows = resp.data?.content || []
  } catch {
    rows = []
  }

  const yardsById = await fetchYardsById()
  const enriched = []
  for (const row of rows) {
    const location = await fetchLocation(row.locationId, yardsById)
    enriched.push(enrich(row, location))
  }

  const current = enriched.find((r) => r.status === 'IN_PROGRESS' || r.status === 'PENDING') || null
  const history = enriched.filter((r) => r.status !== 'IN_PROGRESS' && r.status !== 'PENDING')
  return { truck, current, history }
}

export function entryApprovalMeta(status) {
  const value = status || 'PENDING'
  return {
    value,
    label: { PENDING: '심사대기', APPROVED: '허가', REJECTED: '반려' }[value] || '심사대기',
    tone: { PENDING: 'waiting', APPROVED: 'done', REJECTED: 'cancel' }[value] || 'waiting',
  }
}

export function assignmentStatusLabel(status) {
  return { PENDING: '대기', IN_PROGRESS: '배차중', COMPLETED: '운송완료', CANCELED: '배차취소' }[status] || status || '-'
}

export function formatDateTime(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  return `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
