// 26.10.23 허브 도착 · 인계 API
//  조회: ADMIN · CONTROL(전체) · HUB_OP(소속 허브) / 도착 · 인계 확정: ADMIN · HUB_OP
//  도착 확정은 처리자가 고른 입차 OCR 기록을 근거로 한다 (OCR 기록만으로 자동 확정하지 않음)
import axios from 'axios'
import { API_BASE } from './apiBase'

const http = axios.create({ baseURL: API_BASE, withCredentials: true })

export const ORDER_STATUS_LABEL = {
  ASSIGNED: '배차 완료',
  LOADED: '상차 완료',
  GATE_OUT: '게이트 통과',
  IN_TRANSIT: '운송 중',
  ARRIVED: '허브 도착',
  HANDED_OVER: '인계 완료',
}

export const hubCandidates = ({ hubId, includeHandedOver = false } = {}) =>
  http.get('/api/hub-arrivals/candidates', { params: { hubId: hubId || undefined, includeHandedOver } }).then((r) => r.data)

export const confirmArrival = ({ orderId, hubVehicleLogId, dockId }) =>
  http.post('/api/hub-arrivals', { orderId, hubVehicleLogId, dockId: dockId || null }).then((r) => r.data)

export const confirmHandover = ({ orderId, containerNoConfirmed, sealNo, damageFree, receiverName }) =>
  http.post('/api/hub-handovers', { orderId, containerNoConfirmed, sealNo, damageFree, receiverName }).then((r) => r.data)

// 26.10.23 도착 확정 때 고를 입고(IN) 도크 (occupied = 하역 전 입고 기록이 쓰는 중)
export const inboundDocks = (hubId) =>
  http.get('/api/hub-arrivals/docks', { params: { hubId: hubId || undefined } }).then((r) => r.data)

// 26.10.23 컨테이너 기사: 내 운송 오더 · 운송 출발
export const myTransportOrders = () => http.get('/api/transport-orders/my').then((r) => r.data)
export const departTransportOrder = (orderId) => http.post(`/api/transport-orders/${orderId}/depart`).then((r) => r.data)

// 26.10.24 하역 완료: 입고 기록(receiptId)을 COMPLETED 로, 도착 때 쓴 도크를 다시 쓸 수 있게 함
export const completeUnload = (receiptId) => http.post(`/api/hub-arrivals/${receiptId}/unload`).then((r) => r.data)
