// 26.10.28 알림을 누르면 이동할 화면 (종 버튼 패널 · 알림 내역 화면이 같이 씀). NotificationBell.vue 에서 옮김
import { authState, isDriverType } from '@/auth/authState.js'

// 26.09.30 추가: 알림을 누르면 관련 기능 화면으로 이동
// 백엔드 알림 유형(NotificationType): CORPORATE_APPROVAL / OVERLOAD_WARNING / NOTICE / SYSTEM
//  - CORPORATE_APPROVAL : 기업회원 가입 신청(관리자) · 승인 완료(사업자)       referenceId = accountId
//  - OVERLOAD_WARNING   : 과적 위반 발생                                      referenceId = checkId
//  - NOTICE             : 차량 등록/진입 허가 결과 등 운영 안내, 공지사항
export function resolveTarget(n) {
  const role = authState.user?.userType
  const text = `${n.title || ''} ${n.message || ''}`
  const ref = n.referenceId
  const admin = role === 'ADMIN'
  // 26.10.28 관리자: 메뉴 정리(가입 승인 탭 · 운영 센터 탭 · 지역 물류센터 관제 등)에 맞춰 알림마다 바로 그 화면 · 탭으로
  if (admin) {
    switch (n.notificationType) {
      case 'CORPORATE_APPROVAL': return { path: '/admin/signup-approvals', query: { tab: 'corp' } }
      case 'DRIVER_APPROVAL_REQUEST': return { path: '/admin/signup-approvals', query: { tab: 'driver' } }
      case 'OVERLOAD_WARNING': return '/admin/overload-checks'
    }
    if (/점검표 제출/.test(text)) return { path: '/w/W-15', query: { tab: /컨테이너/.test(n.title || '') ? 'c' : 'v' } } // 26.10.29 앱 점검표 제출 알림
    if (/공지/.test(text)) return ref ? `/admin/notices/${ref}` : '/admin/notices'
    if (/미등록|진입|허가/.test(text)) return { path: '/admin/trucks', query: { tab: 'appr' } }
    if (/차량|배정/.test(text)) return '/admin/trucks'
    if (/사고|예외|파손|경보/.test(text)) return { path: '/admin/ops-center', query: { tab: 'incidents' } }
    if (/게이트 ?예약|예약|불참/.test(text)) return { path: '/admin/ops-center', query: { tab: 'reservations' } }
    if (/인수/.test(text)) return { path: '/admin/center', query: { tab: 'rcv' } }
    if (/지선|봉인 불일치|센터 도착/.test(text)) return { path: '/admin/center', query: { tab: 'region' } }
    if (/씰|봉인/.test(text)) return '/admin/gate-seal'
    if (/반납|공컨|빈 컨테이너/.test(text)) return '/w/W-07'
    if (/컨테이너 점검|점검 불합격|불합격|점검표|재점검/.test(text)) return { path: '/admin/containers', query: { tab: 'inspection' } }
    if (/하역|도착|도크/.test(text)) return '/admin/arrivals'
    if (/지연|ETA/.test(text)) return { path: '/admin/ops-center', query: { tab: 'board' } }
    if (/계량|과적/.test(text)) return '/admin/weighbridge'
    if (/AMR|정비|충전|로봇/.test(text)) return '/sbot/hub'
    // 맞는 화면이 없으면 알림 내역에서 그 알림을 바로 보여 준다
    return { path: '/admin/notifications', query: { id: n.notificationId } }
  }
  switch (n.notificationType) {
    case 'CORPORATE_APPROVAL':
      return role === 'CORPORATE_APPROVED' ? '/company' : '/'
    // fix_1027: 기사 가입 승인 요청 → W-10 기사 가입 승인 화면
    case 'DRIVER_APPROVAL_REQUEST':
      return role === 'CORPORATE_APPROVED' ? '/w/W-10' : null
    case 'OVERLOAD_WARNING':
      return isDriverType(role) ? '/driver/app/status' : '/company'
    case 'NOTICE':
    case 'SYSTEM':
    default:
      // 26.10.29 앱 점검표 제출 알림 → 점검표 화면 (통합 관제사 · 운송사 담당자)
      if (/점검표 제출/.test(text) && ['CONTROL', 'CORPORATE_APPROVED'].includes(role)) return { path: '/w/W-15', query: { tab: /컨테이너/.test(n.title || '') ? 'c' : 'v' } }
      if (/공지/.test(text)) return ref && /게시|공지사항/.test(text) ? `/notice/${ref}` : '/notice'
      if (/차량|진입|허가|반려|배정/.test(text)) {
        if (role === 'CORPORATE_APPROVED') return /배정/.test(text) ? '/company/drivers' : '/company'
        if (isDriverType(role)) return '/driver/app/my-page'
      }
      if (/배차|컨테이너|적재/.test(text) && isDriverType(role)) return '/driver/app/dispatch-list'
      if (/정산|세금계산서/.test(text) && isDriverType(role)) return '/driver/app/settlement'
      if (role === 'CORPORATE_APPROVED') return '/company'
      if (isDriverType(role)) return '/driver/app/status'
      // 그 밖의 계정(관제사 등)은 알림 내역에서 그 알림을 보여 준다
      return { path: '/notifications', query: { id: n.notificationId } }
  }
}


// 알림 시각 표시 (서버가 epoch 초로 내려주면 1000 을 곱한다)
export function toDate(d) {
  return new Date(typeof d === 'number' && d < 1e12 ? d * 1000 : d)
}

// 알림 종류 이름 (알림 내역 화면의 필터 · 배지)
export const NOTIF_TYPE = {
  CORPORATE_APPROVAL: '가입 승인',
  DRIVER_APPROVAL_REQUEST: '기사 가입',
  OVERLOAD_WARNING: '과적 경고',
  NOTICE: '안내',
  SYSTEM: '시스템',
}

// 전체 알림 내역 화면 주소 (관리자는 관리자 화면 안, 그 밖의 계정은 단독 화면)
export function historyPath() {
  return authState.user?.userType === 'ADMIN' ? '/admin/notifications' : '/notifications'
}
