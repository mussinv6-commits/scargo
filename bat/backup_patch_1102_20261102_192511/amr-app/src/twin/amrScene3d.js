// ─────────────────────────────────────────────────────────────
// S카고 허브 3D 디지털 트윈 렌더러 (Three.js)
// 시뮬레이션(amrSim.js)의 평면도 좌표를 미터로 바꿔서 실사형 창고로 그린다.
//   1px = 0.05m  →  건물 약 42m x 32m
// ─────────────────────────────────────────────────────────────
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js'
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js'
import { CSS2DRenderer, CSS2DObject } from 'three/addons/renderers/CSS2DRenderer.js'
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js'
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js'
import { ShaderPass } from 'three/addons/postprocessing/ShaderPass.js'
import { AISLES, ROWS, NS, SX, DOCK_Y, HXL, HXR, STATION_Y, CHARGERS, BAY, GW, HUBS, ZONES, COLORS, status, lvCol, COLD_ROW, COLD_ROOM, LEVEL_H, FL_HOME, FIRE_REFUGE, QUAR, FIRE_ZONE_RECT, sideOn, ean13Bits, fmtPlate, INTER_NODES, INTER_R } from './amrSim.js'

const M = 0.05
const X = (x) => (x - 500) * M
const Z = (y) => (y - 320) * M
const WALL_L = X(80), WALL_R = X(920), WALL_N = Z(0), FLOOR_S = Z(640)
const WALL_H = 7.2
const LANE_OFF = 0.7

/* ── 캔버스 텍스처 ── */
function canvasTex(w, h, draw, repeat) {
  const c = document.createElement('canvas'); c.width = w; c.height = h
  const g = c.getContext('2d'); draw(g, w, h)
  const t = new THREE.CanvasTexture(c)
  t.colorSpace = THREE.SRGBColorSpace
  t.anisotropy = 8
  if (repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(repeat[0], repeat[1]) }
  return t
}
function noise(g, w, h, n, a, light) {
  for (let i = 0; i < n; i++) {
    g.fillStyle = light ? `rgba(255,255,255,${Math.random() * a})` : `rgba(0,0,0,${Math.random() * a})`
    const s = Math.random() * 2 + 0.5
    g.fillRect(Math.random() * w, Math.random() * h, s, s)
  }
}
const FONT = "'Pretendard','IBM Plex Sans KR','Apple SD Gothic Neo','Malgun Gothic',sans-serif"

const texConcrete = () => canvasTex(512, 512, (g, w, h) => {
  g.fillStyle = '#9aa1a7'; g.fillRect(0, 0, w, h)
  for (let i = 0; i < 26; i++) {
    const x = Math.random() * w, y = Math.random() * h, r = 40 + Math.random() * 120
    const gr = g.createRadialGradient(x, y, 0, x, y, r)
    const d = Math.random() < 0.5
    gr.addColorStop(0, d ? 'rgba(60,66,72,0.10)' : 'rgba(255,255,255,0.07)'); gr.addColorStop(1, 'rgba(0,0,0,0)')
    g.fillStyle = gr; g.fillRect(0, 0, w, h)
  }
  noise(g, w, h, 9000, 0.12, false); noise(g, w, h, 5000, 0.10, true)
  g.strokeStyle = 'rgba(40,46,52,0.55)'; g.lineWidth = 2; g.strokeRect(1, 1, w - 2, h - 2)
}, [8, 6])
const texAsphalt = () => canvasTex(256, 256, (g, w, h) => {
  g.fillStyle = '#2f3438'; g.fillRect(0, 0, w, h); noise(g, w, h, 6000, 0.25, true); noise(g, w, h, 6000, 0.3, false)
}, [10, 10])
const texPanel = () => canvasTex(256, 128, (g, w, h) => {
  g.fillStyle = '#c3cad1'; g.fillRect(0, 0, w, h)
  for (let x = 0; x < w; x += 16) {
    const gr = g.createLinearGradient(x, 0, x + 16, 0)
    gr.addColorStop(0, '#aab2ba'); gr.addColorStop(0.35, '#d7dde2'); gr.addColorStop(0.7, '#c1c8cf'); gr.addColorStop(1, '#9ea7b0')
    g.fillStyle = gr; g.fillRect(x, 0, 16, h)
  }
  noise(g, w, h, 1500, 0.06, false)
}, [24, 1])
const texDoor = () => canvasTex(128, 128, (g, w, h) => {
  g.fillStyle = '#d8dde2'; g.fillRect(0, 0, w, h)
  for (let y = 0; y < h; y += 8) { g.fillStyle = '#b4bcc4'; g.fillRect(0, y, w, 2) }
})
const texHatch = () => canvasTex(256, 64, (g, w, h) => {
  g.fillStyle = '#f2c230'; g.fillRect(0, 0, w, h); g.fillStyle = '#1b1b1b'
  for (let x = -h; x < w + h; x += 32) { g.beginPath(); g.moveTo(x, 0); g.lineTo(x + 16, 0); g.lineTo(x + 16 - h, h); g.lineTo(x - h, h); g.fill() }
}, [1, 1])
function texSign(text, sub, bg, fg) {
  return canvasTex(512, 192, (g, w, h) => {
    g.fillStyle = bg; g.fillRect(0, 0, w, h)
    g.fillStyle = 'rgba(0,0,0,0.18)'; g.fillRect(0, h - 10, w, 10)
    g.fillStyle = fg; g.textBaseline = 'middle'
    g.font = `800 92px ${FONT}`; g.fillText(text, 30, sub ? 78 : h / 2)
    if (sub) { g.font = `600 40px ${FONT}`; g.globalAlpha = 0.85; g.fillText(sub, 34, 150); g.globalAlpha = 1 }
  })
}
/* ── 택배 박스: 크라프트 골판지 + 브랜드 테이프 + 취급 스티커 + 운송장 ──
   면별 텍스처는 (크기 · 취급표시) 조합마다 한 번만 만들어 공유하고,
   화물마다 다른 운송장(번호 · 권역 · 무게 · 바코드)만 작은 라벨 판으로 붙인다. */
const SIZE_MM = { S: [720, 420, 600], M: [920, 620, 740], L: [1040, 860, 840] }
const HANDLE_STYLE = {
  파손주의: { bg: '#d62828', fg: '#ffffff', ko: '파손주의', en: 'FRAGILE', icon: 'glass' },
  취급주의: { bg: '#f5a524', fg: '#111111', ko: '취급주의', en: 'HANDLE WITH CARE', icon: 'hands' },
  중량물: { bg: '#ffd21f', fg: '#111111', ko: '중량물', en: 'HEAVY', icon: 'weight', stripe: true },
  냉장: { bg: '#1f6fd1', fg: '#ffffff', ko: '냉장 보관', en: 'KEEP COLD 2~8°C', icon: 'snow' },
}
function rr(g, x, y, w, h, r) { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath() }
// 면(mm 단위)을 캔버스에 그린다: 가로·세로 비율이 달라도 3D 면에서 글자가 찌그러지지 않게 스케일을 따로 준다.
function faceTex(px, py, wmm, hmm, draw) {
  return canvasTex(px, py, (g, w, h) => { g.save(); g.scale(w / wmm, h / hmm); draw(g, wmm, hmm); g.restore() })
}
function kraftBase(g, w, h, cold) {
  if (cold) {
    g.fillStyle = '#eef2f4'; g.fillRect(0, 0, w, h)
    for (let i = 0; i < 260; i++) { g.fillStyle = `rgba(150,165,175,${Math.random() * 0.12})`; g.beginPath(); g.arc(Math.random() * w, Math.random() * h, 3 + Math.random() * 7, 0, 7); g.fill() }
  } else {
    const gr = g.createLinearGradient(0, 0, w, h)
    gr.addColorStop(0, '#c9a06a'); gr.addColorStop(0.5, '#c29763'); gr.addColorStop(1, '#bb8f5b')
    g.fillStyle = gr; g.fillRect(0, 0, w, h)
    g.globalAlpha = 0.07; g.fillStyle = '#6b4a25'
    for (let x = 0; x < w; x += 9) g.fillRect(x, 0, 2.5, h) // 골판지 결
    g.globalAlpha = 1
    for (let i = 0; i < 600; i++) { g.fillStyle = `rgba(80,52,20,${Math.random() * 0.12})`; g.fillRect(Math.random() * w, Math.random() * h, 2 + Math.random() * 5, 2 + Math.random() * 4) }
    for (let i = 0; i < 6; i++) { g.fillStyle = 'rgba(70,45,18,0.06)'; g.beginPath(); g.ellipse(Math.random() * w, Math.random() * h, 30 + Math.random() * 90, 15 + Math.random() * 40, Math.random() * 3, 0, 7); g.fill() }
  }
  // 모서리 음영 (찌그러짐 · 손때)
  const e = Math.min(w, h) * 0.08
  ;[[0, 0, w, e, 0, 0, 0, e], [0, h - e, w, e, 0, h, 0, h - e], [0, 0, e, h, 0, 0, e, 0], [w - e, 0, e, h, w, 0, w - e, 0]].forEach(([x, y, ww, hh, x0, y0, x1, y1]) => {
    const gr = g.createLinearGradient(x0, y0, x1, y1); gr.addColorStop(0, 'rgba(40,25,10,0.28)'); gr.addColorStop(1, 'rgba(40,25,10,0)')
    g.fillStyle = gr; g.fillRect(x, y, ww, hh)
  })
}
function brandTape(g, x, y, w, h, vertical) {
  g.fillStyle = 'rgba(214,190,140,0.95)'; g.fillRect(x, y, w, h)
  g.fillStyle = 'rgba(255,255,255,0.18)'; vertical ? g.fillRect(x + w * 0.15, y, w * 0.12, h) : g.fillRect(x, y + h * 0.15, w, h * 0.12)
  g.save(); g.beginPath(); g.rect(x, y, w, h); g.clip()
  g.fillStyle = '#0b2540'; g.font = `800 ${Math.min(w, h) * 0.42}px ${FONT}`; g.textBaseline = 'middle'
  if (vertical) { g.translate(x + w / 2, y); g.rotate(Math.PI / 2); for (let t = 10; t < h; t += 230) g.fillText('S CARGO ▲', t, 0) }
  else for (let t = x + 10; t < x + w; t += 230) g.fillText('S CARGO ▲', t, y + h / 2)
  g.restore()
}
function upArrows(g, x, y, s) {
  // ISO 780 "이쪽 위로" 표시
  g.strokeStyle = '#1a140c'; g.lineWidth = s * 0.06; g.strokeRect(x, y, s, s * 1.15)
  g.fillStyle = '#1a140c'
  ;[0.3, 0.7].forEach((fx) => {
    const cx = x + s * fx
    g.fillRect(cx - s * 0.045, y + s * 0.42, s * 0.09, s * 0.48)
    g.beginPath(); g.moveTo(cx, y + s * 0.12); g.lineTo(cx + s * 0.15, y + s * 0.45); g.lineTo(cx - s * 0.15, y + s * 0.45); g.closePath(); g.fill()
  })
  g.fillRect(x + s * 0.15, y + s * 0.98, s * 0.7, s * 0.07)
}
function sticker(g, x, y, s, st) {
  // 취급 스티커 (정사각형, s mm)
  g.save()
  g.shadowColor = 'rgba(0,0,0,0.25)'; g.shadowBlur = s * 0.03
  rr(g, x, y, s, s, s * 0.06); g.fillStyle = st.bg; g.fill(); g.shadowBlur = 0
  if (st.stripe) {
    g.save(); rr(g, x, y, s, s, s * 0.06); g.clip(); g.fillStyle = '#111'
    for (let k = -s; k < s * 2; k += s * 0.16) { g.beginPath(); g.moveTo(x + k, y); g.lineTo(x + k + s * 0.08, y); g.lineTo(x + k + s * 0.08 - s, y + s); g.lineTo(x + k - s, y + s); g.fill() }
    g.fillStyle = st.bg; g.fillRect(x + s * 0.09, y + s * 0.09, s * 0.82, s * 0.82); g.restore()
  }
  g.fillStyle = st.fg; g.strokeStyle = st.fg
  const cx = x + s / 2, iy = y + s * 0.34, r = s * 0.2
  g.lineWidth = s * 0.035; g.lineCap = 'round'; g.lineJoin = 'round'
  if (st.icon === 'glass') {
    g.beginPath(); g.moveTo(cx - r * 0.75, iy - r); g.quadraticCurveTo(cx - r * 0.8, iy + r * 0.25, cx, iy + r * 0.35); g.quadraticCurveTo(cx + r * 0.8, iy + r * 0.25, cx + r * 0.75, iy - r); g.closePath(); g.fill()
    g.fillRect(cx - s * 0.015, iy + r * 0.3, s * 0.03, r * 0.65); g.fillRect(cx - r * 0.45, iy + r * 0.9, r * 0.9, s * 0.03)
    g.strokeStyle = st.bg; g.lineWidth = s * 0.025; g.beginPath(); g.moveTo(cx - r * 0.1, iy - r); g.lineTo(cx + r * 0.15, iy - r * 0.55); g.lineTo(cx - r * 0.12, iy - r * 0.2); g.lineTo(cx + r * 0.1, iy + r * 0.1); g.stroke()
  } else if (st.icon === 'hands') {
    // 박스를 받쳐 든 두 손
    g.strokeRect(cx - r * 0.55, iy - r * 0.9, r * 1.1, r * 0.95)
    ;[-1, 1].forEach((d) => { g.beginPath(); g.moveTo(cx + d * r * 1.05, iy + r * 0.75); g.quadraticCurveTo(cx + d * r * 0.95, iy + r * 0.15, cx + d * r * 0.5, iy + r * 0.1); g.lineTo(cx + d * r * 0.05, iy + r * 0.12); g.stroke() })
  } else if (st.icon === 'weight') {
    g.beginPath(); g.moveTo(cx - r * 0.45, iy - r * 0.55); g.lineTo(cx + r * 0.45, iy - r * 0.55); g.lineTo(cx + r * 0.85, iy + r * 0.8); g.lineTo(cx - r * 0.85, iy + r * 0.8); g.closePath(); g.fill()
    g.beginPath(); g.arc(cx, iy - r * 0.75, r * 0.28, 0, 7); g.stroke()
    g.fillStyle = st.bg; g.font = `900 ${s * 0.11}px ${FONT}`; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText('kg', cx, iy + r * 0.25)
  } else if (st.icon === 'snow') {
    for (let k = 0; k < 3; k++) { const an = (k * Math.PI) / 3; g.beginPath(); g.moveTo(cx - Math.cos(an) * r, iy - Math.sin(an) * r); g.lineTo(cx + Math.cos(an) * r, iy + Math.sin(an) * r); g.stroke() }
    for (let k = 0; k < 6; k++) { const an = (k * Math.PI) / 3, ex = cx + Math.cos(an) * r * 0.65, ey = iy + Math.sin(an) * r * 0.65; g.beginPath(); g.moveTo(ex + Math.cos(an + 0.9) * r * 0.25, ey + Math.sin(an + 0.9) * r * 0.25); g.lineTo(ex, ey); g.lineTo(ex + Math.cos(an - 0.9) * r * 0.25, ey + Math.sin(an - 0.9) * r * 0.25); g.stroke() }
  }
  g.fillStyle = st.fg; g.textAlign = 'center'; g.textBaseline = 'middle'
  g.font = `900 ${s * (st.ko.length > 3 ? 0.17 : 0.2)}px ${FONT}`; g.fillText(st.ko, cx, y + s * 0.72)
  g.font = `800 ${s * (st.en.length > 8 ? 0.075 : 0.11)}px ${FONT}`; g.fillText(st.en, cx, y + s * 0.88)
  g.restore()
}
// 여러 지오메트리를 하나로 합친다 (위치 · 법선 · UV만)
function mergeGeos(list) {
  const P = [], N = [], U = []
  list.forEach((g0) => {
    const g = g0.index ? g0.toNonIndexed() : g0
    P.push(...g.attributes.position.array); N.push(...g.attributes.normal.array); U.push(...g.attributes.uv.array)
    g0.dispose(); if (g !== g0) g.dispose()
  })
  const out = new THREE.BufferGeometry()
  out.setAttribute('position', new THREE.Float32BufferAttribute(P, 3))
  out.setAttribute('normal', new THREE.Float32BufferAttribute(N, 3))
  out.setAttribute('uv', new THREE.Float32BufferAttribute(U, 2))
  return out
}
// BoxGeometry 면 그룹(+X,-X,윗,밑,+Z,-Z)을 같은 재질끼리 묶어 3그룹으로: [짧은면, 윗·밑면, 긴면]
function compactBox(geo) {
  const g = geo.groups
  geo.clearGroups()
  geo.addGroup(g[0].start, g[0].count + g[1].count, 0)
  geo.addGroup(g[2].start, g[2].count + g[3].count, 1)
  geo.addGroup(g[4].start, g[4].count + g[5].count, 2)
  return geo
}
const cargoMatCache = new Map()
function cargoMaterials(size, handle) {
  // BoxGeometry 면 순서: +X, -X, +Y(윗면), -Y, +Z(앞), -Z(뒤)
  const key = size + '|' + handle
  if (cargoMatCache.has(key)) return cargoMatCache.get(key)
  const cold = handle === '냉장', st = HANDLE_STYLE[handle]
  const [W, Hh, D] = SIZE_MM[size]
  const tapeW = 60
  const endFace = faceTex(256, 256, D, Hh, (g, w, h) => {
    // 짧은 면(±X): 윗면 테이프가 내려와 붙고, 큰 취급 스티커
    kraftBase(g, w, h, cold)
    if (cold) { g.fillStyle = '#1f6fd1'; g.fillRect(0, h * 0.08, w, h * 0.09) }
    else brandTape(g, w / 2 - tapeW / 2, 0, tapeW, Math.min(110, h * 0.3), true)
    const s = Math.min(w * 0.55, h * 0.58)
    if (st) sticker(g, w * 0.5 - s / 2, h * 0.36, s, st)
    upArrows(g, w * 0.08, h * 0.12, Math.min(80, h * 0.2))
  })
  const longFace = faceTex(384, 256, W, Hh, (g, w, h) => {
    // 긴 면(±Z): 회사 인쇄 · 이쪽 위로 · 스티커 (운송장은 화물별 라벨로 따로 붙음)
    kraftBase(g, w, h, cold)
    if (cold) {
      g.fillStyle = '#1f6fd1'; g.fillRect(0, h * 0.08, w, h * 0.09)
      g.fillStyle = '#ffffff'; g.font = `800 ${h * 0.06}px ${FONT}`; g.textBaseline = 'middle'; g.fillText('냉장 COLD CHAIN  2~8°C', w * 0.04, h * 0.125)
    }
    g.fillStyle = cold ? '#1f6fd1' : 'rgba(20,30,50,0.85)'; g.font = `900 ${h * 0.1}px ${FONT}`; g.textBaseline = 'alphabetic'
    g.fillText('S CARGO', w * 0.05, h * 0.92)
    g.font = `700 ${h * 0.045}px ${FONT}`; g.fillText('LOGISTICS · 허브', w * 0.05, h * 0.97)
    upArrows(g, w * 0.82, h * 0.1, Math.min(90, h * 0.2))
    if (st) sticker(g, w * 0.04, h * 0.2, Math.min(w * 0.3, h * 0.5), st)
  })
  const topFace = faceTex(384, 320, W, D, (g, w, h) => {
    kraftBase(g, w, h, cold)
    if (!cold) {
      g.fillStyle = 'rgba(60,38,14,0.55)'; g.fillRect(0, h / 2 - 1.5, w, 3) // 날개 이음선
      brandTape(g, 0, h / 2 - tapeW / 2, w, tapeW, false)
    } else { g.strokeStyle = 'rgba(31,111,209,0.6)'; g.lineWidth = 8; g.strokeRect(20, 20, w - 40, h - 40) }
    if (st) sticker(g, w * 0.66, h * 0.05, Math.min(w * 0.3, h * 0.4), st)
  })
  const rough = cold ? 0.55 : 0.88
  const mk = (t) => new THREE.MeshStandardMaterial({ map: t, roughness: rough, metalness: 0, envMapIntensity: 0.55 })
  const mEnd = mk(endFace), mLong = mk(longFace)
  const mats = [mEnd, mk(topFace), mLong] // compactBox 그룹 순서
  cargoMatCache.set(key, mats)
  return mats
}
// 화물별 운송장 라벨 (권역 색 띠 · 권역명 · 송장번호 · 무게 · 바코드)
function waybillTex(c) {
  const h = HUBS[c.hub]
  return canvasTex(256, 168, (g, w, hh) => {
    g.fillStyle = '#fbfbf7'; g.fillRect(0, 0, w, hh)
    g.fillStyle = h.col; g.fillRect(0, 0, w, 46)
    g.fillStyle = '#0b0f14'; g.font = `900 34px ${FONT}`; g.textBaseline = 'middle'; g.fillText(h.name, 10, 25)
    g.font = `800 15px ${FONT}`; g.textAlign = 'right'; g.fillText('D' + (c.hub + 4) + ' →  ' + h.name + ' HUB', w - 8, 25); g.textAlign = 'left'
    g.font = `800 21px ${FONT}`; g.fillText(c.id, 10, 66)
    g.font = `600 14px ${FONT}`; g.fillStyle = '#333'
    g.fillText(c.w + 'kg · ' + c.size + ' · ' + c.handle, 10, 88)
    // 상품 바코드 EAN-13 (바코드 폴더와 같은 번호 체계 · 95칸을 2px씩)
    g.fillStyle = '#0b0f14'
    const code = c.ean || '8802610141003'
    ean13Bits(code).forEach((b, i) => { if (b) g.fillRect(14 + i * 2, 98, 2, (i < 3 || (i >= 45 && i < 50) || i >= 92) ? 50 : 44) })
    g.font = `600 13px ${FONT}`; g.fillText(code[0] + '  ' + code.slice(1, 7) + '   ' + code.slice(7), 12, 162)
    g.font = `500 10px ${FONT}`; g.textAlign = 'right'; g.fillText('S CARGO', w - 8, 162); g.textAlign = 'left'
    g.strokeStyle = 'rgba(0,0,0,0.15)'; g.strokeRect(0.5, 0.5, w - 1, hh - 1)
  })
}
/* ── 공용 재질 ── */
function makeMaterials(tex) {
  return {
    floor: new THREE.MeshStandardMaterial({ map: tex.concrete, color: 0xb4b9be, roughness: 0.4, metalness: 0.0, envMapIntensity: 0.7 }),
    asphalt: new THREE.MeshStandardMaterial({ map: tex.asphalt, roughness: 0.95 }),
    ground: new THREE.MeshStandardMaterial({ color: 0x14181c, roughness: 1 }),
    wall: new THREE.MeshStandardMaterial({ map: tex.panel, roughness: 0.55, metalness: 0.35 }),
    steel: new THREE.MeshStandardMaterial({ color: 0x3b434c, roughness: 0.5, metalness: 0.7 }),
    upright: new THREE.MeshStandardMaterial({ color: 0x1f56a8, roughness: 0.45, metalness: 0.55 }),
    beam: new THREE.MeshStandardMaterial({ color: 0xe8701a, roughness: 0.45, metalness: 0.45 }),
    wood: new THREE.MeshStandardMaterial({ color: 0xb08a5a, roughness: 0.95 }),
    woodDark: new THREE.MeshStandardMaterial({ color: 0x8c6a42, roughness: 0.95 }),
    cardboard: new THREE.MeshStandardMaterial({ color: 0xc39864, roughness: 0.92 }),
    wrap: new THREE.MeshStandardMaterial({ color: 0xdfe8ef, roughness: 0.18, metalness: 0.05, transparent: true, opacity: 0.3, depthWrite: false }),
    yellow: new THREE.MeshStandardMaterial({ color: 0xf2c230, roughness: 0.6 }),
    white: new THREE.MeshStandardMaterial({ color: 0xd9dde1, roughness: 0.6 }),
    laneFill: new THREE.MeshStandardMaterial({ color: 0x7d858c, roughness: 0.55, transparent: true, opacity: 0.55, depthWrite: false }),
    hatch: new THREE.MeshStandardMaterial({ map: tex.hatch, roughness: 0.6 }),
    door: new THREE.MeshStandardMaterial({ map: tex.door, roughness: 0.5, metalness: 0.4 }),
    rubber: new THREE.MeshStandardMaterial({ color: 0x15171a, roughness: 0.9 }),
    chassis: new THREE.MeshStandardMaterial({ color: 0x2a3036, roughness: 0.35, metalness: 0.65 }),
    deck: new THREE.MeshStandardMaterial({ color: 0xa7b0b9, roughness: 0.4, metalness: 0.7 }),
    glass: new THREE.MeshStandardMaterial({ color: 0x0c1218, roughness: 0.08, metalness: 0.9 }),
    truckBody: new THREE.MeshStandardMaterial({ color: 0xe9edf0, roughness: 0.45, metalness: 0.25 }),
    truckCab: new THREE.MeshStandardMaterial({ color: 0x24456b, roughness: 0.3, metalness: 0.6 }),
    courierCab: new THREE.MeshStandardMaterial({ color: 0xf4f6f8, roughness: 0.3, metalness: 0.4 }),
    tire: new THREE.MeshStandardMaterial({ color: 0x111316, roughness: 0.85 }),
    cabinet: new THREE.MeshStandardMaterial({ color: 0xc9312c, roughness: 0.4, metalness: 0.5 }),
    lampOn: new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xfff2dc, emissiveIntensity: 2.2 }),
  }
}

