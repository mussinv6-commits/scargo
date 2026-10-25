// 26.10.23 서버 시각 → Date (여러 화면 공용)
//  백엔드는 SwaggerConfig 의 @EnableWebMvc 때문에 Spring Boot 기본 Jackson 설정(ISO 문자열)이 꺼져 있어
//  OffsetDateTime 은 epoch 초(숫자, 예: 1792742008.12), LocalDateTime 은 배열([2026,10,23,17,5,0])로 내려온다.
//  숫자를 그대로 new Date() 에 넣으면 밀리초로 읽혀 1970년이 된다 → 여기서 한 번에 맞춘다. ISO 문자열 · Date 는 그대로
export function toDate(v) {
  if (v == null || v === '') return null
  if (v instanceof Date) return v
  if (typeof v === 'number') return new Date(v < 1e12 ? v * 1000 : v)
  if (Array.isArray(v)) {
    const [y, mo = 1, d = 1, h = 0, mi = 0, s = 0, ns = 0] = v
    return new Date(y, mo - 1, d, h, mi, s, Math.floor(ns / 1e6))
  }
  return new Date(v)
}
