// web.html(기획 목업) 의 권한표. v9: 권한 없는 메뉴는 숨김 (화면 ID 는 코드·라우트에만, 화면에는 이름만)
export const T = { 'W-01': '로그인', 'W-02': '관제 현황', 'W-03': '네트워크 관제', 'W-04': '허브 관제', 'W-05': '지역센터 관제', 'W-06': 'AMR 관제', 'W-07': '운송오더·물품 관리', 'W-08': '지선 배차 관리', 'W-09': '예외 알림 센터', 'W-10': '기사 가입 승인', 'W-11': '기준정보', 'W-12': '구간 관제 지도', 'W-13': '구간 차량 관제', 'W-14': '구간 계측 이력', 'W-15': '차량 점검표', 'W-16': '직원관리' }
// 사이드바 순서·그룹 (web.html nav 그대로)
export const NAV = [
  { grp: null, items: ['W-01'] },
  { grp: '공통 컨트롤타워', items: ['W-02', 'W-03', 'W-09', 'W-15', 'W-16', 'W-10', 'W-11'] },
  { grp: '항만 → 허브', items: ['W-07', 'W-12', 'W-13', 'W-14'] }, // 26.10.26: W-12~14 = 인천항 → 곤지암 구간 관제 (toll_demo 서버 화면을 iframe 으로 표시)
  { grp: '허브 → 물류센터', items: ['W-04', 'W-05', 'W-06', 'W-08'] },
]
export const R = {
  ADMIN: 'all',
  CONTROL: ['W-01', 'W-02', 'W-03', 'W-04', 'W-05', 'W-06', 'W-07', 'W-08', 'W-09', 'W-12', 'W-13', 'W-14', 'W-15'],
  HUB_OP: ['W-01', 'W-02', 'W-04', 'W-06', 'W-08', 'W-09'],
  CENTER_OP: ['W-01', 'W-02', 'W-05', 'W-09'],
  carrier: ['W-01', 'W-02', 'W-07', 'W-08', 'W-09', 'W-10', 'W-15'],
}
export const RN = { ADMIN: '총관리자', CONTROL: '통합 관제사', HUB_OP: '허브 관제사', CENTER_OP: '지역센터 관제사', carrier: '운송사 담당자' }
export const HOME = { ADMIN: 'W-02', CONTROL: 'W-02', HUB_OP: 'W-04', CENTER_OP: 'W-05', carrier: 'W-02' }
// 백엔드 user_type 7종 → 목업 권한 키. 기사 2종은 웹 권한 없음("앱을 이용해 주세요")
export const WEB_ROLE_OF = { ADMIN: 'ADMIN', CONTROL: 'CONTROL', HUB_OP: 'HUB_OP', CENTER_OP: 'CENTER_OP', CORPORATE_APPROVED: 'carrier' }
export const APP_ONLY = ['CONTAINER_DRIVER', 'BRANCH_DRIVER']
// fix_1223: user_type 문자열 정규화(공백·대소문자, role/userRole 필드도 허용)
export const userTypeOf = (user) => String(user?.userType ?? user?.user_type ?? user?.role ?? user?.userRole ?? "").trim().toUpperCase()
export const webRole = (user) => (user ? WEB_ROLE_OF[userTypeOf(user)] || null : null)
// 26.10.26: 예전 톨게이트·검문소·고속도로 요금 화면은 없애고 그 번호를 구간 관제 3화면이 쓴다 (고속도로 요금은 기사 앱 F-01)
export const REMOVED = []
export const canSee = (role, id) => !!role && !REMOVED.includes(id) && (R[role] === 'all' || R[role].includes(id))

// v13: 로그인 계정별 첫 화면 (로그인 직후·잘못된 화면 접근 시 공통 사용)
export const DRIVER_TYPES = ['CONTAINER_DRIVER', 'BRANCH_DRIVER', 'GENERAL'] // 통합: GENERAL(회원가입 일반회원)도 기사 화면
export function homeOf(user) {
  if (!user) return '/scargo'
  if (user.userType === 'ADMIN' && user.adminScope === 'SBOT') return '/sbot'
  if (DRIVER_TYPES.includes(user.userType)) return '/driver/app/status'
  if (user.userType === 'CORPORATE_PENDING') return '/pending'
  if (user.userType === 'ADMIN') return '/admin' // 26.10.26 총관리자는 카르고 대시보드로
  const r = webRole(user)
  return r ? '/w/' + HOME[r] : '/scargo'
}
// v13: 상단바 역할 메뉴 (그 권한이 볼 수 있는 W 화면만, 화면 ID 없이 이름만)
export function wMenuGroups(role) {
  return NAV.filter(g => g.grp).map(g => g.items.filter(id => canSee(role, id)).map(id => ({ to: '/w/' + id, label: T[id] }))).filter(g => g.length)
}
