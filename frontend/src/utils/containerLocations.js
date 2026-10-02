import axios from 'axios'
import { API_BASE } from './apiBase'

const opt = { withCredentials: true }

function toList(data) {
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.content)) return data.content
  return []
}

function locationLabel(location, fallbackName) {
  const sector = location?.sector || fallbackName
  const yard = location?.yardName || (location?.yardId != null ? `야드 #${location.yardId}` : '')
  const parts = [yard, sector].filter(Boolean)
  return parts.length ? parts.join(' · ') : '적재 위치 미지정'
}

async function fetchAllLoadingRecords() {
  const size = 100
  const all = []
  for (let page = 0; page < 30; page += 1) {
    const { data } = await axios.get(`${API_BASE}/api/loading-records`, {
      ...opt,
      params: { page, size, sort: 'loadedAt,desc' },
    })
    const rows = toList(data)
    all.push(...rows)
    const totalPages = data?.totalPages
    if (rows.length < size || (totalPages != null && page + 1 >= totalPages)) break
  }
  return all
}

export async function fetchContainerLocationRows({ vehicleNos = null, includeCompany = false } = {}) {
  const [records, locations, yards, trucks] = await Promise.all([
    fetchAllLoadingRecords(),
    axios.get(`${API_BASE}/api/loading-locations`, opt).then((r) => toList(r.data)).catch(() => []),
    axios.get(`${API_BASE}/api/yards`, opt).then((r) => toList(r.data)).catch(() => []),
    includeCompany
      ? axios.get(`${API_BASE}/api/trucks`, opt).then((r) => toList(r.data)).catch(() => [])
      : Promise.resolve([]),
  ])

  const yardById = Object.fromEntries(yards.map((y) => [y.yardId, y]))
  const locById = Object.fromEntries(
    locations.map((loc) => {
      const yard = yardById[loc.yardId] || {}
      return [loc.locationId, { ...loc, yardName: yard.yardName || yard.name || null }]
    })
  )

  const companyByVehicle = Object.fromEntries(
    trucks.map((t) => [t.vehicleNo, t.companyName || (t.companyId != null ? `업체 #${t.companyId}` : '')])
  )

  let rows = records.map((r) => ({
    recordId: r.recordId,
    vehicleNo: r.vehicleNo || '-',
    companyName: companyByVehicle[r.vehicleNo] || '-',
    containerNo: r.containerNo || '-',
    locationLabel: locationLabel(locById[r.locationId], r.locationName),
    loadedAt: r.loadedAt,
  }))

  if (Array.isArray(vehicleNos)) {
    const allow = new Set(vehicleNos.filter(Boolean))
    rows = rows.filter((r) => allow.has(r.vehicleNo))
  }

  return rows
}

export function filterRowsByVehicleNo(rows, query) {
  const q = String(query || '').trim().toUpperCase().replace(/\s/g, '')
  if (!q) return rows
  return rows.filter((r) => String(r.vehicleNo || '').toUpperCase().replace(/\s/g, '').includes(q))
}

function dateKey(d) {
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
}

export function splitTodayAndPast(rows) {
  const today = dateKey(new Date())
  const todayRows = []
  const pastRows = []
  rows.forEach((r) => {
    if (r.loadedAt && dateKey(new Date(r.loadedAt)) === today) todayRows.push(r)
    else pastRows.push(r)
  })
  return { todayRows, pastRows }
}

export function formatLoadedAt(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
