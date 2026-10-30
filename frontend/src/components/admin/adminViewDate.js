import { toDate } from '@/utils/serverTime.js' // 26.10.23 서버 시각(epoch 초) → Date
import { computed, ref } from 'vue'

function startOfDay(d) {
  const x = new Date(d)
  x.setHours(0, 0, 0, 0)
  return x
}

function pad(n) {
  return String(n).padStart(2, '0')
}

export function toDateInput(d) {
  const x = d instanceof Date ? d : new Date(d)
  return `${x.getFullYear()}-${pad(x.getMonth() + 1)}-${pad(x.getDate())}`
}

/** 사이트 개설일. 조회 날짜는 이 날부터 오늘까지만 고를 수 있다. */
const SITE_OPEN = startOfDay(new Date('2026-10-01T00:00:00'))

export const viewDate = ref(startOfDay(new Date()))

export const isViewToday = computed(() => {
  const a = startOfDay(new Date())
  const b = startOfDay(viewDate.value)
  return a.getTime() === b.getTime()
})

export const viewDateInput = computed({
  get: () => toDateInput(viewDate.value),
  set(v) {
    if (!v) return
    const picked = startOfDay(new Date(`${v}T00:00:00`))
    const today = startOfDay(new Date())
    if (picked > today) viewDate.value = today
    else if (picked < SITE_OPEN) viewDate.value = SITE_OPEN
    else viewDate.value = picked
  },
})

export const minDateInput = computed(() => toDateInput(SITE_OPEN))
export const maxDateInput = computed(() => toDateInput(new Date()))

export const viewDateLabel = computed(() => {
  const d = viewDate.value
  const week = ['일', '월', '화', '수', '목', '금', '토'][d.getDay()]
  return `${d.getFullYear()}. ${pad(d.getMonth() + 1)}. ${pad(d.getDate())} (${week})`
})

/** 오늘이면 '오늘', 그 외는 '1일전', '2일전' ... */
export const viewDateRelativeLabel = computed(() => {
  const today = startOfDay(new Date())
  const picked = startOfDay(viewDate.value)
  const diff = Math.round((today.getTime() - picked.getTime()) / 86400000)
  if (diff <= 0) return '오늘'
  return `${diff}일전`
})

/** 26.10.30 보고 구분: 일간 · 주간(월~일) · 월간 · 연간. 기준 날짜가 속한 기간을 본다 */
export const PERIODS = [
  { key: 'day', label: '일간' },
  { key: 'week', label: '주간' },
  { key: 'month', label: '월간' },
  { key: 'year', label: '연간' },
]
export const viewPeriod = ref('day')

/** 선택 기간의 [시작, 끝(포함)] — 개설일 이전 · 오늘 이후는 잘라낸다 */
export const viewRange = computed(() => {
  const d = startOfDay(viewDate.value)
  let s = new Date(d), e = new Date(d)
  if (viewPeriod.value === 'week') {
    const off = (d.getDay() + 6) % 7 // 월요일 시작
    s = new Date(d.getFullYear(), d.getMonth(), d.getDate() - off)
    e = new Date(s.getFullYear(), s.getMonth(), s.getDate() + 6)
  } else if (viewPeriod.value === 'month') {
    s = new Date(d.getFullYear(), d.getMonth(), 1)
    e = new Date(d.getFullYear(), d.getMonth() + 1, 0)
  } else if (viewPeriod.value === 'year') {
    s = new Date(d.getFullYear(), 0, 1)
    e = new Date(d.getFullYear(), 11, 31)
  }
  const today = startOfDay(new Date())
  if (s < SITE_OPEN) s = new Date(SITE_OPEN)
  if (e > today) e = today
  if (e < s) e = new Date(s)
  return { start: s, end: e, from: toDateInput(s), to: toDateInput(e) }
})

export const viewPeriodLabel = computed(() => PERIODS.find((p) => p.key === viewPeriod.value)?.label || '일간')

/** 기간 표시: 일간은 날짜 하나, 그 외는 '시작 ~ 끝' */
export const viewRangeLabel = computed(() => {
  const { from, to } = viewRange.value
  return viewPeriod.value === 'day' || from === to ? from : `${from} ~ ${to}`
})

export function isSameViewDay(value) {
  if (!value) return false
  const x = toDate(value)
  if (Number.isNaN(x.getTime())) return false
  const t = startOfDay(x).getTime()
  const { start, end } = viewRange.value
  return t >= start.getTime() && t <= end.getTime()
}

/** 오늘(일간)은 전체 목록, 그 외에는 선택 기간에 해당하는 행만 */
export function rowsForViewDate(list, field) {
  const rows = list || []
  if (viewPeriod.value === 'day' && isViewToday.value) return rows
  return rows.filter((row) => isSameViewDay(row?.[field]))
}

export function resetViewDateToday() {
  viewDate.value = startOfDay(new Date())
  viewPeriod.value = 'day'
}
