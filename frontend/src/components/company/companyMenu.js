// 사업자 사이드바 / 상단 역할 메뉴가 같은 이름을 쓰도록 한곳에 모은다.
export const COMPANY_MENU = [
  { to: '/company', label: '대시보드', icon: 'bi-house-door-fill', exact: true },
  { to: '/company/mapping', label: '운송 관리', icon: 'bi-truck' },
  { to: '/company/container-locations', label: '컨테이너 위치 조회', icon: 'bi-geo-alt' },
  { to: '/company/trucks/new', label: '차량 관리', icon: 'bi-truck-front' },
  { to: '/company/drivers', label: '기사 관리', icon: 'bi-person-badge' },
  { to: '/company/info', label: '업체 정보', icon: 'bi-building' },
]
