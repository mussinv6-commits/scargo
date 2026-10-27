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
    { to: '/admin/container-locations', label: '컨테이너 위치 조회', icon: 'bi-geo-alt', img: dashIcons.sidebar.적재위치관리 },
    { to: '/admin/inspections', label: '컨테이너 점검표', icon: 'bi-clipboard-check', img: dashIcons.sidebar.컨테이너관리 }, // 26.10.23
    { to: '/admin/center-receipts', label: '지역센터 인수 현황', icon: 'bi-box-arrow-in-down', img: dashIcons.sidebar.적재기록조회 }, // 26.10.23
  ],
  yard: [
    { to: '/admin/yards', label: '야드 관리', icon: 'bi-map', img: dashIcons.sidebar.야드관리 },
    { to: '/admin/loading-locations', label: '적재 위치 관리', icon: 'bi-geo-alt', img: dashIcons.sidebar.적재위치관리 },
    { to: '/admin/loading-records', label: '적재 기록 조회', icon: 'bi-journal-text', img: dashIcons.sidebar.적재기록조회 },
  ],
  etc: [
    { to: '/admin/ops-center', label: '운영 센터', icon: 'bi-speedometer2', img: dashIcons.sidebar.홈 }, // 26.10.24 현황판·사고·예약·보고서·KPI 통합
    { to: '/admin/gate-ocr', label: '게이트 OCR 검사', icon: 'bi-upc-scan', img: dashIcons.sidebar.검문소관리 }, // 26.10.01: 상단 메뉴에서 이동
    { to: '/admin/weighbridge', label: '검사소 계량', icon: 'bi-truck-front', img: dashIcons.sidebar.과적검사관리 }, // 26.10.01 추가
    { to: '/admin/overload-checks', label: '과적 검사 관리', icon: 'bi-speedometer', img: dashIcons.sidebar.과적검사관리 },
    { to: '/admin/gate-seal', label: '게이트 OCR · 씰 확인', icon: 'bi-upc-scan', img: dashIcons.sidebar.검문소관리 }, // 26.10.26 S-Bot 에서 이동
    { to: '/admin/arrivals', label: '도착 · 점검', icon: 'bi-clipboard-check', img: dashIcons.sidebar.적재기록조회 },
    { to: '/admin/move', label: '택배 이동', icon: 'bi-truck', img: dashIcons.sidebar.차량관리 },
    { to: '/admin/center', label: '물류센터 관제', icon: 'bi-buildings', img: dashIcons.sidebar.야드관리 },
    { to: '/admin/gates', label: '검문소 관리', icon: 'bi-shield-check', img: dashIcons.sidebar.검문소관리 },
  ],
  // 26.10.26 통합 관제(/w) 사이드바를 카르고 관리자 메뉴 형식으로 추가 (총관리자는 전부 열림)
  tower: [
    { to: '/w/W-02', label: '관제 현황', icon: 'bi-speedometer' },
    { to: '/w/W-03', label: '네트워크 관제', icon: 'bi-diagram-3' },
    { to: '/w/W-09', label: '예외 알림 센터', icon: 'bi-bell' },
    { to: '/w/W-15', label: '차량 점검표', icon: 'bi-clipboard-check' },
    { to: '/w/W-16', label: '직원관리', icon: 'bi-people' },
    { to: '/w/W-10', label: '기사 가입 승인', icon: 'bi-person-check' },
    { to: '/w/W-11', label: '기준정보', icon: 'bi-sliders' },
  ],
  port: [
    { to: '/w/W-07', label: '운송오더·물품 관리', icon: 'bi-boxes' },
    { to: '/w/W-12', label: '구간 관제 지도', icon: 'bi-signpost' },
    { to: '/w/W-13', label: '구간 차량 관제', icon: 'bi-shield-check' },
    { to: '/w/W-14', label: '구간 계측 이력', icon: 'bi-speedometer2' },
  ],
  hubc: [
    { to: '/w/W-04', label: '허브 관제', icon: 'bi-hdd-network' },
    { to: '/w/W-05', label: '지역센터 관제', icon: 'bi-buildings' },
    { to: '/w/W-08', label: '지선 배차 관리', icon: 'bi-truck-front' },
  ],
  notice: [
    { to: '/admin/notices', label: '공지사항', icon: 'bi-megaphone', img: dashIcons.sidebar.적재기록조회 },
    { to: '/admin/menu-guide', label: '메뉴 안내', icon: 'bi-signpost-split', img: dashIcons.sidebar.적재기록조회 }, // 26.10.26 사이드바 전체 구성을 펼쳐서 보여 주는 화면
  ],
}

export const ADMIN_MENU_FLAT = [
  ...ADMIN_MENU.member,
  ...ADMIN_MENU.vehicle,
  ...ADMIN_MENU.yard,
  ...ADMIN_MENU.etc,
  ...ADMIN_MENU.tower,
  ...ADMIN_MENU.port,
  ...ADMIN_MENU.hubc,
  ...ADMIN_MENU.notice,
]
