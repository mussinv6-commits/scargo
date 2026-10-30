// 26.10.29 컨테이너 인수증 (허브 인계 기록 → A4 인수증 양식)
//  - 웹(인수 내역)과 기사 앱(운송 이력)이 같은 파일을 쓴다 (두 프로젝트에 같은 내용으로 복사)
//  - 외부 라이브러리 없이: 캔버스에 A4 양식을 그리고 → 그 그림을 넣은 PDF 를 직접 만들어 내려받는다
//  - 서명 · 사진은 로그인이 필요한 주소라, 부르는 쪽이 loadImage(url) 로 이미지를 읽어 넘긴다
//
//  사용: const canvas = await drawReceipt(row, loadImage)
//        downloadPdf(canvas, receiptFileName(row))  /  printCanvas(canvas)

const W = 1240, H = 1754 // A4 · 150dpi
const M = 80 // 바깥 여백
const FONT = "'Malgun Gothic', '맑은 고딕', 'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif"
const NAVY = '#14213d', LINE = '#3d4863', SOFT = '#eef1f6', MUTED = '#5b6478'

const pad2 = (n) => String(n).padStart(2, '0')
export function toDate(v) {
  if (v == null || v === '') return null
  const d = typeof v === 'number' ? new Date(v < 1e12 ? v * 1000 : v) : new Date(v)
  return isNaN(d) ? null : d
}
export function fmtDateTime(v) {
  const d = toDate(v)
  return d ? `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}` : '-'
}
/** 문서 번호: HO-인계일자-인계번호 (예: HO-20261029-000005) */
export function receiptNo(r) {
  const d = toDate(r.completedAt) || new Date()
  return `HO-${d.getFullYear()}${pad2(d.getMonth() + 1)}${pad2(d.getDate())}-${String(r.handoverId ?? r.orderId ?? 0).padStart(6, '0')}`
}
export function receiptFileName(r) {
  // 한글 파일명은 일부 브라우저 · 휴대폰에서 'download' 로 바뀌어 영문으로
  return `Receipt_${String(r.containerNo || r.orderNo || r.orderId).replace(/[^A-Za-z0-9-]/g, '')}_${receiptNo(r)}.pdf`
}
export function photoList(r) {
  const p = r.photoUrls
  if (!p) return []
  if (Array.isArray(p)) return p
  try { const a = JSON.parse(p); return Array.isArray(a) ? a : [] } catch { return [] }
}

function text(ctx, s, x, y, { size = 22, weight = 400, color = '#111', align = 'left', max } = {}) {
  ctx.font = `${weight} ${size}px ${FONT}`
  ctx.fillStyle = color
  ctx.textAlign = align
  ctx.textBaseline = 'middle'
  let str = String(s ?? '-')
  // 칸보다 길면 글자를 조금씩 줄이고(최소 16px), 그래도 길면 끝을 … 로
  if (max) { let sz = size; while (sz > 16 && ctx.measureText(str).width > max) { sz -= 1; ctx.font = `${weight} ${sz}px ${FONT}` } }
  if (max) while (str.length > 1 && ctx.measureText(str).width > max) str = str.slice(0, -2) + '…'
  ctx.fillText(str, x, y)
}
function box(ctx, x, y, w, h, fill) {
  if (fill) { ctx.fillStyle = fill; ctx.fillRect(x, y, w, h) }
  ctx.strokeStyle = LINE; ctx.lineWidth = 1.5; ctx.strokeRect(x, y, w, h)
}
function fitImage(ctx, img, x, y, w, h) {
  if (!img) return false
  const r = Math.min(w / img.width, h / img.height)
  const iw = img.width * r, ih = img.height * r
  ctx.drawImage(img, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih)
  return true
}
function check(ctx, x, y, ok, label) {
  ctx.strokeStyle = LINE; ctx.lineWidth = 2; ctx.strokeRect(x, y - 13, 26, 26)
  if (ok) { ctx.strokeStyle = NAVY; ctx.lineWidth = 4; ctx.beginPath(); ctx.moveTo(x + 5, y); ctx.lineTo(x + 11, y + 7); ctx.lineTo(x + 22, y - 7); ctx.stroke() }
  text(ctx, label, x + 38, y, { size: 22, weight: ok ? 700 : 400, color: ok ? '#111' : MUTED })
}

