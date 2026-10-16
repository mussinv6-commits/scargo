// ─────────────────────────────────────────────────────────────
// AMR ↔ 서버(DB) 연동 — 웹 관제 화면과 AMR 앱이 같이 쓴다
//  - 서버 주소는 이 파일 하나에서만 정한다 (앱은 화면에서 바꿀 수 있고 기기에 저장)
//  - 서버가 꺼져 있으면 조용히 실패하고 시뮬레이션만으로 계속 돈다
// ─────────────────────────────────────────────────────────────
const KEY = 'scargo_amr_api'
const envBase = (() => { try { return import.meta.env && import.meta.env.VITE_AMR_API } catch { return '' } })()
function defaultBase() {
  try { const h = window.location.hostname; if (h && h !== 'localhost' && h !== '127.0.0.1' && /^\d+\.\d+\.\d+\.\d+$/.test(h)) return 'http://' + h + ':8080' } catch { /* 무시 */ }
  return 'http://localhost:8080'
}
let base = (() => { try { return localStorage.getItem(KEY) } catch { return null } })() || envBase || defaultBase()
let online = false
const listeners = new Set()
function setOnline(v) { if (v !== online) { online = v; listeners.forEach((f) => f(v)) } }

// 26.10.16 AMR 관리자 OTP: 6자리를 통과하면 받는 24시간 출입증(토큰)을 기기에 저장하고 요청마다 붙인다
const TOKEN_KEY = 'scargo_amr_token', EXP_KEY = 'scargo_amr_token_exp'
const ls = { get(k) { try { return localStorage.getItem(k) } catch { return null } }, set(k, v) { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, v) } catch { /* 무시 */ } } }
let token = ls.get(TOKEN_KEY), tokenExp = +(ls.get(EXP_KEY) || 0)
let needAuth = false // 앱: 서버가 인증을 요구하는데 토큰이 없으면 요청을 아예 보내지 않는다 (2초마다 401이 쌓이지 않게)
let cookieAuth = false // 웹: 팀 관리자 로그인 쿠키로 인증 (토큰 대신)
const authListeners = new Set()
function setToken(t, exp) { token = t || null; if (token) needAuth = false; tokenExp = t ? new Date(exp).getTime() : 0; ls.set(TOKEN_KEY, token); ls.set(EXP_KEY, token ? String(tokenExp) : null); authListeners.forEach((f) => f(!!token)) }
function locked() { if (cookieAuth) { authListeners.forEach((f) => f(false)); return } needAuth = true; if (token) setToken(null); else authListeners.forEach((f) => f(false)) } // 서버가 401 → 다시 6자리 입력
function headers(body) { const h = {}; if (body) h['Content-Type'] = 'application/json'; if (token) h['X-AMR-Token'] = token; return h }

async function req(method, path, body, timeout = 3000) {
  if (needAuth && !token && path !== '/api/amr/health') return null
  const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), timeout)
  try {
    const r = await fetch(base + path, { method, signal: ctl.signal, headers: headers(body), body: body ? JSON.stringify(body) : undefined, credentials: cookieAuth ? 'include' : 'same-origin' })
    if (r.status === 401) { setOnline(true); locked(); return null }
    if (!r.ok) throw new Error('HTTP ' + r.status)
    setOnline(true)
    const txt = await r.text(); return txt ? JSON.parse(txt) : null
  } catch (e) { setOnline(false); return null } finally { clearTimeout(t) }
}

const num = (v, d = 2) => (v == null || Number.isNaN(v) ? null : Math.round(v * 10 ** d) / 10 ** d)

