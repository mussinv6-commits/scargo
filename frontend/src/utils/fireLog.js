// 26.10.29 화재·배터리 화재 기록: AMR 관제(3D)에서 일어난 화재 훈련/사고를 같은 브라우저의 공용 저장소에 남기고,
//  S-Bot 대시보드가 읽어 배너·이력·일일 보고서에 쓴다 (센터 현황의 sbot_shared_v3 와 같은 방식).
const KEY = 'sbot_fire_v1'
const MAX = 50
const ls = () => { try { return typeof localStorage !== 'undefined' ? localStorage : null } catch { return null } }
export function readFire() {
  const s = ls(); if (!s) return { active: null, log: [] }
  try { const o = JSON.parse(s.getItem(KEY)); return { active: o && o.active || null, log: Array.isArray(o && o.log) ? o.log : [] } } catch { return { active: null, log: [] } }
}
function save(o) { const s = ls(); if (!s) return; try { s.setItem(KEY, JSON.stringify(o)) } catch { /* 저장 공간 부족 등은 무시 */ } }
// 화재 이벤트 한 건을 기록한다. type: 'fire' | 'bfire', p: 시뮬레이터가 보낸 내용, now: 시각(ms)
export function recordFire(type, p, now = Date.now()) {
  const o = readFire()
  if (type === 'fire') {
    if (p.on) o.active = { kind: 'hub', label: p.label || '허브', zone: p.zone, since: now, stage: '대피 중' }
    else {
      const a = o.active
      o.log.unshift({ kind: 'hub', label: (a && a.label) || p.label || '허브', start: a ? a.since : now - (p.secs || 0) * 1000, end: now, evac: p.evac || 0, total: p.total || 0, secs: Math.round(p.secs || 0), note: '허브 화재 경보 훈련 · 관리자 해제' })
      o.active = null
    }
  } else if (type === 'bfire') {
    const st = p.stage
    if (st === 0) {
      const a = o.active
      o.log.unshift({ kind: 'batt', label: p.code, start: a ? a.since : now, end: now, evac: 0, total: 0, secs: a ? Math.round((now - a.since) / 1000) : 0, temp: a && a.temp || 0, note: '배터리 화재 · 관리자 확인 · 배터리 교체 후 복귀' })
      o.active = null
    } else {
      const names = { 1: '온도 이상 · 작업 중단', 2: '격리 구역 이동', 3: '배터리 화재 · 소화 설비 작동', 4: '진압 완료 · 접근 금지' }
      const prev = o.active && o.active.kind === 'batt' ? o.active : null
      o.active = { kind: 'batt', label: p.code, since: prev ? prev.since : now, zone: null, stage: names[st] || '', temp: p.temp || (prev && prev.temp) || 0 }
    }
  }
  if (o.log.length > MAX) o.log.length = MAX
  save(o)
  return o
}

// 26.10.29 허브 관제를 새로 열었는데 화재가 없으면(새로고침 등으로 종료 신호를 놓침) 남아 있는 "진행 중" 표시만 지운다 (기록 목록은 그대로)
export function clearActiveFire() { const o = readFire(); if (o.active) { o.active = null; save(o) } }
