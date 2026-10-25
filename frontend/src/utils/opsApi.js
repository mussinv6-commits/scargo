// 26.10.24 운영 현황판 · 예외/사고 접수 API
//  현황판: ADMIN · CONTROL(전체) · HUB_OP(소속 허브) / 접수: 관리자 · 관제 · 허브 · 업체 · 기사 / 처리: 관리자 · 관제 · 허브
import axios from 'axios'
import { API_BASE } from './apiBase'

const http = axios.create({ baseURL: API_BASE, withCredentials: true })

export const opsOverview = () => http.get('/api/ops/overview').then((r) => r.data)

export const INCIDENT_TYPE = { DAMAGE: '컨테이너 파손', SEAL: '씰 이상', DELAY: '운송 지연', VEHICLE: '차량 문제', SAFETY: '안전 사고', OTHER: '기타' }
export const SEVERITY = { HIGH: '높음', MEDIUM: '보통', LOW: '낮음' }
export const INCIDENT_STATUS = { OPEN: '접수', IN_PROGRESS: '처리 중', RESOLVED: '해결' }

export const listIncidents = (status) =>
  http.get('/api/incidents', { params: { status: status || undefined } }).then((r) => r.data)
export const createIncident = (body) => http.post('/api/incidents', body).then((r) => r.data)
export const updateIncident = (id, body) => http.patch(`/api/incidents/${id}`, body).then((r) => r.data)

// 26.10.24 KPI 일별 추이 (ADMIN · CONTROL 전체, HUB_OP 소속 허브)
export const kpiTrend = (days = 14) => http.get('/api/reports/kpi', { params: { days } }).then((r) => r.data)

// 26.10.24 게이트 사전 예약 (업체 · 기사 = 자기 업체, 관리자 · 관제 = 전체)
export const RESERVATION_STATUS = { BOOKED: '예약', CHECKED_IN: '입차 확인', CANCELLED: '취소', NO_SHOW: '불참' }
export const RESERVATION_DIRECTION = { IN: '반입', OUT: '반출' }
export const listCompaniesForSelect = () => http.get('/api/companies').then((r) => r.data) // ADMIN 만 가능 (그 외는 403 → 번호 직접 입력)
export const listReservations = ({ date, status } = {}) =>
  http.get('/api/gate-reservations', { params: { date: date || undefined, status: status || undefined } }).then((r) => r.data)
export const createReservation = (body) => http.post('/api/gate-reservations', body).then((r) => r.data)
export const updateReservation = (id, status) => http.patch(`/api/gate-reservations/${id}`, { status }).then((r) => r.data)

// 26.10.24 자동 백업 안내 (ADMIN · CONTROL)
export const backupInfo = () => http.get('/api/ops/backup-info').then((r) => r.data)

// 26.10.24 경보 규칙 / 업체별 실적
export const opsAlerts = () => http.get('/api/ops/alerts').then((r) => r.data)
export const companyScorecard = (days = 30) => http.get('/api/reports/company-scorecard', { params: { days } }).then((r) => r.data)
