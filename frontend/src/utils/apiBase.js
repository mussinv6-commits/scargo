// 백엔드(Spring Boot, scargo) 기본 주소.
// 여러 화면(운송현황/배차목록/정산매출/MY차량)에서 공통으로 사용한다.
// 26.10.08 수정: 기사 앱(안드로이드)에서는 백엔드가 localhost가 아니라 노트북 IP라서,
// 앱 로그인 화면에서 서버 주소를 바꿔 저장할 수 있게 했다. (웹은 저장값이 없으므로 기존과 동일)
const API_BASE_KEY = 'scargo_api_base'
const DEFAULT_API_BASE = 'http://localhost:8080'

function readSaved() {
  try { return localStorage.getItem(API_BASE_KEY) } catch { return null }
}

export const API_BASE = readSaved() || import.meta.env.VITE_API_BASE || DEFAULT_API_BASE

// 앱 로그인 화면의 "서버 주소" 저장용. 저장 후 앱을 새로고침해야 모든 화면에 반영된다.
export function setApiBase(url) {
  const v = String(url || '').trim().replace(/\/+$/, '')
  try {
    if (v) localStorage.setItem(API_BASE_KEY, v)
    else localStorage.removeItem(API_BASE_KEY)
  } catch { /* 저장 불가 환경은 무시 */ }
}
