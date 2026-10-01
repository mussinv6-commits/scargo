// 26.09.30 추가: 화면 전체에서 공통으로 쓰는 API 보조 함수 모음
// ------------------------------------------------------------------
// 1) friendlyError : 서버 에러(SQL 문장, 스택트레이스, JSON 객체 등)를
//    사용자에게 그대로 보여주지 않고 이해하기 쉬운 한글 문장으로 바꿔준다.
//    (과적검사 신규등록 시 "밑에 코드가 같이 나오던" 문제 수정)
// 2) boolAlias / withBoolAliases : Jackson 은 boolean 필드 isXxx 를
//    JSON 에서 "xxx" 로 내보내는 경우가 있다 (primitive boolean + Lombok).
//    그래서 화면이 isSemiTrailer 를 읽으면 항상 undefined → 체크해도 적용 안 되는
//    것처럼 보였다. 읽을 때는 둘 중 있는 값을, 보낼 때는 두 이름 모두 보낸다.
// 3) updateWithFallback : 백엔드가 PUT/PATCH 중 어느 쪽을 쓰는지 화면마다 달라
//    수정이 405 로 실패하던 문제를 막기 위해, 하나가 405 면 다른 메서드로 재시도한다.
// ------------------------------------------------------------------

const TECH_PATTERN =
  /(exception|sql|jdbc|hibernate|constraint|could not|org\.|java\.|springframework|jakarta\.|\bat [a-z]+\.|stack|trace|nested|insert into|update .* set|select .* from|\bnull\b.*\bnot\b|JSON parse|Cannot deserialize|Required request|MethodArgument)/i

function translateTechnical(raw) {
  const text = String(raw || '')
  if (/duplicate (entry|key)|unique|already exists|이미 (존재|등록)/i.test(text)) {
    return '이미 등록된 값입니다. 중복되지 않는 값으로 다시 입력해주세요.'
  }
  if (/foreign key|a foreign key constraint fails|referential|child record/i.test(text)) {
    return '다른 데이터에서 사용 중이라 처리할 수 없습니다. 연결된 데이터를 먼저 정리해주세요.'
  }
  if (/data too long|value too long|too long for column/i.test(text)) {
    return '입력값이 너무 깁니다. 길이를 줄여서 다시 입력해주세요.'
  }
  if (/cannot be null|must not be null|not-null|null value/i.test(text)) {
    return '필수 항목이 비어 있습니다. 표시(*)된 항목을 모두 입력해주세요.'
  }
  if (/check constraint/i.test(text)) {
    return '허용되지 않는 값이 포함되어 있습니다. 선택 항목의 값을 확인해주세요.'
  }
  if (/JSON parse|Cannot deserialize|not a valid|incorrect .* value|NumberFormat|invalid json/i.test(text)) {
    return '입력 형식이 올바르지 않습니다. 숫자/날짜/JSON 형식을 확인해주세요.'
  }
  return ''
}

function looksTechnical(msg) {
  if (!msg) return false
  const s = String(msg)
  return s.length > 140 || s.includes('\n') || TECH_PATTERN.test(s)
}

const STATUS_MESSAGE = {
  400: '입력값을 다시 확인해주세요.',
  401: '로그인이 필요합니다. 다시 로그인해주세요.',
  403: '이 작업을 할 권한이 없습니다.',
  404: '요청한 대상을 찾을 수 없습니다. 이미 삭제되었을 수 있습니다.',
  405: '서버에서 지원하지 않는 요청입니다.',
  409: '이미 등록되어 있거나 다른 데이터와 연결되어 있어 처리할 수 없습니다.',
  413: '첨부파일 용량이 너무 큽니다.',
  415: '요청 형식이 올바르지 않습니다.',
  500: '서버 처리 중 오류가 발생했습니다. 입력값을 확인한 뒤 다시 시도해주세요.',
}

/**
 * 서버 에러 → 사용자용 한글 메시지
 * @param {any} err axios 에러
 * @param {string} fallback 알 수 없는 경우 보여줄 기본 문장
 */
