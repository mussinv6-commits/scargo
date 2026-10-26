// 컨트롤타워 API. 서버(/api/web/**)가 응답하면 그 값으로, 아니면 목업(MOCK) 그대로.
import { MOCK } from './engine'
import { apiBase } from './base'

// 서버 exceptions.center_id(logistics_centers) → 목업 거점 id. TODO: GET /api/web/centers 로 center_code 기준 매핑
const CENTER_TO_SITE = { 1: 1, 2: 11, 3: 12, 4: 13 }
const LABEL = { SEAL_MISMATCH: '씰 불일치 (개봉 보류)', SEAL_DAMAGED: '씰 훼손', SEAL_LOST: '씰 분실', OVERLOAD: '과적', SORT_FAILED: '오분류', ITEM_DAMAGED: '센터 파손', OTHER: '기타' }

export async function refresh() {
  const get = async (p) => { const r = await fetch(apiBase + p, { credentials: 'include' }); if (!r.ok) throw new Error(r.status); return r.json() }
  try {
    const list = await get('/api/web/exceptions')
    if (Array.isArray(list)) MOCK.exceptions = list.map(e => ({
      id: e.exceptionId, type: e.exceptionType, label: LABEL[e.exceptionType] || e.exceptionType, severity: e.severity,
      siteId: e.centerId == null ? null : (CENTER_TO_SITE[e.centerId] ?? e.centerId),   // NULL = 도로 위 (assignment_id 로 연결)
      target: e.message, at: (e.occurredAt || '').slice(11, 16), status: e.status,
      owner: e.assigneeAccountId ? '#' + e.assigneeAccountId : null,   // TODO: 담당자 이름은 accounts 조인 응답으로
    }))
  } catch { /* 목업 유지 */ }
  // 26.10.20 씰 번호 앱 → 웹
  try { MOCK.seals = await get('/api/web/seals') } catch { /* 권한 없음·서버 없음: 패널에 안내 */ }
  try {
    const m = await get('/api/web/hubs/1/monitor')
    if (Array.isArray(m.branches) && m.branches.length) MOCK.hub.branches = m.branches.map(l => ({
      depart: (l.depart || '').slice(11, 16) || '-', dest: '#' + l.destCenterId, status: l.status + (l.domesticSealNo ? ' · 봉인 ' + l.domesticSealNo : ''),
      tripId: 'LH#' + l.tripId, units: l.units, late: false }))
  } catch { /* 목업 유지 */ }
  // TODO: inbound·work·ports·returns 는 서버 응답이 채워지면 MOCK.hub 에 병합
}

/** W-09 처리: OPEN → IN_PROGRESS → RESOLVED. RESOLVED 는 처리 내용(resolution_note) 필수 */
export async function advanceException(id, note) {
  try {
    const r = await fetch(apiBase + '/api/web/exceptions/' + id, { method: 'PUT', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(note ? { note } : {}) })
    return r.ok
  } catch { return true /* 서버 없음: 목업만 */ }
}
