// 26.10.24 목록 Excel 내보내기 (CSV, UTF-8 BOM 포함 → Excel 에서 한글이 깨지지 않고 바로 열림)
//  columns: [{ key, label, type?, formatter?, format?, trueLabel?, falseLabel?, badge? }]  (CrudTable 컬럼과 같은 모양)
//  rows: 화면에 보이는 전체 행 (페이지 나누기 전 데이터)

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

export function exportExcel(fileBaseName, columns, rows) {
  const stamp = new Date().toISOString().slice(0, 10)
  const blob = new Blob(['﻿' + toCsv(columns, rows)], { type: 'text/csv;charset=utf-8' })
  downloadBlob(blob, `${fileBaseName}_${stamp}.csv`)
}
