// 26.11.02 보고서 미리보기 · PDF · 인쇄: 엑셀로 내려받는 보고서(xlsxLite 시트)를 내용 · 양식 그대로 A4 세로 그림 여러 쪽으로 그린다.
//  인수증(handoverReceipt.js)과 같은 방식(그림 → PDF · 인쇄)이라 외부 라이브러리가 필요 없다.
const FONT = '"Malgun Gothic","Apple SD Gothic Neo","Noto Sans KR",sans-serif'
// xlsxLite STYLES 번호 → 모양 (fill · 글자색 · 굵게 · 글자 크기(pt) · 가운데 · 테두리 · 줄바꿈)
const BOLD = new Set([1, 3, 4, 5, 8, 10, 11, 12, 18, 19, 20])
const FILL = { 1: '#13243F', 3: '#F3E9C6', 4: '#F3E9C6', 8: '#2B5797', 10: '#D5F0DD', 11: '#FCEFC0', 12: '#F8D0D0', 15: '#EEF2F8', 18: '#F3E9C6', 20: '#EEF2F8' }
const COLOR = { 1: '#FFFFFF', 8: '#FFFFFF', 3: '#13243F', 5: '#13243F', 19: '#13243F', 6: '#6B7380', 17: '#6B7380', 16: '#2B5797' }
const SIZE = { 5: 15, 19: 15, 8: 12, 6: 10, 17: 10 }
const CENTER = new Set([1, 10, 11, 12, 14, 19, 20, 21])
const NOBORDER = new Set([0, 5, 6, 8, 17, 19])

const PW = 1240, PH = 1754 // A4 세로 150dpi
const MARGIN = 56

function colA1(ref) { const m = /^([A-Z]+)(\d+)$/.exec(ref); let c = 0; for (const ch of m[1]) c = c * 26 + ch.charCodeAt(0) - 64; return [c - 1, +m[2] - 1] }
function parseMerges(list) {
  const map = new Map(), covered = new Set()
  ;(list || []).forEach((s) => {
    const [a, b] = s.split(':'); const [c0, r0] = colA1(a), [c1, r1] = colA1(b)
    map.set(c0 + ',' + r0, { c1, r1 })
    for (let r = r0; r <= r1; r++) for (let c = c0; c <= c1; c++) if (r !== r0 || c !== c0) covered.add(c + ',' + r)
  })
  return { map, covered }
}
function cellText(o) {
  if (o == null || o === '') return ''
  const c = typeof o === 'object' ? o : { v: o }, v = c.v
  if (v == null || v === '') return ''
  if (typeof v === 'number') return c.s === 13 || c.s === 18 ? v.toFixed(1) + '%' : v.toLocaleString('ko-KR')
  return String(v)
}
function wrapLines(ctx, text, maxW, wrap) {
  const out = []
  String(text).split('\n').forEach((para) => {
    if (!wrap) { out.push(para); return }
    let line = ''
    for (const ch of para) {
      if (ctx.measureText(line + ch).width > maxW && line) { out.push(line); line = ch } else line += ch
    }
    out.push(line)
  })
  return out
}

/** 시트 한 장 → A4 세로 canvas 여러 장 */
export function drawSheetPages(sheet) {
  const widths = (sheet.widths || []).map((w) => Math.round(w * 7 + 5))
  const nCols = widths.length || Math.max(...sheet.rows.map((r) => (r || []).length), 1)
  while (widths.length < nCols) widths.push(75)
  const totalW = widths.reduce((a, b) => a + b, 0)
  const k = (PW - 2 * MARGIN) / totalW
  const xs = [0]; widths.forEach((w, i) => xs.push(xs[i] + w * k))
  const rowH = sheet.rows.map((_, i) => ((sheet.heights && sheet.heights[i]) || 18) * (96 / 72) * k)
  const { map, covered } = parseMerges(sheet.merges)
  // 쪽 나누기: 합친 칸이 걸친 줄 사이에서는 끊지 않는다
  const noBreak = new Set(); map.forEach((m, key) => { const r0 = +key.split(',')[1]; for (let r = r0; r < m.r1; r++) noBreak.add(r) })
  const pages = []; let cur = []; let y = 0; const maxH = PH - 2 * MARGIN - 30
  for (let r = 0; r < sheet.rows.length; r++) {
    if (y + rowH[r] > maxH && cur.length && !noBreak.has(r - 1)) { pages.push(cur); cur = []; y = 0 }
    cur.push({ r, y }); y += rowH[r]
  }
  if (cur.length) pages.push(cur)
  const mk = () => { const c = document.createElement('canvas'); c.width = PW; c.height = PH; const g = c.getContext('2d'); g.fillStyle = '#fff'; g.fillRect(0, 0, PW, PH); return [c, g] }
  const canvases = pages.map((rows, pi) => {
    const [cv, g] = mk()
    const y0 = rows[0].y
    rows.forEach(({ r, y: ry }) => {
      const row = sheet.rows[r] || []
      for (let c = 0; c < nCols; c++) {
        const key = c + ',' + r
        if (covered.has(key)) continue
        const m = map.get(key), c1 = m ? m.c1 : c, r1 = m ? m.r1 : r
        const x = MARGIN + xs[c], w = xs[c1 + 1] - xs[c]
        let h = rowH[r]; for (let rr = r + 1; rr <= r1; rr++) h += rowH[rr] || 0
        const yy = MARGIN + ry - y0
        const raw = row[c], s = raw && typeof raw === 'object' ? (raw.s || 0) : 0
        if (FILL[s]) { g.fillStyle = FILL[s]; g.fillRect(x, yy, w, h) }
        if (!NOBORDER.has(s)) { g.strokeStyle = s === 3 || s === 4 || s === 18 ? '#13243F' : '#D0D5DD'; g.lineWidth = 1; g.strokeRect(x + 0.5, yy + 0.5, w - 1, h - 1) }
        const t = cellText(raw); if (!t) continue
        const pt = SIZE[s] || 11, px = pt * (96 / 72) * k * 1.0
        g.font = `${BOLD.has(s) ? 'bold ' : ''}${Math.max(9, px)}px ${FONT}`
        g.fillStyle = COLOR[s] || '#1f2937'; g.textBaseline = 'middle'
        const pad = 6, center = CENTER.has(s), isNum = raw && typeof raw === 'object' ? typeof raw.v === 'number' : typeof raw === 'number'
        let fpx = Math.max(9, px), lines = wrapLines(g, t, w - 2 * pad, true)
        while (fpx > 7 && lines.length * fpx * 1.3 > h - 2) { fpx -= 1; g.font = `${BOLD.has(s) ? 'bold ' : ''}${fpx}px ${FONT}`; lines = wrapLines(g, t, w - 2 * pad, true) }
        const lh = fpx * 1.3
        const top = yy + (h - lh * lines.length) / 2 + lh / 2
        g.textAlign = center ? 'center' : isNum ? 'right' : 'left'
        const tx = center ? x + w / 2 : isNum ? x + w - pad : x + pad + (s === 8 || s === 15 ? 6 : 0)
        lines.forEach((ln, i) => g.fillText(ln, tx, top + i * lh, w - 2 * pad))
      }
    })
    g.font = `11px ${FONT}`; g.fillStyle = '#6B7380'; g.textAlign = 'center'; g.textBaseline = 'alphabetic'
    g.fillText(`${sheet.name || ''}  ·  ${pi + 1} / ${pages.length}`, PW / 2, PH - 28)
    return cv
  })
  return canvases
}
export function drawReportPages(sheets) { return sheets.flatMap((s) => drawSheetPages(s)) }

