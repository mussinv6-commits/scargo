// 26.10.28 보고서 양식 공통 모듈: 제목 · 문서 정보 · 결재란 · 번호 붙은 구역 · 표 · 비고
//  모든 엑셀 내보내기가 같은 모양(제목 / 문서번호 / 결재란 / 1. 2. 3. 구역)으로 나오도록 한 곳에서 만든다.
//  서식 번호는 xlsxLite.js 의 STYLES 와 같다: 1 머리글 · 2 숫자 · 4 합계 글자 · 3 합계 숫자 · 7 글자 · 8 구역 제목 · 14 가운데 · 17 주석 · 19 제목 · 20 라벨

export const pad2 = (n) => String(n).padStart(2, '0')
export const colName = (i) => { let s = ''; i++; while (i > 0) { const m = (i - 1) % 26; s = String.fromCharCode(65 + m) + s; i = Math.floor((i - 1) / 26) } return s }
const ref = (c, r) => colName(c) + (r + 1)

// 글자 폭 계산 (한글 · 전각은 2칸)
export const textWidth = (v) => [...String(v ?? '')].reduce((a, ch) => a + (ch.charCodeAt(0) > 0x2e80 ? 2 : 1), 0)

export function nowInfo(d = new Date()) {
  const day = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
  const wk = ['일', '월', '화', '수', '목', '금', '토'][d.getDay()]
  return {
    day, time: `${pad2(d.getHours())}:${pad2(d.getMinutes())}`, stamp: `${day} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`,
    kor: `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${wk})`,
    docNo: (code) => `${code}-${day.replace(/-/g, '')}-${pad2(d.getHours())}${pad2(d.getMinutes())}`,
  }
}

