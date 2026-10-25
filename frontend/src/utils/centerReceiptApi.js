// 26.10.23 지역센터 인수 확정 API (지선 운행 ARRIVED → RECEIVED)
//  조회: ADMIN · CONTROL · CENTER_OP(소속 센터) / 확정: ADMIN · CENTER_OP. 범위 · 상태는 서버가 다시 확인
import axios from 'axios'
import { API_BASE } from './apiBase'

const http = axios.create({ baseURL: API_BASE, withCredentials: true })

export const LEG_STATUS_LABEL = { IN_TRANSIT: '운송 중', ARRIVED: '센터 도착', RECEIVED: '인수 확정' }

export const centerReceipts = (status) =>
  http.get('/api/center-receipts', { params: { status: status || undefined } }).then((r) => r.data)
export const confirmCenterReceipt = (assignmentId) =>
  http.post(`/api/center-receipts/${assignmentId}/receive`).then((r) => r.data)
