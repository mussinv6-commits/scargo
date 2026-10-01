// 사업자 사이드바 / 상단 역할 메뉴가 같은 이름을 쓰도록 한곳에 모은다.
export const COMPANY_MENU = [
  { to: '/company', label: '대시보드', icon: 'bi-house-door-fill', exact: true },
  { to: '/company/mapping', label: '운송 관리', icon: 'bi-truck' },
  { to: '/company/trucks/new', label: '차량 관리', icon: 'bi-truck-front' },
  { to: '/company/drivers', label: '기사 관리', icon: 'bi-person-badge' },
  { to: '/company/info', label: '업체 정보', icon: 'bi-building' },
]

export const COMPANY_EXTRA_MENU = [
  { to: '/notice', label: '공지사항', icon: 'bi-megaphone' },
]
