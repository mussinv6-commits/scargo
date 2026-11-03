// 26.11.01 이 폰(기사 앱)에서만 보여줄 과적검문소 허용 목록
//  VITE_CHECKPOINT_ALLOW=CHECKPOINT-18,인천항 과적검문소
//  비우면(또는 미설정) 서버가 준 검문소 전부 표시 · 안내
//  매칭: station code · id · 이름 (이름 부분일치, CHECKPOINT-N → "과적검문소 N")
let runtimeAllow = null // 앱 설정에서 덮어씀. null=env 사용, ''=전체
export function setCheckpointAllow(v) {
  if (v === null || v === undefined) runtimeAllow = null
  else runtimeAllow = String(v)
  try { localStorage.setItem('scargo.checkpointAllow', runtimeAllow === null ? '' : runtimeAllow) } catch {}
}
try { const s = localStorage.getItem('scargo.checkpointAllow'); if (s !== null) runtimeAllow = s } catch {}

function parseAllow() {
  if (runtimeAllow !== null) {
    const raw = String(runtimeAllow || '').trim()
    if (!raw) return null
    const list = raw.split(/[,;|/]+/).map((s) => s.trim()).filter(Boolean)
    return list.length ? list : null
  }
  let raw = ''
  try { raw = (import.meta.env && import.meta.env.VITE_CHECKPOINT_ALLOW) || '' } catch { raw = '' }
  raw = String(raw || '').trim()
  if (!raw) return null
  const list = raw.split(/[,;|/]+/).map((s) => s.trim()).filter(Boolean)
  return list.length ? list : null
}

export function checkpointAllowList() { return parseAllow() }

export function isAllowedCheckpoint(c) {
  const allow = parseAllow()
  if (!allow) return true
  if (!c) return false
  const id = String(c.id ?? c.checkpointId ?? '')
  const name = String(c.name ?? c.checkpointName ?? c.message ?? '')
  const code = String(c.code ?? c.stationCode ?? c.checkpointCode ?? '')
  return allow.some((a) => {
    if (!a) return false
    if (a === id || a === code || a === name) return true
    if (name && (name === a || name.includes(a))) return true
    const m = /^CHECKPOINT-(\d+)$/i.exec(a)
    if (m) {
      const n = m[1]
      if (name === `과적검문소 ${n}` || new RegExp(`과적검문소\\s*${n}\\b`).test(name)) return true
      if (a.toUpperCase() === 'CHECKPOINT-18' && /인천항/.test(name)) return true
    }
    return false
  })
}

export function filterCheckpoints(list) {
  const arr = Array.isArray(list) ? list : []
  if (!parseAllow()) return arr
  return arr.filter(isAllowedCheckpoint)
}

export function filterCheckpointAlerts(alerts) {
  const arr = Array.isArray(alerts) ? alerts : []
  if (!parseAllow()) return arr
  return arr.filter((a) => a?.type !== 'CHECKPOINT' || isAllowedCheckpoint({ name: a.message || a.title, id: a.id }))
}
