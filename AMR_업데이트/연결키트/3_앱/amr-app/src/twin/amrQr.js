// ─────────────────────────────────────────────────────────────
// 26.10.16 QR 코드 생성기 (외부 라이브러리 없이 · OTP 등록용)
//  - 바이트 모드 · 오류 정정 M · 버전 1~10 (최대 약 210자) — otpauth:// 주소에 충분
//  - qrMatrix(text) → 2차원 배열(true = 검은 칸), drawQr(canvas, text, px) → 캔버스에 그림
// ─────────────────────────────────────────────────────────────
const ECC_M = [0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26] // 블록당 오류 정정 바이트
const BLK_M = [0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5]          // 블록 수

function rawModules(v) {
  let r = (16 * v + 128) * v + 64
  if (v >= 2) { const n = Math.floor(v / 7) + 2; r -= (25 * n - 10) * n - 55; if (v >= 7) r -= 36 }
  return r
}
const dataCodewords = (v) => Math.floor(rawModules(v) / 8) - ECC_M[v] * BLK_M[v]

// GF(256) 곱셈 · 리드-솔로몬
function gmul(x, y) { let z = 0; for (let i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11d); z ^= ((y >>> i) & 1) * x } return z & 0xff }
function rsDivisor(deg) {
  const r = new Array(deg).fill(0); r[deg - 1] = 1; let root = 1
  for (let i = 0; i < deg; i++) { for (let j = 0; j < deg; j++) { r[j] = gmul(r[j], root); if (j + 1 < deg) r[j] ^= r[j + 1] } root = gmul(root, 2) }
  return r
}
function rsRemainder(data, div) {
  const r = div.map(() => 0)
  for (const b of data) { const f = b ^ r.shift(); r.push(0); div.forEach((c, i) => { r[i] ^= gmul(c, f) }) }
  return r
}

function alignPos(v, size) {
  if (v === 1) return []
  const n = Math.floor(v / 7) + 2, step = Math.ceil((v * 4 + 4) / (n * 2 - 2)) * 2, r = [6]
  for (let p = size - 7; r.length < n; p -= step) r.splice(1, 0, p)
  return r
}