// 보고서를 한 장씩 쌓는 도우미. G = 전체 칸 수 (7 이상: 결재란이 들어갈 자리)
export function createReport(cols) {
  const G = Math.max(7, cols)
  const rows = [], merges = [], heights = {}
  const blank = () => Array(G).fill('')
  const add = (cells, h) => { const r = blank(); cells.forEach((c, i) => { r[i] = c }); rows.push(r); if (h) heights[rows.length - 1] = h; return rows.length - 1 }
  const merge = (c0, r0, c1, r1) => { if (c0 !== c1 || r0 !== r1) merges.push(`${ref(c0, r0)}:${ref(c1, r1)}`) }
  const rep = (c0, c1, s) => Array.from({ length: c1 - c0 + 1 }, () => ({ v: '', s }))

  // 제목 + 문서 정보(왼쪽) + 결재란(오른쪽)
  function header(title, meta, approvers = ['담당', '팀장', '센터장'], stamps = []) {   // 26.10.31 stamps: 결재란 칸별 [{name, at}] (확정 도장)
    const t = add([{ v: title, s: 19 }, ...rep(1, G - 1, 19)], 38); merge(0, t, G - 1, t)
    const top = rows.length
    const info = meta.slice(0, 4); while (info.length < 4) info.push(['', ''])
    const vEnd = G - 5 // 값 칸은 1 ~ vEnd
    for (let i = 0; i < 4; i++) {
      const r = add([{ v: info[i][0], s: 20 }, { v: info[i][1], s: 7 }, ...rep(2, vEnd, 7)], 24)
      merge(1, r, vEnd, r)
      rows[r][G - 4] = { v: i === 0 ? '결\n재' : '', s: 20 }
      for (let k = 0; k < 3; k++) rows[r][G - 3 + k] = i === 0 ? { v: approvers[k], s: 20 } : { v: '', s: 7 }
    }
    stamps.forEach((st, k) => { if (st && st.name && k < 3) rows[top + 1][G - 3 + k] = { v: st.name + (st.at ? '\n' + st.at : ''), s: 21 } })
    merge(G - 4, top, G - 4, top + 3)
    for (let k = 0; k < 3; k++) merge(G - 3 + k, top + 1, G - 3 + k, top + 3)
    // 결재 도장 칸은 높게
    for (let i = 1; i < 4; i++) heights[top + i] = 26
    add([], 8)
  }
  function section(title) { const r = add([{ v: title, s: 8 }, ...rep(1, G - 1, 8)], 24); merge(0, r, G - 1, r) }
  // 문단 (개요 · 특이사항 · 종합 의견) : 전체 폭, 여러 줄
  function paragraph(text, lines = 2) {
    const r = add([{ v: text, s: 9 }, ...rep(1, G - 1, 9)], Math.max(24, 20 * lines + 6)); merge(0, r, G - 1, r)
  }
  // 표: columns [{label, type?: 'num'|'text'|'center', sum?: true}] · data [[...]] · total 이 true 면 sum 열만 합계 (수식)
  function table(columns, data, opt = {}) {
    // 26.11.02 columns[i].span: 그 열이 차지할 칸 수 (표 열이 적을 때 글자가 좁은 칸에 갇히지 않도록 칸을 합친다)
    // 긴 글이 들어가는 마지막 열은 남은 칸을 모두 차지하게 한다 (칸이 좁아 글자가 작아지거나 겹치는 것 방지)
    if (!columns.some((c) => c.span) && columns.length < G) {
      const li = columns.length - 1, lc = columns[li]
      const mx = Math.max(0, ...data.map((r2) => (typeof r2[li] === 'string' ? textWidth(r2[li]) : 0)))
      if (lc.type !== 'num' && lc.type !== 'center' && mx > 24) columns = columns.map((c, i) => (i === li ? { ...c, span: G - li } : c))
    }
    const starts = []; let used = 0
    columns.forEach((c) => { starts.push(used); used += c.span || 1 })
    const flat = (cells) => cells.flatMap((cell, i) => { const n = columns[i].span || 1; return [cell, ...rep(1, n - 1, cell.s)] })
    const mergeSpans = (r) => columns.forEach((c, i) => { if ((c.span || 1) > 1) merge(starts[i], r, starts[i] + c.span - 1, r) })
    const hr = add(flat(columns.map((c) => ({ v: c.label, s: 1 }))), 44); mergeSpans(hr)
    const first = rows.length
    data.forEach((row) => {
      const r = add(flat(columns.map((c, i) => {
        const v = row[i]
        if (v && typeof v === 'object') return v // {v, s, f} 를 그대로 쓴다 (색 · 수식)
        if (typeof v === 'number' && Number.isFinite(v)) return { v, s: Number.isInteger(v) ? 2 : 7 }
        return { v: v == null ? '' : String(v), s: c.type === 'center' ? 14 : 7 }
      })), opt.rowHeight || 20); mergeSpans(r)
    })
    const last = rows.length - 1
    if (opt.total && data.length) {
      const r = add(flat(columns.map((c, i) => {
        if (i === 0) return { v: opt.totalLabel || '합계', s: 4 }
        if (c.sum) { const sv = data.reduce((a, r2) => a + (Number(r2[i]) || 0), 0); return { v: sv, f: `SUM(${colName(starts[i])}${first + 1}:${colName(starts[i])}${last + 1})`, s: 3 } }
        return { v: '', s: 4 }
      })), 22); mergeSpans(r)
    }
    if (!data.length) { const r = add([{ v: opt.empty || '해당 내역이 없습니다.', s: 14 }, ...rep(1, used - 1, 14)], 24); merge(0, r, used - 1, r) }
    add([], 8)
    return hr
  }
  function note(text) { const r = add([{ v: text, s: 17 }, ...rep(1, G - 1, 17)], 30); merge(0, r, G - 1, r) }
  function sheet(name, widths, extra = {}) {
    const w = Array.from({ length: G }, (_, i) => Math.max(8, Math.min(46, widths[i] || 12)))
    return { name: String(name).replace(/[\\/?*[\]:]/g, ' ').slice(0, 31), widths: w, landscape: G > 9, heights, merges, rows, ...extra }
  }
  return { G, rows, merges, heights, add, merge, header, section, paragraph, table, note, sheet }
}
