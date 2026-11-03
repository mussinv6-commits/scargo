import { errText } from './api' // 26.10.29 QA: 오류는 기사용 문구로
// 로그인 상태(토큰 + 내 정보)를 앱 전체에서 공유
import { reactive } from 'vue'
import { Api, getToken, setToken, isNative } from './api'
import { resetTrip } from './tracker'
import { resetInspectionCache } from './inspection'
import { resetSignup } from '../mockup/store.js'

const KEY_ME = 'scargo.me'

function loadMe() {
  try { return JSON.parse(localStorage.getItem(KEY_ME) || 'null') } catch { return null }
}

export const session = reactive({
  token: getToken(),
  me: loadMe(),
  devTools: false,
  demoSim: false,  // fix_1600: 시연 주행 버튼 노출 (서버 /api/app/demo-sim)
  simButton: loadSimButton(), // 26.10.24: 내 정보에서 켜는 시연 주행(배속) 버튼 - 서버 설정 없이도 표시
  conn: 'ok',        // fix_app_1026: 'checking' | 'ok' | 'down' (앱 시작 시 서버·토큰 확인)
  connError: '',
  serverSheet: false // fix_app_1026: 서버 주소 설정 시트 열기 (어느 화면에서나)
})

// 26.10.24: 시연 주행 버튼 표시 (기기에 저장, 기본 켜짐)
function loadSimButton() {
  try { return localStorage.getItem('scargo.simButton') !== 'off' } catch { return true }
}
export function setSimButton(on) {
  session.simButton = !!on
  try { localStorage.setItem('scargo.simButton', on ? 'on' : 'off') } catch { /* 무시 */ }
}

export const isLoggedIn = () => !!session.token

export async function login(userId, userPw) {
  const res = await Api.login(userId, userPw)
  // 26.10.27: 이전 기사의 운행·점검 상태가 남지 않게 (같은 화면에서 다른 기사로 로그인하는 경우)
  resetTrip()
  resetInspectionCache()
  setToken(res.token)
  session.token = res.token
  saveMe(res.me)
  Api.demoSim().then(v => { session.demoSim = v }) // fix_1600
  return res
}

export function saveMe(me) {
  session.me = me
  try { localStorage.setItem(KEY_ME, JSON.stringify(me)) } catch { /* 무시 */ }
}

export async function refreshMe() {
  saveMe(await Api.me())
  session.devTools = await Api.devEnabled()
  session.demoSim = await Api.demoSim()
}

// fix_app_1026: 앱 시작 시 서버 연결 + 토큰 확인. 저장된 me 만으로 로그인된 척하지 않음
//  - 성공: me 갱신, conn='ok' / 401: api.js 핸들러가 토큰·me 삭제 후 로그인 화면 / 연결 실패·5xx: conn='down'
export async function verifySession() {
  if (!session.token) { session.conn = 'ok'; return true }
  session.conn = 'checking'; session.connError = ''
  try {
    // 26.11.01 APK: 시작 시 서버 주소가 죽어 있으면 같은 Wi-Fi 에서 다시 찾음
    if (isNative) {
      try { const { ensureApiBase } = await import('./discover.js'); await ensureApiBase() } catch { /* 탐색 실패는 아래 me 호출에서 처리 */ }
    }
    saveMe({ ...(await Api.me()), approvalStatus: session.me?.approvalStatus })
    session.conn = 'ok'
    Api.devEnabled().then(v => { session.devTools = v })
    Api.demoSim().then(v => { session.demoSim = v })
    return true
  } catch (e) {
    if (e.status === 401) { session.conn = 'ok'; return false }
    session.conn = 'down'; session.connError = errText(e)
    return false
  }
}

export function clearSession() {
  resetTrip() // 26.10.27: 로그아웃·토큰 만료 시 운행 상태(오더·도착·경로)도 비움
  resetInspectionCache()
  setToken(null)
  session.token = null
  session.me = null
  try { localStorage.removeItem(KEY_ME) } catch { /* 무시 */ }
}

export async function logout() {
  resetTrip()
  try { await Api.logout() } catch { /* 서버가 꺼져 있어도 로그아웃은 진행 */ }
  clearSession()
  resetSignup() // 26.10.28: 로그아웃 시에만 가입 입력 초기화 (401 처리로 가입 중 폼이 지워지지 않게)
}
