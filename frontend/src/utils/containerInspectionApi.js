// 26.10.23 컨테이너 점검표(7-point) API
//  업체 · 허브 · 판정은 서버가 정한다 (요청에 companyId · hubId · overallStatus 를 보내지 않음)
//  권한 판단은 서버가 하고, 응답의 canEdit · canCancel · canReinspect 로 버튼만 보이거나 숨긴다
import axios from 'axios'
import { API_BASE } from './apiBase'

const http = axios.create({ baseURL: API_BASE, withCredentials: true })
const BASE = '/api/container-inspections'

// 점검 항목 7개 (모두 필수). true = 양호
export const INSPECTION_ITEMS = [
  { key: 'front_wall', label: '전면 벽' },
  { key: 'left_side', label: '좌측면' },
  { key: 'right_side', label: '우측면' },
  { key: 'floor', label: '바닥' },
  { key: 'roof', label: '지붕' },
  { key: 'doors', label: '도어' },
  { key: 'undercarriage', label: '하부' },
]

export const OVERALL_LABEL = { PENDING: '판정 전', PASS: '합격', FAIL: '불합격' }
export const RECORD_LABEL = { DRAFT: '작성 중', FINALIZED: '확정', CANCELLED: '취소' }
export const ACTION_LABEL = { CREATE: '작성', UPDATE: '수정', FINALIZE: '확정', CANCEL: '취소', REINSPECT: '재점검' }

// 화면 미리보기용 판정 (저장 · 확정 때는 서버가 다시 계산)
export function previewJudge(items) {
  const values = INSPECTION_ITEMS.map((i) => items?.[i.key])
  if (values.some((v) => v !== true && v !== false)) return 'PENDING'
  return values.every((v) => v === true) ? 'PASS' : 'FAIL'
}

// 입력되지 않은 항목은 보내지 않는다
export function cleanItems(items) {
  const out = {}
  INSPECTION_ITEMS.forEach(({ key }) => {
    if (items?.[key] === true || items?.[key] === false) out[key] = items[key]
  })
  return out
}

export function formatDateTime(v) {
  if (!v) return '-'
  // 서버 시각이 epoch 초(숫자)로 내려온다 (amrTwinApp.js 의 toDate 와 같은 처리)
  const d = new Date(typeof v === 'number' && v < 1e12 ? v * 1000 : v)
  if (Number.isNaN(d.getTime())) return String(v)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

export const listInspections = (params = {}) => http.get(BASE, { params }).then((r) => r.data)
export const getInspection = (id) => http.get(`${BASE}/${id}`).then((r) => r.data)
export const createInspection = ({ containerNo, orderId, items, memo }) =>
  http.post(BASE, { containerNo, orderId: orderId || null, items: cleanItems(items), memo }).then((r) => r.data)
export const updateInspection = (id, { items, memo }) =>
  http.put(`${BASE}/${id}`, { items: cleanItems(items), memo }).then((r) => r.data)
export const finalizeInspection = (id) => http.post(`${BASE}/${id}/finalize`).then((r) => r.data)
export const cancelInspection = (id, reason) => http.post(`${BASE}/${id}/cancel`, { reason }).then((r) => r.data)
export const reinspectInspection = (id, { items, memo, reason } = {}) =>
  http.post(`${BASE}/${id}/reinspect`, { items: cleanItems(items), memo, reason }).then((r) => r.data)
export const inspectionHistory = (id) => http.get(`${BASE}/${id}/history`).then((r) => r.data)

// 26.10.24 손상 사진 (작성 중 기록만 올리기 · 삭제, 조회는 점검 기록을 볼 수 있는 계정)
export const listPhotos = (id) => http.get(`${BASE}/${id}/photos`).then((r) => r.data)
export const uploadPhoto = (id, file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post(`${BASE}/${id}/photos`, form).then((r) => r.data)
}
export const deletePhoto = (id, photoId) => http.delete(`${BASE}/${id}/photos/${photoId}`)
// 로그인 쿠키가 필요해서 <img src> 대신 blob 으로 받아 미리보기 주소를 만든다
export const photoBlobUrl = (id, photoId) =>
  http.get(`${BASE}/${id}/photos/${photoId}/file`, { responseType: 'blob' }).then((r) => URL.createObjectURL(r.data))
