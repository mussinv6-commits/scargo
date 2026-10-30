// 26.10.29 인수 내역 · 인수증 API (허브 인계 기록). 볼 수 있는 범위는 서버가 정한다
//  ADMIN · CONTROL 전체 / HUB_OP 소속 허브 / 운송사 담당자 자기 회사
import axios from 'axios'
import { API_BASE } from './apiBase'

const http = axios.create({ baseURL: API_BASE, withCredentials: true })

export const handoverList = ({ from, to, q } = {}) =>
  http.get('/api/handovers', { params: { from: from || undefined, to: to || undefined, q: q || undefined } }).then((r) => r.data)
export const handoverOne = (orderId) => http.get(`/api/handovers/${orderId}`).then((r) => r.data)

/** 서명 · 사진 (/uploads/**, 로그인 필요) → 캔버스에 그릴 수 있는 이미지 */
export async function loadUploadImage(url) {
  const res = await http.get(url, { responseType: 'blob' })
  const src = URL.createObjectURL(res.data)
  return await new Promise((ok, no) => { const im = new Image(); im.onload = () => ok(im); im.onerror = no; im.src = src })
}
export const uploadUrl = (u) => (u ? API_BASE + u : '')
