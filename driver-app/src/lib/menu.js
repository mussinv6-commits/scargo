// fix_1517: 기사 앱 공통 메뉴 (하단 탭·홈 바로가기·사이드 메뉴·내 정보에서 같은 아이콘·색 사용)
import { session } from './session'
export const isBranch = () => session.me?.userType === 'BRANCH_DRIVER'
export const DRIVER_HOME = '/m/D-00'
export function driverMenu() {
  const b = isBranch()
  return [
    { key: 'home', to: DRIVER_HOME, label: '홈', icon: 'home', color: '#102644', bg: '#E6EBF4' },
    { key: 'work', to: b ? '/m/P-01' : '/m/T-01', label: b ? '지선 운행' : '운송 오더', icon: b ? 'truck' : 'box', color: '#2851C6', bg: '#E5EDFF' },
    ...(b ? [] : [{ key: 'check', to: '/m/C-00', label: '출발 전 점검', icon: 'check', color: '#0E7C86', bg: '#DDF3F4' }]),
    { key: 'history', to: '/history', label: '운행 이력', icon: 'box', color: '#5B4B8A', bg: '#EFEAF8' },
    { key: 'inspHist', to: '/inspection-history', label: '점검 이력', icon: 'check', color: '#0E7C86', bg: '#DDF3F4' },
    { key: 'schedule', to: '/schedule', label: '배차 일정', icon: 'box', color: '#1B7A3D', bg: '#E3F6EA' },
    { key: 'logbook', to: '/logbook', label: '운행 일지', icon: 'navi', color: '#0E7C86', bg: '#DDF3F4' },
    { key: 'nearby', to: '/nearby', label: '휴게·주유·차고', icon: 'navi', color: '#1B7A3D', bg: '#E3F6EA' },
    { key: 'pay', to: '/settlements', label: '정산·수당', icon: 'TOLL', color: '#8A6A1F', bg: '#FBF1DA' },
    { key: 'docs', to: '/documents', label: '문서함', icon: 'box', color: '#5B4B8A', bg: '#EFEAF8' },
    { key: 'incident', to: '/incident', label: '사고·이상 신고', icon: 'bell', color: '#C62828', bg: '#FDE4E4' },
    { key: 'support', to: '/support', label: '고객센터', icon: 'user', color: '#2851C6', bg: '#E5EDFF' },
    { key: 'navi', to: '/navi', label: '내비', icon: 'navi', color: '#1B7A3D', bg: '#E3F6EA' },
    { key: 'toll', to: '/m/F-01', label: '통행 요금', icon: 'TOLL', color: '#8A6A1F', bg: '#FBF1DA' },
    { key: 'alerts', to: '/alerts', label: '알림', icon: 'bell', color: '#C62828', bg: '#FDE4E4' },
    { key: 'settings', to: '/settings', label: '앱 설정', icon: 'user', color: '#55607A', bg: '#ECEFF4' },
    { key: 'me', to: '/me', label: '내 정보', icon: 'user', color: '#55607A', bg: '#ECEFF4' },
  ]
}
// 하단 탭 5개 (부가 메뉴는 홈 바로가기·내 정보에서)
export const tabMenu = () => driverMenu().filter(m => ['home','work','navi','alerts','me'].includes(m.key))
