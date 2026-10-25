// 26.10.23 AMR 앱 출입증 감사 조회 (ADMIN = 전체 + 실패 기록, HUB_OP = 소속 허브 출입증). 토큰 · 코드 값은 서버가 내려주지 않음
import axios from 'axios'
import { API_BASE } from './apiBase'

const http = axios.create({ baseURL: API_BASE, withCredentials: true })

export const REVOKE_REASON_LABEL = { LOGOUT: '앱 로그아웃', ROTATE: '허브 앱 연결 해제', ADMIN_REVOKE_ALL: '관리자 전체 해제', ADMIN_REVOKE: '관리자 해제' }
export const FAILURE_REASON_LABEL = { BAD_CODE: '코드 틀림', REUSED_CODE: '이미 쓴 코드', IP_LOCKED: '잠금 중 시도', GLOBAL_LOCKED: '전체 잠금', NO_KEY: '연결 코드 없음' }

export const amrAuthAudit = () => http.get('/api/amr/auth/audit').then((r) => r.data)
