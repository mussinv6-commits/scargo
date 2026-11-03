// 26.11.01 같은 Wi-Fi 에서 PC 백엔드(8080) 자동 찾기
//  AMR 앱은 웹 [앱 연결] QR 에 server=http://PC_IP:8080 이 들어 있어 IP 가 바뀌어도 QR 한 번으로 맞춰짐.
//  기사 앱에는 그 QR 이 없어, 저장된 주소가 안 되면 같은 대역 IP 를 /api/app/health 로 훑어 찾는다.
import { Capacitor } from '@capacitor/core'
import { getApiBase, getSavedApiBase, getDefaultApiBase, setApiBase, isNative } from './api'

const KEY_LAST_OK = 'scargo.apiBase.lastOk'
const KEY_MANUAL = 'scargo.apiBase.manual' // '1' = 사용자가 ServerSheet 에서 직접 저장
const PORT = 8080
const HEALTH = '/api/app/health'
const PROBE_MS = 900
const BATCH = 24

function lsGet(k) { try { return localStorage.getItem(k) } catch { return null } }
function lsSet(k, v) {
  try {
    if (v == null || v === '') localStorage.removeItem(k)
    else localStorage.setItem(k, v)
  } catch { /* 무시 */ }
}

export function markApiBaseManual(on) { lsSet(KEY_MANUAL, on ? '1' : null) }
export function isApiBaseManual() { return lsGet(KEY_MANUAL) === '1' }
export function rememberLastOkBase(url) {
  const u = normalizeBase(url)
  if (u) lsSet(KEY_LAST_OK, u)
}
export function getLastOkBase() { return lsGet(KEY_LAST_OK) || '' }

export function normalizeBase(url) {
  let u = String(url || '').trim().replace(/\/+$/, '')
  if (!u) return ''
  if (!/^https?:\/\//i.test(u)) u = 'http://' + u
  try {
    const x = new URL(u)
    if (!x.port) x.port = String(PORT)
    return x.origin
  } catch { return u }
}

function hostOf(url) {
  try { return new URL(normalizeBase(url)).hostname } catch { return '' }
}

function subnetHosts(ip) {
  const m = /^(\d+)\.(\d+)\.(\d+)\.(\d+)$/.exec(ip || '')
  if (!m) return []
  const a = +m[1], b = +m[2], c = +m[3], self = +m[4]
  // 사설망만 (휴대폰이 공인 IP 를 주면 스캔 안 함)
  const priv = a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168)
  if (!priv) return []
  const pref = `${a}.${b}.${c}.`
  const out = []
  // 흔한 PC 주소 먼저
  for (const h of [self, 1, 2, 10, 20, 50, 100, 101, 102, 110, 120, 150, 200, 254]) {
    if (h >= 1 && h <= 254) out.push(pref + h)
  }
  for (let i = 1; i <= 254; i++) out.push(pref + i)
  return [...new Set(out)]
}

/** WebView 에서 로컬 IP 추정 (플러그인 없이) */
export function guessLocalIp(timeoutMs = 1500) {
  return new Promise((resolve) => {
    let done = false
    const finish = (v) => { if (done) return; done = true; try { pc.close() } catch { /* */ } resolve(v || null) }
    let pc
    try {
      pc = new RTCPeerConnection({ iceServers: [] })
      pc.createDataChannel('scargo')
      pc.onicecandidate = (ev) => {
        const c = ev.candidate && ev.candidate.candidate
        if (!c) return
        const m = /([0-9]{1,3}(?:\.[0-9]{1,3}){3})/.exec(c)
        if (!m) return
        const ip = m[1]
        if (ip.startsWith('127.') || ip.startsWith('0.')) return
        finish(ip)
      }
      pc.createOffer().then((o) => pc.setLocalDescription(o)).catch(() => finish(null))
    } catch { finish(null); return }
    setTimeout(() => finish(null), timeoutMs)
  })
}

async function probe(base, ms = PROBE_MS) {
  const b = normalizeBase(base)
  if (!b) return null
  const ctl = new AbortController()
  const t = setTimeout(() => ctl.abort(), ms)
  try {
    const r = await fetch(b + HEALTH, { method: 'GET', signal: ctl.signal, cache: 'no-store' })
    if (!r.ok) return null
    let j = null
    try { j = await r.json() } catch { /* 본문 없음 */ }
    if (j && j.ok === false) return null
    return b
  } catch { return null } finally { clearTimeout(t) }
}

async function probeMany(bases) {
  const list = [...new Set(bases.map(normalizeBase).filter(Boolean))]
  for (let i = 0; i < list.length; i += BATCH) {
    const chunk = list.slice(i, i + BATCH)
    const found = await Promise.all(chunk.map((b) => probe(b)))
    const hit = found.find(Boolean)
    if (hit) return hit
  }
  return null
}

/**
 * 같은 Wi-Fi 에서 scargo 백엔드를 찾는다.
 * @returns {Promise<string|null>} http://ip:8080 또는 null
 */
export async function discoverApiBase({ force = false } = {}) {
  if (!isNative && !force) {
    // 브라우저 개발은 Vite 프록시 우선
    const cur = getApiBase()
    if (!cur) return ''
  }

  const seeds = []
  const push = (u) => { const n = normalizeBase(u); if (n) seeds.push(n) }
  push(getApiBase())
  push(getSavedApiBase())
  push(getLastOkBase())
  push(getDefaultApiBase())
  // 에뮬레이터 기본
  push('http://10.0.2.2:8080')
  push('http://localhost:8080')
  push('http://127.0.0.1:8080')

  // 1) 시드 주소부터 빠르게 확인
  const quick = await probeMany(seeds)
  if (quick) { rememberLastOkBase(quick); return quick }

  // 2) 로컬 IP / 마지막 IP 대역 스캔
  const tipHosts = []
  for (const s of seeds) {
    const h = hostOf(s)
    if (h && /^\d+\.\d+\.\d+\.\d+$/.test(h)) tipHosts.push(h)
  }
  const localIp = await guessLocalIp()
  if (localIp) tipHosts.unshift(localIp)

  const hosts = []
  for (const tip of tipHosts) hosts.push(...subnetHosts(tip))
  const uniqHosts = [...new Set(hosts)]
  if (!uniqHosts.length) return null

  const scanned = await probeMany(uniqHosts.map((h) => `http://${h}:${PORT}`))
  if (scanned) { rememberLastOkBase(scanned); return scanned }
  return null
}

let ensuring = null
/**
 * 현재 주소가 안 되면 자동 탐색 후 setApiBase.
 * 수동 저장 주소가 있어도 연결 실패 시 탐색하고, 찾으면 그 주소로 갱신(IP 변경 대응).
 */
export async function ensureApiBase({ forceDiscover = false } = {}) {
  if (!Capacitor.isNativePlatform() && !forceDiscover) {
    return getApiBase() // 웹 개발: 프록시
  }
  if (ensuring) return ensuring
  ensuring = (async () => {
    const cur = normalizeBase(getApiBase())
    if (cur && !forceDiscover) {
      const ok = await probe(cur, 1500)
      if (ok) { rememberLastOkBase(ok); return ok }
    }
    const found = await discoverApiBase({ force: true })
    if (found) {
      setApiBase(found)
      // 수동 플래그는 유지하되 실사용 주소는 갱신 (사용자가 시트에서 다시 고정 가능)
      rememberLastOkBase(found)
      return found
    }
    return cur || ''
  })().finally(() => { ensuring = null })
  return ensuring
}