// ---------------------------------------------------------------- PDF (쪽마다 그림 한 장, A4 세로)
const b64ToBytes = (b64) => { const s = atob(b64); const a = new Uint8Array(s.length); for (let i = 0; i < s.length; i++) a[i] = s.charCodeAt(i); return a }
export function pagesToPdfBlob(canvases) {
  const enc = new TextEncoder(), parts = [], offs = []
  let len = 0
  const add = (x) => { const b = typeof x === 'string' ? enc.encode(x) : x; parts.push(b); len += b.length }
  const obj = (n, body) => { offs[n] = len; add(`${n} 0 obj\n`); for (const b of [].concat(body)) add(b); add('\nendobj\n') }
  const n = canvases.length, pw = 595.28, ph = 841.89
  // 개체 번호: 1 카탈로그 · 2 쪽 목록 · 3.. = (쪽 · 그림 · 내용) 3개씩
  add('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n')
  obj(1, '<< /Type /Catalog /Pages 2 0 R >>')
  obj(2, `<< /Type /Pages /Kids [${canvases.map((_, i) => `${3 + i * 3} 0 R`).join(' ')}] /Count ${n} >>`)
  canvases.forEach((cv, i) => {
    const pg = 3 + i * 3, im = pg + 1, ct = pg + 2
    const jpg = b64ToBytes(cv.toDataURL('image/jpeg', 0.92).split(',')[1])
    const content = `q ${pw} 0 0 ${ph} 0 0 cm /Im0 Do Q`
    obj(pg, `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pw} ${ph}] /Resources << /XObject << /Im0 ${im} 0 R >> >> /Contents ${ct} 0 R >>`)
    obj(im, [`<< /Type /XObject /Subtype /Image /Width ${cv.width} /Height ${cv.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpg.length} >>\nstream\n`, jpg, '\nendstream'])
    obj(ct, `<< /Length ${content.length} >>\nstream\n${content}\nendstream`)
  })
  const total = 3 + n * 3, xref = len
  add(`xref\n0 ${total}\n0000000000 65535 f \n`)
  for (let i = 1; i < total; i++) add(String(offs[i]).padStart(10, '0') + ' 00000 n \n')
  add(`trailer\n<< /Size ${total} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`)
  return new Blob(parts, { type: 'application/pdf' })
}
export function downloadPagesPdf(canvases, fileName) {
  const url = URL.createObjectURL(pagesToPdfBlob(canvases))
  const a = document.createElement('a'); a.href = url; a.download = fileName; document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 4000)
}
/** 인쇄: 쪽마다 A4 한 장 */
export function printPages(canvases, title = '보고서') {
  const w = window.open('', '_blank'); if (!w) return false
  const imgs = canvases.map((c) => `<img src="${c.toDataURL('image/png')}">`).join('')
  w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${title}</title><style>@page{size:A4;margin:0}html,body{margin:0}img{width:210mm;height:297mm;display:block;page-break-after:always}</style></head><body>${imgs}<script>window.onload=function(){setTimeout(function(){window.print()},300)}<\/script></body></html>`)
  w.document.close(); return true
}