export function friendlyError(err, fallback = '요청 처리 중 오류가 발생했습니다.') {
  const res = err?.response
  if (!res) {
    if (err?.request) return '서버에 연결할 수 없습니다. 백엔드 서버가 실행 중인지 확인해주세요.'
    return err?.message && !looksTechnical(err.message) ? err.message : fallback
  }

  const data = res.data

  // Bean Validation(@Valid) 에러 목록이 오는 경우 → 필드별 문구만 모아서 보여준다
  if (data && typeof data === 'object') {
    const list = data.errors || data.fieldErrors || data.violations
    if (Array.isArray(list) && list.length) {
      const msgs = list
        .map((e) => e?.defaultMessage || e?.message || (typeof e === 'string' ? e : ''))
        .filter((m) => m && !looksTechnical(m))
      if (msgs.length) return [...new Set(msgs)].join('\n')
    }
  }

  const raw = typeof data === 'string' ? data : data?.message || data?.detail || data?.error || ''
  if (raw && !looksTechnical(raw) && !/^(Bad Request|Internal Server Error|Not Found|Forbidden|Unauthorized|Conflict)$/i.test(raw)) {
    return raw
  }
  const translated = translateTechnical(raw) || translateTechnical(typeof data === 'object' ? JSON.stringify(data) : '')
  if (translated) return translated
  return STATUS_MESSAGE[res.status] || fallback
}

/**
 * 삭제 실패 전용 메시지: 백엔드는 FK 위반(DataIntegrityViolation)을 별도 처리하지 않아
 * 500 + 빈 메시지로 내려온다. 삭제에서 500 이 나는 경우는 거의 연결 데이터 때문이므로 그렇게 안내.
 */
export function deleteError(err, what = '데이터') {
  const status = err?.response?.status
  if (status === 500 || status === 409) {
    return `이 ${what} 항목은 다른 데이터(적재 기록·컨테이너·차량 등)에 연결되어 있어 삭제할 수 없습니다.\n연결된 데이터를 먼저 수정하거나 삭제해주세요.`
  }
  return friendlyError(err, '삭제 중 오류가 발생했습니다.')
}

/** 읽기: row.isX 가 없으면 row.x 를 쓴다 */
export function boolAlias(row, key) {
  if (!row) return false
  if (row[key] !== undefined && row[key] !== null) return row[key] === true || row[key] === 'true'
  const alt = key.startsWith('is') ? key.charAt(2).toLowerCase() + key.slice(3) : null
  if (alt && row[alt] !== undefined && row[alt] !== null) return row[alt] === true || row[alt] === 'true'
  return false
}

/** 목록 행 정규화: boolean 키를 isXxx 로 통일하고, id 키가 없으면 id 에서 채운다 */
export function normalizeRows(list, { bools = [], idKey } = {}) {
  const arr = Array.isArray(list) ? list : Array.isArray(list?.content) ? list.content : []
  return arr.map((r) => {
    const row = { ...r }
    bools.forEach((k) => {
      row[k] = boolAlias(r, k)
    })
    if (idKey && (row[idKey] === undefined || row[idKey] === null) && row.id !== undefined) {
      row[idKey] = row.id
    }
    return row
  })
}

/** 보내기: isXxx 와 xxx 두 이름으로 모두 넣는다 (백엔드 DTO 형태와 무관하게 적용되도록) */
export function withBoolAliases(payload, keys) {
  const out = { ...payload }
  keys.forEach((k) => {
    const v = payload[k] === true || payload[k] === 'true'
    out[k] = v
    if (k.startsWith('is')) out[k.charAt(2).toLowerCase() + k.slice(3)] = v
  })
  return out
}

/** 빈 문자열 → null (숫자/날짜/JSON 컬럼에 '' 가 들어가 500 이 나던 문제 방지) */
export function emptyToNull(payload) {
  const out = {}
  Object.entries(payload).forEach(([k, v]) => {
    out[k] = typeof v === 'string' && v.trim() === '' ? null : typeof v === 'string' ? v.trim() : v
  })
  return out
}

export function toNum(v) {
  return v === '' || v === null || v === undefined ? null : Number(v)
}

/** 경로 변수 안전 인코딩 (차량번호 12가3456 처럼 한글이 들어가는 경우) */
export function seg(v) {
  return encodeURIComponent(String(v))
}

/**
 * 수정 요청: 먼저 preferred 메서드로 보내고, 405(Method Not Allowed)면 다른 메서드로 재시도.
 */
export async function updateWithFallback(api, url, body, preferred = 'put') {
  const other = preferred === 'put' ? 'patch' : 'put'
  try {
    return await api[preferred](url, body)
  } catch (err) {
    if (err?.response?.status === 405) return api[other](url, body)
    throw err
  }
}
