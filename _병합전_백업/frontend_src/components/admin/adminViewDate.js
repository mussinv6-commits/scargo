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

export function isSameViewDay(value) {
  if (!value) return false
  const x = new Date(value)
  if (Number.isNaN(x.getTime())) return false
  const t = viewDate.value
  return x.getFullYear() === t.getFullYear() && x.getMonth() === t.getMonth() && x.getDate() === t.getDate()
}

/** 오늘은 전체 목록, 과거 날짜를 고르면 그 날짜에 해당하는 행만 */
export function rowsForViewDate(list, field) {
  const rows = list || []
  if (isViewToday.value) return rows
  return rows.filter((row) => isSameViewDay(row?.[field]))
}

export function resetViewDateToday() {
  viewDate.value = startOfDay(new Date())
}
