import { API_BASE } from '@/utils/apiBase.js'
// 26.10.21 v9: 모든 화면이 서버 실데이터만 사용 (예시 데이터 없음). 실패하면 null
export async function get(p) {
  try { const r = await fetch(API_BASE + p, { credentials: 'include' }); if (!r.ok) return null; return await r.json() } catch { return null }
}
// 저장·처리용: { ok, data, msg }
export async function req(method, p, body, isForm) {
  try {
    const r = await fetch(API_BASE + p, { method, credentials: 'include', headers: isForm ? undefined : { 'Content-Type': 'application/json' }, body: isForm ? body : body ? JSON.stringify(body) : undefined })
    const t = await r.text(); let d = null; try { d = t ? JSON.parse(t) : null } catch { d = t }
    return { ok: r.ok, data: d, msg: r.ok ? '' : (d && d.message) || (r.status === 403 ? '권한이 없습니다.' : '처리하지 못했습니다. (' + r.status + ')') }
  } catch { return { ok: false, data: null, msg: '서버에 연결할 수 없습니다.' } }
}
export async function send(method, p, body) { return (await req(method, p, body)).ok }
import { toDate } from '@/utils/serverTime.js'
// fix_1249: 서버가 epoch 초(소수) 로 줄 때도 처리 (1e12 미만이면 초 단위)
const D = toDate // fix_1630: utils/serverTime.js 공통
const p2 = (n) => String(n).padStart(2, '0')
export const hm = (t) => { const d = D(t); return d && !isNaN(d) ? p2(d.getHours()) + ':' + p2(d.getMinutes()) : '—' }
export const md = (t) => { const d = D(t); return d && !isNaN(d) ? p2(d.getMonth() + 1) + '/' + p2(d.getDate()) + ' ' + hm(t) : '—' }
export const lv = { CRITICAL: '긴급', WARNING: '경고', INFO: '정보' }
export const lvc = (s) => (s === 'CRITICAL' ? 'crit' : s === 'WARNING' ? 'warn' : 'info')
export const EXL = { SEAL_MISMATCH: '씰 불일치 (개봉 보류)', SEAL_DAMAGED: '씰 훼손', SEAL_LOST: '씰 분실', OVERLOAD: '과적', SORT_FAILED: '오분류', ITEM_DAMAGED: '센터 파손', CONTAINER_RETURN_DUE: '공컨 반납 기한 임박', CONTAINER_RETURN_OVERDUE: '공컨 반납 기한 초과', SEAL_UNREGISTERED: '씰 미등록', RETURN_CY_CHANGED: '반납지 변경 (기사 확인 대기)', CONTAINER_DAMAGED: '공컨 손상 기록', OTHER: '기타' }
export const SRC = { seal_events: '허브 앱 씰 확인', toll_passages: '톨게이트 입구', parcels: '센터 하차 스캔', items: '센터 검수', branch_units: '지선 상차 스캔', transport_orders: '반납 기한 점검 (10분 주기)', return_plan_changes: '반납 CY 변경 (W-07)', empty_container_inspections: '허브 공컨 점검' }
// fix_1830: 상태 코드 → 한글 (화면 공통)
export const ST = { CREATED: '등록', PLANNED: '계획', SCHEDULED: '예정', PENDING: '대기', ASSIGNED: '배차됨', DISPATCHED: '배차됨', LOADING: '상차 중', LOADED: '상차 완료', PICKED_UP: '상차 완료', IN_TRANSIT: '운행 중', DEPARTED: '출발', ARRIVED: '도착', INSPECTED: '봉인 확인', HANDED_OVER: '인계 완료', UNLOADING: '하차 중', UNLOADED: '하차 완료', DELIVERED: '하차 완료', RECEIVED: '인수 완료', COMPLETED: '완료', DONE: '완료', CANCELED: '취소', RETURNING_EMPTY: '공컨 반납 중', RETURN_ARRIVED: '반납지 도착', EMPTY_RETURNED: '반납 완료', OPEN: '미해결', ACK: '확인 중', ACKNOWLEDGED: '확인 중', IN_PROGRESS: '처리 중', RESOLVED: '해결', CLOSED: '종료', APPROVED: '승인', REJECTED: '반려', ACTIVE: '사용 중', AVAILABLE: '사용 가능', IDLE: '대기', RUNNING: '가동', CHARGING: '충전 중', STOPPED: '정지', ERROR: '오류', FAULT: '고장', MAINT: '정비', BLOCKED: '차단', HOLD: '보류', OPENED: '개봉', INTACT: '정상', SEALED: '봉인', DAMAGED: '손상', MISMATCH: '불일치', LOST: '분실', OK: '정상', PASS: '통과', FAIL: '불량', WARN: '주의', SORTING: '분류 중', EMPTY: '비어 있음', OCCUPIED: '사용 중', FREE: '비어 있음', CONFIRMED: '확정', TOLL_PASS: '통과', DOCKED: '도킹', PASSED: '통과', AVAILABLE_DOCK: '비어 있음' }
export const stl = (s) => (s == null || s === '' ? '—' : ST[s] || s)
