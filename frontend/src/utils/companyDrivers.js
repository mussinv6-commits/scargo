// 26.09.30 추가: 사업자 소속 기사 목록 조회 (CompanyInfo / CompanyDrivers 공용)
// 1순위: GET /api/accounts/my-company/drivers (세션 기준, CORPORATE_APPROVED 전용)
// 2순위: GET /api/accounts/company/{companyId}/drivers (로그인 정보의 companyId 사용)
// 기존에는 기사 목록과 차량 목록을 Promise.all 로 묶어서, 둘 중 하나만 실패해도
// 기사 정보가 전혀 표시되지 않았다.
import axios from 'axios'
import { API_BASE } from './apiBase'
import { authState } from '@/auth/authState.js'
import { friendlyError, normalizeRows } from './apiHelpers'

const opt = { withCredentials: true }

export async function fetchCompanyDrivers() {
  try {
    const { data } = await axios.get(`${API_BASE}/api/accounts/my-company/drivers`, opt)
    return normalizeRows(data)
  } catch (first) {
    const companyId = authState.user?.companyId
    if (!companyId) throw new Error(friendlyError(first, '소속 기사 목록을 불러오지 못했습니다.'))
    try {
      const { data } = await axios.get(`${API_BASE}/api/accounts/company/${companyId}/drivers`, opt)
      return normalizeRows(data)
    } catch (second) {
      const status = second?.response?.status
      if (status === 401 || status === 403) {
        throw new Error('로그인 세션이 만료되었습니다. 다시 로그인한 뒤 확인해주세요.')
      }
      throw new Error(friendlyError(second, '소속 기사 목록을 불러오지 못했습니다.'))
    }
  }
}

export async function fetchCompanyTrucks() {
  try {
    const { data } = await axios.get(`${API_BASE}/api/mappings/my-trucks`, opt)
    return normalizeRows(data, { bools: ['isSemiTrailer'] })
  } catch (err) {
    const companyId = authState.user?.companyId
    if (!companyId) throw new Error(friendlyError(err, '소속 차량 목록을 불러오지 못했습니다.'))
    const { data } = await axios.get(`${API_BASE}/api/trucks/options`, { ...opt, params: { companyId } })
    return normalizeRows(data, { bools: ['isSemiTrailer'] })
  }
}
