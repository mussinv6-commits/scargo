// 26.09.30 추가: 기사에게 배정된 차량 조회 공용 함수
// 기존에는 운송현황/배차목록/정산 화면이 localStorage(scargo_myTruck_*)에서 차량번호를 읽었는데,
// 이 값을 저장하는 곳이 없어져서 차량이 배정되어 있어도 항상 "차량을 먼저 등록" 화면이 떴다.
// → 백엔드 GET /api/trucks/my (배정 차량 없으면 204) 로 조회
import axios from 'axios'
import { API_BASE } from './apiBase'
import { normalizeRows } from './apiHelpers'

export async function fetchMyTruck() {
  try {
    const resp = await axios.get(`${API_BASE}/api/trucks/my`, { withCredentials: true })
    if (resp.status === 204 || !resp.data || !resp.data.vehicleNo) return null
    return normalizeRows([resp.data], { bools: ['isSemiTrailer'] })[0]
  } catch (err) {
    return null
  }
}

export async function fetchMyVehicleNo() {
  const t = await fetchMyTruck()
  return t?.vehicleNo || null
}