export function createAmrScene(container, sim, opts = {}) {
  const onPick = opts.onPick || (() => {})
  const S = sim.S

  /* ── 렌더러 · 카메라 ── */
  const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1))
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 0.92
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  // 26.10.28 시계: 실제 현재 시각 (+ 낮/밤 표시) — 3D 화면 왼쪽 위
  const clockEl = document.createElement('div')
  clockEl.className = 'twin-clock'
  clockEl.style.cssText = 'position:absolute;left:12px;top:10px;z-index:6;pointer-events:none;font:600 13px/1.25 ui-monospace,Consolas,"Malgun Gothic",monospace;padding:6px 10px;border-radius:8px;background:rgba(10,16,24,.62);color:#e8eef6;border:1px solid rgba(255,255,255,.14);backdrop-filter:blur(4px);text-align:left;white-space:nowrap'
  const clockTick = () => { const d = new Date(), p = (n) => String(n).padStart(2, '0'), wd = '일월화수목금토'[d.getDay()]
    const day = clockEl.dataset.theme === 'light'
    clockEl.style.background = day ? 'rgba(255,255,255,.75)' : 'rgba(10,16,24,.62)'; clockEl.style.color = day ? '#1c2733' : '#e8eef6'; clockEl.style.borderColor = day ? 'rgba(0,0,0,.14)' : 'rgba(255,255,255,.14)'
    clockEl.innerHTML = '<div style="font-size:17px;letter-spacing:.5px">' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()) + '</div><div style="font-size:11px;opacity:.75;font-weight:500">' + d.getFullYear() + '.' + p(d.getMonth() + 1) + '.' + p(d.getDate()) + ' (' + wd + ')</div>' }
  clockTick(); const clockTimer = setInterval(clockTick, 1000)
  renderer.domElement.className = 'twin-gl'
  container.appendChild(renderer.domElement)
  try { if (getComputedStyle(container).position === 'static') container.style.position = 'relative' } catch (e) { /* 무시 */ }
  container.appendChild(clockEl)

  const labelRenderer = new CSS2DRenderer()
  labelRenderer.domElement.className = 'twin-labels'
  container.appendChild(labelRenderer.domElement)

  const scene = new THREE.Scene()
  scene.background = new THREE.Color(0x0b1016)
  scene.fog = new THREE.Fog(0x0b1016, 70, 150)
  const pmrem = new THREE.PMREMGenerator(renderer)
  const envTex = pmrem.fromScene(new RoomEnvironment(renderer), 0.04).texture
  scene.environment = envTex

  const camera = new THREE.PerspectiveCamera(38, 1, 0.1, 400)
  const HOME_POS = new THREE.Vector3(9, 33, 38), HOME_TGT = new THREE.Vector3(-1, 0, 1)
  camera.position.copy(HOME_POS)
  const controls = new OrbitControls(camera, renderer.domElement)
  controls.target.copy(HOME_TGT)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  // 26.10.15 지도 앱처럼: 한 손가락 · 왼쪽 드래그 = 이동, 두 손가락 · 오른쪽 드래그 = 회전, 핀치 · 휠 = 확대
  controls.mouseButtons = { LEFT: THREE.MOUSE.PAN, MIDDLE: THREE.MOUSE.DOLLY, RIGHT: THREE.MOUSE.ROTATE }
  controls.touches = { ONE: THREE.TOUCH.PAN, TWO: THREE.TOUCH.DOLLY_ROTATE }
  controls.screenSpacePanning = false // 바닥을 따라 미끄러지듯 이동
  controls.zoomToCursor = true
  // 각도 제한: 기울기 30~65°, 좌우 회전은 기본 방향 ±45° (방향을 잃지 않게)
  const HOME_AZ = Math.atan2(HOME_POS.x - HOME_TGT.x, HOME_POS.z - HOME_TGT.z)
  controls.minPolarAngle = Math.PI / 6; controls.maxPolarAngle = Math.PI * 0.36
  controls.minAzimuthAngle = HOME_AZ - Math.PI / 4; controls.maxAzimuthAngle = HOME_AZ + Math.PI / 4
  controls.minDistance = 6
  controls.maxDistance = 80
  // 이동 범위: 창고 + 바깥 마당 + 입구 · 출구 게이트까지 (그 밖 빈 바닥으로는 못 나감)
  const SITE = { x0: -43, x1: 39, z0: -21, z1: 21 }
  let viewMode = 'auto'
  controls.addEventListener('start', () => { if (viewMode !== 'free') { viewMode = 'free'; opts.onViewChange && opts.onViewChange(viewMode) } })

  // 2D 모드: 위에서 똑바로 내려다보는 평면 지도 (회전 없음 · 이동/확대만 · 빛 효과 없이 가볍게)
  const cam2 = new THREE.OrthographicCamera(-20, 20, 20, -20, 0.1, 200)
  cam2.up.set(0, 0, -1); cam2.position.set(0, 90, 0); cam2.lookAt(0, 0, 0)
  const controls2 = new OrbitControls(cam2, renderer.domElement)
  controls2.enableRotate = false; controls2.screenSpacePanning = true; controls2.enableDamping = true; controls2.dampingFactor = 0.1
  controls2.mouseButtons = { LEFT: THREE.MOUSE.PAN, MIDDLE: THREE.MOUSE.DOLLY, RIGHT: THREE.MOUSE.PAN }
  controls2.touches = { ONE: THREE.TOUCH.PAN, TWO: THREE.TOUCH.DOLLY_PAN }
  controls2.zoomToCursor = true
  controls2.minZoom = 0.5; controls2.maxZoom = 6; controls2.enabled = false
  controls2.addEventListener('start', () => { if (viewMode !== 'free') { viewMode = 'free'; opts.onViewChange && opts.onViewChange(viewMode) } })
  let is2D = false
  const activeCam = () => (is2D ? cam2 : camera)

  // 26.10.28 화질: 후처리 화면에도 4배 안티앨리어싱(MSAA)을 적용해 랙 · 선반 모서리가 계단처럼 보이지 않게 한다
  const composer = new EffectComposer(renderer, new THREE.WebGLRenderTarget(1, 1, { type: THREE.HalfFloatType, samples: 4 }))
  composer.addPass(new RenderPass(scene, camera))
  const bloom = new UnrealBloomPass(new THREE.Vector2(256, 256), 0.45, 0.5, 1.3)
  composer.addPass(bloom)
  composer.addPass(new OutputPass())
  // 26.10.28 색 보정: 어두운 곳은 차가운 청록, 밝은 곳은 따뜻하게(영화 같은 색 분리) + 약한 대비 + 가장자리 어둡게 + 아주 약한 필름 입자
  const GradeShader = {
    uniforms: { tDiffuse: { value: null }, uTime: { value: 0 }, uVig: { value: 0.30 }, uGrain: { value: 0.018 } },
    vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }',
    fragmentShader: `uniform sampler2D tDiffuse; uniform float uTime, uVig, uGrain; varying vec2 vUv;
      float h(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233)) + uTime) * 43758.5453); }
      void main(){
        vec3 c = texture2D(tDiffuse, vUv).rgb;
        float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
        c = mix(c, c * c * (3.0 - 2.0 * c), 0.22);                       // 부드러운 S 곡선 대비
        c += mix(vec3(-0.010, 0.006, 0.020), vec3(0.020, 0.010, -0.014), smoothstep(0.15, 0.85, l)) * 0.9; // 그림자 청록 · 하이라이트 따뜻
        c = mix(vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), c, 1.08);   // 채도 소폭 상승
        vec2 q = vUv - 0.5; c *= 1.0 - uVig * smoothstep(0.30, 0.95, length(q * vec2(1.15, 1.0)));
        c += (h(vUv * 1024.0) - 0.5) * uGrain;
        gl_FragColor = vec4(c, 1.0);
      }`,
  }
  const grade = new ShaderPass(GradeShader)
  composer.addPass(grade)

  /* ── 조명 ── */
  // 실내 조명: 천장 고천장등(따뜻한 백색) 느낌의 키 라이트 + 약한 하늘빛
  scene.add(new THREE.HemisphereLight(0xdfe7f0, 0x2a2620, 0.32))
  const sun = new THREE.DirectionalLight(0xffe9cf, 1.15)
  sun.position.set(16, 34, 18)
  sun.castShadow = true
  sun.shadow.mapSize.set(2048, 2048)
  Object.assign(sun.shadow.camera, { left: -32, right: 32, top: 26, bottom: -26, near: 1, far: 90 })
  sun.shadow.bias = -0.0004
  sun.shadow.normalBias = 0.03
  sun.shadow.camera.updateProjectionMatrix()
  scene.add(sun)
  const fill = new THREE.DirectionalLight(0xbcd3ff, 0.22)
  fill.position.set(-20, 18, -10)
  scene.add(fill)

  const tex = { concrete: texConcrete(), asphalt: texAsphalt(), panel: texPanel(), door: texDoor(), hatch: texHatch() }
  const MAT = makeMaterials(tex)
  const disposables = []
  const box = (w, h, d) => { const g = new THREE.BoxGeometry(w, h, d); disposables.push(g); return g }
  const mesh = (geo, mat, x, y, z, shadow = true) => { const m = new THREE.Mesh(geo, mat); m.position.set(x, y, z); m.castShadow = shadow; m.receiveShadow = true; return m }
  const world = new THREE.Group(); scene.add(world)
  const fx = {} // 저사양 모드에서 끄는 효과들

  /* ── 바닥 · 야드 ── */
  const ground = mesh(new THREE.PlaneGeometry(300, 300), MAT.ground, 0, -0.02, 0, false); ground.rotation.x = -Math.PI / 2; world.add(ground)
  ;[[-34, 26], [34, 26]].forEach(([cx, wdt]) => {
    const p = mesh(new THREE.PlaneGeometry(wdt, 44), MAT.asphalt, cx, -0.01, 0, false); p.rotation.x = -Math.PI / 2; world.add(p)
  })
  /* ── 26.10.28 주변 환경: 하늘 · 야적장 · 울타리 · 가로등 · 먼 도시 (대기 차량 영역은 건드리지 않음) ── */
  {
    const env = new THREE.Group(); env.name = 'env'; world.add(env)
    const cvs = (w, h, fn) => { const c = document.createElement('canvas'); c.width = w; c.height = h; fn(c.getContext('2d'), w, h); const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4; disposables.push(t); return t }
    let rs = 11; const rnd = () => { rs = (rs * 16807) % 2147483647; return rs / 2147483647 }
    // 하늘 돔 (위: 짙은 남색 → 지평선: 연한 보랏빛)
    const skyTex = cvs(8, 256, (g, w, h) => { const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#060b18'); gr.addColorStop(0.55, '#14233d'); gr.addColorStop(0.85, '#2c3d5e'); gr.addColorStop(1, '#46587a'); g.fillStyle = gr; g.fillRect(0, 0, w, h) })
    const skyDay = cvs(8, 256, (g, w, h) => { const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#3f7fc4'); gr.addColorStop(0.55, '#7db3e3'); gr.addColorStop(0.88, '#cfe3f2'); gr.addColorStop(1, '#e6eef3'); g.fillStyle = gr; g.fillRect(0, 0, w, h) })
    const sky = new THREE.Mesh(new THREE.SphereGeometry(190, 24, 16, 0, Math.PI * 2, 0, Math.PI / 2), new THREE.MeshBasicMaterial({ map: skyTex, side: THREE.BackSide, fog: false, depthWrite: false }))
    sky.position.y = -2; sky.renderOrder = -10; env.add(sky); disposables.push(sky.geometry, sky.material)
    // 별
    { const n = 160, pos = new Float32Array(n * 3); for (let i = 0; i < n; i++) { const a = rnd() * Math.PI * 2, e = 0.25 + rnd() * 1.1, r = 180; pos[i * 3] = Math.cos(a) * Math.cos(e) * r; pos[i * 3 + 1] = Math.sin(e) * r; pos[i * 3 + 2] = Math.sin(a) * Math.cos(e) * r }
      const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(pos, 3)); const pm = new THREE.PointsMaterial({ color: 0xcfe0ff, size: 1.3, sizeAttenuation: false, fog: false, transparent: true, opacity: 0.8, depthWrite: false }); const pts = new THREE.Points(g, pm); pts.renderOrder = -9; env.add(pts); env.userData.stars = pts; disposables.push(g, pm) }
    // 지면: 야적장 콘크리트 + 바깥 도로/잔디
    const concT = cvs(256, 256, (g, w, h) => { g.fillStyle = '#4b5159'; g.fillRect(0, 0, w, h); for (let i = 0; i < 1400; i++) { const v = 70 + rnd() * 40; g.fillStyle = `rgba(${v},${v + 3},${v + 8},0.35)`; g.fillRect(rnd() * w, rnd() * h, 2, 2) } g.strokeStyle = 'rgba(20,24,30,0.5)'; g.lineWidth = 2; g.strokeRect(0, 0, w, h) })
    concT.wrapS = concT.wrapT = THREE.RepeatWrapping; concT.repeat.set(14, 14)
    const apron = new THREE.Mesh(new THREE.PlaneGeometry(130, 90), new THREE.MeshStandardMaterial({ map: concT, roughness: 0.92 })); apron.rotation.x = -Math.PI / 2; apron.position.set(0, -0.015, 0); apron.receiveShadow = true; env.add(apron); disposables.push(apron.geometry, apron.material)
    const grassT = cvs(128, 128, (g, w, h) => { g.fillStyle = '#18251d'; g.fillRect(0, 0, w, h); for (let i = 0; i < 900; i++) { g.fillStyle = `rgba(${30 + rnd() * 30},${55 + rnd() * 40},${35 + rnd() * 20},0.5)`; g.fillRect(rnd() * w, rnd() * h, 2, 3) } })
    grassT.wrapS = grassT.wrapT = THREE.RepeatWrapping; grassT.repeat.set(40, 40)
    const grass = new THREE.Mesh(new THREE.PlaneGeometry(320, 320), new THREE.MeshStandardMaterial({ map: grassT, roughness: 1 })); grass.rotation.x = -Math.PI / 2; grass.position.y = -0.03; env.add(grass); disposables.push(grass.geometry, grass.material)
    // 바깥 도로 (남쪽) + 점선
    const roadT = cvs(512, 128, (g, w, h) => { g.fillStyle = '#23272d'; g.fillRect(0, 0, w, h); for (let i = 0; i < 1500; i++) { const v = 40 + rnd() * 30; g.fillStyle = `rgba(${v},${v},${v + 4},0.4)`; g.fillRect(rnd() * w, rnd() * h, 2, 2) } g.fillStyle = '#d8c25a'; for (let x = 0; x < w; x += 64) g.fillRect(x + 8, h / 2 - 2, 36, 4); g.fillStyle = '#c9ced6'; g.fillRect(0, 6, w, 3); g.fillRect(0, h - 9, w, 3) })
    roadT.wrapS = THREE.RepeatWrapping; roadT.repeat.set(8, 1)
    const road = new THREE.Mesh(new THREE.PlaneGeometry(320, 12), new THREE.MeshStandardMaterial({ map: roadT, roughness: 0.9 })); road.rotation.x = -Math.PI / 2; road.position.set(0, -0.01, 40); road.receiveShadow = true; env.add(road); disposables.push(road.geometry, road.material)
    // 울타리 (야적장 둘레, 입구 쪽 비움)
    const fenceMat = new THREE.MeshStandardMaterial({ color: 0x8a94a0, metalness: 0.6, roughness: 0.5 })
    const postG = box(0.14, 2.6, 0.14), railG = box(1, 0.08, 0.06)
    const fence = (x0, z0, x1, z1) => { const L = Math.hypot(x1 - x0, z1 - z0), n = Math.max(1, Math.round(L / 3)), ang = Math.atan2(z1 - z0, x1 - x0)
      for (let i = 0; i <= n; i++) { const t = i / n; env.add(mesh(postG, fenceMat, x0 + (x1 - x0) * t, 1.3, z0 + (z1 - z0) * t, false)) }
      ;[0.5, 1.3, 2.3].forEach((y) => { const r = mesh(railG, fenceMat, (x0 + x1) / 2, y, (z0 + z1) / 2, false); r.scale.x = L; r.rotation.y = -ang; env.add(r) })
      const mesh2 = mesh(box(L, 1.7, 0.02), new THREE.MeshStandardMaterial({ color: 0x9aa4b0, transparent: true, opacity: 0.22, roughness: 0.6 }), (x0 + x1) / 2, 1.45, (z0 + z1) / 2, false); mesh2.rotation.y = -ang; env.add(mesh2) }
    fence(-62, -30, 62, -30); fence(-62, -30, -62, 28); fence(62, -30, 62, 28); fence(-62, 28, -8, 28); fence(8, 28, 62, 28)
    // 정문 (남쪽 가운데) — 차단기 + 경비실
    env.add(mesh(box(2.6, 2.6, 2.6), new THREE.MeshStandardMaterial({ color: 0xcfd6de, roughness: 0.6 }), -9.5, 1.3, 28.2))
    env.add(mesh(box(2.8, 0.2, 2.8), new THREE.MeshStandardMaterial({ color: 0x2a3340 }), -9.5, 2.7, 28.2))
    env.add(mesh(box(1.4, 0.9, 0.05), new THREE.MeshStandardMaterial({ color: 0x7fd4ff, emissive: 0x2f7fa8, emissiveIntensity: 0.9 }), -9.5, 1.6, 26.85, false))
    const bar = mesh(box(8, 0.14, 0.14), new THREE.MeshStandardMaterial({ color: 0xffffff }), -4.2, 1.2, 28.2, false); env.add(bar)
    for (let i = 0; i < 6; i++) env.add(mesh(box(0.55, 0.15, 0.15), new THREE.MeshStandardMaterial({ color: 0xe0352b }), -7.6 + i * 1.5, 1.2, 28.2, false))
    env.add(mesh(box(0.3, 1.4, 0.3), new THREE.MeshStandardMaterial({ color: 0x2a3340 }), -8.2, 0.7, 28.2))
    // 직원·방문 주차장 (동·서 바깥) — 허브 주변은 컨테이너 야적이 아니라 주차장과 적재 대기 공간
    const lineMat = new THREE.MeshBasicMaterial({ color: 0xcfd6de }), carCols = [0xd9dde3, 0x20252c, 0x8b1e2d, 0x2f5f9a, 0x9aa3ad, 0x3b6b4e, 0xe8e4d8]
    const carBody = box(4.3, 0.75, 1.8), carCab = box(2.2, 0.62, 1.64), wheelG = new THREE.CylinderGeometry(0.34, 0.34, 0.24, 12); disposables.push(wheelG)
    const wheelM = new THREE.MeshStandardMaterial({ color: 0x15181c, roughness: 0.9 }), glassM = new THREE.MeshStandardMaterial({ color: 0x1b2836, roughness: 0.2, metalness: 0.6 })
    const car = (x, z, rotY) => { const g = new THREE.Group(); const col = carCols[Math.floor(rnd() * carCols.length)], bm = new THREE.MeshStandardMaterial({ color: col, roughness: 0.4, metalness: 0.5 })
      g.add(mesh(carBody, bm, 0, 0.62, 0)); g.add(mesh(carCab, glassM, -0.1, 1.28, 0)); g.add(mesh(box(2.0, 0.06, 1.5), bm, -0.1, 1.62, 0, false))
      ;[[-1.35, -0.9], [-1.35, 0.9], [1.35, -0.9], [1.35, 0.9]].forEach(([wx, wz]) => { const w = mesh(wheelG, wheelM, wx, 0.34, wz, false); w.rotation.x = Math.PI / 2; g.add(w) })
      g.add(mesh(box(0.06, 0.18, 1.2), new THREE.MeshBasicMaterial({ color: 0xffe9b0 }), 2.17, 0.7, 0, false)); g.add(mesh(box(0.06, 0.16, 1.3), new THREE.MeshBasicMaterial({ color: 0xc0261f }), -2.17, 0.75, 0, false))
      g.position.set(x, 0, z); g.rotation.y = rotY; env.add(g) }
    const lot = (cx, z0, n, filled) => { for (let i = 0; i < n; i++) { const z = z0 + i * 3.2; const ln = new THREE.Mesh(new THREE.PlaneGeometry(5.2, 0.12), lineMat); ln.rotation.x = -Math.PI / 2; ln.position.set(cx, 0.012, z - 1.6); env.add(ln); if (i === n - 1) { const l2 = ln.clone(); l2.position.z = z + 1.6; env.add(l2) }
      if (rnd() < filled) car(cx + (rnd() - 0.5) * 0.3, z, Math.PI / 2 * 0 + (rnd() < 0.5 ? 0 : 0)) } }
    lot(-54, -18, 9, 0.6); lot(54, -18, 9, 0.6)
    // 가로등 (발광 헤드 + 빛 번짐)
    const glowT = cvs(64, 64, (g, w, h) => { const gr = g.createRadialGradient(32, 32, 0, 32, 32, 32); gr.addColorStop(0, 'rgba(255,230,170,0.9)'); gr.addColorStop(0.35, 'rgba(255,200,120,0.28)'); gr.addColorStop(1, 'rgba(255,200,120,0)'); g.fillStyle = gr; g.fillRect(0, 0, w, h) })
    const glows = []
    const poleMat = new THREE.MeshStandardMaterial({ color: 0x2b323c, metalness: 0.5, roughness: 0.5 }), lampMat = new THREE.MeshStandardMaterial({ color: 0xfff1d0, emissive: 0xffd89a, emissiveIntensity: 2.2 })
    const pole = (x, z, face) => { env.add(mesh(box(0.2, 9, 0.2), poleMat, x, 4.5, z)); const arm = mesh(box(2.2, 0.14, 0.14), poleMat, x + face * 1.0, 9, z, false); env.add(arm)
      env.add(mesh(box(0.9, 0.16, 0.5), lampMat, x + face * 2.0, 8.9, z, false))
      const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowT, blending: THREE.AdditiveBlending, transparent: true, depthWrite: false, fog: false })); sp.scale.set(9, 9, 1); sp.position.set(x + face * 2.0, 8.7, z); env.add(sp); glows.push(sp.material)
      const pool = new THREE.Mesh(new THREE.CircleGeometry(7, 24), new THREE.MeshBasicMaterial({ map: glowT, blending: THREE.AdditiveBlending, transparent: true, depthWrite: false, opacity: 0.55 })); pool.rotation.x = -Math.PI / 2; pool.position.set(x + face * 2.0, 0.02, z); env.add(pool); glows.push(pool.material); pool.material.userData.base = 0.55 }
    ;[-52, -30, 30, 52].forEach((x) => { pole(x, -27, 1); pole(x, 25, -1) })
    // 나무 · 관목 (울타리 바깥)
    const trunkM = new THREE.MeshStandardMaterial({ color: 0x4a3828, roughness: 1 }), leafM = [0x1f4a31, 0x28603d, 0x1a3d2a].map((c) => new THREE.MeshStandardMaterial({ color: c, roughness: 1, flatShading: true }))
    const tree = (x, z, s) => { env.add(mesh(new THREE.CylinderGeometry(0.2 * s, 0.3 * s, 2.4 * s, 6), trunkM, x, 1.2 * s, z, false)); for (let k = 0; k < 3; k++) env.add(mesh(new THREE.IcosahedronGeometry((1.9 - k * 0.35) * s, 0), leafM[k % 3], x, (3 + k * 1.4) * s, z, true)) }
    for (let i = 0; i < 26; i++) { const x = -90 + i * 7 + (rnd() - 0.5) * 3; tree(x, -40 - rnd() * 8, 0.9 + rnd() * 0.7); if (Math.abs(x) > 14) tree(x + 2, 52 + rnd() * 8, 0.9 + rnd() * 0.6) }
    for (let i = 0; i < 8; i++) { tree(-74 - rnd() * 8, -28 + i * 8, 0.9 + rnd() * 0.6); tree(74 + rnd() * 8, -28 + i * 8, 0.9 + rnd() * 0.6) }
    // 먼 풍경: 산 능선 + 멀리 떨어진 낮은 창고·공장 (허브 주변에 주거 건물 없음)
    const ridge = (zc, hmax, col, seed) => { let r2 = seed; const rr = () => { r2 = (r2 * 16807) % 2147483647; return r2 / 2147483647 }
      const sh = new THREE.Shape(); sh.moveTo(-260, 0); let y0 = hmax * 0.4; for (let x = -260; x <= 260; x += 16) { y0 = Math.max(hmax * 0.15, Math.min(hmax, y0 + (rr() - 0.5) * hmax * 0.55)); sh.lineTo(x, y0) } sh.lineTo(260, 0)
      const m = new THREE.Mesh(new THREE.ShapeGeometry(sh), new THREE.MeshBasicMaterial({ color: col, fog: false })); m.position.set(0, -0.5, zc); env.add(m); disposables.push(m.geometry, m.material); return m }
    const ridges = [ridge(-150, 38, 0x0f1a2a, 3), ridge(-125, 22, 0x0b1420, 9)]
    const flatT = cvs(64, 32, (g, w, h) => { g.fillStyle = '#1a222d'; g.fillRect(0, 0, w, h); g.fillStyle = 'rgba(255,214,140,0.8)'; for (let x = 6; x < w - 4; x += 14) if (rnd() < 0.5) g.fillRect(x, 20, 6, 3) })
    const farB = []
    for (let i = 0; i < 9; i++) { const w = 18 + rnd() * 20, h = 5 + rnd() * 4, x = -120 + i * 30 + (rnd() - 0.5) * 10
      const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, 12), new THREE.MeshBasicMaterial({ map: flatT, color: 0x6b7a92, fog: false })); b.position.set(x, h / 2, -110 - rnd() * 8); env.add(b); farB.push(b.material); disposables.push(b.geometry, b.material)
      const st = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.5, 0.5), new THREE.MeshBasicMaterial({ color: 0xff3b30, fog: false })); st.position.set(x + w / 4, h + 5, -110); env.add(st); disposables.push(st.geometry, st.material) }
    /* ── 소방 안전 설비: 스프링클러 · 소화기/소화전 · 비상구 · 소방차 전용 구역 · 옥외 소화전 ── */
    const FW = WALL_R - WALL_L; const fire = new THREE.Group(); fire.name = 'fire'; world.add(fire); fx.fire = fire
    const redM = new THREE.MeshStandardMaterial({ color: 0xc8261e, roughness: 0.45, metalness: 0.3 }), brassM = new THREE.MeshStandardMaterial({ color: 0xc9a14a, roughness: 0.35, metalness: 0.8 })
    const sign = (txt, sub, w, h, bg, fg, emissive) => { const t = cvs(256, Math.round(256 * h / w), (g, cw, ch) => { g.fillStyle = bg; g.fillRect(0, 0, cw, ch); g.strokeStyle = fg; g.lineWidth = 6; g.strokeRect(5, 5, cw - 10, ch - 10); g.fillStyle = fg; g.textAlign = 'center'; g.textBaseline = 'middle'
      g.font = '700 ' + Math.round(ch * (sub ? 0.38 : 0.5)) + 'px "Malgun Gothic",sans-serif'; g.fillText(txt, cw / 2, sub ? ch * 0.37 : ch / 2); if (sub) { g.font = '600 ' + Math.round(ch * 0.2) + 'px "Malgun Gothic",sans-serif'; g.fillText(sub, cw / 2, ch * 0.74) } })
      const m = new THREE.MeshStandardMaterial({ map: t, roughness: 0.5, emissive: emissive ? 0xffffff : 0x000000, emissiveMap: emissive ? t : null, emissiveIntensity: emissive ? 0.9 : 0 }); return new THREE.Mesh(new THREE.PlaneGeometry(w, h), m) }
    // 1) 스프링클러 배관: 천장 아래 적색 주배관 3줄 + 가지관 + 헤드 (헤드는 약 3 m 간격)
    const pipeY = WALL_H - 0.55, xm = (WALL_L + WALL_R) / 2, pipeMain = box(FW, 0.16, 0.16)
    ;[-7, 1, 9].forEach((z) => { fire.add(mesh(pipeMain, redM, xm, pipeY, z, false)); for (let x = WALL_L + 2; x < WALL_R - 1; x += 3) {
      fire.add(mesh(box(0.06, 0.28, 0.06), brassM, x, pipeY - 0.2, z, false)); const dfl = mesh(new THREE.CylinderGeometry(0.16, 0.16, 0.025, 14), brassM, x, pipeY - 0.36, z, false); fire.add(dfl) } })
    fire.add(mesh(box(0.16, 0.16, 30), redM, WALL_L + 0.6, pipeY + 0.15, 1, false)); fire.add(mesh(box(0.18, 0.18, 30), redM, WALL_R - 0.6, pipeY + 0.15, 1, false))
    ;[WALL_L + 0.6, WALL_R - 0.6].forEach((x) => { fire.add(mesh(box(0.22, pipeY + 0.15, 0.22), redM, x, (pipeY + 0.15) / 2, WALL_N + 0.5, false)); const v = mesh(new THREE.CylinderGeometry(0.3, 0.3, 0.1, 16), brassM, x, 1.4, WALL_N + 0.5, false); v.rotation.x = Math.PI / 2; fire.add(v) })
    // 2) 소화기 + 소화전함 (북쪽 벽 기둥마다) + 위치 표지
    for (let x = WALL_L + FW / 6; x < WALL_R - 0.5; x += FW / 6) {
      const ext = mesh(new THREE.CylinderGeometry(0.13, 0.13, 0.55, 14), redM, x + 0.4, 0.28, WALL_N + 0.55, false); fire.add(ext)
      fire.add(mesh(box(0.08, 0.1, 0.08), MAT.steel, x + 0.4, 0.6, WALL_N + 0.55, false))
      const sg = sign('소화기', '', 0.7, 0.35, '#c8261e', '#ffffff', false); sg.position.set(x + 0.4, 2.1, WALL_N + 0.18); fire.add(sg)
      if (Math.round((x - WALL_L) / (FW / 6)) % 2 === 0) { fire.add(mesh(box(0.9, 1.1, 0.25), redM, x - 0.5, 1.0, WALL_N + 0.2, false)); const hs = sign('소화전', '', 0.7, 0.3, '#ffffff', '#c8261e', false); hs.position.set(x - 0.5, 1.0, WALL_N + 0.34); fire.add(hs) }
    }
    // 3) 비상구 유도등 (북쪽 벽 양끝 + 기둥 사이) — 초록 발광
    ;[WALL_L + 2.2, (WALL_L + WALL_R) / 2, WALL_R - 2.2].forEach((x) => { const ex = sign('비상구', 'EXIT', 1.3, 0.5, '#0f9a4a', '#ffffff', true); ex.position.set(x, 3.4, WALL_N + 0.18); fire.add(ex) })
    // 4) 소방차 전용 구역 (건물 남쪽 외부): 붉은 테두리 + 흰 사선 + 안내 문구
    const flT = cvs(1024, 256, (g, w, h) => { g.fillStyle = '#3a3f46'; g.fillRect(0, 0, w, h); g.fillStyle = '#c8261e'; g.fillRect(0, 0, w, 18); g.fillRect(0, h - 18, w, 18)
      g.save(); g.beginPath(); g.rect(0, 18, w, h - 36); g.clip(); g.strokeStyle = 'rgba(255,255,255,0.55)'; g.lineWidth = 16; for (let x = -h; x < w + h; x += 64) { g.beginPath(); g.moveTo(x, h); g.lineTo(x + h, 0); g.stroke() } g.restore()
      g.fillStyle = 'rgba(30,34,40,0.88)'; g.fillRect(w * 0.2, 70, w * 0.6, 116); g.fillStyle = '#ffe14d'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.font = '800 64px "Malgun Gothic",sans-serif'; g.fillText('소방차 전용', w / 2, 112); g.fillStyle = '#ffffff'; g.font = '600 34px "Malgun Gothic",sans-serif'; g.fillText('주정차 금지 · FIRE LANE', w / 2, 160) })
    flT.anisotropy = 8
    const fl = new THREE.Mesh(new THREE.PlaneGeometry(28, 5.2), new THREE.MeshStandardMaterial({ map: flT, roughness: 0.8 })); fl.rotation.x = -Math.PI / 2; fl.position.set(xm, 0.025, FLOOR_S + 4.6); fl.receiveShadow = true; fire.add(fl)
    // 5) 옥외 소화전 + 표지 기둥, 송수구(건물 외벽)
    const hyd = (x, z) => { fire.add(mesh(new THREE.CylinderGeometry(0.17, 0.2, 0.75, 14), redM, x, 0.38, z)); fire.add(mesh(new THREE.SphereGeometry(0.2, 12, 8), redM, x, 0.8, z)); fire.add(mesh(new THREE.CylinderGeometry(0.07, 0.07, 0.5, 10), redM, x + 0.2, 0.55, z).rotateZ(Math.PI / 2))
      fire.add(mesh(box(0.06, 1.8, 0.06), MAT.steel, x - 0.5, 0.9, z, false)); const sg = sign('소화전', '', 0.7, 0.3, '#ffffff', '#c8261e', false); sg.position.set(x - 0.5, 1.7, z + 0.05); fire.add(sg) }
    hyd(WALL_R + 3, FLOOR_S + 1.8); hyd(WALL_L - 3, FLOOR_S + 1.8); hyd(xm - 6, FLOOR_S + 1.8)
    const sc2 = sign('송수구', '소방차 연결', 1.0, 0.6, '#c8261e', '#ffffff', false); sc2.position.set(xm + 5, 1.5, FLOOR_S + 0.3); fire.add(sc2); fire.add(mesh(box(1.0, 0.8, 0.2), redM, xm + 5, 0.7, FLOOR_S + 0.25, false))
    /* ── 화재 경보 연출: 구역 붉은 점멸 · 불꽃 · 연기 · 스프링클러 살수 · 경광등 + 화면 테두리/배너 + 시험 버튼 ── */
    const alarm = new THREE.Group(); alarm.name = 'alarm'; alarm.visible = false; world.add(alarm); fx.alarm = alarm
    const flameT = cvs(64, 64, (g) => { const gr = g.createRadialGradient(32, 38, 2, 32, 34, 30); gr.addColorStop(0, 'rgba(255,245,190,1)'); gr.addColorStop(0.3, 'rgba(255,150,40,0.85)'); gr.addColorStop(0.7, 'rgba(220,50,10,0.3)'); gr.addColorStop(1, 'rgba(200,30,0,0)'); g.fillStyle = gr; g.fillRect(0, 0, 64, 64) })
    const softT = cvs(32, 32, (g) => { const gr = g.createRadialGradient(16, 16, 0, 16, 16, 16); gr.addColorStop(0, 'rgba(255,255,255,0.9)'); gr.addColorStop(1, 'rgba(255,255,255,0)'); g.fillStyle = gr; g.fillRect(0, 0, 32, 32) })
    const zoneGlow = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ color: 0xff2a1a, transparent: true, opacity: 0.25, depthWrite: false, blending: THREE.AdditiveBlending })); zoneGlow.rotation.x = -Math.PI / 2; zoneGlow.position.y = 0.06; alarm.add(zoneGlow)
    const flames = []; for (let i = 0; i < 14; i++) { const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: flameT, blending: THREE.AdditiveBlending, transparent: true, depthWrite: false, fog: false })); sp.userData = { ph: rnd() * 6, ox: rnd() - 0.5, oz: rnd() - 0.5, sz: 1.6 + rnd() * 1.8 }; alarm.add(sp); flames.push(sp) }
    const NS_P = 240, smokePos = new Float32Array(NS_P * 3), smokeSeed = Array.from({ length: NS_P }, () => ({ u: rnd(), v: rnd(), a: rnd() * 6.28, sp: 0.5 + rnd() * 0.9 }))
    const smokeGeo = new THREE.BufferGeometry(); smokeGeo.setAttribute('position', new THREE.BufferAttribute(smokePos, 3)); disposables.push(smokeGeo)
    const smoke = new THREE.Points(smokeGeo, new THREE.PointsMaterial({ map: softT, color: 0x5a5f66, size: 7, transparent: true, opacity: 0.4, depthWrite: false, sizeAttenuation: true })); smoke.frustumCulled = false; alarm.add(smoke)
    const NW = 280, wPos = new Float32Array(NW * 3), wSeed = Array.from({ length: NW }, () => ({ u: rnd(), v: rnd(), t: rnd() }))
    const wGeo = new THREE.BufferGeometry(); wGeo.setAttribute('position', new THREE.BufferAttribute(wPos, 3)); disposables.push(wGeo)
    const water = new THREE.Points(wGeo, new THREE.PointsMaterial({ map: softT, color: 0xcfeaff, size: 0.5, transparent: true, opacity: 0.95, depthWrite: false })); water.frustumCulled = false; alarm.add(water)
    const beacons = [[WALL_L + 1.5, WALL_N + 1], [WALL_R - 1.5, WALL_N + 1], [WALL_L + 1.5, FLOOR_S - 1], [WALL_R - 1.5, FLOOR_S - 1]].map(([x, z]) => { const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowT, color: 0xff2a1a, blending: THREE.AdditiveBlending, transparent: true, depthWrite: false, fog: false })); sp.scale.set(7, 7, 1); sp.position.set(x, WALL_H - 0.8, z); alarm.add(sp); return sp })
    // 대피 구역 바닥 표시 (평상시에도 보임)
    const refT = cvs(512, 128, (g, w, h) => { g.fillStyle = 'rgba(20,90,50,0.55)'; g.fillRect(0, 0, w, h); g.strokeStyle = '#4fdc8a'; g.lineWidth = 8; g.setLineDash([36, 20]); g.strokeRect(8, 8, w - 16, h - 16); g.setLineDash([]); g.fillStyle = '#e8ffef'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.font = '700 40px "Malgun Gothic",sans-serif'; g.fillText('AMR 화재 대피 구역', w / 2, h / 2) })
    const refZ = new THREE.Mesh(new THREE.PlaneGeometry(X(850) - X(705), Z(640) - Z(575)), new THREE.MeshBasicMaterial({ map: refT, transparent: true, opacity: 0.9, depthWrite: false })); refZ.rotation.x = -Math.PI / 2; refZ.position.set((X(705) + X(850)) / 2, 0.02, (Z(575) + Z(640)) / 2); fire.add(refZ)
    // 화면 효과 + 시험 버튼
    const ov = document.createElement('div'); ov.className = 'twin-fire-ov'; ov.style.cssText = 'position:absolute;inset:0;z-index:5;pointer-events:none;opacity:0;box-shadow:inset 0 0 90px 24px rgba(255,40,30,.75);transition:opacity .2s'
    const bn = document.createElement('div'); bn.className = 'twin-fire-bn'; bn.style.cssText = 'position:absolute;left:50%;bottom:14px;transform:translateX(-50%);z-index:7;pointer-events:none;display:none;font:700 14px "Malgun Gothic",sans-serif;color:#fff;background:rgba(190,24,18,.92);border:1px solid #ff9a90;border-radius:10px;padding:8px 18px;white-space:nowrap;box-shadow:0 4px 18px rgba(0,0,0,.5)'
    const ctl = document.createElement('div'); ctl.className = 'twin-fire-ctl'; ctl.style.cssText = 'position:absolute;left:12px;top:66px;z-index:7;display:flex;gap:6px;align-items:center;font:600 12px "Malgun Gothic",sans-serif'
    const sel = document.createElement('select'); sel.style.cssText = 'font:inherit;padding:5px 6px;border-radius:7px;background:rgba(10,16,24,.7);color:#e8eef6;border:1px solid rgba(255,255,255,.18)'
    ;['A 인천 랙', 'B 서울 랙', 'C 경기 랙', 'D 냉장 창고', '충전소'].forEach((n, i) => { const o = document.createElement('option'); o.value = i === 0 ? 1 : i === 1 ? 2 : i === 2 ? 3 : i === 3 ? 0 : 4; o.textContent = n; sel.appendChild(o) })
    const fb = document.createElement('button'); fb.style.cssText = 'font:inherit;padding:6px 10px;border-radius:7px;cursor:pointer;color:#fff;background:rgba(170,30,24,.88);border:1px solid #ff8a80'
    ctl.appendChild(fb); ctl.appendChild(sel)
    container.appendChild(ov); container.appendChild(bn); container.appendChild(ctl)
    const fireBtn = () => { const F = sim.S.fire; fb.textContent = F && F.on ? '✅ 화재 종료 (관리자)' : '🔥 화재 경보 훈련'; fb.style.background = F && F.on ? 'rgba(30,110,60,.9)' : 'rgba(170,30,24,.88)'; fb.style.borderColor = F && F.on ? '#7be0a4' : '#ff8a80' }
    // 안내방송(음성) + 사이렌: 브라우저 정책상 버튼을 누른 순간 소리가 켜진다. 🔊/🔇 로 끌 수 있다
    let actx = null, sirenOsc = null, sirenGain = null, voiceOn = true, last = { on: false, stage: 0, evacAll: false }
    const mb = document.createElement('button'); mb.style.cssText = 'font:inherit;padding:6px 8px;border-radius:7px;cursor:pointer;color:#fff;background:rgba(10,16,24,.7);border:1px solid rgba(255,255,255,.22)'; mb.textContent = '🔊'; mb.title = '안내방송 소리 켜기/끄기'
    mb.onclick = () => { voiceOn = !voiceOn; mb.textContent = voiceOn ? '🔊' : '🔇'; if (!voiceOn) { try { speechSynthesis.cancel() } catch (e) { /* 무시 */ } sirenSet(false) } }
    ctl.appendChild(mb)
    const ensureAudio = () => { try { if (!actx) { actx = new (window.AudioContext || window.webkitAudioContext)(); sirenGain = actx.createGain(); sirenGain.gain.value = 0; sirenGain.connect(actx.destination); sirenOsc = actx.createOscillator(); sirenOsc.type = 'triangle'; sirenOsc.connect(sirenGain); sirenOsc.start() } if (actx.state === 'suspended') actx.resume() } catch (e) { actx = null } }
    const sirenSet = (on) => { if (!actx || !sirenGain) return; sirenGain.gain.setTargetAtTime(on && voiceOn ? 0.03 : 0, actx.currentTime, 0.08) }
    const speak = (text) => { if (!voiceOn) return; try { const u = new SpeechSynthesisUtterance(text); u.lang = 'ko-KR'; u.rate = 0.95; u.pitch = 0.9; const v = (speechSynthesis.getVoices() || []).find((x) => /ko/i.test(x.lang)); if (v) u.voice = v; speechSynthesis.speak(u) } catch (e) { /* 음성 미지원 */ } }
    const sirenTick = (t, on) => { if (!actx || !sirenOsc) return; if (on) sirenOsc.frequency.setValueAtTime(Math.floor(t * 1.6) % 2 ? 960 : 700, actx.currentTime) }
    fb.onclick = () => { if (!sim.setFire) return; ensureAudio(); const F = sim.S.fire; sim.setFire(!(F && F.on), +sel.value); fireBtn() }
    fireBtn()
    fx.fireEls = [ov, bn, ctl]
    fx.fireFx = (now, dt) => {
      const F = sim.S.fire, on = !!(F && F.on); alarm.visible = on && !is2D
      // 안내방송: 상태가 바뀌는 순간 한 번씩
      if (on && !last.on) { speak('화재가 발생했습니다. ' + F.label + ' 구역에서 연기가 감지되었습니다. 직원 여러분은 비상구를 통해 안전하게 대피해 주시기 바랍니다. 무인 장비는 대피 구역으로 이동합니다.'); sirenSet(true) }
      if (on && F.stage >= 2 && last.stage < 2) speak('스프링클러가 작동하였습니다. 소방 설비가 연동되었습니다. 신규 입차를 중단합니다.')
      if (on && F.total && F.evac >= F.total && !last.evacAll) { speak('모든 무인 장비가 대피 구역에 도착했습니다.'); sirenSet(false) }
      if (!on && last.on) { speak('화재 경보가 해제되었습니다. 장비가 작업에 복귀합니다.'); sirenSet(false) }
      if (on && !(F.total && F.evac >= F.total)) sirenTick(now / 1000, true)
      last = { on, stage: on ? F.stage : 0, evacAll: !!(on && F.total && F.evac >= F.total) }; ctl.style.display = is2D ? 'none' : 'flex'
      const t = now / 1000; ov.style.opacity = on ? String(0.35 + 0.35 * Math.abs(Math.sin(t * 3))) : '0'
      bn.style.display = on ? 'block' : 'none'
      if (!on) { fireBtn(); return }
      bn.textContent = '🔥 화재 경보 · ' + F.label + ' · AMR 대피 ' + F.evac + '/' + F.total + (F.stage >= 2 ? ' · 스프링클러 작동 중' : ' · 연기 감지') + ' · 입차 게이트 차단'
      fireBtn()
      const r = FIRE_ZONE_RECT(F.zone), x0 = X(r.x0), x1 = X(r.x1), z0 = Z(r.y0), z1 = Z(r.y1), cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, W = x1 - x0, D = z1 - z0
      zoneGlow.position.x = cx; zoneGlow.position.z = cz; zoneGlow.scale.set(W, D, 1); zoneGlow.material.opacity = 0.12 + 0.2 * Math.abs(Math.sin(t * 3.2))
      // 불꽃: 구역 한가운데 한 지점에서 시작해 시간이 갈수록 퍼짐
      const spread = Math.min(1, F.t / 25), fxp = cx + (F.zone === 4 ? -W * 0.15 : 0), fzp = cz
      flames.forEach((f, i) => { const u = f.userData, k = i / flames.length; const vis = k < 0.25 + spread * 0.75
        f.visible = vis; f.position.set(fxp + u.ox * (3 + spread * W * 0.55), 2.0 + Math.abs(Math.sin(t * 7 + u.ph)) * 0.8, fzp + u.oz * D * 0.5)
        const s2 = u.sz * 3.6 * (0.7 + 0.35 * Math.sin(t * 9 + u.ph * 2)) * (F.stage >= 2 ? 0.85 : 1); f.scale.set(s2, s2 * 1.5, 1); f.material.opacity = 0.75 + 0.25 * Math.sin(t * 11 + u.ph) })
      // 연기: 구역 위로 올라가 천장 아래에 퍼진다
      const sp2 = smokeGeo.attributes.position; smokeSeed.forEach((q, i) => { const age = ((t * 0.12 * q.sp + q.u) % 1); sp2.setXYZ(i, fxp + Math.cos(q.a + t * 0.2) * (2 + age * W * 0.5), 1 + age * (WALL_H - 1.4), fzp + Math.sin(q.a * 1.3) * (1.5 + age * D * 0.7)) }); sp2.needsUpdate = true
      smoke.material.opacity = Math.min(0.62, 0.2 + spread * 0.45)
      // 스프링클러 살수
      water.visible = F.stage >= 2; if (water.visible) { const wp = wGeo.attributes.position; wSeed.forEach((q, i) => { const k = (t * 1.4 + q.t) % 1; wp.setXYZ(i, x0 + 1 + q.u * (W - 2), pipeY - 0.4 - k * (pipeY - 0.4), z0 + 0.5 + q.v * (D - 1)) }); wp.needsUpdate = true }
      beacons.forEach((b, i) => { b.material.opacity = Math.max(0, Math.sin(t * 5 + i * 1.6)) })
    }
    /* ── AMR 배터리 화재: 격리 구역(방화벽) + 단계별 연출 (이상 징후 → 격리 이동 → 열폭주 → 진압 후 접근 금지) ── */
    {
      const qx0 = X(858), qx1 = WALL_R - 0.15, qzN = Z(575), qzS = FLOOR_S - 0.1, qH = 3.2
      const wallM = new THREE.MeshStandardMaterial({ color: 0x8e949c, roughness: 0.8 })
      const hazT = cvs(256, 32, (g, w, h) => { g.fillStyle = '#ffd21f'; g.fillRect(0, 0, w, h); g.fillStyle = '#141414'; for (let x = -h; x < w + h; x += 32) { g.beginPath(); g.moveTo(x, h); g.lineTo(x + 16, h); g.lineTo(x + 16 + h, 0); g.lineTo(x + h, 0); g.fill() } }); hazT.wrapS = hazT.wrapT = THREE.RepeatWrapping
      const hazM = new THREE.MeshStandardMaterial({ map: hazT, roughness: 0.6 })
      const wall = (x0, z0, x1, z1) => { const L = Math.hypot(x1 - x0, z1 - z0), ang = Math.atan2(z1 - z0, x1 - x0); const m = mesh(box(L, qH, 0.28), wallM, (x0 + x1) / 2, qH / 2, (z0 + z1) / 2); m.rotation.y = -ang; fire.add(m)
        const hz = mesh(box(L, 0.28, 0.3), hazM, (x0 + x1) / 2, qH + 0.14, (z0 + z1) / 2, false); hz.rotation.y = -ang; fire.add(hz) }
      wall(qx0, qzN, qx0, qzS)                 // 서쪽 벽
      wall(qx0, qzN, X(895) - 0.5, qzN)        // 북쪽 벽 (출입 틈 1.7 m: AMR 전용 통로)
      const pad = new THREE.Mesh(new THREE.PlaneGeometry(qx1 - qx0, qzS - qzN), new THREE.MeshStandardMaterial({ color: 0x3a2a2a, roughness: 0.9 })); pad.rotation.x = -Math.PI / 2; pad.position.set((qx0 + qx1) / 2, 0.025, (qzN + qzS) / 2); fire.add(pad)
      const qs = sign('배터리 격리 구역', '방화벽 · 접근 금지', 3.0, 1.0, '#8a1510', '#ffffff', false); qs.rotation.y = -Math.PI / 2; qs.position.set(qx0 - 0.17, 2.0, (qzN + qzS) / 2); fire.add(qs)
      const bf = new THREE.Group(); bf.visible = false; world.add(bf)
      const bfFlames = [], bfSmoke = [], bfSparks = []
      for (let i = 0; i < 7; i++) { const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: flameT, blending: THREE.AdditiveBlending, transparent: true, depthWrite: false, fog: false })); sp.userData = { ph: rnd() * 6, ox: rnd() - 0.5, oz: rnd() - 0.5 }; bf.add(sp); bfFlames.push(sp) }
      for (let i = 0; i < 46; i++) { const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: softT, color: 0x2e3238, transparent: true, depthWrite: false, opacity: 0.5 })); sp.userData = { a: rnd(), sp: 0.35 + rnd() * 0.7, dx: rnd() - 0.5, dz: rnd() - 0.5 }; bf.add(sp); bfSmoke.push(sp) }
      const NSP = 80, spPos = new Float32Array(NSP * 3), spG = new THREE.BufferGeometry(); spG.setAttribute('position', new THREE.BufferAttribute(spPos, 3)); disposables.push(spG)
      const sparks = new THREE.Points(spG, new THREE.PointsMaterial({ map: softT, color: 0xffb040, size: 0.35, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending })); sparks.frustumCulled = false; bf.add(sparks)
      const beacon = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowT, color: 0xffa020, blending: THREE.AdditiveBlending, transparent: true, depthWrite: false, fog: false })); beacon.scale.set(5, 5, 1); bf.add(beacon)
      const qWater = new THREE.Points(wGeo, new THREE.PointsMaterial({ map: softT, color: 0xcfeaff, size: 0.45, transparent: true, depthWrite: false })); qWater.frustumCulled = false; qWater.visible = false; world.add(qWater)
      const qwG = new THREE.BufferGeometry(); const qwPos = new Float32Array(120 * 3); qwG.setAttribute('position', new THREE.BufferAttribute(qwPos, 3)); disposables.push(qwG); qWater.geometry = qwG
      const qwSeed = Array.from({ length: 120 }, () => ({ u: rnd(), v: rnd(), t: rnd() }))
      // 화면 문구 · 버튼
      const bb = document.createElement('button'); bb.style.cssText = 'font:inherit;padding:6px 10px;border-radius:7px;cursor:pointer;color:#fff;background:rgba(190,110,10,.9);border:1px solid #ffc070'
      ctl.appendChild(bb)
      const bfSt = () => sim.S.bfire
      const bfBtn = () => { const B = bfSt(); bb.textContent = !B ? '🔋 배터리 화재 훈련' : B.stage >= 4 ? '관리자 확인 (장비 복귀)' : '훈련 중단'; bb.style.background = !B ? 'rgba(190,110,10,.9)' : B.stage >= 4 ? 'rgba(30,110,60,.9)' : 'rgba(80,80,90,.9)' }
      bb.onclick = () => { ensureAudio(); const B = bfSt(); if (!B) sim.setBatteryFire(1); else sim.clearBatteryFire(); bfBtn() }
      bfBtn()
      // 26.10.29 비상 정지: 대상(전체 / 장비 하나)을 고르고 누르면 멈추고, 다시 누르면 재개
      const eSel = document.createElement('select'); eSel.style.cssText = 'font:inherit;padding:5px 6px;border-radius:7px;background:rgba(10,16,24,.7);color:#e8eef6;border:1px solid rgba(255,255,255,.18)'
      ;[['all', '전체'], ...sim.S.amrs.map((r, i) => [String(i), r.name])].forEach(([v, n]) => { const o = document.createElement('option'); o.value = v; o.textContent = n; eSel.appendChild(o) })
      const eb = document.createElement('button'); eb.style.cssText = 'font:inherit;padding:6px 10px;border-radius:7px;cursor:pointer;color:#fff;border:1px solid #ff8a80'
      ctl.style.flexWrap = 'wrap'; ctl.appendChild(eSel); ctl.appendChild(eb)
      const eOn = () => (eSel.value === 'all' ? sim.S.amrs.some((r) => r.estop) : !!(sim.S.amrs[+eSel.value] && sim.S.amrs[+eSel.value].estop))
      const eBtn = () => { const on = eOn(); eb.textContent = on ? '▶ 정지 해제' : '🛑 비상 정지'; eb.style.background = on ? 'rgba(30,110,60,.9)' : 'rgba(120,20,20,.92)' }
      eb.onclick = () => { const on = eOn(); sim.setEstop(eSel.value === 'all' ? null : +eSel.value, !on); eBtn() }
      eSel.onchange = eBtn; eBtn()
      const prevFx0 = fx.fireFx; fx.fireFx = (now, dt) => { prevFx0(now, dt); eBtn() }
      fx.fireEls.push(bf, qWater)
      let lastB = 0
      const prevFx = fx.fireFx
      fx.fireFx = (now, dt) => {
        prevFx(now, dt)
        const B = bfSt(), st = B ? B.stage : 0; bfBtn(); bf.visible = !!B && !is2D; qWater.visible = !!B && st >= 3 && !is2D
        if (st !== lastB) {
          const n = B ? B.a.name.replace('AMR-0', 'AMR ') + '번' : ''
          if (st === 1) speak(n + ' 배터리 온도 이상이 감지되었습니다. 작업을 중단하고 격리 구역으로 이동합니다.')
          if (st === 2) speak(n + ' 위험 단계입니다. 충전을 차단하고 배터리 격리 구역으로 이동합니다. 주변 장비는 접근하지 마십시오.')
          if (st === 3) { speak('격리 구역에서 배터리 화재가 발생했습니다. 방화벽 안에서 소방 설비가 작동합니다. 허브 운영에는 영향이 없습니다.'); sirenSet(true) }
          if (st === 4) { speak('화재가 진압되었습니다. 해당 장비는 관리자 확인 전까지 접근과 재가동이 금지됩니다.'); sirenSet(false) }
          if (st === 0 && lastB > 0) { speak('관리자 확인이 완료되었습니다. 장비가 점검을 마치고 복귀합니다.'); sirenSet(false) }
          lastB = st
        }
        if (st === 3) sirenTick(now / 1000, true)
        if (!B || is2D) { if (!sim.S.fire || !sim.S.fire.on) { /* 화재 모드 아닐 때 화면 효과 끄기는 prevFx 가 처리 */ } return }
        const F = sim.S.fire, hubFire = !!(F && F.on), t = now / 1000, a = B.a, g = amrs3[B.i] && amrs3[B.i].g
        const px = g ? g.position.x : X(a.x), pz = g ? g.position.z : Z(a.y)
        bf.position.set(px, 0, pz)
        const hot = st >= 3, warm = st >= 1
        beacon.position.set(0, 2.4, 0); beacon.material.color.set(st >= 3 ? 0xff2a1a : 0xffa020); beacon.material.opacity = Math.max(0, Math.sin(t * (st >= 2 ? 8 : 4))); beacon.scale.setScalar(st >= 3 ? 8 : 5)
        const k = Math.min(1, B.t3 != null ? (B.t - B.t3) / 6 : 0)
        bfFlames.forEach((f, i) => { const u = f.userData; f.visible = st === 3 || (st === 4 && i < 2); const sc = (st === 4 ? 0.9 : 1.8 + k * 1.6) * (0.75 + 0.3 * Math.sin(t * 9 + u.ph)); f.scale.set(sc, sc * 1.6, 1); f.position.set(u.ox * 1.1, 0.9 + Math.abs(Math.sin(t * 8 + u.ph)) * 0.5, u.oz * 0.8); f.material.opacity = 0.8 })
        bfSmoke.forEach((sp, i) => { const u = sp.userData, ag = ((t * 0.18 * u.sp + u.a) % 1); const dens = st === 1 ? 0.12 : st === 2 ? 0.3 : st === 3 ? 0.95 : 0.4; sp.visible = i / bfSmoke.length < dens
          sp.position.set(u.dx * (1 + ag * 4), 0.8 + ag * (WALL_H - 1), u.dz * (1 + ag * 3)); const s2 = 1.2 + ag * 4.5; sp.scale.set(s2, s2, 1); sp.material.opacity = (st === 4 ? 0.22 : 0.5) * (1 - ag * 0.7); sp.material.color.set(st === 4 ? 0x9aa0a6 : 0x2e3238) })
        sparks.visible = st === 3; if (sparks.visible) { const pa = spG.attributes.position; for (let i = 0; i < NSP; i++) { const q = ((t * 1.2 + i * 0.137) % 1), ang = i * 2.399; pa.setXYZ(i, Math.cos(ang) * q * 2.2, 0.8 + Math.sin(q * Math.PI) * 2.6, Math.sin(ang) * q * 2.2) } pa.needsUpdate = true }
        // 격리 구역 소방: 천장에서 살수 (열폭주 단계)
        if (qWater.visible) { const wp = qwG.attributes.position; qwSeed.forEach((q, i) => { const kk = (t * 1.5 + q.t) % 1; wp.setXYZ(i, qx0 + 0.3 + q.u * (qx1 - qx0 - 0.6), pipeY - kk * pipeY, qzN + 0.3 + q.v * (qzS - qzN - 0.6)) }); wp.needsUpdate = true }
        if (!hubFire) {
          ov.style.boxShadow = st >= 3 ? 'inset 0 0 70px 14px rgba(255,40,30,.65)' : 'inset 0 0 70px 14px rgba(255,160,30,.55)'
          ov.style.opacity = String(st >= 4 ? 0.18 : 0.25 + 0.3 * Math.abs(Math.sin(t * (st >= 3 ? 3.2 : 2))))
          bn.style.display = 'block'; bn.style.background = st >= 4 ? 'rgba(20,100,60,.92)' : st >= 3 ? 'rgba(190,24,18,.92)' : 'rgba(180,100,10,.92)'
          bn.textContent = st === 1 ? '⚠ ' + a.name + ' 배터리 온도 이상 · ' + Math.round(a.temp) + '℃ · 작업 중단' : st === 2 ? '⚠ ' + a.name + ' 위험 단계 · ' + Math.round(a.temp) + '℃ · 격리 구역 이동 중' : st === 3 ? '🔥 ' + a.name + ' 배터리 열폭주 · 격리 구역 내 화재 · ' + Math.round(a.temp) + '℃ · 허브 운영 정상' : '✔ ' + a.name + ' 진압 완료 · 접근·재가동 금지 · 관리자 확인 대기'
        } else { ov.style.boxShadow = 'inset 0 0 90px 24px rgba(255,40,30,.75)' }
      }
    }
    const stM = env.children.filter((o) => o.isMesh && o.geometry && o.geometry.type === 'BoxGeometry' && o.material.color && o.material.color.getHex() === 0xff3b30)
    fx.env = env
    fx.envSet = (t) => {
      const day = t === 'light'
      sky.material.map = day ? skyDay : skyTex; sky.material.needsUpdate = true
      if (env.userData.stars) env.userData.stars.visible = !day
      glows.forEach((m) => { m.opacity = day ? 0 : (m.userData.base != null ? m.userData.base : 1) })
      lampMat.emissiveIntensity = day ? 0.15 : 2.2
      ridges[0].material.color.set(day ? 0x6f8aa4 : 0x0f1a2a); ridges[1].material.color.set(day ? 0x587792 : 0x0b1420)
      farB.forEach((m) => { if (!m.userData.map) m.userData.map = m.map; m.map = day ? null : m.userData.map; m.color.set(day ? 0xaab6c4 : 0x6b7a92); m.needsUpdate = true })
      stM.forEach((m) => { m.visible = !day })
    }
    fx.envSet('dark')
  }
  const floorW = WALL_R - WALL_L, floorD = FLOOR_S - WALL_N
  const floor = mesh(new THREE.PlaneGeometry(floorW, floorD), MAT.floor, (WALL_L + WALL_R) / 2, 0, (WALL_N + FLOOR_S) / 2, false)
  floor.rotation.x = -Math.PI / 2; world.add(floor)
  // 26.10.28 바닥 마모: 바퀴 자국(통로를 따라 두 줄) · 코너 쓸림 · 기름 얼룩 · 먼지 — 새것 같은 매끈한 바닥을 실제 창고처럼
  {
    let sd = 7; const rd = () => { sd = (sd * 16807) % 2147483647; return sd / 2147483647 }
    const W = 2048, H = 1024, wt = canvasTex(W, H, (cg) => {
      const cx = (px) => ((px - 80) / 840) * W, cy = (py) => (py / 640) * H
      cg.lineCap = 'round'
      const track = (x0, y0, x1, y1, a) => { for (const o of [-9, 9]) { cg.strokeStyle = `rgba(10,12,14,${a * (0.6 + rd() * 0.4)})`; cg.lineWidth = 5 + rd() * 5
        const horiz = Math.abs(x1 - x0) > Math.abs(y1 - y0); let t = 0; while (t < 1) { const n = Math.min(1, t + 0.02 + rd() * 0.08)
          if (rd() > 0.18) { cg.beginPath(); cg.moveTo(cx(x0 + (x1 - x0) * t) + (horiz ? 0 : o), cy(y0 + (y1 - y0) * t) + (horiz ? o : 0)); cg.lineTo(cx(x0 + (x1 - x0) * n) + (horiz ? 0 : o), cy(y0 + (y1 - y0) * n) + (horiz ? o : 0)); cg.stroke() } t = n } } }
      AISLES.forEach((y) => track(120, y, 880, y, 0.16)); ;[HXL, HXR].forEach((x) => track(x, 20, x, 600, 0.17))
      for (let i = 0; i < 38; i++) { const x = cx(110 + rd() * 780), y = cy(20 + rd() * 590), r = 18 + rd() * 70, gr = cg.createRadialGradient(x, y, 0, x, y, r)
        gr.addColorStop(0, `rgba(8,10,12,${0.10 + rd() * 0.16})`); gr.addColorStop(1, 'rgba(8,10,12,0)'); cg.fillStyle = gr; cg.beginPath(); cg.ellipse(x, y, r, r * (0.5 + rd() * 0.5), rd() * 3, 0, 7); cg.fill() }
      INTER_NODES.forEach((n) => { for (let k = 0; k < 3; k++) { const x = cx(n.x) + (rd() - 0.5) * 40, y = cy(n.y) + (rd() - 0.5) * 40, r = 26 + rd() * 30, gr = cg.createRadialGradient(x, y, 0, x, y, r)
        gr.addColorStop(0, 'rgba(8,10,12,0.20)'); gr.addColorStop(1, 'rgba(8,10,12,0)'); cg.fillStyle = gr; cg.beginPath(); cg.arc(x, y, r, 0, 7); cg.fill() } })
    })
    disposables.push(wt)
    const wm = new THREE.Mesh(new THREE.PlaneGeometry(floorW, floorD), new THREE.MeshBasicMaterial({ map: wt, transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -1 }))
    disposables.push(wm.geometry); wm.rotation.x = -Math.PI / 2; wm.position.set((WALL_L + WALL_R) / 2, 0.006, (WALL_N + FLOOR_S) / 2); wm.renderOrder = 0; world.add(wm)
  }
  // 슬래브 단면 (남쪽 컷어웨이)
  world.add(mesh(box(floorW, 0.4, 0.3), MAT.steel, (WALL_L + WALL_R) / 2, -0.2, FLOOR_S + 0.15, false))

  // 야드 주차선 (트럭 도크 앞)
  DOCK_Y.forEach((y) => {
    ;[-1, 1].forEach((side) => {
      ;[-1.7, 1.7].forEach((dz) => {
        const l = mesh(new THREE.PlaneGeometry(14, 0.14), MAT.yellow, side * (Math.abs(WALL_L) + 7.5), 0.005, Z(y) + dz, false)
        l.rotation.x = -Math.PI / 2; world.add(l)
      })
    })
  })

  /* ── 통로 차선 ── */
  const laneSegs = []
  laneSegs.push([HXL, 40, HXL, 560], [HXR, 40, HXR, 560])
  AISLES.forEach((y) => laneSegs.push([HXL, y, HXR, y]))
  DOCK_Y.forEach((y) => { laneSegs.push([96, y, HXL, y]); laneSegs.push([HXR, y, 904, y]) })
  const spurs = CHARGERS.concat([BAY, FL_HOME]).map((c) => [c.x, 540, c.x, STATION_Y])
  // 차선 도색은 재질별로 모아서 InstancedMesh 한 번에 그린다 (드로우콜 절약)
  const stripeSets = new Map()
  function stripe(x1, z1, x2, z2, w, mat, y) {
    if (!stripeSets.has(mat)) stripeSets.set(mat, [])
    stripeSets.get(mat).push([(x1 + x2) / 2, y, (z1 + z2) / 2, Math.hypot(x2 - x1, z2 - z1), w, -Math.atan2(z2 - z1, x2 - x1)])
  }
  function flushStripes() {
    const unit = new THREE.PlaneGeometry(1, 1); disposables.push(unit)
    const o = new THREE.Object3D()
    stripeSets.forEach((list, mat) => {
      const im = new THREE.InstancedMesh(unit, mat, list.length); im.receiveShadow = true; im.renderOrder = 1
      list.forEach(([x, y, z, len, w, rz], i) => { o.position.set(x, y, z); o.rotation.set(-Math.PI / 2, 0, rz); o.scale.set(len, w, 1); o.updateMatrix(); im.setMatrixAt(i, o.matrix) })
      world.add(im)
    })
    stripeSets.clear()
  }
  const dashMat = new THREE.MeshStandardMaterial({ color: 0xf4f4f0, roughness: 0.6 })
  laneSegs.concat(spurs).forEach(([a, b, c, d], idx) => {
    const x1 = X(a), z1 = Z(b), x2 = X(c), z2 = Z(d)
    const isSpur = idx >= laneSegs.length
    const horiz = Math.abs(z2 - z1) < 0.01
    const ext = isSpur ? 0 : 1.3
    const ex1 = horiz ? x1 - (x1 < x2 ? ext : -ext) : x1, ex2 = horiz ? x2 + (x1 < x2 ? ext : -ext) : x2
    const ez1 = horiz ? z1 : z1 - (z1 < z2 ? ext : -ext), ez2 = horiz ? z2 : z2 + (z1 < z2 ? ext : -ext)
    stripe(ex1, ez1, ex2, ez2, isSpur ? 1.5 : 2.7, MAT.laneFill, 0.004)
    if (!isSpur) {
      const nx = horiz ? 0 : 1.3, nz = horiz ? 1.3 : 0
      stripe(ex1 + nx, ez1 + nz, ex2 + nx, ez2 + nz, 0.1, MAT.yellow, 0.008)
      stripe(ex1 - nx, ez1 - nz, ex2 - nx, ez2 - nz, 0.1, MAT.yellow, 0.008)
    }
    const len = Math.hypot(x2 - x1, z2 - z1), n = Math.floor(len / 1.6)
    for (let k = 0; k < n; k++) {
      const t0 = (k * 1.6 + 0.2) / len, t1 = (k * 1.6 + 1.0) / len
      stripe(x1 + (x2 - x1) * t0, z1 + (z2 - z1) * t0, x1 + (x2 - x1) * t1, z1 + (z2 - z1) * t1, 0.08, dashMat, 0.009)
    }
  })

  flushStripes()

  /* ── 바닥 오버레이: 바퀴 자국 · 기름 얼룩 · 진행 방향 화살표 · 보행자 통로 ── */
  {
    const CW = 2048, CHh = 1560
    const fx = (x) => ((x - 80) / 840) * CW, fy = (y) => (y / 640) * CHh, PXM = CW / 42 // 캔버스 px / m
    const ov = canvasTex(CW, CHh, (g) => {
      g.clearRect(0, 0, CW, CHh)
      // 신축 이음 (6m 간격)
      g.strokeStyle = 'rgba(30,34,38,0.28)'; g.lineWidth = 2
      for (let x = 0; x < CW; x += PXM * 6) { g.beginPath(); g.moveTo(x, 0); g.lineTo(x, CHh); g.stroke() }
      for (let y = 0; y < CHh; y += PXM * 6) { g.beginPath(); g.moveTo(0, y); g.lineTo(CW, y); g.stroke() }
      // 바퀴 자국: 차로마다 좌우 통행 바퀴 선
      g.lineCap = 'round'
      laneSegs.forEach(([a, b, c, d]) => {
        const horiz = b === d
        ;[-0.7, 0.7].forEach((off) => [-0.33, 0.33].forEach((wo) => {
          for (let k = 0; k < 3; k++) {
            const o = (off + wo + (Math.random() - 0.5) * 0.08) * PXM
            g.strokeStyle = `rgba(24,22,20,${0.035 + Math.random() * 0.03})`; g.lineWidth = PXM * (0.07 + Math.random() * 0.06)
            g.beginPath()
            if (horiz) { g.moveTo(fx(a) - PXM, fy(b) + o); g.lineTo(fx(c) + PXM, fy(d) + o) } else { g.moveTo(fx(a) + o, fy(b) - PXM); g.lineTo(fx(c) + o, fy(d) + PXM) }
            g.stroke()
          }
        }))
      })
      // 교차로 회전 자국
      AISLES.forEach((y) => [HXL, HXR].forEach((x) => {
        for (let k = 0; k < 4; k++) { g.strokeStyle = 'rgba(24,22,20,0.05)'; g.lineWidth = PXM * 0.08; g.beginPath(); g.arc(fx(x), fy(y), PXM * (0.6 + k * 0.25), Math.random() * 6, Math.random() * 6 + 1.6); g.stroke() }
      }))
      // 기름 · 물 얼룩
      for (let i = 0; i < 26; i++) {
        const x = Math.random() * CW, y = Math.random() * CHh, r = PXM * (0.2 + Math.random() * 0.7)
        const gr = g.createRadialGradient(x, y, 0, x, y, r); gr.addColorStop(0, 'rgba(20,18,16,0.16)'); gr.addColorStop(1, 'rgba(20,18,16,0)')
        g.fillStyle = gr; g.beginPath(); g.ellipse(x, y, r, r * (0.5 + Math.random() * 0.5), Math.random() * 3, 0, 7); g.fill()
      }
      // 진행 방향 화살표 (우측 통행)
      g.fillStyle = 'rgba(245,245,238,0.82)'
      const arrow = (x, y, ang) => {
        g.save(); g.translate(x, y); g.rotate(ang); const L = PXM * 0.9, Wd = PXM * 0.32
        g.beginPath(); g.moveTo(L / 2, 0); g.lineTo(L / 2 - Wd * 1.1, -Wd); g.lineTo(L / 2 - Wd * 1.1, -Wd * 0.4); g.lineTo(-L / 2, -Wd * 0.4); g.lineTo(-L / 2, Wd * 0.4); g.lineTo(L / 2 - Wd * 1.1, Wd * 0.4); g.lineTo(L / 2 - Wd * 1.1, Wd); g.closePath(); g.fill(); g.restore()
      }
      AISLES.forEach((y) => { for (let x = HXL + 70; x < HXR - 40; x += 140) { arrow(fx(x), fy(y) + PXM * 0.7, 0); arrow(fx(x + 70), fy(y) - PXM * 0.7, Math.PI) } })
      ;[HXL, HXR].forEach((x) => { for (let y = 110; y < 520; y += 120) { arrow(fx(x) + PXM * 0.7, fy(y), -Math.PI / 2); arrow(fx(x) - PXM * 0.7, fy(y + 60), Math.PI / 2) } })
      // 보행자 통로 (북쪽 벽을 따라 초록 띠)
      const wy0 = fy(6), wy1 = fy(30)
      g.fillStyle = 'rgba(46,140,84,0.55)'; g.fillRect(fx(96), wy0, fx(904) - fx(96), wy1 - wy0)
      g.fillStyle = 'rgba(245,245,238,0.85)'; g.fillRect(fx(96), wy0 - 4, fx(904) - fx(96), 5); g.fillRect(fx(96), wy1 - 1, fx(904) - fx(96), 5)
      g.font = `800 ${PXM * 0.42}px ${FONT}`; g.textBaseline = 'middle'
      for (let x = 140; x < 880; x += 110) {
        const cx = fx(x), cy = (wy0 + wy1) / 2
        g.beginPath(); g.arc(cx, cy - PXM * 0.2, PXM * 0.09, 0, 7); g.fill()
        g.fillRect(cx - PXM * 0.05, cy - PXM * 0.1, PXM * 0.1, PXM * 0.28)
        g.fillText('보행자 통로', cx + PXM * 0.25, cy)
      }
      // 충전 베이 번호 · 정비 구역 글자
      g.font = `900 ${PXM * 0.55}px ${FONT}`; g.textAlign = 'center'; g.fillStyle = 'rgba(245,245,238,0.8)'
      CHARGERS.forEach((c, i) => g.fillText('C' + (i + 1), fx(c.x), fy(c.y) + PXM * 1.05))
      g.fillStyle = 'rgba(242,194,48,0.85)'; g.fillText('정비 구역 · 관계자 외 출입금지', fx(BAY.x), fy(BAY.y) - PXM * 1.55)
      // 도크 앞 STOP 선
      g.fillStyle = 'rgba(245,245,238,0.85)'
      DOCK_Y.forEach((y) => {
        ;[[HXL - 34, 1], [HXR + 34, -1]].forEach(([x, d]) => {
          g.fillRect(fx(x) - 3, fy(y) - PXM * 1.3, 6, PXM * 2.6)
          g.save(); g.translate(fx(x) - d * PXM * 0.55, fy(y)); g.rotate(d * -Math.PI / 2); g.font = `900 ${PXM * 0.5}px ${FONT}`; g.fillText('STOP', 0, 0); g.restore()
        })
      })
    })
    ov.anisotropy = 8
    disposables.push(ov)
    const ovm = new THREE.MeshStandardMaterial({ map: ov, transparent: true, roughness: 0.6, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -1 })
    const o = new THREE.Mesh(new THREE.PlaneGeometry(floorW, floorD), ovm)
    o.rotation.x = -Math.PI / 2; o.position.set((WALL_L + WALL_R) / 2, 0.003, (WALL_N + FLOOR_S) / 2); o.receiveShadow = true; o.renderOrder = 1
    world.add(o)
  }

  /* ── 건물: 벽 · 기둥 · 지붕 트러스 · 조명 ── */
  const wallT = 0.3
  // 북쪽 벽
  world.add(mesh(box(floorW + 0.6, WALL_H, wallT), MAT.wall, (WALL_L + WALL_R) / 2, WALL_H / 2, WALL_N - wallT / 2))
  // 동·서 벽 (도크 개구부 제외)
  const DOOR_W = 3.4, DOOR_H = 3.9
  ;[WALL_L - wallT / 2, WALL_R + wallT / 2].forEach((wx) => {
    const cuts = DOCK_Y.map((y) => Z(y)).sort((a, b) => a - b)
    let zPrev = WALL_N
    cuts.forEach((zc) => {
      const z0 = zc - DOOR_W / 2
      if (z0 > zPrev) world.add(mesh(box(wallT, WALL_H, z0 - zPrev), MAT.wall, wx, WALL_H / 2, (zPrev + z0) / 2))
      world.add(mesh(box(wallT, WALL_H - DOOR_H, DOOR_W), MAT.wall, wx, DOOR_H + (WALL_H - DOOR_H) / 2, zc))
      zPrev = zc + DOOR_W / 2
    })
    world.add(mesh(box(wallT, WALL_H, FLOOR_S - zPrev), MAT.wall, wx, WALL_H / 2, (zPrev + FLOOR_S) / 2))
  })
  // 기둥 · 트러스
  for (let x = WALL_L; x <= WALL_R + 0.01; x += floorW / 6) world.add(mesh(box(0.45, WALL_H + 0.3, 0.45), MAT.steel, x, (WALL_H + 0.3) / 2, WALL_N + 0.25))
  const lampGeo = new THREE.CylinderGeometry(0.35, 0.45, 0.35, 20); disposables.push(lampGeo)
  const lampPos = []
  const lampDisc = new THREE.CircleGeometry(0.33, 20); disposables.push(lampDisc)
  ;[-11, -3, 5, 13].forEach((z) => {
    world.add(mesh(box(floorW + 0.4, 0.42, 0.22), MAT.steel, (WALL_L + WALL_R) / 2, WALL_H + 0.2, z, false))
    for (let x = WALL_L + 4; x < WALL_R - 2; x += 7) lampPos.push([x, z])
  })
  {
    const cableGeo = box(0.02, 0.55, 0.02), o = new THREE.Object3D()
    const ih = new THREE.InstancedMesh(lampGeo, MAT.steel, lampPos.length), id = new THREE.InstancedMesh(lampDisc, MAT.lampOn, lampPos.length), ic = new THREE.InstancedMesh(cableGeo, MAT.steel, lampPos.length)
    lampPos.forEach(([x, z], i) => {
      o.position.set(x, WALL_H - 0.4, z); o.rotation.set(0, 0, 0); o.updateMatrix(); ih.setMatrixAt(i, o.matrix)
      o.position.set(x, WALL_H - 0.05, z); o.updateMatrix(); ic.setMatrixAt(i, o.matrix)
      o.position.set(x, WALL_H - 0.58, z); o.rotation.set(Math.PI / 2, 0, 0); o.updateMatrix(); id.setMatrixAt(i, o.matrix)
    })
    world.add(ih, id, ic)
  }

  /* ── 공기감: 조명 아래 빛 웅덩이 · 빛 기둥 · 떠다니는 먼지 ── */
  {
    const poolTex = canvasTex(128, 128, (g, w, h) => {
      const gr = g.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2)
      gr.addColorStop(0, 'rgba(255,236,205,1)'); gr.addColorStop(0.45, 'rgba(255,236,205,0.35)'); gr.addColorStop(1, 'rgba(255,236,205,0)')
      g.fillStyle = gr; g.fillRect(0, 0, w, h)
    })
    const shaftTex = canvasTex(8, 128, (g, w, h) => {
      const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, 'rgba(255,240,215,1)'); gr.addColorStop(1, 'rgba(255,240,215,0)')
      g.fillStyle = gr; g.fillRect(0, 0, w, h)
    })
    disposables.push(poolTex, shaftTex)
    const poolMat = new THREE.MeshBasicMaterial({ map: poolTex, transparent: true, opacity: 0.16, depthWrite: false, blending: THREE.AdditiveBlending })
    const shaftMat = new THREE.MeshBasicMaterial({ map: shaftTex, transparent: true, opacity: 0.045, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide })
    const poolGeo = new THREE.PlaneGeometry(7.5, 7.5), shaftGeo = new THREE.CylinderGeometry(0.34, 2.9, WALL_H - 0.6, 24, 1, true)
    disposables.push(poolGeo, shaftGeo)
    const ip = new THREE.InstancedMesh(poolGeo, poolMat, lampPos.length), is = new THREE.InstancedMesh(shaftGeo, shaftMat, lampPos.length), o = new THREE.Object3D()
    lampPos.forEach(([x, z], i) => {
      o.position.set(x, 0.012, z); o.rotation.set(-Math.PI / 2, 0, 0); o.updateMatrix(); ip.setMatrixAt(i, o.matrix)
      o.position.set(x, (WALL_H - 0.6) / 2, z); o.rotation.set(0, 0, 0); o.updateMatrix(); is.setMatrixAt(i, o.matrix)
    })
    ip.renderOrder = 2; is.renderOrder = 3; world.add(ip, is)
    fx.shafts = is
  }
  const DUST_N = 700
  const dustBase = new Float32Array(DUST_N * 3), dustPos = new Float32Array(DUST_N * 3)
  for (let i = 0; i < DUST_N; i++) {
    const [lx, lz] = lampPos[i % lampPos.length]
    const r = Math.sqrt(Math.random()) * 2.6, an = Math.random() * 6.283, y = 0.3 + Math.random() * (WALL_H - 1.2)
    dustBase[i * 3] = lx + Math.cos(an) * r * (y / WALL_H + 0.2); dustBase[i * 3 + 1] = y; dustBase[i * 3 + 2] = lz + Math.sin(an) * r * (y / WALL_H + 0.2)
  }
  dustPos.set(dustBase)
  const dustGeo = new THREE.BufferGeometry(); dustGeo.setAttribute('position', new THREE.BufferAttribute(dustPos, 3))
  const dustTex = canvasTex(32, 32, (g, w, h) => { const gr = g.createRadialGradient(16, 16, 0, 16, 16, 16); gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(1, 'rgba(255,255,255,0)'); g.fillStyle = gr; g.fillRect(0, 0, w, h) })
  disposables.push(dustTex)
  const dust = new THREE.Points(dustGeo, new THREE.PointsMaterial({ map: dustTex, size: 0.045, color: 0xfff0d8, transparent: true, opacity: 0.55, depthWrite: false, blending: THREE.AdditiveBlending }))
  dust.frustumCulled = false; world.add(dust)
  fx.dust = dust

  /* ── 도크: 셔터 · 범퍼 · 신호등 ── */
  const dockDoors = { in: [], out: [] }
  const lightGeo = new THREE.SphereGeometry(0.12, 12, 8); disposables.push(lightGeo)
  ;[['in', WALL_L], ['out', WALL_R]].forEach(([kind, wx]) => {
    const outward = kind === 'in' ? -1 : 1
    DOCK_Y.forEach((y, i) => {
      const zc = Z(y)
      const door = mesh(box(0.12, DOOR_H, DOOR_W - 0.2), MAT.door, wx + outward * 0.05, DOOR_H / 2, zc, false)
      world.add(door)
      ;[-1, 1].forEach((s) => {
        world.add(mesh(box(0.35, 0.5, 0.25), MAT.rubber, wx + outward * 0.35, 1.1, zc + s * (DOOR_W / 2 - 0.25)))
        world.add(mesh(box(0.1, DOOR_H, 0.12), MAT.yellow, wx + outward * 0.18, DOOR_H / 2, zc + s * (DOOR_W / 2 + 0.05)))
      })
      const lampMat = new THREE.MeshStandardMaterial({ color: 0x220000, emissive: 0xff3b3b, emissiveIntensity: 2 })
      const lamp = new THREE.Mesh(lightGeo, lampMat); lamp.position.set(wx - outward * 0.3, DOOR_H + 0.4, zc - DOOR_W / 2 - 0.4); world.add(lamp)
      // 도크 레벨러 (바닥 철판)
      const lev = mesh(box(1.6, 0.04, DOOR_W - 0.4), MAT.steel, wx - outward * 0.8, 0.02, zc, false); world.add(lev)
      dockDoors[kind].push({ door, lampMat, open: 0 })
      // 도크 번호 · 권역 표지
      const label = kind === 'in' ? 'D' + (i + 1) : 'D' + (i + 4)
      const sub = kind === 'in' ? '입고 · 운송장 OCR' : HUBS[i].name + '행'
      const col = kind === 'in' ? '#2a3440' : HUBS[i].col
      const fg = kind === 'in' ? '#ffffff' : '#0b0f14'
      const st = texSign(label, sub, col, fg)
      const sm = new THREE.MeshStandardMaterial({ map: st, roughness: 0.6, emissive: 0xffffff, emissiveMap: st, emissiveIntensity: 0.25 })
      const sign = new THREE.Mesh(new THREE.PlaneGeometry(2.6, 0.98), sm)
      sign.position.set(wx - outward * 0.2, DOOR_H + 1.35, zc)
      sign.rotation.y = outward > 0 ? -Math.PI / 2 : Math.PI / 2
      world.add(sign)
    })
  })

  /* ── 랙: 업라이트(파랑) · 빔(주황) — 바닥 칸은 AMR, 위 3단은 무인 지게차 ── */
  const RACK_H = 6.6, LEVELS = LEVEL_H
  const rackX0 = X(236.5), rackX1 = X(679.5), rackW = rackX1 - rackX0
  const uprightGeo = box(0.09, RACK_H, 0.09), braceGeo = box(0.04, 0.04, 1), beamGeo = box(rackW, 0.13, 0.07)
  const nUp = ROWS.length * (NS + 1) * 3
  const upInst = new THREE.InstancedMesh(uprightGeo, MAT.upright, nUp); upInst.castShadow = true; upInst.receiveShadow = true
  const braceInst = new THREE.InstancedMesh(braceGeo, MAT.upright, ROWS.length * (NS + 1) * 2 * 5); braceInst.castShadow = true
  const dm = new THREE.Object3D()
  let ui = 0, bi = 0
  const palletGeo = box(1.1, 0.13, 0.95)
  const beamPos = []
  ROWS.forEach(([y0, y1], r) => {
    const z0 = Z(y0) + 0.05, z1 = Z(y1) - 0.05, zm = (z0 + z1) / 2
    const cold = r === COLD_ROW
    // 냉장 창고 줄은 북쪽 면 한 줄짜리 랙
    const zs = cold ? [z0, zm] : [z0, zm, z1], pairs = cold ? [[z0, zm]] : [[z0, zm], [zm, z1]]
    for (let i = 0; i <= NS; i++) {
      const x = X(237.5 + i * 49)
      zs.forEach((z) => { dm.position.set(x, RACK_H / 2, z); dm.rotation.set(0, 0, 0); dm.scale.set(1, 1, 1); dm.updateMatrix(); upInst.setMatrixAt(ui++, dm.matrix) })
      pairs.forEach(([za, zb]) => {
        const len = zb - za
        ;[0.6, 2.4, 4.2, 6.0].forEach((hy) => { dm.position.set(x, hy, (za + zb) / 2); dm.rotation.set(0, 0, 0); dm.scale.set(1, 1, len); dm.updateMatrix(); braceInst.setMatrixAt(bi++, dm.matrix) })
        dm.position.set(x, 3.3, (za + zb) / 2); dm.rotation.set(Math.atan2(5.4, len), 0, 0); dm.scale.set(1, 1, Math.hypot(5.4, len)); dm.updateMatrix(); braceInst.setMatrixAt(bi++, dm.matrix)
      })
    }
    LEVELS.forEach((ly) => zs.forEach((z) => beamPos.push([(rackX0 + rackX1) / 2, ly, z])))
    // 구역 표지판 (랙 서쪽 끝 위)
    const zn = ZONES[r]
    const st = texSign(zn.tag + '  ' + zn.name, cold ? '냉장 · 냉동 창고' : zn.name + '행 보관', zn.col, '#0b0f14')
    const sm = new THREE.MeshStandardMaterial({ map: st, roughness: 0.5, emissive: 0xffffff, emissiveMap: st, emissiveIntensity: 0.35, side: THREE.DoubleSide })
    const sign = new THREE.Mesh(new THREE.PlaneGeometry(3.2, 1.2), sm)
    const zc = cold ? (z0 + zm) / 2 : zm
    sign.position.set(rackX0 - 0.2, RACK_H + 0.9, zc); world.add(sign)
    world.add(mesh(box(0.05, 1.0, 0.05), MAT.steel, rackX0 - 0.2, RACK_H + 0.2, zc, false))
    // 바닥 구역 컬러 표시
    const zp = mesh(new THREE.PlaneGeometry(rackW + 0.4, (cold ? zm - z0 : z1 - z0) + 0.2), new THREE.MeshStandardMaterial({ color: new THREE.Color(zn.col), roughness: 0.6, transparent: true, opacity: cold ? 0.22 : 0.12, depthWrite: false }), (rackX0 + rackX1) / 2, 0.006, zc, false)
    zp.rotation.x = -Math.PI / 2; world.add(zp)
  })
  upInst.count = ui; braceInst.count = bi
  {
    const bm = new THREE.InstancedMesh(beamGeo, MAT.beam, beamPos.length); bm.castShadow = true; bm.receiveShadow = true
    beamPos.forEach(([x, y, z], i) => { dm.position.set(x, y, z); dm.rotation.set(0, 0, 0); dm.scale.set(1, 1, 1); dm.updateMatrix(); bm.setMatrixAt(i, dm.matrix) })
    world.add(bm)
  }
  world.add(upInst, braceInst)
  // 빔 위치 라벨 (흰 바탕 · 바코드) — 랙 앞뒤 빔마다
  {
    const lt = canvasTex(128, 64, (g, w, h) => {
      g.fillStyle = '#f7f7f2'; g.fillRect(0, 0, w, h); g.fillStyle = '#111'; g.font = `800 20px ${FONT}`; g.fillText('LOC', 6, 22)
      for (let x = 6; x < w - 6; x += 3) if (Math.random() < 0.6) g.fillRect(x, 30, Math.random() < 0.3 ? 2 : 1, 28)
    })
    const lg = new THREE.PlaneGeometry(0.2, 0.1); disposables.push(lg)
    const lbl = new THREE.InstancedMesh(lg, new THREE.MeshStandardMaterial({ map: lt, roughness: 0.6 }), ROWS.length * LEVELS.length * NS * 2)
    let k = 0
    ROWS.forEach(([y0, y1], r) => {
      const zf = Z(y0) + 0.05 - 0.045, zb = Z(y1) - 0.05 + 0.045
      LEVELS.forEach((ly) => { for (let i = 0; i < NS; i++) {
        dm.position.set(X(SX(i)), ly, zf); dm.rotation.set(0, Math.PI, 0); dm.scale.set(1, 1, 1); dm.updateMatrix(); lbl.setMatrixAt(k++, dm.matrix)
        if (r !== COLD_ROW) { dm.position.set(X(SX(i)), ly, zb); dm.rotation.set(0, 0, 0); dm.updateMatrix(); lbl.setMatrixAt(k++, dm.matrix) }
      } })
    })
    lbl.count = k; world.add(lbl)
  }
  // 랙 끝 보호대 (노란 가드레일)
  ROWS.forEach(([y0, y1], r) => {
    const yb = r === COLD_ROW ? (y0 + y1) / 2 : y1
    const zc = (Z(y0) + Z(yb)) / 2, len = Z(yb) - Z(y0) + 0.2
    ;[rackX0 - 0.25, rackX1 + 0.25].forEach((gx) => {
      world.add(mesh(box(0.12, 0.42, len), MAT.yellow, gx, 0.21, zc))
      ;[-len / 2 + 0.06, len / 2 - 0.06].forEach((dz) => world.add(mesh(box(0.1, 0.5, 0.1), MAT.yellow, gx, 0.25, zc + dz)))
    })
  })

  /* ── 충전 · 정비 스테이션 ── */
  CHARGERS.forEach((c) => {
    const x = X(c.x), z = Z(c.y)
    world.add(mesh(box(1.5, 0.03, 1.2), MAT.rubber, x, 0.015, z, false))
    world.add(mesh(box(0.42, 1.1, 0.28), MAT.white, x, 0.55, z + 0.95))
    const led = new THREE.Mesh(box(0.3, 0.06, 0.02), new THREE.MeshStandardMaterial({ color: 0x0b2a18, emissive: 0x4fdc8a, emissiveIntensity: 2.4 }))
    led.position.set(x, 0.85, z + 0.8); world.add(led)
  })
  const bx = X(BAY.x), bz = Z(BAY.y)
  const hatchFrame = (w, d) => {
    ;[[0, -d / 2, w, 0.3], [0, d / 2, w, 0.3], [-w / 2, 0, 0.3, d], [w / 2, 0, 0.3, d]].forEach(([dx, dz, ww, dd]) => {
      const p = mesh(new THREE.PlaneGeometry(ww, dd), MAT.hatch, bx + dx, 0.01, bz + dz, false); p.rotation.x = -Math.PI / 2; world.add(p)
    })
  }
  hatchFrame(3.8, 2.6)
  world.add(mesh(box(1.4, 1.1, 0.55), MAT.cabinet, bx + 2.7, 0.55, bz + 0.6))
  world.add(mesh(box(0.08, 2.6, 0.08), MAT.steel, bx - 1.9, 1.3, bz + 1.3, false))
  world.add(mesh(box(0.08, 2.6, 0.08), MAT.steel, bx + 1.9, 1.3, bz + 1.3, false))
  world.add(mesh(box(3.9, 0.12, 0.12), MAT.yellow, bx, 2.6, bz + 1.3, false))
  {
    const st = texSign('MAINTENANCE', '정비 스테이션', '#c9312c', '#ffffff')
    const sm = new THREE.MeshStandardMaterial({ map: st, emissive: 0xffffff, emissiveMap: st, emissiveIntensity: 0.3, side: THREE.DoubleSide })
    const s = new THREE.Mesh(new THREE.PlaneGeometry(3, 1.1), sm); s.position.set(bx, 3.3, bz + 1.3); world.add(s)
  }
  {
    const st = texSign('CHARGE', '충전 · 대기', '#1f3a2b', '#4fdc8a')
    const sm = new THREE.MeshStandardMaterial({ map: st, emissive: 0xffffff, emissiveMap: st, emissiveIntensity: 0.3, side: THREE.DoubleSide })
    const s = new THREE.Mesh(new THREE.PlaneGeometry(2.6, 0.95), sm); s.position.set(X(360), 2.2, Z(600) + 1.3); world.add(s)
  }

  /* ── 엣지 게이트웨이 (북쪽 벽) ── */
  const gwPos = new THREE.Vector3(X(GW.x), 3.4, WALL_N + 0.25)
  world.add(mesh(box(1.0, 0.7, 0.25), MAT.white, gwPos.x, gwPos.y, gwPos.z))
  ;[-0.3, 0.3].forEach((dx) => world.add(mesh(box(0.03, 0.55, 0.03), MAT.rubber, gwPos.x + dx, gwPos.y + 0.6, gwPos.z, false)))
  const gwLedMat = new THREE.MeshStandardMaterial({ color: 0x062421, emissive: 0x3fe0cc, emissiveIntensity: 1.5 })
  const gwLed = new THREE.Mesh(new THREE.SphereGeometry(0.07, 12, 8), gwLedMat); gwLed.position.set(gwPos.x + 0.3, gwPos.y, gwPos.z + 0.14); world.add(gwLed)
  const gwTag = tag('EDGE GW', 'twin-tag twin-tag-gw'); gwTag.position.set(gwPos.x, gwPos.y + 1.3, gwPos.z + 0.3); world.add(gwTag)

  /* ── OCR 게이트 (입고 도크 앞) ── */
  const scanners = DOCK_Y.map((y) => {
    const x = X(118), z = Z(y)
    const g = new THREE.Group()
    ;[-1.45, 1.45].forEach((dz) => g.add(mesh(box(0.14, 2.9, 0.14), MAT.steel, 0, 1.45, dz)))
    g.add(mesh(box(0.18, 0.16, 3.05), MAT.steel, 0, 2.9, 0))
    const cam = mesh(box(0.3, 0.2, 0.36), MAT.rubber, 0.05, 2.72, 0); g.add(cam)
    const lens = new THREE.Mesh(new THREE.CircleGeometry(0.06, 16), new THREE.MeshStandardMaterial({ color: 0x000000, emissive: 0x7fd6ff, emissiveIntensity: 1.5 }))
    lens.position.set(0.05, 2.6, 0); lens.rotation.x = Math.PI / 2; g.add(lens)
    const beamMat = new THREE.MeshBasicMaterial({ color: 0xffb84d, transparent: true, opacity: 0, side: THREE.DoubleSide, depthWrite: false, blending: THREE.AdditiveBlending })
    const beam = new THREE.Mesh(new THREE.PlaneGeometry(2.6, 2.6), beamMat); beam.rotation.y = Math.PI / 2; beam.position.set(0.05, 1.3, 0); g.add(beam)
    const lbl = tag('', 'twin-tag twin-tag-ocr'); lbl.position.set(0, 3.5, 0); lbl.visible = false; g.add(lbl)
    g.position.set(x, 0, z); world.add(g)
    return { g, beam, beamMat, lbl }
  })

  /* ── 트럭 ── */
  function wheel(group, x, z, r) {
    const geo = new THREE.CylinderGeometry(r, r, 0.32, 18); disposables.push(geo)
    const w = mesh(geo, MAT.tire, x, r, z); w.rotation.x = Math.PI / 2; group.add(w)
    // 26.10.28 휠: 림 · 허브캡 · 볼트 (바깥쪽으로 살짝 튀어나오게)
    const rimGeo = new THREE.CylinderGeometry(r * 0.6, r * 0.6, 0.345, 20), capGeo = new THREE.CylinderGeometry(r * 0.22, r * 0.22, 0.36, 12); disposables.push(rimGeo, capGeo)
    const rim = mesh(rimGeo, MAT.deck, x, r, z, false); rim.rotation.x = Math.PI / 2; group.add(rim)
    const cap = mesh(capGeo, MAT.steel, x, r, z, false); cap.rotation.x = Math.PI / 2; group.add(cap)
    for (let i = 0; i < 6; i++) { const a = (i / 6) * Math.PI * 2, bx = Math.cos(a) * r * 0.4, by = Math.sin(a) * r * 0.4
      const bolt = mesh(lugGeo, MAT.rubber, x + bx, r + by, z, false); bolt.rotation.x = Math.PI / 2; bolt.scale.y = 1; group.add(bolt) }
  }
  const lugGeo = new THREE.CylinderGeometry(0.028, 0.028, 0.37, 6); disposables.push(lugGeo)
  const ttHead = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xfff4d6, emissiveIntensity: 1.8 })
  const ttMark = new THREE.MeshStandardMaterial({ color: 0xff9a1f, emissive: 0xff8a00, emissiveIntensity: 1.4 })
  const ttRib = new THREE.MeshStandardMaterial({ color: 0xcfd6dc, roughness: 0.5, metalness: 0.5 })
  const ttGrille = new THREE.MeshStandardMaterial({ color: 0x15181c, roughness: 0.4, metalness: 0.7 })
  // 26.10.28 트럭 공통 디테일: 전조등 · 그릴 · 범퍼 · 측면 창 · 사이드미러 (dir: 앞이 향하는 X 방향)
  function cabDetail(g, cabX, dir, cw, cd, yMid, yTop, lightY) {
    const fx = cabX + dir * (cw / 2 + 0.01)
    g.add(mesh(box(0.05, 0.55, cd * 0.62), ttGrille, fx, lightY + 0.0, 0, false))
    for (let i = -2; i <= 2; i++) g.add(mesh(box(0.06, 0.025, cd * 0.62), MAT.deck, fx + dir * 0.012, lightY + i * 0.1, 0, false))
    ;[-1, 1].forEach((sd) => {
      g.add(mesh(box(0.06, 0.16, 0.34), ttHead, fx, lightY, sd * (cd / 2 - 0.3), false))
      g.add(mesh(box(0.5, 0.62, 0.02), MAT.glass, cabX - dir * 0.15, yMid, sd * (cd / 2 + 0.012), false))            // 측면 창
      g.add(mesh(box(0.05, 0.3, 0.12), MAT.rubber, cabX + dir * (cw / 2 - 0.2), yTop - 0.45, sd * (cd / 2 + 0.18), false)) // 사이드미러
    })
    g.add(mesh(box(0.22, 0.28, cd + 0.05), MAT.steel, fx + dir * 0.08, lightY - 0.55, 0))                              // 앞 범퍼
  }
  // 화물차 번호판 (영업용 노란 번호판) · 후미등 · 후부 안전판
  let plateSeq = 0
  function plateText(kind) {
    const IN = ['부산 98사 4417', '부산 96아 2085', '인천 94자 7731'], OUT = ['인천 82배 3051', '서울 87배 1294', '경기 85배 6620']
    return (kind === 'in' ? IN : OUT)[plateSeq++ % 3]
  }
  const plateGeo = new THREE.PlaneGeometry(0.52, 0.11), tailGeo = box(0.05, 0.12, 0.3); disposables.push(plateGeo)
  const tailMat = new THREE.MeshStandardMaterial({ color: 0x300606, emissive: 0xff2a1f, emissiveIntensity: 1.6 })
  const chevTex = canvasTex(256, 32, (g, w, h) => { for (let x = -h, k = 0; x < w + h; x += 24, k++) { g.fillStyle = k % 2 ? '#d61f1f' : '#f4f4f0'; g.beginPath(); g.moveTo(x, 0); g.lineTo(x + 24, 0); g.lineTo(x + 24 - h, h); g.lineTo(x - h, h); g.fill() } })
  const chevMat = new THREE.MeshStandardMaterial({ map: chevTex, roughness: 0.4, emissive: 0xffffff, emissiveMap: chevTex, emissiveIntensity: 0.15 })
  disposables.push(chevTex)
  // 번호판 텍스처: OCR 결과가 오면 같은 텍스처를 다시 그린다 (앞 · 뒤 번호판이 같이 바뀜)
  function plateTex(text) {
    let t = null
    const draw = (txt) => {
      const cv = t ? t.image : null, cg = cv.getContext('2d'), w = cv.width, h = cv.height
      cg.fillStyle = '#f2c200'; cg.fillRect(0, 0, w, h); cg.strokeStyle = '#1a1a1a'; cg.lineWidth = 4; cg.strokeRect(3, 3, w - 6, h - 6)
      cg.fillStyle = '#121212'; cg.font = `800 ${txt.length > 11 ? 30 : 34}px ${FONT}`; cg.textAlign = 'center'; cg.textBaseline = 'middle'; cg.fillText(txt, w / 2, h / 2 + 2)
      t.needsUpdate = true
    }
    t = canvasTex(256, 56, () => {}); t.userData.draw = draw; draw(text); disposables.push(t)
    return t
  }
  function plateMesh(g, x, dir, y, pt) {
    const pl = new THREE.Mesh(plateGeo, new THREE.MeshStandardMaterial({ map: pt, roughness: 0.35, metalness: 0.2 }))
    pl.position.set(x + dir * 0.06, y, 0); pl.rotation.y = dir > 0 ? Math.PI / 2 : -Math.PI / 2; g.add(pl)
  }
  function truckRear(g, x, dir, y, Wd, text) {
    const pt = plateTex(text); g.userData.plateTex = pt
    const pm = new THREE.MeshStandardMaterial({ map: pt, roughness: 0.35, metalness: 0.2 })
    const pl = new THREE.Mesh(plateGeo, pm); pl.position.set(x + dir * 0.06, y, 0); pl.rotation.y = dir > 0 ? Math.PI / 2 : -Math.PI / 2; g.add(pl)
    ;[-1, 1].forEach((s) => { const tl = new THREE.Mesh(tailGeo, tailMat); tl.position.set(x + dir * 0.04, y + 0.1, s * (Wd / 2 - 0.25)); g.add(tl) })
    const bar = new THREE.Mesh(box(0.06, 0.14, Wd - 0.3), chevMat); bar.position.set(x + dir * 0.1, y - 0.22, 0); g.add(bar)
  }
  // 26.10.28 입고 화물차 소속: 이름은 DB에서 온다 (번호판 → trucks.company → companies.company_name, 없으면 hub_vehicles.carrier).
  //  입구 OCR이 번호판을 읽고 서버가 소속을 돌려주면 트레일러 옆면 · 캡 문에 업체명을 칠하고, 업체마다 고유 색을 쓴다.
  const carrierColor = (name) => { let h = 0; for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) % 360; return { col: `hsl(${h},62%,38%)`, cab: `hsl(${h},55%,26%)` } }
  function drawDecal(cg, w, h, name, big) {
    cg.clearRect(0, 0, w, h); if (!name) return
    const c = carrierColor(name).col
    cg.fillStyle = c; cg.beginPath(); cg.moveTo(0, h * 0.18); cg.lineTo(h * 0.5, h * 0.18); cg.lineTo(h * 0.3, h * 0.82); cg.lineTo(0, h * 0.82); cg.closePath(); cg.fill()
    let fs = Math.round(h * (big ? 0.5 : 0.46)); cg.textBaseline = 'middle'; cg.font = `900 ${fs}px ${FONT}`
    while (cg.measureText(name).width > w - h * 0.75 && fs > 12) { fs -= 2; cg.font = `900 ${fs}px ${FONT}` }
    cg.fillStyle = c; cg.fillText(name, h * 0.62, h * 0.5)
  }
  const carrierDecal = (wpx, hpx, big) => { const t = canvasTex(wpx, hpx, () => {}); t.userData.big = big; return t }
  function makeTrailerTruck() {
    const liv = { name: '', texs: [], stripeMat: null, cabMat: null }
    // 입고: 세미 트레일러 (뒷문이 건물 쪽 +X)
    const g = new THREE.Group()
    const L = 8.2, Wd = 2.5, Hh = 2.8
    const bodyMat = new THREE.MeshStandardMaterial({ color: 0xe6eaee, roughness: 0.45, metalness: 0.3, transparent: true, opacity: 1 })
    const roofMat = new THREE.MeshStandardMaterial({ color: 0xdfe4e8, roughness: 0.4, metalness: 0.3, transparent: true, opacity: 0.18, depthWrite: false })
    g.add(mesh(box(L, 0.25, Wd), MAT.steel, -L / 2, 1.15, 0))
    ;[-1, 1].forEach((s) => g.add(mesh(box(L, Hh, 0.06), bodyMat, -L / 2, 1.3 + Hh / 2, s * (Wd / 2))))
    g.add(mesh(box(0.06, Hh, Wd), bodyMat, -L, 1.3 + Hh / 2, 0))
    g.add(mesh(box(L, 0.05, Wd), roofMat, -L / 2, 1.3 + Hh, 0, false))
    const stripeMat = new THREE.MeshStandardMaterial({ color: 0x24456b, roughness: 0.4 }); liv.stripeMat = stripeMat
    ;[-1, 1].forEach((s) => g.add(mesh(box(L * 0.92, 0.35, 0.02), stripeMat, -L / 2, 1.9, s * (Wd / 2 + 0.03), false)))
    // 소속 표기: 옆면 큰 로고 · 캡 문 작은 로고
    { const dt = carrierDecal(1024, 256, true); disposables.push(dt); liv.texs.push(dt)
      const dm = new THREE.MeshStandardMaterial({ map: dt, transparent: true, roughness: 0.5, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 })
      const dg = new THREE.PlaneGeometry(4.8, 1.2); disposables.push(dg)
      ;[-1, 1].forEach((s) => { const d = new THREE.Mesh(dg, dm); d.position.set(-L / 2 - 0.2, 3.05, s * (Wd / 2 + 0.075)); if (s < 0) d.rotation.y = Math.PI; g.add(d) }) }
    // 트랙터 (캡)
    const cabX = -L - 2.1
    liv.cabMat = new THREE.MeshStandardMaterial({ color: 0x24456b, roughness: 0.3, metalness: 0.6 })
    g.add(mesh(new RoundedBoxGeometry(2.4, 2.6, 2.45, 3, 0.18), liv.cabMat, cabX, 1.75, 0))
    { const dt = carrierDecal(512, 128, false); disposables.push(dt); liv.texs.push(dt)
      const dm = new THREE.MeshStandardMaterial({ map: dt, transparent: true, roughness: 0.5, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 })
      const dg = new THREE.PlaneGeometry(1.3, 0.32); disposables.push(dg)
      ;[-1, 1].forEach((s) => { const d = new THREE.Mesh(dg, dm); d.position.set(cabX + 0.05, 1.35, s * (1.225 + 0.22)); if (s < 0) d.rotation.y = Math.PI; g.add(d) }) }
    liv.set = (name) => {
      name = name || ''; if (liv.name === name) return; liv.name = name
      const cc = name ? carrierColor(name) : null
      liv.stripeMat.color.set(cc ? cc.col : '#24456b'); liv.cabMat.color.set(cc ? cc.cab : '#24456b')
      liv.texs.forEach((t) => { const cv = t.image; drawDecal(cv.getContext('2d'), cv.width, cv.height, name, t.userData.big); t.needsUpdate = true })
    }
    g.userData.livery = liv
    g.add(mesh(box(0.05, 1.0, 2.1), MAT.glass, cabX - 1.21, 2.4, 0, false))
    g.add(mesh(box(2.6, 0.5, 2.3), MAT.steel, cabX + 0.5, 0.75, 0))
    ;[[-1.2, 0.5], [-L + 1.3, 0.5], [-L + 2.5, 0.5], [cabX - 0.6, 0.5], [cabX + 1.0, 0.5]].forEach(([wx, r]) => { wheel(g, wx, Wd / 2 - 0.1, r); wheel(g, wx, -Wd / 2 + 0.1, r) })
    truckRear(g, 0.02, 1, 0.75, Wd, plateText('in'))
    plateMesh(g, cabX - 1.24, -1, 0.85, g.userData.plateTex) // 캡 앞 번호판 (입구 카메라가 읽는 쪽)
    ;[-1, 1].forEach((s) => g.add(mesh(box(0.08, 0.32, 0.05), MAT.steel, cabX - 0.9, 2.5, s * 1.38, false)))
    // 26.10.28 디테일: 트레일러 보강 리브 · 하부 스커트 · 흙받이 · 측면 표시등 · 후문 이음선
    ;[-1, 1].forEach((s) => {
      for (let i = 1; i < 9; i++) g.add(mesh(box(0.07, Hh - 0.1, 0.035), ttRib, -i * (L / 9), 1.3 + Hh / 2, s * (Wd / 2 + 0.03), false))
      g.add(mesh(box(L * 0.72, 0.3, 0.04), MAT.rubber, -L / 2 - 0.4, 0.98, s * (Wd / 2 - 0.1), false))
      for (let i = 0; i < 7; i++) g.add(mesh(box(0.1, 0.06, 0.03), ttMark, -0.5 - i * (L / 7.5), 1.3 + Hh - 0.05, s * (Wd / 2 + 0.05), false))
      g.add(mesh(box(0.04, 0.46, 0.5), MAT.rubber, -1.75, 0.5, s * (Wd / 2 - 0.28), false))
    })
    g.add(mesh(box(0.03, Hh - 0.1, 0.02), MAT.rubber, -0.04, 1.3 + Hh / 2, 0, false))
    cabDetail(g, cabX, -1, 2.4, 2.45, 2.45, 3.0, 1.15)
    g.add(mesh(new THREE.CylinderGeometry(0.07, 0.07, 2.3, 10), MAT.deck, cabX + 1.35, 2.95, 1.05)); disposables.push(g.children[g.children.length - 1].geometry)
    const tank = mesh(new THREE.CylinderGeometry(0.26, 0.26, 1.3, 16), MAT.deck, cabX + 0.55, 0.62, 1.12); tank.rotation.z = Math.PI / 2; g.add(tank); disposables.push(tank.geometry)
    return g
  }
  function makeCourierTruck(hub) {
    // 출고: 권역행 택배차 (뒷문이 건물 쪽 -X)
    const g = new THREE.Group()
    const L = 5.2, Wd = 2.3, Hh = 2.3
    const h = HUBS[hub]
    const sideTex = canvasTex(512, 256, (cg, w, hh) => {
      cg.fillStyle = '#f4f6f8'; cg.fillRect(0, 0, w, hh)
      cg.fillStyle = h.col; cg.fillRect(0, hh - 70, w, 70)
      cg.fillStyle = '#0b2540'; cg.font = `800 54px ${FONT}`; cg.fillText('S CARGO', 30, 90)
      cg.fillStyle = '#0b0f14'; cg.font = `800 40px ${FONT}`; cg.fillText(h.name + '행', 30, hh - 22)
    })
    const sideMat = new THREE.MeshStandardMaterial({ map: sideTex, roughness: 0.45, metalness: 0.2 })
    const roofMat = new THREE.MeshStandardMaterial({ color: 0xf4f6f8, roughness: 0.4, transparent: true, opacity: 0.18, depthWrite: false })
    g.add(mesh(box(L, 0.22, Wd), MAT.steel, L / 2, 0.95, 0))
    ;[-1, 1].forEach((s) => g.add(mesh(box(L, Hh, 0.06), sideMat, L / 2, 1.06 + Hh / 2, s * (Wd / 2))))
    g.add(mesh(box(0.06, Hh, Wd), MAT.truckBody, L, 1.06 + Hh / 2, 0))
    g.add(mesh(box(L, 0.05, Wd), roofMat, L / 2, 1.06 + Hh, 0, false))
    const cabX = L + 1.15
    const cab = mesh(new RoundedBoxGeometry(2.0, 2.1, 2.2, 3, 0.2), MAT.courierCab, cabX, 1.4, 0); g.add(cab)
    g.add(mesh(box(0.05, 0.8, 1.9), MAT.glass, cabX + 1.01, 1.95, 0, false))
    g.add(mesh(box(2.05, 0.18, 2.24), new THREE.MeshStandardMaterial({ color: new THREE.Color(h.col), roughness: 0.4 }), cabX, 0.75, 0))
    ;[[1.0, 0.45], [L - 0.9, 0.45], [cabX + 0.3, 0.45]].forEach(([wx, r]) => { wheel(g, wx, Wd / 2 - 0.1, r); wheel(g, wx, -Wd / 2 + 0.1, r) })
    truckRear(g, -0.02, -1, 0.62, Wd, plateText('out'))
    plateMesh(g, cabX + 1.02, 1, 0.62, g.userData.plateTex) // 캡 앞 번호판
    ;[-1, 1].forEach((s) => g.add(mesh(box(0.08, 0.3, 0.05), MAT.steel, cabX + 0.7, 2.0, s * 1.25, false)))
    // 26.10.28 디테일: 적재함 리브 · 표시등 · 후문 이음선 · 캡 전조등/그릴/창
    ;[-1, 1].forEach((s) => {
      for (let i = 1; i < 6; i++) g.add(mesh(box(0.06, Hh - 0.1, 0.03), ttRib, i * (L / 6), 1.06 + Hh / 2, s * (Wd / 2 + 0.03), false))
      for (let i = 0; i < 5; i++) g.add(mesh(box(0.1, 0.06, 0.03), ttMark, 0.5 + i * (L / 5.3), 1.06 + Hh - 0.05, s * (Wd / 2 + 0.05), false))
      g.add(mesh(box(0.04, 0.4, 0.45), MAT.rubber, 1.6, 0.45, s * (Wd / 2 - 0.25), false))
    })
    g.add(mesh(box(0.03, Hh - 0.1, 0.02), MAT.rubber, 0.04, 1.06 + Hh / 2, 0, false))
    cabDetail(g, cabX, 1, 2.0, 2.2, 1.95, 2.5, 0.95)
    return g
  }
  const inTrucks = DOCK_Y.map((y) => { const t = makeTrailerTruck(); t.position.set(WALL_L - 0.6, 0, Z(y)); world.add(t); return t })
  const outTrucks = DOCK_Y.map((y, i) => { const t = makeCourierTruck(i); t.position.set(WALL_R + 0.6, 0, Z(y)); world.add(t); return t })

  /* ── 입구 · 출구 번호판 OCR (gate_api.py · YOLO + CRNN) ── */
  // 트럭이 게이트 앞(off = 0.45)에서 멈췄을 때 캡 앞 번호판 위치에 카메라 게이트를 세운다
  const GATE_OFF = 0.18 * 22 // ease(0.3) * 22 — amrSim 의 GATE_OFF(0.3) 와 맞춘다
  function ocrGantry(x, dir, label) {
    const g = new THREE.Group(), z0 = Z(DOCK_Y[0]) - 2.2, z1 = Z(DOCK_Y[2]) + 2.2, zc = (z0 + z1) / 2
    ;[z0, z1].forEach((z) => g.add(mesh(box(0.22, 5.4, 0.22), MAT.steel, 0, 2.7, z)))
    g.add(mesh(box(0.3, 0.32, z1 - z0 + 0.3), MAT.steel, 0, 5.4, zc))
    const beams = DOCK_Y.map((y) => {
      g.add(mesh(box(0.42, 0.26, 0.34), MAT.rubber, dir * 0.15, 5.1, Z(y)))
      const lens = new THREE.Mesh(new THREE.CircleGeometry(0.07, 16), new THREE.MeshStandardMaterial({ color: 0x000000, emissive: 0x7fd6ff, emissiveIntensity: 1.6 }))
      lens.position.set(dir * 0.37, 5.1, Z(y)); lens.rotation.y = dir * Math.PI / 2; g.add(lens)
      // 인식 중일 때 번호판 쪽으로 비추는 빛 (스캔 장면)
      const bm = new THREE.MeshBasicMaterial({ color: 0xffb84d, transparent: true, opacity: 0, side: THREE.DoubleSide, depthWrite: false, blending: THREE.AdditiveBlending })
      const beam = new THREE.Mesh(new THREE.ConeGeometry(1.4, 4.4, 24, 1, true), bm)
      beam.position.set(dir * 1.6, 3.0, Z(y)); beam.rotation.z = dir * (Math.PI / 2 - 0.55); g.add(beam)
      return { beam, bm }
    })
    const t = tag(label, 'twin-tag twin-tag-gate'); t.position.set(0, 6.3, zc); g.add(t)
    g.position.set(x, 0, 0); world.add(g)
    return beams
  }
  const gateBeams = [
    ocrGantry(WALL_L - 0.6 - GATE_OFF - 8.2 - 2.1 - 1.2 - 1.4, 1, 'IN GATE · 번호판 OCR'),
    ocrGantry(WALL_R + 0.6 + GATE_OFF + 5.2 + 1.15 + 1.0 + 1.4, -1, 'OUT GATE · 번호판 OCR'),
  ]
  const KIND_KO = { CARGO: '화물차', COURIER: '택배차', UNKNOWN: '미등록' }
  const plateTags = [inTrucks, outTrucks].map((arr, side) => arr.map((t) => {
    const o = tag('', 'twin-tag twin-tag-plate'); o.position.set(side ? 3 : -6, side ? 4.2 : 5.0, 0); o.visible = false; t.add(o)
    return { o, key: '', plate: '', dockedAt: -1 }
  }))
  function plateTagUpdate(d, i, side, tr, t) {
    const pt = plateTags[side][i], oc = d.ocr
    const gb = gateBeams[side][i]
    const scanning = oc && tr.visible && ((oc.st === 'scan' && d.state === 'arriving') || (oc.exit === 'scan' && d.state === 'leaving'))
    gb.bm.opacity = scanning ? 0.16 + 0.12 * Math.abs(Math.sin(t * 9)) : 0
    if (!oc || !tr.visible) { pt.o.visible = false; return }
    if (d.state === 'docked') { if (pt.dockedAt < 0) pt.dockedAt = t } else pt.dockedAt = -1
    if (pt.oc !== oc) { pt.oc = oc; pt.changedAt = t } // 앱: 서버 결과가 새로 들어오면 잠깐 보여 준다
    const showing = d.state !== 'docked' || t - pt.dockedAt < 10 || t - (pt.changedAt || -99) < 8
    pt.o.visible = showing
    // 번호판 텍스처를 인식 결과로 바꾼다
    const want = oc.plate ? fmtPlate(oc.plate) : ''
    if (want && want !== pt.plate) { pt.plate = want; tr.userData.plateTex.userData.draw(want) }
    if (tr.userData.livery) tr.userData.livery.set(oc.st === 'ok' ? oc.company : '') // 소속(DB) 도색
    if (!showing) return
    const where = side ? '출고 D' + (i + 4) : '입고 D' + (i + 1)
    let cls = '', head = '', body = '', foot = ''
    const conf = (c) => (c != null ? ' ' + c + '%' : '')
    if (d.state === 'leaving' && oc.exit) {
      cls = ' is-out'
      if (oc.exit === 'scan') { head = '출차 OCR · 인식 중…'; body = fmtPlate(oc.plate) }
      else { head = (oc.exit.ok ? '출차 OCR ✓' : '출차 번호 불일치') + conf(oc.exit.conf); body = fmtPlate(oc.exit.plate) || '판독 불가'; foot = side ? HUBS[d.hub].name + '행 · ' + d.boxes.length + '건 출고 기록' : '하차 완료 · 출차 기록'; if (!oc.exit.ok) cls = ' is-block' }
    } else if (oc.st === 'scan') { cls = ' is-scan'; head = '입차 OCR · 번호판 인식 중…'; body = '· · · · ·' }
    else if (oc.st === 'block') { cls = ' is-block'; head = '미등록 차량 · 진입 차단' + conf(oc.conf); body = fmtPlate(oc.plate) || '판독 불가'; foot = 'DB 등록 차량 아님 → 회차' }
    else if (oc.st === 'off') { cls = ' is-off'; head = 'OCR 서버 응답 없음'; body = fmtPlate(oc.plate); foot = '예시 번호 · gate_api.py 확인' }
    else { head = '입차 OCR ✓' + conf(oc.conf); body = fmtPlate(oc.plate); foot = (oc.company ? oc.company + ' · ' : '') + (KIND_KO[oc.kind] || '') + ' · 등록 차량 → ' + where }
    const key = cls + head + body + foot
    if (key === pt.key) return
    pt.key = key
    pt.o.element.className = 'twin-tag twin-tag-plate' + cls
    pt.o.element.innerHTML = '<i>' + head + '</i><b>' + body + '</b>' + (foot ? '<span>' + foot + '</span>' : '')
  }

  /* ── 현장 소품: 볼라드 · 소화기 · 도크 실 · 롤테이너 · 안전 표지 ── */
  {
    const bolGeo = new THREE.CylinderGeometry(0.11, 0.11, 1.0, 16), bandGeo = new THREE.CylinderGeometry(0.115, 0.115, 0.08, 16)
    disposables.push(bolGeo, bandGeo)
    const bollards = []
    DOCK_Y.forEach((y) => [WALL_L + 0.45, WALL_R - 0.45].forEach((x) => [-1, 1].forEach((s) => bollards.push([x, Z(y) + s * (DOOR_W / 2 + 0.35)]))))
    const bi1 = new THREE.InstancedMesh(bolGeo, MAT.yellow, bollards.length), bi2 = new THREE.InstancedMesh(bandGeo, MAT.rubber, bollards.length * 2)
    bi1.castShadow = true
    bollards.forEach(([x, z], k) => {
      dm.position.set(x, 0.5, z); dm.rotation.set(0, 0, 0); dm.scale.set(1, 1, 1); dm.updateMatrix(); bi1.setMatrixAt(k, dm.matrix)
      ;[0.68, 0.84].forEach((y, j) => { dm.position.set(x, y, z); dm.updateMatrix(); bi2.setMatrixAt(k * 2 + j, dm.matrix) })
    })
    world.add(bi1, bi2)
    // 도크 실 (트럭이 붙는 문 바깥 검은 쿠션)
    ;[[WALL_L - 0.3, -1], [WALL_R + 0.3, 1]].forEach(([wx]) => DOCK_Y.forEach((y) => {
      const zc = Z(y)
      world.add(mesh(box(0.35, 0.35, DOOR_W + 0.5), MAT.rubber, wx, DOOR_H + 0.1, zc, false))
      ;[-1, 1].forEach((s) => world.add(mesh(box(0.35, DOOR_H, 0.25), MAT.rubber, wx, DOOR_H / 2, zc + s * (DOOR_W / 2 + 0.12), false)))
    }))
    // 소화기 + 표지 (북쪽 벽 기둥마다)
    const extMat = new THREE.MeshStandardMaterial({ color: 0xc81e1e, roughness: 0.35, metalness: 0.3 })
    const extSign = canvasTex(128, 128, (g, w, h) => { g.fillStyle = '#c81e1e'; g.fillRect(0, 0, w, h); g.fillStyle = '#fff'; g.font = `900 34px ${FONT}`; g.textAlign = 'center'; g.fillText('소화기', w / 2, 58); g.font = `800 20px ${FONT}`; g.fillText('FIRE EXT.', w / 2, 96) })
    const extSignMat = new THREE.MeshStandardMaterial({ map: extSign, emissive: 0xffffff, emissiveMap: extSign, emissiveIntensity: 0.2 })
    for (let x = WALL_L + floorW / 6; x < WALL_R - 1; x += floorW / 6) {
      const e = mesh(new THREE.CylinderGeometry(0.09, 0.09, 0.5, 14), extMat, x + 0.4, 0.75, WALL_N + 0.2); world.add(e)
      const sg = new THREE.Mesh(new THREE.PlaneGeometry(0.4, 0.4), extSignMat); sg.position.set(x + 0.4, 1.55, WALL_N + 0.02); world.add(sg)
    }
    // 북쪽 벽: 고창(채광창) · 안전 배너 · AMR 운행 표지
    const winMat = new THREE.MeshStandardMaterial({ color: 0x8fb3d9, emissive: 0xa9c8ec, emissiveIntensity: 0.55, roughness: 0.2, metalness: 0.4 })
    for (let x = WALL_L + 1.5; x < WALL_R - 1.5; x += 3.2) world.add(mesh(box(2.6, 0.75, 0.05), winMat, x + 1.3, WALL_H - 0.9, WALL_N + 0.01, false))
    const bTex = canvasTex(1024, 128, (g, w, h) => {
      g.fillStyle = '#0b2540'; g.fillRect(0, 0, w, h); g.fillStyle = '#ff6b00'; g.fillRect(0, h - 14, w, 14)
      g.fillStyle = '#fff'; g.font = `900 58px ${FONT}`; g.textBaseline = 'middle'; g.fillText('S CARGO 허브', 34, 58)
      g.font = `700 34px ${FONT}`; g.fillStyle = '#ffd38a'; g.fillText('안전이 최우선입니다 · 무재해 365일', 560, 58)
    })
    const ban = new THREE.Mesh(new THREE.PlaneGeometry(12, 1.5), new THREE.MeshStandardMaterial({ map: bTex, emissive: 0xffffff, emissiveMap: bTex, emissiveIntensity: 0.18 }))
    ban.position.set(-3, 4.6, WALL_N + 0.02); world.add(ban)
    const aTex = canvasTex(256, 320, (g, w, h) => {
      g.fillStyle = '#ffd21f'; g.fillRect(0, 0, w, h); g.fillStyle = '#111'; g.fillRect(0, 0, w, 70)
      g.fillStyle = '#ffd21f'; g.font = `900 44px ${FONT}`; g.textAlign = 'center'; g.fillText('주 의', w / 2, 50)
      g.fillStyle = '#111'; g.font = `900 40px ${FONT}`; g.fillText('AMR', w / 2, 140); g.fillText('운행 구역', w / 2, 190)
      g.font = `700 24px ${FONT}`; g.fillText('보행자는 초록 통로', w / 2, 250); g.fillText('로만 다니세요', w / 2, 282)
    })
    const aMat = new THREE.MeshStandardMaterial({ map: aTex, emissive: 0xffffff, emissiveMap: aTex, emissiveIntensity: 0.15 })
    ;[-15, 9].forEach((x) => { const sg = new THREE.Mesh(new THREE.PlaneGeometry(0.8, 1.0), aMat); sg.position.set(x, 2.2, WALL_N + 0.02); world.add(sg) })
  }
  // 롤테이너 (출고 대기 · 권역 색) — 동쪽 도크 사이 빈 공간
  const cageTex = canvasTex(128, 128, (g, w, h) => { g.clearRect(0, 0, w, h); g.strokeStyle = '#c9d0d6'; g.lineWidth = 3; for (let k = 0; k <= w; k += 16) { g.beginPath(); g.moveTo(k, 0); g.lineTo(k, h); g.stroke(); g.beginPath(); g.moveTo(0, k); g.lineTo(w, k); g.stroke() } })
  disposables.push(cageTex)
  const cageMat = new THREE.MeshStandardMaterial({ map: cageTex, transparent: true, alphaTest: 0.3, side: THREE.DoubleSide, metalness: 0.7, roughness: 0.4 })
  const smallBoxes = { 일반: [], 취급주의: [] }
  function rollCage(x, z, hub, fill) {
    const g = new THREE.Group(), Wc = 0.8, Dc = 0.7, Hc = 1.6
    g.add(mesh(box(Wc, 0.06, Dc), MAT.steel, 0, 0.16, 0))
    ;[[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(([a, b]) => { const w = mesh(new THREE.CylinderGeometry(0.05, 0.05, 0.05, 10), MAT.tire, a * (Wc / 2 - 0.08), 0.06, b * (Dc / 2 - 0.08)); w.rotation.x = Math.PI / 2; g.add(w) })
    ;[[0, Dc / 2, Wc, 0], [0, -Dc / 2, Wc, 0], [-Wc / 2, 0, Dc, Math.PI / 2]].forEach(([dx, dz, len, ry]) => { const p = new THREE.Mesh(new THREE.PlaneGeometry(len, Hc), cageMat); p.position.set(dx, 0.19 + Hc / 2, dz); p.rotation.y = ry; g.add(p) })
    const tagM = new THREE.MeshStandardMaterial({ color: new THREE.Color(HUBS[hub].col), emissive: new THREE.Color(HUBS[hub].col), emissiveIntensity: 0.25 })
    g.add(mesh(box(0.5, 0.2, 0.01), tagM, 0, 1.45, Dc / 2 + 0.01, false))
    const marks = []
    for (let k = 0; k < fill; k++) { const m = new THREE.Object3D(); m.position.set(((k % 2) - 0.5) * 0.36, 0.33 + Math.floor(k / 4) * 0.27, ((Math.floor(k / 2) % 2) - 0.5) * 0.32); m.rotation.y = (Math.random() - 0.5) * 0.2; m.scale.set(0.34, 0.26, 0.3); g.add(m); marks.push(m) }
    g.position.set(x, 0, z); g.rotation.y = (Math.random() - 0.5) * 0.15; world.add(g)
    g.updateMatrixWorld(true); marks.forEach((m) => { smallBoxes['일반'].push(m.matrixWorld.clone()); g.remove(m) })
  }
  ;[[215, 0], [385, 1]].forEach(([y, k]) => { rollCage(X(800), Z(y) - 0.5, k, 7); rollCage(X(822), Z(y) + 0.4, k + 1, 5); rollCage(X(860), Z(y), k === 0 ? 2 : 0, 8) })
  // 입고 대기 팔레트 (서쪽 도크 사이, 랩핑)
  ;[[215], [385]].forEach(([y]) => {
    const g = new THREE.Group()
    g.add(mesh(palletGeo, MAT.wood, 0, 0.07, 0))
    const marks = []
    for (let L = 0; L < 3; L++) for (let k = 0; k < 4; k++) { const m = new THREE.Object3D(); m.position.set(((k % 2) - 0.5) * 0.45, 0.29 + L * 0.3, (Math.floor(k / 2) - 0.5) * 0.38); m.scale.set(0.44, 0.3, 0.37); g.add(m); marks.push(m) }
    g.add(mesh(box(0.95, 0.92, 0.8), MAT.wrap, 0, 0.6, 0, false))
    g.position.set(X(128), 0, Z(y)); g.rotation.y = 0.1; world.add(g)
    g.updateMatrixWorld(true); marks.forEach((m) => { smallBoxes['취급주의'].push(m.matrixWorld.clone()); g.remove(m) })
  })
  // 롤테이너 · 대기 팔레트의 작은 박스들을 한 번에 그린다
  {
    const unit = compactBox(box(1, 1, 1))
    Object.entries(smallBoxes).forEach(([hd, list]) => {
      if (!list.length) return
      const im = new THREE.InstancedMesh(unit, cargoMaterials('S', hd), list.length); im.castShadow = true; im.receiveShadow = true
      list.forEach((mx, i) => im.setMatrixAt(i, mx)); world.add(im)
    })
  }

  /* ── 냉장 · 냉동 창고: 단열 패널 방 + 고속 셔터 + 냉각기 ── */
  const coldDoors = []
  let coldDisp = null, coldMist = null
  {
    const CR = COLD_ROOM, H = 7.0, T = 0.16
    const x0 = X(CR.x0), x1 = X(CR.x1), z0 = Z(CR.y0), z1 = Z(CR.y1), zc = Z(CR.doorY)
    const panelTex = canvasTex(256, 128, (g, w, h) => {
      g.fillStyle = '#eef3f6'; g.fillRect(0, 0, w, h)
      for (let x = 0; x < w; x += 32) { g.fillStyle = 'rgba(120,140,155,0.35)'; g.fillRect(x, 0, 1.5, h); g.fillStyle = 'rgba(255,255,255,0.6)'; g.fillRect(x + 2, 0, 1, h) }
      for (let i = 0; i < 400; i++) { g.fillStyle = `rgba(130,150,165,${Math.random() * 0.05})`; g.fillRect(Math.random() * w, Math.random() * h, 2, 2) }
    }, [8, 1])
    disposables.push(panelTex)
    const panel = new THREE.MeshStandardMaterial({ map: panelTex, roughness: 0.45, metalness: 0.15 })
    const glassy = new THREE.MeshStandardMaterial({ color: 0xcfe6f5, roughness: 0.1, metalness: 0.1, transparent: true, opacity: 0.16, depthWrite: false, side: THREE.DoubleSide })
    const trim = new THREE.MeshStandardMaterial({ color: 0x9fb2c2, roughness: 0.4, metalness: 0.6 })
    const W = x1 - x0, D = z1 - z0
    // 북쪽 벽 (불투명) · 남쪽 벽 (안이 보이도록 반투명 컷어웨이 + 아래 걸레받이)
    world.add(mesh(box(W, H, T), panel, (x0 + x1) / 2, H / 2, z0))
    const sw = new THREE.Mesh(box(W, H - 0.5, 0.04), glassy); sw.position.set((x0 + x1) / 2, 0.5 + (H - 0.5) / 2, z1); sw.renderOrder = 4; world.add(sw)
    world.add(mesh(box(W, 0.5, T), panel, (x0 + x1) / 2, 0.25, z1))
    world.add(mesh(box(W + 0.2, 0.18, 0.22), trim, (x0 + x1) / 2, H, z0, false), mesh(box(W + 0.2, 0.18, 0.22), trim, (x0 + x1) / 2, H, z1, false))
    // 동 · 서 벽: 통로 높이에 고속 셔터 문
    const DW = 2.8, DH = 3.4
    ;[x0, x1].forEach((wx, k) => {
      const zA = z0, zB = zc - DW / 2, zC = zc + DW / 2, zD = z1
      if (zB > zA) world.add(mesh(box(T, H, zB - zA), panel, wx, H / 2, (zA + zB) / 2))
      world.add(mesh(box(T, H, zD - zC), panel, wx, H / 2, (zC + zD) / 2))
      world.add(mesh(box(T, H - DH, DW), panel, wx, DH + (H - DH) / 2, zc))
      world.add(mesh(box(T + 0.18, 0.4, DW + 0.3), MAT.steel, wx, DH + 0.2, zc, false)) // 셔터 감김통
      const doorMat = new THREE.MeshStandardMaterial({ color: 0x1f5fae, roughness: 0.55, metalness: 0.1 })
      const door = mesh(box(0.06, DH, DW - 0.1), doorMat, wx, DH / 2, zc, false)
      const win = mesh(box(0.07, 0.35, DW - 0.5), new THREE.MeshStandardMaterial({ color: 0xbfe2ff, transparent: true, opacity: 0.5, roughness: 0.1 }), 0, 0.6, 0, false); door.add(win)
      world.add(door)
      ;[-1, 1].forEach((sd) => world.add(mesh(box(0.14, DH, 0.12), MAT.yellow, wx, DH / 2, zc + sd * (DW / 2 + 0.06), false)))
      coldDoors.push({ door, open: 0, px: k ? CR.x1 : CR.x0, py: CR.doorY })
      // 문 옆 온도 표시판
      const side = k ? 1 : -1
      const disp = canvasTex(256, 128, () => {})
      const dm2 = new THREE.MeshStandardMaterial({ map: disp, emissive: 0xffffff, emissiveMap: disp, emissiveIntensity: 0.9 })
      const pnl = new THREE.Mesh(new THREE.PlaneGeometry(1.1, 0.55), dm2)
      pnl.position.set(wx + side * 0.1, 2.4, zc + DW / 2 + 0.9); pnl.rotation.y = side > 0 ? Math.PI / 2 : -Math.PI / 2; world.add(pnl)
      if (!coldDisp) coldDisp = []
      coldDisp.push(disp)
    })
    // 냉각기 (천장 아래 북쪽 벽) + 바닥 성에
    const evapTex = canvasTex(256, 96, (g, w, h) => {
      g.fillStyle = '#dfe7ee'; g.fillRect(0, 0, w, h)
      ;[48, 128, 208].forEach((cx) => { g.fillStyle = '#2a3440'; g.beginPath(); g.arc(cx, h / 2, 34, 0, 7); g.fill(); g.strokeStyle = '#7f909f'; g.lineWidth = 3; for (let k = 0; k < 6; k++) { g.beginPath(); g.moveTo(cx, h / 2); g.lineTo(cx + Math.cos(k) * 30, h / 2 + Math.sin(k) * 30); g.stroke() } })
    })
    disposables.push(evapTex)
    const evapMat = new THREE.MeshStandardMaterial({ map: evapTex, roughness: 0.4, metalness: 0.3 })
    const evaps = []
    for (let k = 0; k < 3; k++) {
      const ex = x0 + W * (0.2 + k * 0.3)
      const ev = new THREE.Mesh(box(2.4, 0.9, 0.8), [MAT.white, MAT.white, MAT.white, MAT.white, evapMat, MAT.white])
      ev.position.set(ex, H - 1.4, z0 + 0.55); world.add(ev); evaps.push(ex)
    }
    const frost = mesh(new THREE.PlaneGeometry(W - 0.2, D - 0.2), new THREE.MeshStandardMaterial({ color: 0xdff2ff, transparent: true, opacity: 0.18, roughness: 0.2, depthWrite: false }), (x0 + x1) / 2, 0.01, (z0 + z1) / 2, false)
    frost.rotation.x = -Math.PI / 2; frost.renderOrder = 2; world.add(frost)
    // 차가운 공기 (냉각기에서 내려오는 흰 김)
    const MN = 260, mb = new Float32Array(MN * 3), mp = new Float32Array(MN * 3)
    for (let i = 0; i < MN; i++) { mb[i * 3] = evaps[i % 3] + (Math.random() - 0.5) * 2.4; mb[i * 3 + 1] = Math.random() * (H - 1.8); mb[i * 3 + 2] = z0 + 0.6 + Math.random() * (D - 1) }
    mp.set(mb)
    const mg = new THREE.BufferGeometry(); mg.setAttribute('position', new THREE.BufferAttribute(mp, 3))
    const mistTex = canvasTex(64, 64, (g, w, h) => { const gr = g.createRadialGradient(32, 32, 0, 32, 32, 32); gr.addColorStop(0, 'rgba(255,255,255,0.9)'); gr.addColorStop(1, 'rgba(255,255,255,0)'); g.fillStyle = gr; g.fillRect(0, 0, w, h) })
    disposables.push(mistTex)
    const mist = new THREE.Points(mg, new THREE.PointsMaterial({ map: mistTex, size: 0.32, color: 0xd8efff, transparent: true, opacity: 0.14, depthWrite: false }))
    mist.frustumCulled = false; world.add(mist)
    fx.mist = mist
    coldMist = { mg, mb, mp, MN, top: H - 1.8 }
    // 창고 이름판 (서쪽 문 위)
    const st = texSign('냉장 · 냉동 창고', '고속 셔터 · 문 열림 최소화', '#1f5fae', '#ffffff')
    const sg = new THREE.Mesh(new THREE.PlaneGeometry(3.0, 1.1), new THREE.MeshStandardMaterial({ map: st, emissive: 0xffffff, emissiveMap: st, emissiveIntensity: 0.3, side: THREE.DoubleSide }))
    sg.position.set(x0 - 0.12, DH + 1.4, zc); sg.rotation.y = -Math.PI / 2; world.add(sg)
  }
  function drawColdDisp(tex, temp) {
    const c = tex.image, g = c.getContext('2d'), w = c.width, h = c.height
    g.fillStyle = '#071018'; g.fillRect(0, 0, w, h); g.strokeStyle = '#1f5fae'; g.lineWidth = 6; g.strokeRect(3, 3, w - 6, h - 6)
    g.fillStyle = '#9fd8ff'; g.font = `800 22px ${FONT}`; g.fillText('냉장 창고 내부', 16, 34)
    g.fillStyle = temp > 6 ? '#ffb84d' : '#4fdc8a'; g.font = `800 58px ${FONT}`; g.fillText(temp.toFixed(1) + '°C', 16, 100)
    g.fillStyle = '#8597aa'; g.font = `600 16px ${FONT}`; g.fillText('기준 2~8°C', 168, 116)
    tex.needsUpdate = true
  }
  coldDisp.forEach((t) => drawColdDisp(t, 3.2))
  let coldTemp = 3.2, coldDispAcc = 0

  /* ── 작업자 (안전모 · 형광 조끼) ── */
  const skinMat = new THREE.MeshStandardMaterial({ color: 0xd9a67e, roughness: 0.7 })
  const pantsMat = new THREE.MeshStandardMaterial({ color: 0x24303d, roughness: 0.85 })
  const vestTex = canvasTex(64, 64, (g, w, h) => { g.fillStyle = '#ff7a1a'; g.fillRect(0, 0, w, h); g.fillStyle = '#e8eef2'; g.fillRect(0, 20, w, 6); g.fillRect(0, 40, w, 6); g.fillRect(14, 0, 6, 20); g.fillRect(44, 0, 6, 20) })
  disposables.push(vestTex)
  const vestMat = new THREE.MeshStandardMaterial({ map: vestTex, roughness: 0.6, emissive: 0xff7a1a, emissiveIntensity: 0.06 })
  const vestMatY = new THREE.MeshStandardMaterial({ map: vestTex, color: 0xd8ff40, roughness: 0.6 })
  const helmetW = new THREE.MeshStandardMaterial({ color: 0xf4f6f8, roughness: 0.35 }), helmetY = new THREE.MeshStandardMaterial({ color: 0xffc21a, roughness: 0.35 })
  function makeWorker(vest, helmet, prop) {
    const g = new THREE.Group(), hip = new THREE.Group(); hip.position.y = 0.9; g.add(hip)
    const legs = [-1, 1].map((s) => { const l = new THREE.Group(); l.position.set(0, 0, s * 0.1); hip.add(l); l.add(mesh(new RoundedBoxGeometry(0.15, 0.88, 0.15, 2, 0.05), pantsMat, 0, -0.44, 0)); l.add(mesh(box(0.26, 0.09, 0.13), MAT.rubber, 0.05, -0.86, 0)); return l })
    const torso = new THREE.Group(); hip.add(torso)
    torso.add(mesh(new RoundedBoxGeometry(0.26, 0.6, 0.42, 3, 0.08), vest, 0, 0.32, 0))
    const head = new THREE.Group(); head.position.y = 0.76; torso.add(head)
    head.add(mesh(new THREE.SphereGeometry(0.11, 16, 12), skinMat, 0, 0, 0))
    const hm = mesh(new THREE.SphereGeometry(0.13, 16, 10, 0, Math.PI * 2, 0, Math.PI / 2), helmet, 0, 0.03, 0); head.add(hm)
    head.add(mesh(box(0.1, 0.02, 0.24), helmet, 0.12, 0.03, 0))
    const arms = [-1, 1].map((s) => { const a = new THREE.Group(); a.position.set(0, 0.56, s * 0.25); torso.add(a); a.add(mesh(new RoundedBoxGeometry(0.11, 0.6, 0.11, 2, 0.04), vest, 0, -0.28, 0)); a.add(mesh(new THREE.SphereGeometry(0.055, 10, 8), skinMat, 0, -0.6, 0)); return a })
    if (prop === 'scanner') { const sc = mesh(box(0.16, 0.05, 0.08), MAT.rubber, 0.1, -0.62, 0); arms[1].add(sc); arms[1].rotation.z = 0.9 }
    if (prop === 'tablet') { const tb = mesh(box(0.02, 0.22, 0.3), MAT.glass, 0.36, 0.3, 0); tb.rotation.z = 0.6; torso.add(tb); arms[0].rotation.z = 0.85; arms[1].rotation.z = 0.85 }
    g.userData = { hip, torso, head, legs, arms, phase: Math.random() * 6 }
    world.add(g)
    return g
  }
  const workers = {
    inbound: makeWorker(vestMat, helmetW, 'scanner'),
    outbound: makeWorker(vestMatY, helmetY, 'tablet'),
    tech: makeWorker(vestMat, helmetY, null),
    office: makeWorker(vestMatY, helmetW, null),
  }
  workers.inbound.position.set(X(140), 0, Z(388)); workers.inbound.rotation.y = Math.PI * 0.85
  workers.outbound.position.set(X(840), 0, Z(250)); workers.outbound.rotation.y = -Math.PI * 0.3
  workers.tech.position.set(X(BAY.x) + 2.2, 0, Z(BAY.y) + 0.1); workers.tech.rotation.y = Math.PI
  workers.office.position.set(X(845), 0, Z(612)); workers.office.rotation.y = Math.PI / 2
  // 관제 데스크 (모니터에 미니 대시보드)
  {
    const dx = X(845), dz = Z(595)
    world.add(mesh(box(2.2, 0.06, 0.8), MAT.white, dx, 0.76, dz))
    ;[[-1.0, -0.32], [1.0, -0.32], [-1.0, 0.32], [1.0, 0.32]].forEach(([a, b]) => world.add(mesh(box(0.05, 0.74, 0.05), MAT.steel, dx + a, 0.37, dz + b, false)))
    const scr = canvasTex(256, 160, (g, w, h) => {
      g.fillStyle = '#0b1622'; g.fillRect(0, 0, w, h); g.fillStyle = '#3fe0cc'; g.font = `800 14px ${FONT}`; g.fillText('AMR 관제', 10, 20)
      g.strokeStyle = '#3fe0cc'; g.lineWidth = 2; g.beginPath(); for (let x = 0; x < 230; x += 10) g.lineTo(12 + x, 110 - Math.sin(x / 25) * 20 - x / 12); g.stroke()
      g.strokeStyle = '#ffb84d'; g.beginPath(); for (let x = 0; x < 230; x += 10) g.lineTo(12 + x, 130 - Math.cos(x / 30) * 10); g.stroke()
      ;['#4fdc8a', '#4fdc8a', '#ffb84d', '#4fdc8a', '#6b7a8a'].forEach((c, i) => { g.fillStyle = c; g.fillRect(150 + i * 18, 12, 12, 12) })
    })
    disposables.push(scr)
    const scrMat = new THREE.MeshStandardMaterial({ map: scr, emissive: 0xffffff, emissiveMap: scr, emissiveIntensity: 0.9, roughness: 0.3 })
    ;[-0.45, 0.45].forEach((o) => {
      const m = new THREE.Mesh(new THREE.PlaneGeometry(0.62, 0.38), scrMat); m.position.set(dx + o, 1.08, dz - 0.18); m.rotation.y = 0; world.add(m)
      world.add(mesh(box(0.66, 0.42, 0.03), MAT.rubber, dx + o, 1.08, dz - 0.2, false))
      world.add(mesh(box(0.05, 0.25, 0.05), MAT.steel, dx + o, 0.9, dz - 0.22, false))
    })
    world.add(mesh(box(0.5, 0.08, 0.5), MAT.rubber, dx + 0.95, 0.48, dz + 0.6)); world.add(mesh(box(0.5, 0.5, 0.06), MAT.rubber, dx + 0.95, 0.75, dz + 0.85))
    workers.office.position.set(dx - 0.2, 0, dz + 0.62); workers.office.rotation.y = Math.PI / 2
  }

  /* ── AMR ── */
  const amrGeo = {
    chassis: new RoundedBoxGeometry(1.25, 0.3, 0.95, 4, 0.09),
    led: box(1.27, 0.035, 0.97),
    deck: new THREE.CylinderGeometry(0.42, 0.42, 0.05, 32),
    plate: box(1.05, 0.03, 0.82),
    lidar: new THREE.CylinderGeometry(0.085, 0.095, 0.11, 20),
    bumper: box(0.06, 0.12, 0.85),
    fan: new THREE.CircleGeometry(3.2, 32, -0.5, 1.0),
    ring: new THREE.RingGeometry(0.95, 1.05, 48),
    selRing: new THREE.RingGeometry(1.0, 1.08, 64),
    maintRing: new THREE.RingGeometry(1.15, 1.32, 64, 1, 0, Math.PI * 2),
  }
  Object.assign(amrGeo, {
    hazard: new THREE.PlaneGeometry(0.85, 0.1), wheel: new THREE.CylinderGeometry(0.1, 0.1, 0.07, 18),
    estopBase: new THREE.CylinderGeometry(0.06, 0.06, 0.03, 16), estop: new THREE.CylinderGeometry(0.04, 0.045, 0.05, 16),
    lamp: box(0.03, 0.04, 0.12), side: new THREE.PlaneGeometry(0.9, 0.16),
  })
  Object.values(amrGeo).forEach((g) => disposables.push(g))
  const headMat = new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xffffff, emissiveIntensity: 1.6 })
  const rearMat = new THREE.MeshStandardMaterial({ color: 0x300606, emissive: 0xff2a1f, emissiveIntensity: 1.2 })
  const sideMats = S.amrs.map((a) => {
    const t = canvasTex(256, 48, (g, w, h) => {
      g.fillStyle = '#20262c'; g.fillRect(0, 0, w, h); g.fillStyle = '#ff6b00'; g.fillRect(0, h - 5, w, 5)
      g.fillStyle = '#e8edf2'; g.font = `900 24px ${FONT}`; g.textBaseline = 'middle'; g.fillText('S-CARGO', 10, 22)
      g.fillStyle = '#3fe0cc'; g.font = `800 22px ${FONT}`; g.textAlign = 'right'; g.fillText(a.name, w - 10, 22)
    })
    disposables.push(t)
    return new THREE.MeshStandardMaterial({ map: t, roughness: 0.5, metalness: 0.3 })
  })
  // 무인 지게차 (카운터밸런스형 · 무인 센서 타워 · 2단 마스트)
  const flBody = new THREE.MeshStandardMaterial({ color: 0xf2b705, roughness: 0.45, metalness: 0.3 })
  const flDark = new THREE.MeshStandardMaterial({ color: 0x2b3036, roughness: 0.5, metalness: 0.5 })
  function makeForklift(a, idx) {
    const g = new THREE.Group(), body = new THREE.Group(); g.add(body)
    body.add(mesh(new RoundedBoxGeometry(1.5, 0.55, 1.05, 3, 0.08), flBody, -0.3, 0.42, 0))
    body.add(mesh(new RoundedBoxGeometry(0.5, 0.8, 1.05, 3, 0.1), flDark, -1.05, 0.55, 0))
    body.add(mesh(box(0.5, 1.0, 0.5), flBody, -0.65, 1.15, 0))
    const lidar = mesh(new THREE.CylinderGeometry(0.09, 0.1, 0.12, 18), MAT.rubber, -0.65, 1.72, 0); body.add(lidar)
    const ledMat = new THREE.MeshStandardMaterial({ color: 0x050a0c, emissive: new THREE.Color(COLORS.cyan), emissiveIntensity: 2.2, toneMapped: false })
    const lidarRing = new THREE.Mesh(new THREE.TorusGeometry(0.095, 0.012, 6, 20), ledMat); lidarRing.rotation.x = Math.PI / 2; lidarRing.position.set(-0.65, 1.75, 0); body.add(lidarRing)
    const beaconMat = new THREE.MeshStandardMaterial({ color: 0x3a2500, emissive: 0xffa21a, emissiveIntensity: 2, toneMapped: false })
    const beacon = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.07, 0.12, 12), beaconMat); beacon.position.set(-0.45, 1.72, 0.18); body.add(beacon)
    const strip = new THREE.Mesh(box(1.52, 0.04, 1.07), ledMat); strip.position.set(-0.3, 0.6, 0); body.add(strip)
    ;[[-0.85, 0.24], [0.3, 0.26]].forEach(([wx, r]) => [-1, 1].forEach((sd) => { const w = mesh(new THREE.CylinderGeometry(r, r, 0.22, 18), MAT.tire, wx, r, sd * 0.47); w.rotation.x = Math.PI / 2; body.add(w) }))
    // 측면 표시
    const st = canvasTex(256, 48, (cg, w, h) => { cg.fillStyle = '#111'; cg.fillRect(0, 0, w, h); cg.fillStyle = '#f2b705'; cg.font = `900 22px ${FONT}`; cg.textBaseline = 'middle'; cg.fillText('무인 지게차', 10, 24); cg.textAlign = 'right'; cg.fillText(a.name, w - 10, 24) })
    disposables.push(st)
    ;[-1, 1].forEach((sd) => { const pl = new THREE.Mesh(new THREE.PlaneGeometry(1.2, 0.22), new THREE.MeshStandardMaterial({ map: st })); pl.position.set(-0.3, 0.42, sd * 0.53); if (sd < 0) pl.rotation.y = Math.PI; body.add(pl) })
    // 마스트: 바깥 마스트 고정, 안쪽 마스트 · 캐리지(포크)는 높이에 따라 올라간다
    ;[-1, 1].forEach((sd) => body.add(mesh(box(0.09, 2.5, 0.1), flDark, 0.55, 1.25, sd * 0.33)))
    body.add(mesh(box(0.1, 0.1, 0.76), flDark, 0.55, 2.5, 0))
    const inner = new THREE.Group(); body.add(inner)
    ;[-1, 1].forEach((sd) => inner.add(mesh(box(0.08, 2.5, 0.08), MAT.steel, 0.62, 1.25, sd * 0.26)))
    inner.add(mesh(box(0.08, 0.08, 0.6), MAT.steel, 0.62, 2.5, 0))
    const carriage = new THREE.Group(); body.add(carriage)
    carriage.add(mesh(box(0.07, 0.65, 0.82), flDark, 0.7, 0.36, 0))
    ;[-1, 1].forEach((sd) => carriage.add(mesh(box(1.15, 0.05, 0.12), MAT.steel, 0.7 + 0.575, 0.04, sd * 0.24)))
    // 바닥에 비추는 파란 안전등
    const spotMat = new THREE.MeshBasicMaterial({ color: 0x3d8bff, transparent: true, opacity: 0.55, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false })
    const spot = new THREE.Mesh(new THREE.CircleGeometry(0.28, 24), spotMat); spot.rotation.x = -Math.PI / 2; spot.position.set(3.0, 0.02, 0); body.add(spot)
    const fanMat = new THREE.MeshBasicMaterial({ color: 0x3fe0cc, transparent: true, opacity: 0, depthWrite: false })
    const ringMat = new THREE.MeshBasicMaterial({ color: 0xffb84d, transparent: true, opacity: 0, depthWrite: false, side: THREE.DoubleSide, toneMapped: false })
    const ring = new THREE.Mesh(amrGeo.ring, ringMat); ring.rotation.x = -Math.PI / 2; ring.position.y = 0.03; g.add(ring)
    const selMat = new THREE.MeshBasicMaterial({ color: 0x3fe0cc, transparent: true, opacity: 0.9, depthWrite: false, side: THREE.DoubleSide, toneMapped: false })
    const sel = new THREE.Mesh(new THREE.RingGeometry(1.5, 1.6, 64), selMat); sel.rotation.x = -Math.PI / 2; sel.position.y = 0.025; g.add(sel)
    const mMat = new THREE.MeshBasicMaterial({ color: 0xff5a5f, transparent: true, opacity: 0 })
    const mRing = new THREE.Mesh(new THREE.RingGeometry(1.15, 1.32, 8), mMat); mRing.visible = false; g.add(mRing)
    const label = tag(a.name, 'twin-tag twin-tag-amr twin-tag-fl'); label.position.set(0, 2.5, 0); g.add(label)
    g.userData.amrIndex = idx; body.traverse((o) => { o.userData.amrIndex = idx })
    addContactShadow(g, 3.0, 1.9); world.add(g)
    return { g, body, ledMat, fanMat, ring, ringMat, sel, selMat, mRing, mMat, label, lidarRing, inner, carriage, beaconMat, spotMat, fl: true, liftK: 0 }
  }
  // 26.10.28 접지 그림자: 바닥과 닿는 부분을 살짝 어둡게 해서 로봇이 바닥에 '붙어' 보이게 한다
  const shadowTex = canvasTex(128, 128, (cg, w, h) => { const gr = cg.createRadialGradient(w / 2, h / 2, 4, w / 2, h / 2, w / 2); gr.addColorStop(0, 'rgba(0,0,0,0.9)'); gr.addColorStop(0.55, 'rgba(0,0,0,0.45)'); gr.addColorStop(1, 'rgba(0,0,0,0)'); cg.fillStyle = gr; cg.fillRect(0, 0, w, h) })
  disposables.push(shadowTex)
  const shadowMat = new THREE.MeshBasicMaterial({ map: shadowTex, transparent: true, opacity: 0.5, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 })
  const addContactShadow = (g, w, d) => { const m = new THREE.Mesh(new THREE.PlaneGeometry(w, d), shadowMat); disposables.push(m.geometry); m.rotation.x = -Math.PI / 2; m.position.y = 0.012; m.renderOrder = 1; g.add(m) }
  const amrs3 = S.amrs.map((a, idx) => {
    if (a.kind === 'fl') return makeForklift(a, idx)
    const g = new THREE.Group()
    const body = new THREE.Group(); g.add(body)
    const ch = mesh(amrGeo.chassis, MAT.chassis, 0, 0.21, 0); body.add(ch)
    const ledMat = new THREE.MeshStandardMaterial({ color: 0x050a0c, emissive: new THREE.Color(COLORS.cyan), emissiveIntensity: 2.2, toneMapped: false })
    const led = new THREE.Mesh(amrGeo.led, ledMat); led.position.y = 0.13; body.add(led)
    const lift = new THREE.Group(); body.add(lift)
    const plate = mesh(amrGeo.plate, MAT.deck, 0, 0.375, 0); lift.add(plate)
    lift.add(mesh(amrGeo.deck, MAT.steel, 0, 0.35, 0, false))
    const lidar = mesh(amrGeo.lidar, MAT.rubber, 0.6, 0.42, 0); body.add(lidar)
    const lidarRing = new THREE.Mesh(new THREE.TorusGeometry(0.09, 0.012, 6, 20), ledMat); lidarRing.rotation.x = Math.PI / 2; lidarRing.position.set(0.6, 0.45, 0); body.add(lidarRing)
    ;[-1, 1].forEach((s) => { const b = mesh(amrGeo.bumper, MAT.rubber, s * 0.64, 0.1, 0, false); body.add(b) })
    // 앞 범퍼 경고 줄무늬 · 구동 바퀴 · 비상정지 버튼 · 전조등/후미등 · 측면 로고
    const hz = new THREE.Mesh(amrGeo.hazard, MAT.hatch); hz.position.set(0.675, 0.12, 0); hz.rotation.y = Math.PI / 2; body.add(hz)
    ;[-1, 1].forEach((s) => { const wl = mesh(amrGeo.wheel, MAT.tire, 0, 0.09, s * 0.44, false); wl.rotation.x = Math.PI / 2; body.add(wl) })
    body.add(mesh(amrGeo.estopBase, MAT.yellow, -0.48, 0.395, 0.34, false))
    body.add(mesh(amrGeo.estop, MAT.cabinet, -0.48, 0.43, 0.34, false))
    // 26.10.29 경광등: 상단 기둥 + 돔. 정상 = 낮은 밝기, 이상·정지·화재 = 상태색으로 깜빡임
    const abMat = new THREE.MeshStandardMaterial({ color: 0x101418, emissive: new THREE.Color(0xffb84d), emissiveIntensity: 0.3, toneMapped: false })
    body.add(mesh(new THREE.CylinderGeometry(0.012, 0.012, 0.22, 6), MAT.steel, -0.55, 0.5, -0.3, false))
    const abDome = new THREE.Mesh(new THREE.SphereGeometry(0.045, 10, 8), abMat); abDome.position.set(-0.55, 0.63, -0.3); body.add(abDome)
    ;[-1, 1].forEach((s) => {
      const hl = new THREE.Mesh(amrGeo.lamp, headMat); hl.position.set(0.63, 0.24, s * 0.3); body.add(hl)
      const rl = new THREE.Mesh(amrGeo.lamp, rearMat); rl.position.set(-0.63, 0.24, s * 0.3); body.add(rl)
      const sd = new THREE.Mesh(amrGeo.side, sideMats[idx]); sd.position.set(0, 0.23, s * 0.48); if (s < 0) sd.rotation.y = Math.PI; body.add(sd)
    })
    const fanMat = new THREE.MeshBasicMaterial({ color: 0x3fe0cc, transparent: true, opacity: 0.07, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide })
    const fan = new THREE.Mesh(amrGeo.fan, fanMat); fan.rotation.x = -Math.PI / 2; fan.position.set(0.6, 0.02, 0); body.add(fan)
    const ringMat = new THREE.MeshBasicMaterial({ color: 0xffb84d, transparent: true, opacity: 0, depthWrite: false, side: THREE.DoubleSide, toneMapped: false })
    const ring = new THREE.Mesh(amrGeo.ring, ringMat); ring.rotation.x = -Math.PI / 2; ring.position.y = 0.03; g.add(ring)
    const selMat = new THREE.MeshBasicMaterial({ color: 0x3fe0cc, transparent: true, opacity: 0.9, depthWrite: false, side: THREE.DoubleSide, toneMapped: false })
    const sel = new THREE.Mesh(amrGeo.selRing, selMat); sel.rotation.x = -Math.PI / 2; sel.position.y = 0.025; g.add(sel)
    const mMat = new THREE.MeshBasicMaterial({ color: 0xff5a5f, transparent: true, opacity: 0.95, depthWrite: false, side: THREE.DoubleSide, toneMapped: false })
    const mRing = new THREE.Mesh(new THREE.RingGeometry(1.15, 1.32, 64, 1, 0, 0.01), mMat); mRing.rotation.x = -Math.PI / 2; mRing.position.y = 0.035; g.add(mRing)
    const label = tag('0' + a.id, 'twin-tag twin-tag-amr'); label.position.set(0, 1.55, 0); g.add(label)
    g.userData.amrIndex = idx
    body.traverse((o) => { o.userData.amrIndex = idx })
    addContactShadow(g, 1.9, 1.5)
    world.add(g)
    return { g, body, ledMat, fanMat, ring, ringMat, sel, selMat, mRing, mMat, label, lidarRing, lift, liftK: 0, abMat }
  })

  /* ── 교차로 예약 표시: 바닥 칸(예약 = 청록) · 대기선(대기 = 주황) · 대기 말풍선 ── */
  const IW = INTER_R * 2 * M
  const tileGeo = new THREE.PlaneGeometry(IW, IW), edgeGeo = new THREE.EdgesGeometry(tileGeo); disposables.push(tileGeo, edgeGeo)
  const interTiles = INTER_NODES.map((n) => {
    const m = new THREE.MeshBasicMaterial({ color: 0x3fe0cc, transparent: true, opacity: 0, depthWrite: false, toneMapped: false })
    const tile = new THREE.Mesh(tileGeo, m); tile.rotation.x = -Math.PI / 2; tile.position.set(X(n.x), 0.03, Z(n.y)); tile.renderOrder = 2; world.add(tile)
    const em = new THREE.LineBasicMaterial({ color: 0xffb84d, transparent: true, opacity: 0, toneMapped: false })
    const edge = new THREE.LineSegments(edgeGeo, em); edge.rotation.x = -Math.PI / 2; edge.position.set(X(n.x), 0.04, Z(n.y)); world.add(edge)
    return { n, m, em }
  })
  const waitTags = S.amrs.map(() => { const o = tag('', 'twin-tag twin-tag-wait'); o.visible = false; world.add(o); return o })
  function interUpdate(t) {
    interTiles.forEach((it) => {
      const slot = S.inter && S.inter[it.n.key]
      const waiting = S.amrs.some((a) => a.iWaitNode === it.n)
      it.m.opacity = slot && slot.owner ? 0.22 : 0
      it.em.opacity = waiting ? 0.55 + 0.4 * Math.abs(Math.sin(t * 6)) : 0
    })
    S.amrs.forEach((a, i) => {
      const o = waitTags[i], g = amrs3[i].g
      let txt = ''
      if (a.iWaitNode && a.iwait > 0.3) txt = '교차로 대기 ' + a.iwait.toFixed(1) + '초'
      else if (a.lastWaitT != null && S.simT - a.lastWaitT < 1.2) txt = '대기 ' + a.lastWait.toFixed(1) + '초 → 통과'
      o.visible = !!txt
      if (!txt) return
      o.position.set(g.position.x, 2.15, g.position.z)
      if (o.element.textContent !== txt) o.element.textContent = txt
      const cls = 'twin-tag twin-tag-wait' + (a.iWaitNode ? '' : ' is-go')
      if (o.element.className !== cls) o.element.className = cls
    })
  }

  // 실내라 반사 환경광을 낮춘다 (밝은 흰 방 환경맵이 모든 면을 하얗게 띄우는 것 방지)
  const envTune = (m) => { if (m && m.isMeshStandardMaterial && m.envMapIntensity === 1) m.envMapIntensity = 0.55 }
  scene.traverse((o) => { if (o.material) (Array.isArray(o.material) ? o.material : [o.material]).forEach(envTune) })

  /* ── 라벨 · 말풍선 ── */
  function tag(text, cls) {
    const el = document.createElement('div'); el.className = cls; el.textContent = text
    const o = new CSS2DObject(el); o.center.set(0.5, 1); return o
  }
  const callout = tag('', 'twin-callout'); callout.visible = false; world.add(callout)
  const maintTag = tag('', 'twin-tag twin-tag-maint'); maintTag.visible = false; world.add(maintTag)

  /* ── 선택 경로 ── */
  const pathMat = new THREE.LineDashedMaterial({ color: 0x3fe0cc, dashSize: 0.45, gapSize: 0.3, transparent: true, opacity: 0.95, toneMapped: false })
  const pathGeo = new THREE.BufferGeometry(); pathGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(3 * 16), 3))
  const pathLine = new THREE.Line(pathGeo, pathMat); pathLine.frustumCulled = false; world.add(pathLine)
  const destMat = new THREE.MeshBasicMaterial({ color: 0x3fe0cc, transparent: true, opacity: 0.9, side: THREE.DoubleSide, depthWrite: false, toneMapped: false })
  const dest = new THREE.Mesh(new THREE.RingGeometry(0.45, 0.55, 40), destMat); dest.rotation.x = -Math.PI / 2; dest.position.y = 0.03; world.add(dest)

  /* ── 화물 표시 링 ── */
  const markMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.95, depthWrite: false, toneMapped: false })
  const mark = new THREE.Mesh(new THREE.TorusGeometry(0.75, 0.035, 8, 48), markMat); mark.rotation.x = Math.PI / 2; world.add(mark)

  /* ── 센서 패킷 ── */
  const PKT_MAX = 48
  const pktMat = new THREE.MeshBasicMaterial({ color: 0xffffff, toneMapped: false })
  const pktInst = new THREE.InstancedMesh(new THREE.SphereGeometry(0.09, 10, 8), pktMat, PKT_MAX); pktInst.frustumCulled = false
  pktInst.setColorAt(0, new THREE.Color(1, 1, 1)); world.add(pktInst)
  let gwPulse = 0

  /* ── 화물 메시 (팔레트 + 박스 + 운송장 라벨) ── */
  const cargoMeshes = new Map()
  const SIZE = { S: [0.72, 0.42, 0.6], M: [0.92, 0.62, 0.74], L: [1.04, 0.86, 0.84] }
  const cargoBoxGeo = {}
  for (const k in SIZE) cargoBoxGeo[k] = compactBox(box(...SIZE[k]))
  // 운송장 라벨 3장(앞 · 뒤 · 윗면)을 크기별 한 지오메트리로
  const labelGeo = {}
  for (const k in SIZE) {
    const [w, h, d] = SIZE[k], pl = () => new THREE.PlaneGeometry(0.3, 0.197)
    const f = pl().translate(w * 0.12, 0.14 + h * 0.52, d / 2 + 0.002)
    const b = pl().rotateY(Math.PI).translate(-w * 0.12, 0.14 + h * 0.52, -d / 2 - 0.002)
    const t = pl().rotateX(-Math.PI / 2).translate(-w * 0.18, 0.14 + h + 0.002, d * 0.2)
    labelGeo[k] = mergeGeos([f, b, t]); disposables.push(labelGeo[k])
  }
  const palletGeoM = mergeGeos([new THREE.BoxGeometry(1.1, 0.05, 0.92).translate(0, 0.115, 0)].concat([-0.38, 0, 0.38].map((dz) => new THREE.BoxGeometry(1.1, 0.09, 0.12).translate(0, 0.045, dz))))
  disposables.push(palletGeoM)
  // 랙 선반용 파렛트: 앞·뒤 빔 사이 간격(약 1.25 m)보다 깊게 만들어 빔 위에 실제로 걸쳐 놓인다 (공중에 뜨지 않게)
  const palletGeoRack = mergeGeos([new THREE.BoxGeometry(1.1, 0.05, 1.46).translate(0, 0.115, 0)].concat([-0.62, 0, 0.62].map((dz) => new THREE.BoxGeometry(1.1, 0.09, 0.13).translate(0, 0.045, dz))))
  disposables.push(palletGeoRack)
  function cargoMesh(c) {
    let m = cargoMeshes.get(c.id)
    if (m) return m
    const g = new THREE.Group()
    g.add(mesh(palletGeoM, MAT.wood, 0, 0, 0))
    const h = SIZE[c.size][1]
    const b = mesh(cargoBoxGeo[c.size], cargoMaterials(c.size, c.handle), 0, 0.14 + h / 2, 0); g.add(b)
    // 운송장: 앞 · 뒤 · 윗면
    const lt = waybillTex(c)
    const lm = new THREE.MeshStandardMaterial({ map: lt, color: 0xcfcfc8, roughness: 0.75, envMapIntensity: 0.3, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 })
    g.add(new THREE.Mesh(labelGeo[c.size], lm))
    g.userData.cargo = c; g.traverse((o) => { o.userData.cargo = c })
    g.userData.h = 0.14 + h
    world.add(g)
    m = { g, c, seen: false, lm, lt }
    cargoMeshes.set(c.id, m)
    return m
  }

  /* ── 위 단 보충 재고: 크기 · 취급표시별 InstancedMesh (팔레트 포함) ── */
  const UP_CAP = ROWS.length * 2 * NS * 3
  const upperSets = new Map()
  const upPallet = new THREE.InstancedMesh(palletGeoRack, MAT.wood, UP_CAP); upPallet.castShadow = true; upPallet.receiveShadow = true; upPallet.count = 0
  upPallet.userData.boxes = []; world.add(upPallet)
  let upperDrawn = -1
  function drawUpper() {
    upperSets.forEach((im) => { im.count = 0; im.userData.boxes.length = 0 })
    upPallet.count = 0; upPallet.userData.boxes.length = 0
    S.upper.forEach((row, r) => row.forEach((side, s) => side.forEach((col, i) => col.forEach((u, lv) => {
      if (!u || !u.box) return
      const c = u.box, key = c.size + '|' + c.handle
      let im = upperSets.get(key)
      if (!im) { im = new THREE.InstancedMesh(cargoBoxGeo[c.size], cargoMaterials(c.size, c.handle), UP_CAP); im.castShadow = true; im.receiveShadow = true; im.count = 0; im.userData.boxes = []; upperSets.set(key, im); world.add(im) }
      const [x, z] = slotPos({ r, s, i }), y = LEVEL_H[lv] + 0.07, h = SIZE[c.size][1]
      dm.position.set(x, y, z); dm.rotation.set(0, 0, 0); dm.scale.set(1, 1, 1); dm.updateMatrix(); upPallet.setMatrixAt(upPallet.count++, dm.matrix); upPallet.userData.boxes.push(c)
      dm.position.set(x, y + 0.14 + h / 2, z); dm.updateMatrix(); im.setMatrixAt(im.count++, dm.matrix); im.userData.boxes.push(c)
    }))))
    upPallet.instanceMatrix.needsUpdate = true
    upperSets.forEach((im) => { im.instanceMatrix.needsUpdate = true })
  }

  /* ── 좌표 변환 ── */
  const ease = (t) => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2)
  const amrPos = (a) => [X(a.x) + a.ox * LANE_OFF, Z(a.y) + a.oy * LANE_OFF]
  function slotPos(L) {
    const [y0, y1] = ROWS[L.r], mid = (y0 + y1) / 2
    return [X(SX(L.i)), Z(L.s ? (mid + y1) / 2 : (y0 + mid) / 2)]
  }

  /* ── 카메라 시점 ── */
  const camGoalPos = new THREE.Vector3().copy(HOME_POS), camGoalTgt = new THREE.Vector3().copy(HOME_TGT)
  let followIdx = 2
  /* ── 바로가기 시점 (부지 전체 · 게이트 · 도크 · 랙 · 충전/정비) ── */
  const GATE_IN_X = WALL_L - 0.6 - GATE_OFF - 8.2 - 2.1 - 1.2 - 1.4, GATE_OUT_X = WALL_R + 0.6 + GATE_OFF + 5.2 + 1.15 + 1.0 + 1.4
  const PRESETS = {
    overview: { tgt: [-2, 0, 0], off: [10, 44, 48], z2: 0.7 },
    gateIn: { tgt: [GATE_IN_X + 5, 0, 0], off: [9, 19, 21], z2: 1.6 },
    dockIn: { tgt: [WALL_L + 3, 0, 0], off: [9, 16, 18], z2: 1.9 },
    rack: { tgt: [0, 0, -1], off: [7, 20, 22], z2: 1.5 },
    dockOut: { tgt: [WALL_R - 2, 0, 0], off: [9, 16, 18], z2: 1.9 },
    gateOut: { tgt: [GATE_OUT_X - 4, 0, 0], off: [9, 19, 21], z2: 1.6 },
    station: { tgt: [X(430), 0, Z(590)], off: [6, 12, 14], z2: 2.4 },
  }
  let gateSide = null // 지금 자동으로 보고 있는 게이트 ('in' | 'out')
  const dockPrev = new WeakMap() // 도크별 직전 상태 (트럭이 들어오기 · 나가기 시작하는 순간을 잡으려고)
  let preset = null, autoGate = false, gateBack = null, gateUntil = 0, gateSeen = new WeakSet(), exitSeen = new WeakSet()
  const backTgt = new THREE.Vector3(), backPos = new THREE.Vector3(); let backZoom = 1, backUntil = 0
  function setPreset(name) {
    const p = PRESETS[name]; if (!p) return
    preset = p; viewMode = 'preset'; opts.onViewChange && opts.onViewChange('preset:' + name)
  }
  function setAutoGate(on) { autoGate = !!on; if (!autoGate) gateSide = null }
  // 26.10.16 입구 · 출구에서 인식이 시작되면 그 게이트를 잠깐(6초) 보여 주고 원래 보던 자리로 돌아간다
  //  - 손으로 움직인 자유 시점 · 로봇 따라가기에서도 동작 (예전엔 자동 · 전체 · 바로가기 시점에서만 동작)
  //  - 들어올 때(입구 OCR) + 택배차가 나갈 때(출구 OCR) 모두
  function autoGateCheck(t) {
    // 트럭 움직임으로도 판단: 입구로 들어오기 시작(empty → arriving) · 택배차가 나가기 시작(docked → leaving)
    //  - 앱은 번호판 인식을 직접 하지 않아서(웹 결과만 받음) 'scan' 상태가 없으므로 이 기준으로 움직인다
    let moveHit = null
    S.inDocks.forEach((d) => { const pv = dockPrev.get(d); if (pv !== undefined && pv !== d.state && d.state === 'arriving' && !moveHit) moveHit = 'gateIn'; dockPrev.set(d, d.state) })
    S.outDocks.forEach((d) => { const pv = dockPrev.get(d); if (pv !== undefined && pv !== d.state && !moveHit && (d.state === 'arriving' || d.state === 'leaving')) moveHit = 'gateOut'; dockPrev.set(d, d.state) })
    if (!autoGate) return
    if (gateUntil && t > gateUntil) {
      gateUntil = 0; gateSide = null
      if (viewMode === 'preset' && gateBack) { // 게이트를 보는 사이 사용자가 직접 움직였으면 그대로 둔다
        if (gateBack.mode === 'preset') { preset = gateBack.preset; viewMode = 'preset' }
        else if (gateBack.mode === 'free') { preset = null; viewMode = 'back'; backUntil = t + 2.5 }
        else setView(gateBack.mode, gateBack.idx)
      }
      gateBack = null; return
    }
    if (gateUntil || viewMode === 'back') return
    if (moveHit) { [...S.inDocks, ...S.outDocks].forEach((d) => { if (d.ocr) { gateSeen.add(d.ocr); if (d.ocr.exit === 'scan') exitSeen.add(d.ocr) } }); goGate(moveHit, t); return } // 같은 트럭으로 두 번 움직이지 않게
    const hit = [...S.inDocks.map((d) => [d, 'gateIn']), ...S.outDocks.map((d) => [d, 'gateOut'])].find(([d]) => d.ocr && ((d.ocr.st === 'scan' && !gateSeen.has(d.ocr)) || (d.ocr.exit === 'scan' && !exitSeen.has(d.ocr))))
    if (!hit) return
    const o = hit[0].ocr
    if (o.exit === 'scan') exitSeen.add(o); else gateSeen.add(o)
    goGate(hit[1], t)
  }
  function goGate(key, t) {
    gateBack = { mode: viewMode, idx: followIdx, preset }
    if (viewMode === 'free') { // 자유 시점: 지금 카메라 자리를 기억
      if (is2D) { backTgt.copy(controls2.target); backZoom = cam2.zoom } else { backTgt.copy(controls.target); backPos.copy(camera.position) }
    }
    preset = PRESETS[key]; viewMode = 'preset'; gateUntil = t + 6; gateSide = key === 'gateIn' ? 'in' : 'out'
    opts.onViewChange && opts.onViewChange('preset:' + key)
  }
  // 앱용: 앱은 인식을 직접 하지 않고 웹이 끝낸 결과만 받으므로, 새 결과가 오면 그 게이트로 바로 이동
  function focusGate(side) {
    if (!autoGate || gateUntil || viewMode === 'back') return
    goGate(side === 'out' ? 'gateOut' : 'gateIn', performance.now() / 1000)
  }
  // 이동 범위 제한 (목표점을 부지 안으로 · 카메라도 같은 만큼 옮겨 시야 유지)
  const clampV = new THREE.Vector3()
  function clampSite(ctl, cam) {
    const tg = ctl.target
    clampV.set(Math.min(SITE.x1, Math.max(SITE.x0, tg.x)) - tg.x, 0, Math.min(SITE.z1, Math.max(SITE.z0, tg.z)) - tg.z)
    if (clampV.x || clampV.z) { tg.add(clampV); cam.position.add(clampV) }
  }
  function setView(mode, idx) {
    preset = null
    viewMode = mode
    if (idx != null) followIdx = idx
    opts.onViewChange && opts.onViewChange(viewMode)
  }

  /* ── 매 프레임 갱신 ── */
  const tmpV = new THREE.Vector3(), tmpC = new THREE.Color()
  // 자동 화질: 3초 평균이 약 20fps 아래면 한 번만 저사양으로 내린다 (사용자가 화질을 직접 고르면 자동 조절 끔)
  let autoQ = true, qAcc = 0, qN = 0, qT = 0
  function autoQuality(dtReal) {
    if (!autoQ || quality === 'low' || !(dtReal > 0) || dtReal > 1) return
    qAcc += dtReal; qN++; qT += dtReal
    if (qT >= 3) { if (qN / qAcc < 20) { setQuality('low'); autoQ = false } qAcc = 0; qN = 0; qT = 0 }
  }
  function update(now, dtSim, dtReal, ui) {
    autoQuality(dtReal)
    if (fx.fireFx) fx.fireFx(now, dtReal)
    const t = now / 1000

    // 도크 · 트럭
    S.inDocks.forEach((d, i) => {
      const tr = inTrucks[i]
      tr.visible = d.state !== 'empty'
      tr.position.x = WALL_L - 0.6 - ease(d.off) * 22
      plateTagUpdate(d, i, 0, tr, t)
      const dd = dockDoors.in[i]; const want = d.state === 'docked' ? 1 : 0
      dd.open += (want - dd.open) * Math.min(1, dtReal * 2.5)
      dd.door.scale.y = Math.max(0.05, 1 - dd.open * 0.95); dd.door.position.y = DOOR_H - (DOOR_H * dd.door.scale.y) / 2
      dd.lampMat.emissive.set(d.state === 'docked' ? 0x4fdc8a : 0xff3b3b)
    })
    S.outDocks.forEach((d, i) => {
      const tr = outTrucks[i]
      tr.visible = d.state !== 'empty'
      tr.position.x = WALL_R + 0.6 + ease(d.off) * 22
      // 26.10.25 출고: 도크에서 곧게 빠진 뒤 서울·경기·인천 방향 도로로 갈라져 나간다 (도착할 때는 같은 곡선을 후진)
      { const u = Math.min(1, Math.max(0, (ease(d.off) - 0.5) / 0.5)), dir = i - 1
        tr.position.z = Z(d.y) + dir * 4 * u * u
        tr.rotation.y = -Math.atan((dir * 8 * u) / 11) }
      plateTagUpdate(d, i, 1, tr, t)
      const dd = dockDoors.out[i]; const want = d.state === 'docked' ? 1 : 0
      dd.open += (want - dd.open) * Math.min(1, dtReal * 2.5)
      dd.door.scale.y = Math.max(0.05, 1 - dd.open * 0.95); dd.door.position.y = DOOR_H - (DOOR_H * dd.door.scale.y) / 2
      dd.lampMat.emissive.set(d.state === 'docked' ? 0x4fdc8a : 0xff3b3b)
    })

    // 화물 배치
    cargoMeshes.forEach((m) => (m.seen = false))
    const place = (c, x, y, z, ry) => { const m = cargoMesh(c); m.seen = true; m.g.visible = true; m.g.position.set(x, y, z); m.g.rotation.y = ry || 0 }
    S.slots.forEach((row) => row.forEach((side) => side.forEach((v) => { if (v.box) { const [x, z] = slotPos(v.box.loc); place(v.box, x, 0, z, 0) } })))
    S.inDocks.forEach((d, i) => { if (d.state === 'empty') return; d.cargo.forEach((c, k) => place(c, inTrucks[i].position.x - 0.9 - k * 1.25, 1.3, Z(d.y), 0)) })
    S.outDocks.forEach((d, i) => { if (d.state === 'empty') return; d.boxes.forEach((c, k) => { const T = outTrucks[i], o = 0.75 + k * 1.15, th = T.rotation.y; place(c, T.position.x + o * Math.cos(th), 1.06, T.position.z - o * Math.sin(th), th) }) })
    S.amrs.forEach((a, i) => {
      const o = amrs3[i]
      if (o.fl) {
        // 무인 지게차: 랙 쪽으로 다가가며(리치) 포크를 올리고 내린다
        let reachD = 0
        if (a.state === 'fwork' && a.task) { const sz = slotPos(a.task.slot)[1]; reachD = Math.max(0, Math.abs(sz - Z(a.y)) - 1.275 - 0.28) * a.reach }
        const [x0, z0] = amrPos(a), cx = Math.cos(a.ang), cz = Math.sin(a.ang)
        o.px = x0 + cx * reachD; o.pz = z0 + cz * reachD
        o.carriage.position.y = a.forkH
        o.inner.position.y = Math.max(0, a.forkH - 1.7)
        if (a.box) place(a.box, o.px + cx * 1.275, a.forkH + 0.02, o.pz + cz * 1.275, -a.ang)
        return
      }
      // 리프트: 적재 중엔 올라가고, 하역 중엔 내려간다
      let want = a.box ? 1 : 0
      if (a.state === 'load') want = Math.min(1, Math.max(0, 1 - a.timer / (a.task && a.task.type === 'in' ? 1.4 : 0.9)) * 1.4)
      if (a.state === 'unload') want = Math.max(0, a.timer / 0.9)
      o.liftK += (want - o.liftK) * Math.min(1, dtReal * 10)
      o.lift.position.y = o.liftK * 0.07
      if (a.box) { const [x, z] = amrPos(a); place(a.box, x, 0.39 + o.liftK * 0.07, z, -a.ang) }
    })
    cargoMeshes.forEach((m, id) => {
      if (m.seen) return
      if (m.c.loc && m.c.loc.type === 'gone') { world.remove(m.g); m.lt.dispose(); m.lm.dispose(); cargoMeshes.delete(id) } else m.g.visible = false
    })

    // AMR
    const T = S.amrs[S.sc.target == null ? 2 : S.sc.target]
    S.amrs.forEach((a, i) => {
      const o = amrs3[i], [lv] = status(a), col = lvCol(lv)
      const [x, z] = o.fl ? [o.px, o.pz] : amrPos(a)
      o.g.position.set(x, 0, z)
      if (o.fl) { const busy = a.v > 3 || a.state === 'fwork'; o.beaconMat.emissiveIntensity = busy ? (Math.sin(t * 12) > 0 ? 3 : 0.3) : 0.4; o.spotMat.opacity = a.v > 3 ? 0.55 : 0 }
      o.body.rotation.y = -a.ang
      tmpC.set(col)
      o.ledMat.emissive.copy(tmpC)
      o.ledMat.emissiveIntensity = lv === 'spare' ? 0.35 : lv === 'ok' ? 1.8 : 2.6 + Math.sin(t * 8) * 0.8
      if (o.abMat) { const hot = lv === 'crit' || lv === 'warn'; o.abMat.emissive.set(hot ? col : 0xffb84d); o.abMat.emissiveIntensity = hot ? (Math.sin(t * 10) > 0 ? 3 : 0.2) : a.v > 3 ? 0.9 : 0.3 }
      o.fanMat.opacity = a.v > 5 ? 0.06 + 0.03 * Math.sin(t * 6 + i) : 0
      o.lidarRing.rotation.z = t * 6
      const warn = lv === 'warn' || lv === 'crit'
      if (warn) { const ph = (t % 1.4) / 1.4; o.ring.scale.setScalar(1 + ph * 1.4); o.ringMat.opacity = (1 - ph) * 0.8; o.ringMat.color.set(col) } else o.ringMat.opacity = 0
      o.sel.visible = i === ui.selected
      o.selMat.color.set(col)
      const cls = 'twin-tag twin-tag-amr is-' + lv + (i === ui.selected ? ' is-sel' : '') + (o.fl ? ' twin-tag-fl' : '')
      if (o.label.element.className !== cls) o.label.element.className = cls
      if (a.state === 'maint') {
        const p = 1 - a.timer / 7
        o.mRing.visible = true
        o.mRing.geometry.dispose(); o.mRing.geometry = new THREE.RingGeometry(1.15, 1.32, 64, 1, Math.PI / 2, Math.max(0.01, p * Math.PI * 2))
        maintTag.visible = true; maintTag.position.set(x, 2.3, z); maintTag.element.textContent = '베어링 교체 ' + Math.round(p * 100) + '%'
      } else o.mRing.visible = false
    })
    if (T.state !== 'maint') maintTag.visible = false

    // 선택 AMR 경로
    const sa = S.amrs[ui.selected]
    if (sa && sa.path.length) {
      const [sx, sz] = amrPos(sa)
      const pts = [[sx, sz]].concat(sa.path.slice(0, 15).map((p) => [X(p.x), Z(p.y)]))
      const arr = pathGeo.attributes.position.array
      pts.forEach(([px, pz], k) => { arr[k * 3] = px; arr[k * 3 + 1] = 0.05; arr[k * 3 + 2] = pz })
      pathGeo.setDrawRange(0, pts.length); pathGeo.attributes.position.needsUpdate = true
      pathLine.computeLineDistances(); pathLine.visible = true
      const e = pts[pts.length - 1]; dest.position.set(e[0], 0.03, e[1]); dest.visible = true
      const pc = lvCol(status(sa)[0]); pathMat.color.set(pc); destMat.color.set(pc)
      dest.scale.setScalar(1 + 0.12 * Math.sin(t * 5))
    } else { pathLine.visible = false; dest.visible = false }

    // 위 단 보충 재고 (시뮬레이션에서 바뀔 때만 다시 배치)
    if (S.upperVer !== upperDrawn) { upperDrawn = S.upperVer; drawUpper() }

    // 냉장 창고: 사람 · 로봇이 다가오면 고속 셔터가 열린다, 문이 열려 있으면 내부 온도가 조금 오른다
    let anyOpen = 0
    coldDoors.forEach((d) => {
      const near = S.amrs.some((a) => Math.abs(a.x - d.px) < 95 && Math.abs(a.y - d.py) < 40)
      d.open += ((near ? 1 : 0) - d.open) * Math.min(1, dtReal * 6)
      d.door.scale.y = Math.max(0.04, 1 - d.open * 0.96); d.door.position.y = 3.4 - (3.4 * d.door.scale.y) / 2
      anyOpen = Math.max(anyOpen, d.open)
    })
    coldTemp += anyOpen > 0.5 ? dtSim * 0.25 : -(coldTemp - 3.0) * dtSim * 0.15
    coldDispAcc += dtReal
    if (coldDispAcc > 1) { coldDispAcc = 0; coldDisp.forEach((tx) => drawColdDisp(tx, coldTemp)) }
    if (coldMist && quality !== 'low') {
      const { mb, mp, MN, top } = coldMist
      for (let i = 0; i < MN; i++) { const k = i * 3; const fall = (t * 0.25 + i * 0.37) % top; mp[k] = mb[k] + Math.sin(t * 0.3 + i) * 0.3; mp[k + 1] = top - fall + 0.3; mp[k + 2] = mb[k + 2] + Math.cos(t * 0.2 + i) * 0.2 }
      coldMist.mg.attributes.position.needsUpdate = true
    }

    // 화물 표시 링
    const cm = ui.cargo && cargoMeshes.get(ui.cargo.id)
    const upLoc = ui.cargo && ui.cargo.loc && ui.cargo.loc.type === 'upper' ? ui.cargo.loc : null
    if ((cm && cm.g.visible) || upLoc) {
      mark.visible = true
      if (upLoc) { const [ux, uz] = slotPos(upLoc); mark.position.set(ux, LEVEL_H[upLoc.lv] + 0.07 + SIZE[ui.cargo.size][1] + 0.4 + 0.06 * Math.sin(t * 4), uz) }
      else mark.position.set(cm.g.position.x, cm.g.position.y + cm.g.userData.h + 0.25 + 0.06 * Math.sin(t * 4), cm.g.position.z)
      markMat.color.set(HUBS[ui.cargo.hub].col)
      mark.rotation.z = t
    } else mark.visible = false

    // OCR 스캔
    scanners.forEach((s) => { s.beamMat.opacity = 0; s.lbl.visible = false })
    S.amrs.forEach((a) => {
      if (a.state !== 'load' || !a.task || a.task.type !== 'in') return
      const s = scanners[a.task.dk]; if (!s) return
      const p = 1 - a.timer / 1.4, done = p > 0.6, c = a.task.box
      s.beamMat.color.set(done ? HUBS[c.hub].col : COLORS.amber)
      s.beamMat.opacity = done ? 0.22 : 0.12 + 0.12 * Math.abs(Math.sin(t * 14))
      s.beam.position.x = done ? 0.05 : 0.05 + Math.sin(t * 9) * 0.25
      s.lbl.visible = true
      s.lbl.element.textContent = done ? '바코드 ✓ ' + (c.ean || c.id) + ' → ' + HUBS[c.hub].name + (c.handle === '냉장' ? ' · 냉장' : '') : '바코드 스캔 중…'
      s.lbl.element.className = 'twin-tag twin-tag-ocr' + (done ? ' is-done' : '')
      s.lbl.element.style.setProperty('--hub', HUBS[c.hub].col)
    })

    interUpdate(t)

    // 센서 패킷 → 게이트웨이
    const pk = S.packets
    for (let i = pk.length - 1; i >= 0; i--) { pk[i].t += dtSim / 1.1; if (pk[i].t >= 1) { pk.splice(i, 1); gwPulse = 1 } }
    if (pk.length > PKT_MAX) pk.splice(0, pk.length - PKT_MAX)
    pk.forEach((p, k) => {
      const e = ease(Math.min(1, p.t)), sx = X(p.x), sz = Z(p.y)
      const mx = (sx + gwPos.x) / 2, mz = (sz + gwPos.z) / 2, my = 6.5
      const u = 1 - e
      tmpV.set(u * u * sx + 2 * u * e * mx + e * e * gwPos.x, u * u * 0.6 + 2 * u * e * my + e * e * gwPos.y, u * u * sz + 2 * u * e * mz + e * e * gwPos.z)
      dm.position.copy(tmpV); dm.rotation.set(0, 0, 0); dm.scale.setScalar(1); dm.updateMatrix(); pktInst.setMatrixAt(k, dm.matrix)
      pktInst.setColorAt(k, tmpC.set(p.lv === 'crit' ? COLORS.red : p.lv === 'warn' ? COLORS.amber : COLORS.cyan))
    })
    pktInst.count = pk.length
    pktInst.instanceMatrix.needsUpdate = true
    if (pktInst.instanceColor) pktInst.instanceColor.needsUpdate = true
    gwPulse = Math.max(0, gwPulse - dtReal * 2.5)
    gwLedMat.emissiveIntensity = 1.2 + gwPulse * 4

    // 먼지 (천천히 떠다님)
    if (quality !== 'low') for (let i = 0; i < DUST_N; i++) {
      const k = i * 3, ph = i * 1.7
      dustPos[k] = dustBase[k] + Math.sin(t * 0.13 + ph) * 0.35
      dustPos[k + 1] = dustBase[k + 1] + Math.sin(t * 0.09 + ph * 0.7) * 0.25
      dustPos[k + 2] = dustBase[k + 2] + Math.cos(t * 0.11 + ph) * 0.35
    }
    dustGeo.attributes.position.needsUpdate = true

    // 작업자: 숨쉬기 · 고개 돌리기 / 정비사는 AMR-03 정비 중에 쪼그려 앉아 작업
    Object.entries(workers).forEach(([k, w]) => {
      const u = w.userData, ph = t + u.phase
      u.torso.rotation.z = Math.sin(ph * 1.3) * 0.015
      u.head.rotation.y = Math.sin(ph * 0.4) * 0.5
      if (k === 'tech') {
        const working = S.amrs.some((a, i) => i < 4 && a.state === 'maint')
        u.crouch = (u.crouch || 0) + ((working ? 1 : 0) - (u.crouch || 0)) * Math.min(1, dtReal * 3)
        const c = u.crouch
        u.hip.position.y = 0.9 - c * 0.42
        u.legs.forEach((l) => { l.rotation.z = c * 1.1 })
        u.torso.rotation.z = c * -0.45
        u.arms.forEach((a, j) => { a.rotation.z = c * (1.2 + Math.sin(t * 9 + j * 2) * 0.25) })
        w.position.x = X(BAY.x) + 2.2 - c * 1.2
      }
      if (k === 'inbound') {
        const scanning = S.amrs.some((a) => a.state === 'load' && a.task && a.task.type === 'in')
        u.arms[1].rotation.z += ((scanning ? 1.5 : 0.9) - u.arms[1].rotation.z) * Math.min(1, dtReal * 4)
      }
    })

    // 말풍선
    if (ui.callout) {
      callout.visible = true
      const [x, z] = amrPos(T)
      callout.position.set(x, 2.1, z)
      const html = `<b>${S.amrs[S.sc.target == null ? 2 : S.sc.target].name} · ${ui.callout.title}</b><span>${ui.callout.line}</span>`
      if (callout.userData.html !== html) { callout.element.innerHTML = html; callout.userData.html = html }
      callout.element.style.setProperty('--c', ui.callout.col)
    } else callout.visible = false

    // 카메라
    if (viewMode === 'back') { // 게이트를 보여 준 뒤 자유 시점의 원래 자리로 부드럽게 복귀
      const k = Math.min(1, dtReal * 2.2)
      if (is2D) { controls2.target.lerp(backTgt, k); cam2.position.set(controls2.target.x, 90, controls2.target.z); cam2.zoom += (backZoom - cam2.zoom) * k; cam2.updateProjectionMatrix() }
      else { controls.target.lerp(backTgt, k); camera.position.lerp(backPos, k) }
      const near = is2D ? controls2.target.distanceTo(backTgt) < 0.05 && Math.abs(cam2.zoom - backZoom) < 0.01 : controls.target.distanceTo(backTgt) < 0.05 && camera.position.distanceTo(backPos) < 0.05
      if (near || t > backUntil) { // 다 왔거나 시간이 지나면 원래 자리에 정확히 맞추고 자유 시점으로
        if (is2D) { controls2.target.copy(backTgt); cam2.position.set(backTgt.x, 90, backTgt.z); cam2.zoom = backZoom; cam2.updateProjectionMatrix() } else { controls.target.copy(backTgt); camera.position.copy(backPos) }
        viewMode = 'free'; opts.onViewChange && opts.onViewChange('free')
      }
    } else if (viewMode !== 'free') {
      let follow = viewMode === 'follow' || (viewMode === 'auto' && ui.phase >= 2 && ui.phase <= 4)
      if (viewMode === 'preset' && preset) follow = false
      const fi = viewMode === 'follow' ? followIdx : (S.sc.target == null ? 2 : S.sc.target)
      if (follow) {
        const a = S.amrs[fi], [x, z] = amrPos(a)
        camGoalTgt.set(x, 0.6, z)
        camGoalPos.set(x + 9, 11, z + 13)
      } else if (viewMode === 'preset' && preset) {
        camGoalTgt.set(...preset.tgt); camGoalPos.set(preset.tgt[0] + preset.off[0], preset.off[1], preset.tgt[2] + preset.off[2])
      } else { camGoalTgt.copy(HOME_TGT); camGoalPos.copy(HOME_POS) }
      const k = Math.min(1, dtReal * 1.6)
      if (is2D) {
        const pz = viewMode === 'preset' && preset
        tmpV.set(follow || pz ? camGoalTgt.x : -2, 0, follow || pz ? camGoalTgt.z : 0)
        controls2.target.lerp(tmpV, k); cam2.position.set(controls2.target.x, 90, controls2.target.z)
        const zGoal = follow ? 2.2 : pz ? preset.z2 : 0.7; cam2.zoom += (zGoal - cam2.zoom) * k; cam2.updateProjectionMatrix()
      } else {
        controls.target.lerp(camGoalTgt, k)
        camera.position.lerp(camGoalPos, k)
      }
    }
    autoGateCheck(t)
    if (is2D) { clampSite(controls2, cam2); controls2.update() } else { clampSite(controls, camera); controls.update() }
  }

  /* ── 화질: 'normal' 일반 / 'low' 저사양 (그림자 · 빛 번짐 · 먼지 · 냉기 끄고 해상도 1배) ── */
  let quality = 'normal'
  function setQuality(q) {
    if (q === quality) return
    quality = q
    const low = q === 'low'
    renderer.setPixelRatio(low ? 1 : Math.min(2, window.devicePixelRatio || 1))
    renderer.shadowMap.enabled = !low
    scene.traverse((o) => { if (o.material) (Array.isArray(o.material) ? o.material : [o.material]).forEach((m) => { m.needsUpdate = true }) })
    ;[fx.dust, fx.mist, fx.shafts].forEach((o) => { if (o) o.visible = !low })
    resize()
  }
  function set2D(on) {
    is2D = !!on
    if (fx.env) fx.env.visible = !is2D
    if (fx.fire) fx.fire.visible = !is2D
    controls.enabled = !is2D; controls2.enabled = is2D
    if (is2D) { controls2.target.set(-2, 0, 0); cam2.position.set(-2, 90, 0); cam2.zoom = 0.7 }
    resize()
  }
  const THEME_BG = { dark: 0x0b1016, light: 0xdfe6ec }
  let theme = 'dark'
  function setTheme(t) {
    theme = t === 'light' ? 'light' : 'dark'
    scene.background.set(THEME_BG[theme]); scene.fog.color.set(THEME_BG[theme])
    if (fx.envSet) fx.envSet(theme)
    clockEl.dataset.theme = theme; clockTick()
  }
  function render() {
    if (is2D) { renderer.render(scene, cam2); labelRenderer.render(scene, cam2); return }
    if (quality === 'low') { renderer.render(scene, camera); labelRenderer.render(scene, camera); return }
    grade.uniforms.uTime.value = (performance.now() % 100000) / 1000
    composer.render()
    labelRenderer.render(scene, camera)
  }

  function resize() {
    const w = container.clientWidth, h = container.clientHeight
    if (!w || !h) return
    renderer.setSize(w, h, false)
    composer.setSize(w, h)
    bloom.setSize(w, h)
    labelRenderer.setSize(w, h)
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    // 2D: 창고 전체(가로 약 50m · 세로 약 34m)가 화면에 들어오게
    const asp = w / h, hh = Math.max(18, 26 / asp), hw = hh * asp
    cam2.left = -hw; cam2.right = hw; cam2.top = hh; cam2.bottom = -hh; cam2.updateProjectionMatrix()
  }
  const ro = new ResizeObserver(resize); ro.observe(container); resize()

  // 클릭 선택 (드래그와 구분)
  const ray = new THREE.Raycaster(), ndc = new THREE.Vector2()
  let downAt = null
  const onDown = (e) => { downAt = [e.clientX, e.clientY] }
  const onUp = (e) => {
    if (!downAt || Math.hypot(e.clientX - downAt[0], e.clientY - downAt[1]) > 5) return
    const r = renderer.domElement.getBoundingClientRect()
    ndc.set(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1)
    ray.setFromCamera(ndc, activeCam())
    const targets = amrs3.map((o) => o.body).concat([...cargoMeshes.values()].filter((m) => m.g.visible).map((m) => m.g), [...upperSets.values()], [upPallet])
    const hit = ray.intersectObjects(targets, true)[0]
    if (!hit) return
    const ud = hit.object.userData
    if (ud.boxes && hit.instanceId != null && ud.boxes[hit.instanceId]) { onPick({ type: 'cargo', cargo: ud.boxes[hit.instanceId] }); return }
    if (ud.amrIndex != null) onPick({ type: 'amr', index: ud.amrIndex })
    else if (ud.cargo) onPick({ type: 'cargo', cargo: ud.cargo })
  }
  renderer.domElement.addEventListener('pointerdown', onDown)
  renderer.domElement.addEventListener('pointerup', onUp)

  function dispose() {
    ro.disconnect()
    clearInterval(clockTimer); clockEl.remove()
    if (fx.fireEls) fx.fireEls.forEach((e) => e.remove())
    try { speechSynthesis.cancel() } catch (e) { /* 무시 */ }
    renderer.domElement.removeEventListener('pointerdown', onDown)
    renderer.domElement.removeEventListener('pointerup', onUp)
    controls.dispose(); controls2.dispose()
    scene.traverse((o) => {
      if (o.geometry) o.geometry.dispose()
      if (o.material) (Array.isArray(o.material) ? o.material : [o.material]).forEach((m) => { if (m.map) m.map.dispose(); m.dispose() })
    })
    disposables.forEach((g) => g.dispose())
    cargoMatCache.forEach((mats) => mats.forEach((m) => { if (m.map) m.map.dispose(); m.dispose() }))
    cargoMatCache.clear()
    Object.values(tex).forEach((t) => t.dispose())
    envTex.dispose(); pmrem.dispose()
    composer.dispose && composer.dispose()
    renderer.dispose()
    renderer.domElement.remove()
    labelRenderer.domElement.remove()
  }

  return { update, render, setView, setPreset, setAutoGate, get autoGate() { return autoGate }, get autoGateSide() { return gateSide }, focusGate, resize, dispose, setQuality: (q) => { autoQ = false; setQuality(q) }, set2D, setTheme, get is2D() { return is2D }, get theme() { return theme }, get quality() { return quality }, camera, controls, renderer, world, toWorld: (x, y) => [X(x), Z(y)], get viewMode() { return viewMode } }
}
