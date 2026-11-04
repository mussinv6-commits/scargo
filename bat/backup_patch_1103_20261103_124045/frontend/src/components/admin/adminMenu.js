// 26.09.30 추가: 관리자 메뉴 정의를 한 곳에 모음.
// 사이드바 / 대시보드 바로가기 / 상단 네비 드롭다운이 모두 같은 이름을 쓰도록 한다.
import { dashIcons } from './dashIcons.js'
export const ADMIN_MENU = {
  member: [
    // 26.10.28 기업회원 가입 승인 + 기사 가입 승인 → '가입 승인' 한 메뉴 (기업 / 기사 탭)
    // 26.10.28 순서 정리(처리순): 가입 승인 → 회원 → 업체 → 직원
    { to: '/admin/signup-approvals', label: '가입 승인', icon: 'bi-person-check', img: dashIcons.sidebar.회원관리 },
    { to: '/admin/accounts', label: '회원 관리', icon: 'bi-people', img: dashIcons.sidebar.회원관리 },
    { to: '/admin/companies', label: '업체 관리', icon: 'bi-building', img: dashIcons.sidebar.업체관리 },
    { to: '/w/W-16', label: '직원관리', icon: 'bi-person-badge', img: dashIcons.sidebar.회원관리 }, // 26.10.28 공통 컨트롤타워에서 이동
  ],
  vehicle: [
    { to: '/admin/trucks', label: '차량 관리', icon: 'bi-truck', img: dashIcons.sidebar.차량관리 },
    { to: '/admin/containers', label: '컨테이너 관리', icon: 'bi-box-seam', img: dashIcons.sidebar.컨테이너관리 }, // 26.10.28 컨테이너 목록 + 점검표 탭
    { to: '/admin/container-locations', label: '컨테이너 위치 조회', icon: 'bi-geo-alt', img: dashIcons.sidebar.적재위치관리 },
  ],
  yard: [
    // 26.10.28 순서 정리(처리순): 야드 → 적재 위치 → 적재 기록
    { to: '/admin/yards', label: '야드 관리', icon: 'bi-map', img: dashIcons.sidebar.야드관리 },
    { to: '/admin/loading-locations', label: '적재 위치 관리', icon: 'bi-geo-alt', img: dashIcons.sidebar.적재위치관리 },
    { to: '/admin/loading-records', label: '적재 기록 조회', icon: 'bi-journal-text', img: dashIcons.sidebar.적재기록조회 },
  ],
  // 26.10.28 메뉴 이동: '안전 · 기타' 그룹을 없애고 각 메뉴를 흐름(항만 → 허브 / 허브 → 물류센터) 그룹으로 옮김. 운영 센터는 대시보드 바로 아래(top)
  top: [
    { to: '/admin/ops-center', label: '운영 관리', icon: 'bi-speedometer2', img: dashIcons.sidebar.홈 }, // 26.10.24 현황판·사고·예약·보고서·KPI 통합
    { to: '/admin/ops-center/alerts', label: '경보 상세', icon: 'bi-exclamation-triangle', img: dashIcons.sidebar.홈 }, // 26.10.29 운영 센터 '지금 확인할 경보'의 긴급 / 주의 전체 내용 (메뉴로도 들어갈 수 있게)
  ],
  // 26.10.26 통합 관제(/w) 사이드바를 카르고 관리자 메뉴 형식으로 추가 (총관리자는 전부 열림)
  tower: [
    { to: '/w/W-02', label: '관제 현황', icon: 'bi-speedometer' },
    { to: '/w/W-03', label: '운송망 관제', icon: 'bi-share' },
    { to: '/w/W-09', label: '이상 상황 관리', icon: 'bi-bell' },
    { to: '/w/W-15', label: '점검표', icon: 'bi-clipboard-check' }, // 26.10.28 QA#11 '차량 점검표' → '점검표'
    // 26.10.28 직원관리 → 회원·업체, 기사 가입 승인 → 회원·업체 '가입 승인'(중복 삭제), 기준정보 → 설정 · 안내
  ],
  port: [
    // 26.10.28 순서 정리(처리순): 오더 등록 → 항만 게이트 출문 → 구간 운행 · 계측 → 허브 도착 · 점검
    { to: '/w/W-07', label: '운송오더·물품 관리', icon: 'bi-boxes' },
    { to: '/admin/gates', label: '검문소 관리', icon: 'bi-shield-check', img: dashIcons.sidebar.검문소관리 }, // 26.10.28 안전·기타 → 항만 → 허브
    { to: '/admin/gate-ocr', label: '항만 게이트 OCR 검사', icon: 'bi-upc-scan', img: dashIcons.sidebar.검문소관리 }, // 26.10.28 안전·기타 → 항만 → 허브
    { to: '/w/W-12', label: '구간 관제', icon: 'bi-signpost' }, // 26.10.29 구간 관제 지도 · 차량 관제 · 계측 이력을 메뉴 1개로 합침 (화면 안 버튼으로 전환)
    { to: '/admin/weighbridge', label: '검사소 계량', icon: 'bi-truck-front', img: dashIcons.sidebar.과적검사관리 }, // 26.10.28 안전·기타 → 항만 → 허브
    { to: '/admin/overload-checks', label: '과적 검사 관리', icon: 'bi-speedometer', img: dashIcons.sidebar.과적검사관리 }, // 26.10.28 안전·기타 → 항만 → 허브
    { to: '/admin/arrivals', label: '허브 입고 관리', icon: 'bi-clipboard-check', img: dashIcons.sidebar.적재기록조회 }, // 26.10.28 안전·기타 → 항만 → 허브
    { to: '/admin/handovers', label: '인수 내역', icon: 'bi-file-earmark-check', img: dashIcons.sidebar.적재기록조회 }, // 26.10.29 허브 인계 기록 · 인수증 PDF
  ],
  hubc: [
    // 26.10.28 순서 정리(처리순): 허브 → 지선 배차 → 이동 → 지역 물류센터 관제 / 26.10.29 허브 게이트(입차 · 씰 확인)를 맨 앞에
    { to: '/admin/gate-seal', label: '허브 게이트 OCR · 씰 확인', icon: 'bi-upc-scan', img: dashIcons.sidebar.검문소관리 }, // 26.10.29 항만 → 허브에서 이동 (허브 입차 게이트), 이름 앞에 '허브'
    { to: '/w/W-04', label: '허브 관제', icon: 'bi-hdd-network' },
    { to: '/w/W-08', label: '지선 배차 관리', icon: 'bi-truck-front' },
    { to: '/admin/move', label: '택배 운송 관제', icon: 'bi-truck', img: dashIcons.sidebar.차량관리 }, // 26.10.28 안전·기타 → 허브 → 물류센터
    { to: '/admin/center', label: '물류센터 관제', icon: 'bi-buildings', img: dashIcons.sidebar.야드관리 }, // 26.10.28 물류센터 관제(3D) + 지역센터 관제 + 지역센터 인수 현황 → 한 메뉴 (탭)
  ],
  notice: [
    // 26.10.28 순서 정리(가나다순)
    { to: '/admin/notices', label: '공지사항', icon: 'bi-megaphone', img: dashIcons.sidebar.적재기록조회 },
    { to: '/w/W-11', label: '운영 기준 설정', icon: 'bi-sliders' }, // 26.10.28 공통 컨트롤타워 → 설정 · 안내
    { to: '/admin/menu-guide', label: '메뉴 안내', icon: 'bi-signpost-split', img: dashIcons.sidebar.적재기록조회 }, // 26.10.26 사이드바 전체 구성을 펼쳐서 보여 주는 화면
    { to: '/admin/notifications', label: '알림 내역', icon: 'bi-bell', img: dashIcons.sidebar.적재기록조회 }, // 26.10.28 지난 알림까지 보는 화면
  ],
}

export const ADMIN_MENU_FLAT = [
  ...ADMIN_MENU.member,
  ...ADMIN_MENU.vehicle,
  ...ADMIN_MENU.yard,
  ...ADMIN_MENU.top,
  ...ADMIN_MENU.tower,
  ...ADMIN_MENU.port,
  ...ADMIN_MENU.hubc,
  ...ADMIN_MENU.notice,
]
