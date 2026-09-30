// 관리자 화면 전용 axios 인스턴스.
// 세션 쿠키(JSESSIONID) 기반 인증이므로 withCredentials 를 항상 켜둔다.
import axios from 'axios'
import { API_BASE } from './apiBase'

export const adminApi = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
})

export function pickErrorMessage(err, fallback = '요청 처리 중 오류가 발생했습니다.') {
  return err?.response?.data?.message || err?.response?.data || fallback
}
