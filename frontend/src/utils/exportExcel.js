// 26.10.24 목록 내보내기 → 26.10.28 보고서 양식(.xlsx)으로 변경
//  columns: [{ key, label, type?, formatter?, format?, trueLabel?, falseLabel?, badge? }]  (CrudTable 컬럼과 같은 모양)
//  rows: 화면에 보이는 전체 행 (페이지 나누기 전 데이터)
//  결과: 제목 · 문서번호 · 결재란 · 1. 보고 개요 · 2. 상세 내역 · 비고 로 된 엑셀 보고서
import { buildXlsx, downloadBlob as dl } from './xlsxLite.js'
import { createReport, nowInfo, textWidth } from './reportXlsx.js'
import { viewDate, viewPeriod, viewPeriodLabel, viewRange, viewRangeLabel } from '@/components/admin/adminViewDate.js' // 26.10.30 보고일자는 화면 위에서 고른 조회 날짜 (예전엔 항상 오늘로 찍혔음)

function cellText(col, row) {
  const v = row?.[col.key]
  try {
    if (col.type === 'boolean') return v ? (col.trueLabel || '예') : (col.falseLabel || '아니오')
    if (col.type === 'badge' && typeof col.badge === 'function') return col.badge(v, row)?.label ?? ''
    if (typeof col.formatter === 'function') return col.formatter(v, row)
    if (typeof col.format === 'function') return col.format(v, row)
  } catch {
    // 형식 지정이 실패하면 원래 값을 그대로 쓴다
  }
  if (v === null || v === undefined) return ''
  if (typeof v === 'object') return JSON.stringify(v)
  return v
}

function csvCell(value) {
  let s = String(value ?? '')
  // 수식으로 해석되는 시작 문자 방지 (CSV 인젝션)
  if (/^[=+\-@]/.test(s) && Number.isNaN(Number(s))) s = "'" + s
  return /[",\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
}

export function toCsv(columns, rows) {
  const cols = columns.filter((c) => c.type !== 'seq' && c.key)
  const head = cols.map((c) => csvCell(c.label ?? c.key)).join(',')
  const body = rows.map((row) => cols.map((c) => csvCell(cellText(c, row))).join(','))
  return [head, ...body].join('\r\n')
}

export function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// 엑셀은 한 시트에 약 100만 행까지 가능하지만, 화면 목록 내보내기는 안전하게 5만 행까지만 담는다
const MAX_ROWS = 50000

export function buildListReport(fileBaseName, columns, rows) {
  const t = nowInfo() // 출력 일시 · 문서번호는 지금 시각
  const d = nowInfo(viewDate.value) // 보고일자 · 본문 날짜는 고른 조회 날짜
  const cols = columns.filter((c) => c.type !== 'seq' && c.key)
  const list = rows.slice(0, MAX_ROWS)
  // 번호 열 + 화면 열. 숫자로만 된 값은 숫자 서식으로 넣는다
  const body = list.map((row, i) => [i + 1, ...cols.map((c) => {
    const v = cellText(c, row)
    return typeof v === 'number' ? v : v == null ? '' : String(v)
  })])
  const tcols = [{ label: '번호', type: 'center' }, ...cols.map((c) => ({ label: String(c.label ?? c.key), type: typeof body[0]?.[cols.indexOf(c) + 1] === 'number' ? 'num' : 'text' }))]
  const rp = createReport(tcols.length)
  rp.header(`${fileBaseName} 보고서`, [['문서번호', t.docNo('SCARGO-RPT')], ['보고일자', viewPeriod.value === 'day' ? d.kor : `${viewPeriodLabel.value} · ${viewRangeLabel.value}`], ['보고 대상', fileBaseName], ['출력 일시', t.stamp]])
  rp.section('1. 보고 개요')
  rp.paragraph(`${viewPeriod.value === 'day' ? d.kor : `${viewRangeLabel.value} (${viewPeriodLabel.value})`} 기준 ${fileBaseName} 현황을 보고함. 총 ${rows.length.toLocaleString('ko-KR')}건${rows.length > MAX_ROWS ? ` (파일에는 앞의 ${MAX_ROWS.toLocaleString('ko-KR')}건만 포함)` : ''}.`, 1)
  rp.section('2. 상세 내역')
  rp.table(tcols, body, { empty: '표시할 내역이 없습니다.' })
  rp.note('※ 이 문서는 S카고 시스템에서 화면의 목록을 기준으로 자동 작성되었습니다.')
  const widths = tcols.map((c, i) => Math.max(textWidth(c.label) + 4, ...body.slice(0, 200).map((r) => textWidth(r[i]) + 2)))
  widths[0] = 7
  return buildXlsx([rp.sheet(fileBaseName, widths)])
}

export function exportExcel(fileBaseName, columns, rows) {
  const t = nowInfo()
  dl(buildListReport(fileBaseName, columns, rows), `${fileBaseName}_${viewPeriod.value === 'day' ? '' : viewPeriodLabel.value + '_'}보고서_${viewPeriod.value === 'day' ? nowInfo(viewDate.value).day : viewRange.value.from + '~' + viewRange.value.to}.xlsx`)
}
