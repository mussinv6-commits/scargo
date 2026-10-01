// 26.09.30 추가: 관리자 메뉴 정의를 한 곳에 모음.
// 사이드바 / 대시보드 바로가기 / 상단 네비 드롭다운이 모두 같은 이름을 쓰도록 한다.
import { dashIcons } from './dashIcons.js'
export const ADMIN_MENU = {
  member: [
    { to: '/admin/accounts', label: '회원 관리', icon: 'bi-people', img: dashIcons.sidebar.회원관리 },
    { to: '/admin/companies', label: '업체 관리', icon: 'bi-building', img: dashIcons.sidebar.업체관리 },
  ],
  vehicle: [
    { to: '/admin/trucks', label: '차량 관리', icon: 'bi-truck', img: dashIcons.sidebar.차량관리 },
    { to: '/admin/containers', label: '컨테이너 관리', icon: 'bi-box-seam', img: dashIcons.sidebar.컨테이너관리 },
  ],
  yard: [
    { to: '/admin/yards', label: '야드 관리', icon: 'bi-map', img: dashIcons.sidebar.야드관리 },
    { to: '/admin/loading-locations', label: '적재 위치 관리', icon: 'bi-geo-alt', img: dashIcons.sidebar.적재위치관리 },
    { to: '/admin/loading-records', label: '적재 기록 조회', icon: 'bi-journal-text', img: dashIcons.sidebar.적재기록조회 },
  ],
  etc: [
    { to: '/admin/gate-ocr', label: '게이트 OCR 검사', icon: 'bi-upc-scan', img: dashIcons.sidebar.검문소관리 }, // 26.10.01: 상단 메뉴에서 이동
    { to: '/admin/weighbridge', label: '계중대 계량', icon: 'bi-truck-front', img: dashIcons.sidebar.과적검사관리 }, // 26.10.01 추가
    { to: '/admin/overload-checks', label: '과적 검사 관리', icon: 'bi-speedometer', img: dashIcons.sidebar.과적검사관리 },
    { to: '/admin/gates', label: '검문소 관리', icon: 'bi-shield-check', img: dashIcons.sidebar.검문소관리 },
    { to: '/notice', label: '공지사항', icon: 'bi-megaphone', img: dashIcons.sidebar.적재기록조회 },
  ],
}

export const ADMIN_MENU_FLAT = [
  ...ADMIN_MENU.member,
  ...ADMIN_MENU.vehicle,
  ...ADMIN_MENU.yard,
  ...ADMIN_MENU.etc,
]
