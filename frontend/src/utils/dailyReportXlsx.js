// 26.10.29 일일 처리량 보고서 엑셀: 다른 보고서와 같은 공통 양식(제목 · 문서번호 · 결재란 · 1. 2. 구역)으로 프론트에서 만든다
//  데이터는 화면과 같은 /api/reports/daily 응답 { scope, rows: [{ section, label, count }] }
import { buildXlsx } from './xlsxLite.js'
import { createReport, nowInfo } from './reportXlsx.js'

const parseDay = (s) => { const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(s || '')); return m ? new Date(+m[1], +m[2] - 1, +m[3]) : new Date() }

const PNAME = { day: '일일', week: '주간', month: '월간', year: '연간' }

export function buildDailyReportXlsx(report, date, to, period = 'day') {
  const now = nowInfo()
  const day = nowInfo(parseDay(date || report?.date))
  const last = to && to !== (date || report?.date) ? nowInfo(parseDay(to)) : null
  const pn = PNAME[period] || '일일'
  const when = last ? `${day.day} ~ ${last.day}` : day.kor
  const rows = Array.isArray(report?.rows) ? report.rows : []
  const scope = report?.scope || '전체 허브'
  const rp = createReport(7) // 7칸: 구분(A) · 항목(B~E 병합) · 건수(F~G 병합)

  rp.header(`${pn} 처리량 보고서`, [['문서번호', now.docNo('SCARGO-RPT')], ['보고일자', last ? `${pn} · ${when}` : day.kor], ['보고 대상', scope], ['출력 일시', now.stamp]])
  rp.section('1. 보고 개요')
  const sections = [...new Set(rows.map((r) => r.section))]
  rp.paragraph(`${when} 기준 ${scope}의 ${pn} 처리량을 보고함. ${sections.length}개 구분, ${rows.length}개 항목.`, 1)
  rp.section('2. 상세 내역')

  const H = 1
  const hr = rp.add([{ v: '구분', s: H }, { v: '항목', s: H }, { v: '', s: H }, { v: '', s: H }, { v: '', s: H }, { v: '건수', s: H }, { v: '', s: H }], 30)
  rp.merge(1, hr, 4, hr); rp.merge(5, hr, 6, hr)
  let runStart = -1
  const closeRun = (end) => { if (runStart >= 0 && end > runStart) rp.merge(0, runStart, 0, end) }
  rows.forEach((r, i) => {
    const n = Number(r.count)
    const cnt = Number.isFinite(n) && r.count !== '' && r.count != null ? { v: n, s: 2 } : { v: r.count == null ? '' : String(r.count), s: 14 }
    const newRun = i === 0 || rows[i - 1].section !== r.section
    const ri = rp.add([{ v: newRun ? (r.section ?? '') : '', s: 14 }, { v: r.label ?? '', s: 7 }, { v: '', s: 7 }, { v: '', s: 7 }, { v: '', s: 7 }, cnt, { v: '', s: 2 }], 22)
    if (newRun) { closeRun(ri - 1); runStart = ri }
    rp.merge(1, ri, 4, ri); rp.merge(5, ri, 6, ri)
  })
  closeRun(rp.rows.length - 1)
  if (!rows.length) { const r = rp.add([{ v: '해당 기간의 내역이 없습니다.', s: 14 }, ...Array(6).fill({ v: '', s: 14 })], 24); rp.merge(0, r, 6, r) }
  rp.add([], 8)
  rp.note('※ 이 문서는 S카고 시스템에서 화면의 조회 결과를 기준으로 자동 작성되었습니다.')
  return buildXlsx([rp.sheet(`${pn} 보고서`, [14, 14, 14, 14, 14, 12, 12])])
}
