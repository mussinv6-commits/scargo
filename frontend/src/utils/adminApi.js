// 관리자 화면 전용 axios 인스턴스.
// 세션 쿠키(JSESSIONID) 기반 인증이므로 withCredentials 를 항상 켜둔다.
import axios from 'axios'
import { API_BASE } from './apiBase'
import { friendlyError } from './apiHelpers'

export const adminApi = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
})

// 26.09.30 수정: 서버가 보낸 JSON 객체/SQL 문장을 그대로 alert 하던 문제 수정
// → apiHelpers.friendlyError 로 사람이 읽을 수 있는 문장만 돌려준다.
export function pickErrorMessage(err, fallback = '요청 처리 중 오류가 발생했습니다.') {
  return friendlyError(err, fallback)
}