export function qrMatrix(text) {
  const bytes = Array.from(new TextEncoder().encode(String(text)))
  let v = 1
  for (; v <= 10; v++) if (4 + (v < 10 ? 8 : 16) + bytes.length * 8 <= dataCodewords(v) * 8) break
  if (v > 10) throw new Error('QR 내용이 너무 깁니다')
  const cap = dataCodewords(v) * 8

  // 데이터 비트
  const bits = []
  const put = (val, len) => { for (let i = len - 1; i >= 0; i--) bits.push((val >>> i) & 1) }
  put(4, 4); put(bytes.length, v < 10 ? 8 : 16); bytes.forEach((b) => put(b, 8))
  put(0, Math.min(4, cap - bits.length)); put(0, (8 - (bits.length % 8)) % 8)
  for (let pad = 0xec; bits.length < cap; pad ^= 0xec ^ 0x11) put(pad, 8)
  const data = []; for (let i = 0; i < bits.length; i += 8) data.push(parseInt(bits.slice(i, i + 8).join(''), 2))

  // 블록 나누기 + 오류 정정 + 섞기
  const nb = BLK_M[v], ecc = ECC_M[v], raw = Math.floor(rawModules(v) / 8)
  const nShort = nb - (raw % nb), shortLen = Math.floor(raw / nb), div = rsDivisor(ecc)
  const blocks = []
  for (let i = 0, k = 0; i < nb; i++) {
    const d = data.slice(k, k + shortLen - ecc + (i < nShort ? 0 : 1)); k += d.length
    const b = d.concat(rsRemainder(d, div)); if (i < nShort) b.splice(shortLen - ecc, 0, 0); blocks.push(b)
  }
  const cw = []
  for (let i = 0; i < blocks[0].length; i++) blocks.forEach((b, j) => { if (i !== shortLen - ecc || j >= nShort) cw.push(b[i]) })

  // 기능 패턴
  const size = v * 4 + 17
  const m = Array.from({ length: size }, () => new Array(size).fill(false))
  const fn = Array.from({ length: size }, () => new Array(size).fill(false))
  const set = (x, y, d) => { m[y][x] = d; fn[y][x] = true }
  for (let i = 0; i < size; i++) { set(6, i, i % 2 === 0); set(i, 6, i % 2 === 0) }
  const finder = (cx, cy) => { for (let dy = -4; dy <= 4; dy++) for (let dx = -4; dx <= 4; dx++) { const x = cx + dx, y = cy + dy, d = Math.max(Math.abs(dx), Math.abs(dy)); if (x >= 0 && x < size && y >= 0 && y < size) set(x, y, d !== 2 && d !== 4) } }
  finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
  const ap = alignPos(v, size)
  ap.forEach((ay, i) => ap.forEach((ax, j) => {
    if ((i === 0 && j === 0) || (i === 0 && j === ap.length - 1) || (i === ap.length - 1 && j === 0)) return
    for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) set(ax + dx, ay + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1)
  }))
  const drawFormat = (mask) => {
    const d = (0 << 3) | mask; let r = d // 오류 정정 M = 00
    for (let i = 0; i < 10; i++) r = (r << 1) ^ ((r >>> 9) * 0x537)
    const b = ((d << 10) | r) ^ 0x5412, bit = (i) => ((b >>> i) & 1) === 1
    for (let i = 0; i <= 5; i++) set(8, i, bit(i))
    set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
    for (let i = 9; i < 15; i++) set(14 - i, 8, bit(i))
    for (let i = 0; i < 8; i++) set(size - 1 - i, 8, bit(i))
    for (let i = 8; i < 15; i++) set(8, size - 15 + i, bit(i))
    set(8, size - 8, true)
  }
  drawFormat(0)
  if (v >= 7) {
    let r = v; for (let i = 0; i < 12; i++) r = (r << 1) ^ ((r >>> 11) * 0x1f25)
    const b = (v << 12) | r
    for (let i = 0; i < 18; i++) { const d = ((b >>> i) & 1) === 1, a = size - 11 + (i % 3), c = Math.floor(i / 3); set(a, c, d); set(c, a, d) }
  }

  // 데이터 배치 (오른쪽 아래부터 지그재그)
  let i = 0
  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5
    for (let vert = 0; vert < size; vert++) for (let j = 0; j < 2; j++) {
      const x = right - j, up = ((right + 1) & 2) === 0, y = up ? size - 1 - vert : vert
      if (!fn[y][x] && i < cw.length * 8) { m[y][x] = ((cw[i >>> 3] >>> (7 - (i & 7))) & 1) === 1; i++ }
    }
  }

  // 마스크 8개 중 벌점이 가장 낮은 것
  const maskFn = [(x, y) => (x + y) % 2 === 0, (x, y) => y % 2 === 0, (x) => x % 3 === 0, (x, y) => (x + y) % 3 === 0,
    (x, y) => (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0, (x, y) => ((x * y) % 2) + ((x * y) % 3) === 0,
    (x, y) => (((x * y) % 2) + ((x * y) % 3)) % 2 === 0, (x, y) => (((x + y) % 2) + ((x * y) % 3)) % 2 === 0]
  const applyMask = (k) => { for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) if (!fn[y][x] && maskFn[k](x, y)) m[y][x] = !m[y][x] }
  const penalty = () => {
    let p = 0, dark = 0
    const line = (get) => { for (let a = 0; a < size; a++) { let run = 1; for (let b = 1; b <= size; b++) { if (b < size && get(a, b) === get(a, b - 1)) run++; else { if (run >= 5) p += run - 2; run = 1 } } } }
    line((a, b) => m[a][b]); line((a, b) => m[b][a])
    for (let y = 0; y < size - 1; y++) for (let x = 0; x < size - 1; x++) { const c = m[y][x]; if (c === m[y][x + 1] && c === m[y + 1][x] && c === m[y + 1][x + 1]) p += 3 }
    const pat = [1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0], pat2 = [0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1]
    const hit = (get, a, b) => [pat, pat2].some((q) => q.every((t, k) => (get(a, b + k) ? 1 : 0) === t))
    for (let a = 0; a < size; a++) for (let b = 0; b + 11 <= size; b++) { if (hit((r, c) => m[r][c], a, b)) p += 40; if (hit((r, c) => m[c][r], a, b)) p += 40 }
    m.forEach((row) => row.forEach((c) => { if (c) dark++ }))
    p += Math.floor(Math.abs(dark * 20 - size * size * 10) / (size * size)) * 10
    return p
  }
  let best = 0, bestP = Infinity
  for (let k = 0; k < 8; k++) { applyMask(k); drawFormat(k); const p = penalty(); if (p < bestP) { bestP = p; best = k } applyMask(k) }
  applyMask(best); drawFormat(best)
  return m
}

export function drawQr(canvas, text, px = 200) {
  const m = qrMatrix(text), n = m.length, q = 4, cell = Math.max(2, Math.floor(px / (n + q * 2)))
  const s = cell * (n + q * 2)
  canvas.width = s; canvas.height = s
  const g = canvas.getContext('2d')
  g.fillStyle = '#fff'; g.fillRect(0, 0, s, s); g.fillStyle = '#000'
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (m[y][x]) g.fillRect((x + q) * cell, (y + q) * cell, cell, cell)
  return canvas
}
