// 관리자 화면 전용 axios 인스턴스.
// 세션 쿠키(JSESSIONID) 기반 인증이므로 withCredentials 를 항상 켜둔다.
import axios from 'axios'
import { API_BASE } from './apiBase'
import { authState } from '@/auth/authState.js'
import { friendlyError } from './apiHelpers'

export const adminApi = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
})

adminApi.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err?.response?.status
    if (status === 403) {
      err.friendlyMessage = authState.user?.userType === 'ADMIN'
        ? '이 작업을 할 권한이 없습니다. 관리자 계정으로 다시 로그인한 뒤 시도해주세요. (다른 탭에서 기사/사업자로 로그인하면 서버 세션이 바뀝니다.)'
        : '알림을 불러오지 못했습니다. 로그아웃 후 다시 로그인한 뒤 시도해주세요.'
    }
    return Promise.reject(err)
  }
)

// 26.09.30 수정: 서버가 보낸 JSON 객체/SQL 문장을 그대로 alert 하던 문제 수정
// → apiHelpers.friendlyError 로 사람이 읽을 수 있는 문장만 돌려준다.
export function pickErrorMessage(err, fallback = '요청 처리 중 오류가 발생했습니다.') {
  if (err?.friendlyMessage) return err.friendlyMessage
  return friendlyError(err, fallback)
}
