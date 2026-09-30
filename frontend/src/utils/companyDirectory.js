// 업체 이름 조회 헬퍼.
// 백엔드에 companyId로 단건 조회하는 API가 없어서(있는 건 businessNo 조회뿐),
// /api/companies/options 로 전체 목록을 한 번만 받아 캐싱해두고 companyId로 찾는다.
import axios from 'axios'
import { API_BASE } from './apiBase'

let cache = null

export async function getCompanyName(companyId) {
  if (!companyId) return null
  if (!cache) {
    try {
      const resp = await axios.get(`${API_BASE}/api/companies/options`)
      cache = new Map(resp.data.map((c) => [c.companyId, c.companyName]))
    } catch (err) {
      console.error(err)
      return null
    }
  }
  return cache.get(companyId) || null
}
