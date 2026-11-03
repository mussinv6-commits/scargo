// 백엔드 API 호출 공통 모듈
// - 브라우저 개발: 서버 주소를 비워 두면 Vite 프록시(/api → localhost:8080)를 사용
// - APK: [내정보 > 서버 주소] 또는 .env 의 VITE_API_BASE 사용
// - 26.11.01 APK: 연결 실패 시 같은 Wi-Fi 대역에서 /api/app/health 로 서버를 자동 탐색(discover.js)
import { Capacitor } from '@capacitor/core'

const KEY_BASE = 'scargo.apiBase'
const KEY_TOKEN = 'scargo.token'

function read(key) {
  try { return localStorage.getItem(key) } catch { return null }
}
function write(key, value) {
  try {
    if (value === null || value === undefined || value === '') localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch { /* 저장소를 못 쓰는 환경은 무시 */ }
}

export const isNative = Capacitor.isNativePlatform()

export function getApiBase() {
  // fix_app_1026: 우선순위 = 저장한 주소 > .env(VITE_API_BASE) > 기본값. 빈 값 저장 = 저장 안 함(기본값 사용)
  const saved = read(KEY_BASE)
  if (saved) return saved
  return isNative ? (import.meta.env.VITE_API_BASE || 'http://10.0.2.2:8080') : ''
}
export function setApiBase(url) {
  let u = (url || '').trim().replace(/\/+$/, '')
  if (u && !/^https?:\/\//i.test(u)) u = 'http://' + u
  if (u && !/:\d+$/.test(u.replace(/^https?:\/\//i, '').split('/')[0])) {
    // host only → :8080
    try { const x = new URL(u); if (!x.port) { x.port = '8080'; u = x.origin } } catch { /* 유지 */ }
  }
  write(KEY_BASE, u)
}

export const getSavedApiBase = () => read(KEY_BASE) || ''
export const getDefaultApiBase = () => (isNative ? (import.meta.env.VITE_API_BASE || 'http://10.0.2.2:8080') : '')

export const getToken = () => read(KEY_TOKEN)
export const setToken = (t) => write(KEY_TOKEN, t)

export class ApiError extends Error {
  constructor(message, status, detail = '') {
    super(message)
    this.status = status
    this.detail = detail // 26.10.29 서버 원문 (화면에는 안 보이고 개발 확인용)
    if (detail && detail !== message) console.warn('[api]', status, detail)
  }
}

// 26.10.29 QA: 오류가 나면 서버 메시지(영어 · 예외 이름 · SQL 등)가 빨간 글자로 그대로 보이던 문제 → 기사용 문구
//  서버가 보낸 한글 안내 문장(예: '국내 운송 씰 번호를 입력하세요.')은 그대로 쓰고, 개발용 메시지는 상황별 문구로 바꾼다
const TECH = /exception|error|java\.|org\.|sql|jdbc|hibernate|constraint|null|undefined|stack|could not|failed|timeout|internal|request|https?:|[{}<>\[\]]/i
const CODE = /\b[A-Z]+_[A-Z_]+\b/ // ASSIGNED_TO 같은 상태 코드
const HANGUL = /[가-힣]/
export function isFriendly(msg) {
  const m = String(msg || '').trim()
  return !!m && m.length <= 90 && HANGUL.test(m) && !TECH.test(m) && !CODE.test(m)
}
export function friendlyMessage(status, raw, fallback) {
  if (isFriendly(raw)) return String(raw).trim()
  if (fallback) return fallback
  if (status === 403) return '이 작업을 할 권한이 없습니다. 운송사 또는 관리자에게 문의해 주세요.'
  if (status === 404) return '요청한 정보를 찾을 수 없습니다. 화면을 새로고침한 뒤 다시 시도해 주세요.'
  if (status === 409) return '이미 처리되었거나 다른 곳에서 바뀐 정보입니다. 화면을 새로고침해 주세요.'
  if (status === 400 || status === 422) return '입력한 내용을 다시 확인해 주세요.'
  if (status === 413) return '파일(사진)이 너무 큽니다. 크기를 줄여 다시 올려 주세요.'
  if (status === 429) return '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
  if (status >= 500) return '일시적인 서버 오류입니다. 잠시 후 다시 시도해 주세요.'
  return '처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}
/** 화면에서 catch(e) 로 받은 오류를 기사용 문구로 (ApiError 가 아닌 오류도 처리) */
export const errText = (e) => (e instanceof ApiError ? e.message : friendlyMessage(0, e?.message))

let onUnauthorized = () => {}
export function setUnauthorizedHandler(fn) { onUnauthorized = fn }

export async function api(path, { method = 'GET', body, form, timeout = 20000 } = {}) {
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (form) payload = form
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), timeout)
  let res
  try {
    res = await fetch(getApiBase() + path, { method, headers, body: payload, signal: ctrl.signal })
  } catch (e) {
    // 26.11.01 같은 Wi-Fi 자동 탐색 후 1회 재시도 (PC IP 변경 대응). 웹 개발(빈 base/프록시)은 건너뜀
    let recovered = false
    if (isNative && e?.name !== 'AbortError') {
      try {
        const { ensureApiBase } = await import('./discover.js')
        const found = await ensureApiBase({ forceDiscover: true })
        if (found) {
          clearTimeout(timer)
          const ctrl2 = new AbortController()
          const timer2 = setTimeout(() => ctrl2.abort(), timeout)
          try {
            res = await fetch(found + path, { method, headers, body: payload, signal: ctrl2.signal })
            recovered = true
          } finally { clearTimeout(timer2) }
        }
      } catch { /* 탐색 실패 → 원래 오류 */ }
    }
    if (!recovered) {
      const timedOut = e?.name === 'AbortError'
      throw new ApiError(timedOut ? '서버 응답이 늦어지고 있습니다. 잠시 후 다시 시도해 주세요.' : '서버에 연결할 수 없습니다. 같은 Wi-Fi인지 · 서버 주소를 확인해 주세요.', 0, `${e?.message || e} (${getApiBase() || '개발 프록시'})`)
    }
  } finally {
    clearTimeout(timer)
  }
  if (res.status === 401) {
    if (path.includes('/auth/login')) { // 26.10.29 로그인 실패는 로그인 화면에서 바로 안내
      let m = ''; try { m = (JSON.parse(await res.text()) || {}).message || '' } catch { /* 본문 없음 */ }
      throw new ApiError(friendlyMessage(401, m, '아이디 또는 비밀번호를 확인해 주세요.'), 401, m)
    }
    onUnauthorized()
    throw new ApiError('로그인 시간이 지났습니다. 다시 로그인해 주세요.', 401)
  }
  const text = await res.text()
  let data = null
  try { data = text ? JSON.parse(text) : null } catch { data = text }
  if (!res.ok) {
    const raw = (data && typeof data === 'object' && (data.message || data.error)) || (typeof data === 'string' ? data : '')
    throw new ApiError(friendlyMessage(res.status, raw), res.status, raw)
  }
  return data
}

// ---- 화면별 API (백엔드 AppOrderController / AppInfoController / AppAuthController) ----
export const Api = {
  health: () => api('/api/app/health', { timeout: 5000 }),
  login: (userId, userPw) => api('/api/app/auth/login', { method: 'POST', body: { userId, userPw } }),
  logout: () => api('/api/app/auth/logout', { method: 'POST' }),
  me: () => api('/api/app/me'),
  home: () => api('/api/app/home'),
  order: (id) => api(`/api/app/orders/${id}`),
  precheck: (id, checklist, pos) => api(`/api/app/orders/${id}/precheck`, { method: 'POST', body: { checklist, lat: pos?.lat, lng: pos?.lng } }),
  depart: (id, pos) => api(`/api/app/orders/${id}/depart`, { method: 'POST', body: { lat: pos?.lat, lng: pos?.lng }, timeout: 30000 }),
  route: (id) => api(`/api/app/orders/${id}/route`, { timeout: 30000 }),
  reroute: (id, pos) => api(`/api/app/orders/${id}/reroute`, { method: 'POST', body: pos, timeout: 30000 }),
  sendLocation: (id, loc) => api(`/api/app/orders/${id}/locations`, { method: 'POST', body: loc }),
  tripStatus: (id) => api(`/api/app/orders/${id}/trip-status`),
  arrive: (id) => api(`/api/app/orders/${id}/arrive`, { method: 'POST' }),
  handover: (id, form) => api(`/api/app/orders/${id}/handover`, { method: 'POST', form, timeout: 60000 }),
  history: (range) => api(`/api/app/orders/history?range=${range}`),
  branchHistory: (range) => api(`/api/app/branch/history?range=${range}`),
  changePassword: (currentPassword, newPassword) => api('/api/app/me/password', { method: 'POST', body: { currentPassword, newPassword } }),
  notifications: () => api('/api/app/notifications?size=50'),
  safetyAlert: () => api('/api/app/safety/alert'), // 26.10.29 허브 화재 경보 상태
  readAll: () => api('/api/app/notifications/read-all', { method: 'POST' }),
  place: (type, id, pos) => api(`/api/app/places/${type}/${encodeURIComponent(id)}` + (pos ? `?lat=${pos.lat}&lng=${pos.lng}` : '')),
  places: (params) => api('/api/app/places?' + new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''))),
  demoSim: () => api('/api/app/demo-sim', { timeout: 5000 }).then(r => !!r?.enabled).catch(() => false), // fix_1600
  devEnabled: () => api('/api/app/dev/enabled', { timeout: 5000 }).then(r => !!r?.enabled).catch(() => false),
  devGateOut: (id) => api(`/api/app/dev/orders/${id}/gate-out`, { method: 'POST' }),
  menuIncidentRuns: () => api('/api/app/menus/incidents/runs'),
  menuIncidents: () => api('/api/app/menus/incidents'),
  menuReportIncident: (form) => api('/api/app/menus/incidents', { method: 'POST', form, timeout: 60000 }),
  menuSupport: () => api('/api/app/menus/support'),
  menuInquiry: (body) => api('/api/app/menus/support/inquiries', { method: 'POST', body }),
  menuSchedule: (month) => api('/api/app/menus/schedule' + (month ? ('?month=' + encodeURIComponent(month)) : '')),
  menuSettlements: (month) => api('/api/app/menus/settlements' + (month ? ('?month=' + encodeURIComponent(month)) : '')),
  menuDocuments: () => api('/api/app/menus/documents'),
  menuUpsertDocument: (form) => api('/api/app/menus/documents', { method: 'POST', form, timeout: 60000 }),
  menuDeleteDocument: (id) => api('/api/app/menus/documents/' + id, { method: 'DELETE' }),
  menuLogbook: (day) => api('/api/app/menus/logbook' + (day ? ('?day=' + encodeURIComponent(day)) : '')),
  menuNearby: (lat, lng) => api('/api/app/menus/nearby?' + new URLSearchParams(Object.entries({ lat, lng }).filter(([, v]) => v != null)).toString()),
  menuInspectionHistory: (range, result) => api('/api/app/menus/inspections/history?' + new URLSearchParams({ range: range || 'month', result: result || 'ALL' })),
  menuSettings: () => api('/api/app/menus/settings'),
  menuSaveSettings: (body) => api('/api/app/menus/settings', { method: 'PUT', body })
}
