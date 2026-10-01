// 26.09.30 추가: 입력 형식 검사용 정규식 모음 (관리자/사업자/기사 화면 공용)

// 자동차 등록번호: 12가3456 / 123가4567 / 서울12가3456 (공백 허용)
export const VEHICLE_NO_PATTERN = /^([가-힣]{2}\s?)?\d{2,3}\s?[가-힣]\s?\d{4}$/
export const VEHICLE_NO_MESSAGE = '차량번호 형식이 올바르지 않습니다. (예: 12가3456, 123가4567)'

// 사업자등록번호: 000-00-00000 (하이픈 생략 가능)
export const BUSINESS_NO_PATTERN = /^\d{3}-?\d{2}-?\d{5}$/
export const BUSINESS_NO_MESSAGE = '사업자등록번호는 숫자 10자리입니다. (예: 123-45-67890)'

export function normalizeVehicleNo(v) {
  return v ? String(v).replace(/\s+/g, '') : v
}

export function normalizeBusinessNo(v) {
  if (!v) return v
  const d = String(v).replace(/\D/g, '')
  return d.length === 10 ? `${d.slice(0, 3)}-${d.slice(3, 5)}-${d.slice(5)}` : v
}
