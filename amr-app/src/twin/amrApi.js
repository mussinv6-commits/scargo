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

async function req(method, path, body, timeout = 3000) {
  const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), timeout)
  try {
    const r = await fetch(base + path, { method, signal: ctl.signal, headers: body ? { 'Content-Type': 'application/json' } : undefined, body: body ? JSON.stringify(body) : undefined })
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
  history(code, minutes = 5) { return req('GET', '/api/amr/robots/' + encodeURIComponent(code) + '/telemetry?minutes=' + minutes) },
  maintenanceList(limit = 10) { return req('GET', '/api/amr/maintenance?limit=' + limit) },
}