/** 인수증 양식을 그린 캔버스 (A4 세로) */
export async function drawReceipt(r, loadImage) {
  const c = document.createElement('canvas'); c.width = W; c.height = H
  const ctx = c.getContext('2d')
  ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, W, H)
  const load = async (u) => { if (!u || !loadImage) return null; try { return await loadImage(u) } catch { return null } }
  const [sig, ...photos] = await Promise.all([load(r.signatureUrl), ...photoList(r).slice(0, 4).map(load)])

  // 머리
  let y = M
  text(ctx, 'S CARGO', M, y + 14, { size: 22, weight: 800, color: NAVY })
  text(ctx, `문서번호  ${receiptNo(r)}`, W - M, y + 14, { size: 18, color: MUTED, align: 'right' })
  y += 70
  text(ctx, '컨테이너 인수증', W / 2, y, { size: 52, weight: 800, color: '#111', align: 'center' })
  y += 42
  text(ctx, 'CONTAINER HANDOVER RECEIPT', W / 2, y, { size: 18, weight: 600, color: MUTED, align: 'center' })
  y += 34
  ctx.fillStyle = NAVY; ctx.fillRect(M, y, W - 2 * M, 4)
  y += 30

  // 1. 운송 정보 (2단 표)
  text(ctx, '1. 운송 정보', M, y, { size: 26, weight: 800, color: NAVY }); y += 26
  const rows = [
    ['오더 번호', r.orderNo, '컨테이너 번호', r.containerNo],
    ['씰 번호', r.sealNo || '-', '차량 번호', r.vehicleNo ? r.vehicleNo + (r.trailerNo ? ` (트레일러 ${r.trailerNo})` : '') : '-'],
    ['운송사', r.companyName || '-', '운송 기사', r.driverName ? r.driverName + (r.driverPhone ? `  ${r.driverPhone}` : '') : '-'],
    ['화주', r.shipperName || '-', '총중량', r.grossWeightKg ? Number(r.grossWeightKg).toLocaleString('ko-KR') + ' kg' : '-'],
    ['출발지', [r.originPort, r.originName].filter(Boolean).join(' · ') || '-', '도착지 (인수처)', r.hubName || '-'],
    ['출발 일시', fmtDateTime(r.departedAt), '도착 일시', fmtDateTime(r.arrivedAt)],
    ['인계 일시', fmtDateTime(r.completedAt), '운행 거리', r.distanceKm ? `${r.distanceKm} km` : '-'],
  ]
  const lw = 190, cw = (W - 2 * M) / 2, rh = 58
  for (const row of rows) {
    for (let k = 0; k < 2; k++) {
      const x = M + k * cw
      box(ctx, x, y, lw, rh, SOFT); box(ctx, x + lw, y, cw - lw, rh)
      text(ctx, row[k * 2], x + lw / 2, y + rh / 2, { size: 20, weight: 700, color: '#1d2538', align: 'center' })
      text(ctx, row[k * 2 + 1], x + lw + 16, y + rh / 2, { size: 21, max: cw - lw - 28 })
    }
    y += rh
  }
  y += 36

  // 2. 인계 확인
  text(ctx, '2. 인계 확인', M, y, { size: 26, weight: 800, color: NAVY }); y += 26
  box(ctx, M, y, W - 2 * M, 72)
  const cy = y + 36, gap = (W - 2 * M) / 3
  check(ctx, M + 30, cy, !!r.containerNoConfirmed, '컨테이너 번호 일치')
  check(ctx, M + 30 + gap, cy, !!r.sealOk, '씰 이상 없음')
  check(ctx, M + 30 + gap * 2, cy, !!r.damageFree, '외관 파손 없음')
  y += 72
  if (r.damageNoted || r.damageFree === false || r.sealOk === false) {
    box(ctx, M, y, W - 2 * M, 56, '#fff6f6')
    text(ctx, '특이사항', M + 20, y + 28, { size: 20, weight: 700, color: '#c62828' })
    const note = [r.sealOk === false ? '씰 이상' : '', r.damageFree === false ? '외관 파손' : '', r.damageNoted ? String(r.damageNoted) : ''].filter(Boolean).join(' · ')
    text(ctx, note || '확인 필요', M + 130, y + 28, { size: 20, max: W - 2 * M - 150 })
    y += 56
  }
  y += 36

  // 3. 사진
  text(ctx, '3. 인계 사진', M, y, { size: 26, weight: 800, color: NAVY }); y += 26
  const ph = 230
  box(ctx, M, y, W - 2 * M, ph)
  const got = photos.filter(Boolean)
  if (!got.length) text(ctx, '첨부된 사진이 없습니다', W / 2, y + ph / 2, { size: 20, color: MUTED, align: 'center' })
  else {
    const pw = (W - 2 * M - 20 * (4 + 1)) / 4
    got.forEach((img, i) => { const x = M + 20 + i * (pw + 20); box(ctx, x, y + 15, pw, ph - 30, SOFT); fitImage(ctx, img, x + 4, y + 19, pw - 8, ph - 38) })
  }
  y += ph + 44

  // 확인 문구
  const ok = r.containerNoConfirmed && r.sealOk && r.damageFree
  text(ctx, ok ? '위 컨테이너를 상기 내용과 같이 이상 없이 인수하였음을 확인합니다.' : '위 컨테이너를 상기 특이사항을 확인하고 인수하였음을 확인합니다.', W / 2, y, { size: 24, weight: 700, align: 'center' })
  y += 44
  text(ctx, fmtDateTime(r.completedAt).slice(0, 10).replace(/-/g, '. ') + '.', W / 2, y, { size: 22, color: '#1d2538', align: 'center' })
  y += 46

  // 서명란
  const sw = (W - 2 * M - 30) / 2, sh = 250
  for (const [k, who, name, img] of [[0, '인계자 (운송 기사)', r.driverName, null], [1, '인수자', r.receiverName, sig]]) {
    const x = M + k * (sw + 30)
    box(ctx, x, y, sw, 56, SOFT); text(ctx, who, x + sw / 2, y + 28, { size: 21, weight: 800, color: '#1d2538', align: 'center' })
    box(ctx, x, y + 56, sw, sh - 56)
    text(ctx, '성명', x + 24, y + 98, { size: 20, color: MUTED })
    text(ctx, name || '-', x + 90, y + 98, { size: 24, weight: 700, max: sw - 110 })
    text(ctx, '서명', x + 24, y + 160, { size: 20, color: MUTED })
    if (img) fitImage(ctx, img, x + 90, y + 120, sw - 120, sh - 130)
    else text(ctx, k === 0 ? '(앱 로그인 기사 · 전자 확인)' : '(서명 없음)', x + 90, y + 160, { size: 18, color: MUTED })
  }
  y += sh + 30

  // 바닥
  ctx.fillStyle = SOFT; ctx.fillRect(M, H - M - 46, W - 2 * M, 46)
  text(ctx, `S Cargo 운송관리 시스템 전자 발행 · 인계 기록 #${r.handoverId ?? '-'} · 오더 #${r.orderId ?? '-'}`, W / 2, H - M - 23, { size: 16, color: MUTED, align: 'center' })
  return c
}

