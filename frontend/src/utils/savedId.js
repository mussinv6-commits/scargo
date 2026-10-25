// 26.10.21 v4: 로그인 "아이디 저장" — 아이디만 localStorage 에 저장한다 (비밀번호는 절대 저장하지 않음)
const DEFAULT_KEY = 'scargo_saved_user_id'

export function loadSavedId(key = DEFAULT_KEY) {
  try { return localStorage.getItem(key) || '' } catch { return '' }
}

// 체크하면 아이디 저장, 해제하면 저장값 삭제
export function storeSavedId(remember, userId, key = DEFAULT_KEY) {
  try {
    if (remember && userId) localStorage.setItem(key, String(userId).trim())
    else localStorage.removeItem(key)
  } catch { /* 저장 불가 환경은 무시 */ }
}