export const amrApi = {
  get base() { return base },
  get online() { return online },
  setBase(url) {
    let u = String(url || '').trim().replace(/\/+$/, '')
    if (u && !/^https?:\/\//.test(u)) u = 'http://' + u
    if (u && !/:\d+$/.test(u)) u += ':8080'
    base = u || defaultBase()
    try { localStorage.setItem(KEY, base) } catch { /* 무시 */ }
    needAuth = false
    setOnline(false)
    return base
  },
  onStatus(fn) { listeners.add(fn); fn(online); return () => listeners.delete(fn) },
  check() { return req('GET', '/api/amr/health') },
  state() { return req('GET', '/api/amr/state') },
  telemetry(phase, amrs) {
    return req('POST', '/api/amr/telemetry', {
      phase,
      items: amrs.map((a) => ({ code: a.name, state: a.state, vibration: num(a.vib), temp: num(a.temp), battery: num(a.batt), health: num(a.health), cumLoadKg: num(a.cumKg, 1), speed: num((a.v || 0) / 20), x: num(a.x), y: num(a.y) })),
    })
  },
  predict(p) { return req('POST', '/api/amr/predictions', { code: p.code, rulHours: p.rulHours, vibration: num(p.vibration), slope: num(p.slope, 4), cumLoadKg: num(p.cumKg, 1) }) },
  approve(id, by) { return req('POST', '/api/amr/maintenance/' + id + '/approve', { by }) },
  start(id) { return req('POST', '/api/amr/maintenance/' + id + '/start') },
  done(id) { return req('POST', '/api/amr/maintenance/' + id + '/done') },
  event(code, level, phase, message) { return req('POST', '/api/amr/events', { code, level, phase, message }) },
  settings() { return req('GET', '/api/amr/settings') },
  saveSettings(rulThresholdHours, chargeAtPct, by) { return req('PUT', '/api/amr/settings', { rulThresholdHours, chargeAtPct, by }) },
  history(code, minutes = 5) { return req('GET', '/api/amr/robots/' + encodeURIComponent(code) + '/telemetry?minutes=' + minutes) },
  maintenanceList(limit = 10) { return req('GET', '/api/amr/maintenance?limit=' + limit) },
  // 26.10.15 입구 · 출구 번호판 OCR: 서버가 gate_api.py(YOLO + CRNN)를 불러 인식하고 DB로 차종 판정
  //  인식에 몇 초 걸릴 수 있어 따로 기다리고, OCR 서버만 꺼져 있어도 DB 연결 표시는 바꾸지 않는다
  async vehicleScan(body) {
    if (needAuth && !token) return { error: 'AMR 관리자 인증 필요', status: 401 }
    const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), 25000)
    try {
      const r = await fetch(base + '/api/amr/vehicles/scan', { method: 'POST', signal: ctl.signal, headers: headers(true), body: JSON.stringify(body), credentials: cookieAuth ? 'include' : 'same-origin' })
      if (r.status === 401) { locked(); return { error: 'AMR 관리자 인증 필요', status: 401 } }
      const txt = await r.text(); let j = null; try { j = txt ? JSON.parse(txt) : null } catch { /* 무시 */ }
      if (!r.ok) return { error: (j && (j.message || j.error)) || 'HTTP ' + r.status, status: r.status }
      setOnline(true); return { data: j }
    } catch (e) { return { error: '서버 연결 실패' } } finally { clearTimeout(t) }
  },
  vehicles(limit = 20) { return req('GET', '/api/amr/vehicles?limit=' + limit) },
  // 26.10.15 KPI: 운반 1건 기록 · 오늘 지표
  task(p) {
    return req('POST', '/api/amr/tasks', { code: p.code, taskType: p.taskType, waybillNo: p.waybillNo, barcode: p.barcode, hub: p.hub, handleType: p.handleType,
      weightKg: p.weightKg, fromLoc: p.fromLoc, toLoc: p.toLoc, interWaitSec: p.interWaitSec, startedAt: p.startedAt, finishedAt: p.finishedAt })
  },
  kpi() { return req('GET', '/api/amr/kpi', null, 5000) },

  // ── 26.10.16 AMR 관리자 인증 (웹 = 팀 관리자 로그인 · 앱 = 웹에 뜬 6자리 / QR) ──
  useCookieAuth() { cookieAuth = true },
  get authed() { return !!token && tokenExp > Date.now() },
  get tokenExpiresAt() { return tokenExp },
  onAuth(fn) { authListeners.add(fn); return () => authListeners.delete(fn) },
  authStatus() { return auth('GET', '/status') },
  appCode() { return auth('GET', '/code') },        // 웹: 앱 연결 코드 + 노트북 주소
  rotate() { return auth('POST', '/rotate', {}) },   // 웹: 모든 앱 로그아웃
  // 26.10.16 인식 사진: 백엔드(8080)를 거쳐 받는다 (휴대폰이 OCR 서버 8001에 직접 못 붙어도 됨 · 이미지 태그라 출입증을 주소에 붙임)
  photoUrl(id) { return base + '/api/amr/vehicles/' + id + '/photo' + (token ? '?t=' + encodeURIComponent(token) : '') },
  async login(code, client) { const r = await auth('POST', '/login', { code, client }); if (r.data) setToken(r.data.token, r.data.expiresAt); return r },
  async logout() { await auth('POST', '/logout', {}); setToken(null) },
}

// 인증 요청: 실패 이유(메시지)를 그대로 돌려준다 · 웹은 팀 관리자 로그인 쿠키를 같이 보낸다
async function auth(method, path, body) {
  const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), 6000)
  try {
    const r = await fetch(base + '/api/amr/auth' + path, { method, signal: ctl.signal, headers: headers(body), body: body ? JSON.stringify(body) : undefined, credentials: cookieAuth ? 'include' : 'same-origin' })
    const txt = await r.text(); let j = null; try { j = txt ? JSON.parse(txt) : null } catch { /* 무시 */ }
    setOnline(true)
    if (!r.ok) return { error: (j && j.message) || 'HTTP ' + r.status, status: r.status }
    return { data: j }
  } catch (e) { setOnline(false); return { error: '서버에 연결할 수 없습니다', offline: true } } finally { clearTimeout(t) }
}