// ---------------------------------------------------------------- PDF (그림 한 장을 A4 한 쪽에)
function b64ToBytes(b64) { const s = atob(b64); const a = new Uint8Array(s.length); for (let i = 0; i < s.length; i++) a[i] = s.charCodeAt(i); return a }
export function canvasToPdfBlob(canvas) {
  const jpg = b64ToBytes(canvas.toDataURL('image/jpeg', 0.92).split(',')[1])
  const enc = new TextEncoder(), parts = [], offs = []
  let len = 0
  const add = (x) => { const b = typeof x === 'string' ? enc.encode(x) : x; parts.push(b); len += b.length }
  const obj = (n, body) => { offs[n] = len; add(`${n} 0 obj\n`); for (const b of [].concat(body)) add(b); add('\nendobj\n') }
  const pw = 595.28, ph = 841.89
  const content = `q ${pw} 0 0 ${ph} 0 0 cm /Im0 Do Q`
  add('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n')
  obj(1, '<< /Type /Catalog /Pages 2 0 R >>')
  obj(2, '<< /Type /Pages /Kids [3 0 R] /Count 1 >>')
  obj(3, `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pw} ${ph}] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>`)
  obj(4, [`<< /Type /XObject /Subtype /Image /Width ${canvas.width} /Height ${canvas.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpg.length} >>\nstream\n`, jpg, '\nendstream'])
  obj(5, `<< /Length ${content.length} >>\nstream\n${content}\nendstream`)
  const xref = len
  add(`xref\n0 6\n0000000000 65535 f \n${[1, 2, 3, 4, 5].map((n) => String(offs[n]).padStart(10, '0') + ' 00000 n \n').join('')}`)
  add(`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`)
  return new Blob(parts, { type: 'application/pdf' })
}
export function downloadPdf(canvas, fileName) {
  const url = URL.createObjectURL(canvasToPdfBlob(canvas))
  const a = document.createElement('a'); a.href = url; a.download = fileName; document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 4000)
}
/** 인쇄: 인수증 그림만 A4 한 쪽으로 */
export function printCanvas(canvas, title = '인수증') {
  const w = window.open('', '_blank'); if (!w) return false
  w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${title}</title><style>@page{size:A4;margin:0}html,body{margin:0}img{width:210mm;height:297mm;display:block}</style></head><body><img src="${canvas.toDataURL('image/png')}" onload="setTimeout(function(){window.print()},200)"></body></html>`)
  w.document.close(); return true
}
