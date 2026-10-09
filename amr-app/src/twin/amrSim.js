// ─────────────────────────────────────────────────────────────
// S카고 물류센터 AMR 시뮬레이션 코어 (그리기 없음)
// 평면도 좌표(px, 1000 x 640) 기준으로 동작하고, 3D 렌더러가 미터로 변환해서 그린다.
//   - 입고: 운송장 OCR로 목적지 허브 확인 → 권역별 랙 보관
//   - 출고: 허브별 출고 도크 택배차로 이동
//   - 예지보전: AMR-03 진동 상승 → 고장 예측 → 예비기 교대 → 계획 정비
// ─────────────────────────────────────────────────────────────
export const W = 1000, H = 640
export const AISLES = [62, 175, 295, 415, 540]
export const ROWS = [[92, 142], [210, 260], [330, 380], [450, 500]]
export const NS = 9
export const SX = (i) => 262 + i * 49
export const DOCK_Y = [130, 300, 470]
export const HXL = 185, HXR = 720
export const STATION_Y = 600
export const CHARGERS = [262, 311, 360, 409, 458].map((x) => ({ x, y: STATION_Y, kind: 'station' }))
export const BAY = { x: 600, y: STATION_Y, kind: 'station' }
export const GW = { x: 870, y: 30 }
// 무인 지게차 대기 · 충전 위치
export const FL_HOME = { x: 660, y: STATION_Y, kind: 'station' }
// 랙 단 높이 (m): 바닥 칸(0층)은 AMR, 위 3단은 무인 지게차가 다룬다
export const LEVEL_H = [1.75, 3.45, 5.15]
// 냉장 · 냉동 창고: 0번 줄 랙을 단열 패널 방으로 감싼다 (북쪽 통로 쪽 한 면만 사용)
export const COLD_ROW = 0
export const COLD_ROOM = { x0: 222, x1: 688, y0: 32, y1: 120, doorY: 62, doors: [222, 688] }
const VMAX = 84, ACC = 150, VMAX_FL = 56

export const COLORS = { cyan: '#3fe0cc', amber: '#ffb84d', red: '#ff5a5f', green: '#4fdc8a', spare: '#6b7a8a', sky: '#9fd8ff' }
export const HUBS = [
  { name: '호남', full: '호남 허브 (광주)', col: '#b48cff' },
  { name: '강원', full: '강원 허브 (원주)', col: '#5aa8ff' },
  { name: '영남', full: '영남 허브 (대구)', col: '#ff7eb3' },
]
export const ZONES = [
  { tag: 'D', name: '냉장', col: COLORS.sky },
  { tag: 'A', name: '호남', col: HUBS[0].col },
  { tag: 'B', name: '강원', col: HUBS[1].col },
  { tag: 'C', name: '영남', col: HUBS[2].col },
]
// 줄 · 면이 실제로 쓰이는지 (냉장 창고 줄은 북쪽 면만)
export const sideOn = (r, s) => !(r === COLD_ROW && s === 1)
export const STATE_KO = { idle: '대기', toPick: '픽업 이동', load: '적재 중', toDrop: '운반 중', unload: '하역 중', toMaint: '정비소 이동', maint: '정비 중', toHome: '복귀 중', toRack: '랙 이동', fwork: '랙 작업 중' }
export const PHASE_T = { 1: '정상 운영', 2: '이상 징후 감지', 3: 'AI 고장 예측', 4: '교대 · 계획 정비', 5: '정상 복귀' }
export const CAPTIONS = {
  1: '입고 도크에서 운송장 OCR로 목적지를 읽고, AMR이 권역별 랙 바닥 칸(A 호남 · B 강원 · C 영남 · D 냉장 창고)에 보관합니다. 위 단 보충 재고는 무인 지게차가 내리고 올리며, 출고는 허브별 택배차로 나갑니다.',
  2: 'AMR-03 구동모터 진동이 평소 범위(1.2~2.0 mm/s)를 벗어나기 시작합니다. 무거운 화물을 가장 많이 나른 기체입니다.',
  3: '진동 추세와 누적 운반 중량으로 고장 시점을 예측합니다. 고장 전에 정비를 예약하고, 예비기 AMR-05를 투입합니다.',
  4: 'AMR-03은 하던 작업만 마치고 정비 스테이션으로 이동합니다. 그동안 AMR-05가 작업을 이어받아 허브 출고는 멈추지 않습니다.',
  5: '베어링 교체 후 진동이 정상으로 돌아오고 누적 운반 중량도 초기화됩니다. AMR-05는 대기 위치로 돌아갑니다.',
}
export const PIPE_ON = { 1: [0, 1, 2], 2: [2, 3], 3: [3, 4], 4: [4, 5], 5: [4] }
export const PH_COL = { 1: COLORS.cyan, 2: COLORS.amber, 3: COLORS.amber, 4: COLORS.red, 5: COLORS.green }

export const zoneRow = (c) => (c.handle === '냉장' ? COLD_ROW : c.hub + 1)
export const tons = (a) => (a.cumKg / 1000).toFixed(2)

export function status(a) {
  if (a.state === 'maint') return ['crit', '정비 중']
  if (a.maintFlag) return ['crit', '정비 필요']
  if (a.vib > 2.6 && a.deg > 0.5) return ['warn', '주의']
  if (a.role === 'spare' && a.state === 'idle') return ['spare', '예비 대기']
  return ['ok', '정상']
}
export const lvCol = (lv) => (lv === 'crit' ? COLORS.red : lv === 'warn' ? COLORS.amber : lv === 'spare' ? COLORS.spare : COLORS.cyan)

export function createSim(hooks = {}) {
  const onLog = hooks.onLog || (() => {})
  const onPhase = hooks.onPhase || (() => {})

  let seed = 11
  const rnd = () => { seed |= 0; seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296 }
  const pick = (a) => a[Math.floor(rnd() * a.length)]
  const CATS = [
    { n: '전자제품', w: [2, 12], h: () => { const r = rnd(); return r < 0.45 ? '파손주의' : r < 0.75 ? '취급주의' : '일반' } },
    { n: '식품', w: [3, 18], h: () => (rnd() < 0.55 ? '냉장' : '일반') },
    { n: '의류', w: [1, 6], h: () => '일반' },
    { n: '생활용품', w: [2, 15], h: () => { const r = rnd(); return r < 0.15 ? '파손주의' : r < 0.4 ? '취급주의' : '일반' } },
    { n: '가구부품', w: [10, 28], h: () => '일반' },
  ]

  const S = {}

  function mkCargo(hub) {
    const c = pick(CATS), w = Math.round(c.w[0] + rnd() * (c.w[1] - c.w[0]))
    const id = 'SC-' + S.cargoSeq++, h = hub == null ? Math.floor(rnd() * 3) : hub
    // 취급 표시: 파손주의 · 취급주의 · 냉장 · 중량물(20kg 이상) · 일반 → 박스 스티커와 운반 속도에 쓰인다
    let handle = c.h()
    if (handle === '일반' && w >= 20) handle = '중량물'
    return { id, hub: h, cat: c.n, w, size: w < 6 ? 'S' : w < 15 ? 'M' : 'L', handle, loc: null, res: false }
  }
  function mkCargoForRow(r) { let c; do { c = mkCargo(r === COLD_ROW ? null : r - 1) } while ((r === COLD_ROW) !== (c.handle === '냉장')); return c }
  function fillInTruck(dk, d, n) { dk.cargo = []; for (let k = 0; k < n; k++) { const c = mkCargo(); c.loc = { type: 'inTruck', d }; dk.cargo.push(c) } }

  function addLog(text, lv) { S.logItems.unshift({ t: S.simT || 0, text, lv }); S.logItems = S.logItems.slice(0, 8); onLog(S.logItems) }
  function setPhase(p) { S.sc.phase = p; S.sc.start = S.sc.t; onPhase(p) }

  function init() {
    seed = 11
    Object.assign(S, { simT: 0, thru: 0, logItems: [], taskFlip: 0, sampleAcc: 0, packets: [], cargoSeq: 24001, scans: [], hubSent: [0, 0, 0], scanVer: 0 })
    S.slots = ROWS.map((_, r) => [0, 1].map((s) => Array.from({ length: NS }, (_, i) => {
      if (!sideOn(r, s)) return { st: -1, box: null }
      if (rnd() < 0.45) { const b = mkCargoForRow(r); b.loc = { type: 'slot', r, s, i }; return { st: 1, box: b } }
      return { st: 0, box: null }
    })))
    // 위 3단 보충 재고 (팔레트 단위)
    S.upper = ROWS.map((_, r) => [0, 1].map((s) => Array.from({ length: NS }, (_, i) => [0, 1, 2].map((lv) => {
      if (!sideOn(r, s)) return null
      if (rnd() < 0.6) { const b = mkCargoForRow(r); b.loc = { type: 'upper', r, s, i, lv }; return { box: b, res: false } }
      return { box: null, res: false }
    }))))
    S.upperVer = 0
    S.inDocks = DOCK_Y.map((y, d) => { const dk = { y, state: d === 2 ? 'empty' : 'docked', off: d === 2 ? 1 : 0, cargo: [], timer: 2 }; if (d !== 2) fillInTruck(dk, d, 3 + (d % 2)); return dk })
    S.outDocks = DOCK_Y.map((y, d) => ({ y, hub: d, state: 'docked', off: 0, boxes: [], res: 0, timer: 0, cap: 4 }))
    const cum = [8200, 9100, 12400, 7600, 3100]
    S.amrs = [0, 1, 2, 3, 4].map((i) => { const h = CHARGERS[i]; return {
      id: i + 1, name: 'AMR-0' + (i + 1), x: h.x, y: h.y, rx: h.x, ry: h.y, ox: 0, oy: 0, ang: -Math.PI / 2,
      home: h, cur: h, dest: h, path: [], state: 'idle', task: null, box: null, lastBox: null, timer: 0, wait: 0.4 + i * 0.6, role: i === 4 ? 'spare' : 'active',
      v: 0, blocked: 0, dirx: 0, diry: -1, pktAcc: i * 0.15, cumKg: cum[i],
      vib: 1.5, temp: 45, batt: 66 + i * 6, health: 98, hist: [], maintFlag: false, release: false, deg: 0, ph: i * 2.1, kind: 'amr' } })
    // 무인 지게차 FL-01: 위 단 ↔ 바닥 칸 보충 · 적치 전담
    S.amrs.push({
      id: 6, name: 'FL-01', kind: 'fl', x: FL_HOME.x, y: FL_HOME.y, ox: 0, oy: 0, ang: -Math.PI / 2,
      home: FL_HOME, cur: FL_HOME, dest: FL_HOME, path: [], state: 'idle', task: null, box: null, lastBox: null, timer: 0, wait: 2.5, role: 'active',
      v: 0, blocked: 0, dirx: 0, diry: -1, pktAcc: 0.4, cumKg: 5300, vib: 1.1, temp: 41, batt: 82, health: 97, hist: [], maintFlag: false, release: false, deg: 0, ph: 1,
      forkH: 0, reach: 0, fw: null, fx: 0, fy: 0,
    })
    S.sc = { phase: 1, t: 0, start: 0, degrading: false, rul: null, slope: 0 }
    addLog('관제 시작 · AMR 4대 가동, 1대 예비 · 무인 지게차 1대', '')
    setPhase(1)
  }

  // ── 경로: 통로 → 간선 → 통로 → 목적지 ──
  const aisleOf = (p) => (p.kind === 'station' ? 540 : p.y)
  function route(P, Q) {
    let hx
    if (P.kind === 'in' || Q.kind === 'in') hx = HXL
    else if (P.kind === 'out' || Q.kind === 'out') hx = HXR
    else hx = Math.abs(P.x - HXL) + Math.abs(Q.x - HXL) <= Math.abs(P.x - HXR) + Math.abs(Q.x - HXR) ? HXL : HXR
    const ya = aisleOf(P), yb = aisleOf(Q), pts = []
    const add = (p) => { const l = pts[pts.length - 1] || P; if (Math.hypot(l.x - p.x, l.y - p.y) > 0.5) pts.push(p) }
    add({ x: P.x, y: ya })
    if (ya !== yb) { add({ x: hx, y: ya }); add({ x: hx, y: yb }) }
    add({ x: Q.x, y: yb }); add({ x: Q.x, y: Q.y })
    return pts
  }
  function goTo(a, Q, st) { a.path = route(a.cur, Q); a.dest = Q; a.state = st }

  // ── 작업 할당: 목적지 허브 기준 ──
  function slotsWhere(fn) { const o = []; S.slots.forEach((r, ri) => r.forEach((s, si) => s.forEach((v, i) => { if (fn(v, ri)) o.push({ r: ri, s: si, i }) }))); return o }
  const slotAt = (sl) => S.slots[sl.r][sl.s][sl.i]
  const slotPt = (sl) => ({ x: SX(sl.i), y: AISLES[sl.r + sl.s], kind: 'rack' })
  function near(a, list) { list.sort((p, q) => Math.hypot(SX(p.i) - a.x, AISLES[p.r + p.s] - a.y) - Math.hypot(SX(q.i) - a.x, AISLES[q.r + q.s] - a.y)); return list[Math.floor(rnd() * Math.min(4, list.length))] }
  function tryIn(a) {
    const ds = S.inDocks.map((d, k) => ({ d, k })).filter((o) => o.d.state === 'docked' && o.d.cargo.some((c) => !c.res))
    for (const { d, k } of ds.sort(() => rnd() - 0.5)) {
      for (const c of d.cargo) {
        if (c.res) continue
        const z = zoneRow(c), em = slotsWhere((v, ri) => ri === z && v.st === 0)
        if (!em.length) continue
        const sl = near(a, em); c.res = true; slotAt(sl).st = 2
        return { type: 'in', dock: d, dk: k, box: c, slot: sl, from: { x: 115, y: d.y, kind: 'in' }, to: slotPt(sl) }
      }
    }
    return null
  }
  function tryOut(a) {
    const ds = S.outDocks.filter((d) => d.state === 'docked' && d.cap - d.boxes.length - d.res > 0)
    for (const d of ds.sort(() => rnd() - 0.5)) {
      const fl = slotsWhere((v) => v.st === 1 && v.box && v.box.hub === d.hub)
      if (!fl.length) continue
      const sl = near(a, fl); d.res++; slotAt(sl).st = 3
      return { type: 'out', dock: d, box: slotAt(sl).box, slot: sl, from: slotPt(sl), to: { x: 885, y: d.y, kind: 'out' } }
    }
    return null
  }
  function assign(a) {
    const full = slotsWhere((v) => v.st === 1).length / slotsWhere((v) => v.st >= 0).length
    let preferIn = S.taskFlip++ % 2 === 0
    if (full > 0.7) preferIn = false
    if (full < 0.25) preferIn = true
    return preferIn ? tryIn(a) || tryOut(a) : tryOut(a) || tryIn(a)
  }
  function doPick(a) {
    const t = a.task, c = t.box
    if (t.type === 'in') { t.dock.cargo.splice(t.dock.cargo.indexOf(c), 1); S.scans.unshift({ t: S.simT, c }); S.scans = S.scans.slice(0, 4); S.scanVer++ }
    else { const s = slotAt(t.slot); s.st = 0; s.box = null }
    c.loc = { type: 'amr', a }; a.box = c; a.lastBox = c
  }
  function doDrop(a) {
    const t = a.task, c = t.box
    if (t.type === 'in') { const s = slotAt(t.slot); s.st = 1; s.box = c; c.loc = { type: 'slot', ...t.slot }; c.res = false }
    else { t.dock.boxes.push(c); t.dock.res--; c.loc = { type: 'outTruck', d: t.dock.hub } }
    a.cumKg += c.w; a.box = null; S.thru++
  }

  // ── 도크 ──
  function updDocks(dt) {
    S.inDocks.forEach((d, k) => {
      if (d.state === 'docked' && d.cargo.length === 0) d.state = 'leaving'
      else if (d.state === 'leaving') { d.off += dt / 2.2; if (d.off >= 1) { d.off = 1; d.state = 'empty'; d.timer = 1.5 + rnd() * 2.5 } }
      else if (d.state === 'empty') { d.timer -= dt; if (d.timer <= 0) { d.state = 'arriving'; fillInTruck(d, k, 3 + Math.floor(rnd() * 2)) } }
      else if (d.state === 'arriving') { d.off -= dt / 2.2; if (d.off <= 0) { d.off = 0; d.state = 'docked' } }
    })
    S.outDocks.forEach((d) => {
      if (d.state === 'docked' && d.boxes.length >= d.cap && d.res === 0) {
        d.state = 'leaving'
        const kg = d.boxes.reduce((s, c) => s + c.w, 0)
        d.boxes.forEach((c) => (c.loc = { type: 'gone', hub: d.hub }))
        S.hubSent[d.hub] += d.boxes.length
        addLog('D' + (d.hub + 4) + ' ' + HUBS[d.hub].name + ' 허브행 택배차 출발 · ' + d.boxes.length + '건 ' + kg + 'kg', 'hub')
      } else if (d.state === 'leaving') { d.off += dt / 2.2; if (d.off >= 1) { d.off = 1; d.state = 'empty'; d.timer = 1.2 + rnd() * 2 } }
      else if (d.state === 'empty') { d.timer -= dt; if (d.timer <= 0) { d.state = 'arriving'; d.boxes = [] } }
      else if (d.state === 'arriving') { d.off -= dt / 2.2; if (d.off <= 0) { d.off = 0; d.state = 'docked' } }
    })
  }

  // ── AMR 이동: 가감속 + 코너 감속 + 앞차 간격 유지 ──
  function blockedAhead(a, dx, dy) {
    for (const b of S.amrs) {
      if (b === a) continue
      const vx = b.x - a.x, vy = b.y - a.y, along = vx * dx + vy * dy, lat = Math.abs(vx * dy - vy * dx)
      if (along <= 2 || along > (b.kind === 'fl' || a.kind === 'fl' ? 44 : 34) || lat > 9) continue
      // 랙 앞에서 작업 중인 지게차: 같은 차선(랙 쪽)이면 끝날 때까지 기다리고, 반대 차선이면 비켜 지나간다
      if (b.kind === 'fl' && b.state === 'fwork') { if (-dy * b.fx + dx * b.fy > 0.5) return true; continue }
      if (a.blocked > 2) continue // 서로 마주 보고 멈춘 경우 풀어 준다
      if (!b.path.length) return true
      if (b.dirx * dx + b.diry * dy > 0.3) return true
    }
    return false
  }
  function move(a, dt) {
    if (!a.path.length) { a.v = 0; return }
    const p = a.path[0], dx = p.x - a.x, dy = p.y - a.y, dist = Math.hypot(dx, dy) || 1e-6
    const ux = dx / dist, uy = dy / dist
    a.dirx = ux; a.diry = uy
    let target = a.kind === 'fl' ? VMAX_FL : VMAX
    if (a.kind === 'fl') { /* 지게차는 짐이 있어도 같은 속도 */ }
    else if (a.box && a.box.handle === '파손주의') target = 60
    else if (a.box && (a.box.handle === '취급주의' || a.box.handle === '중량물')) target = 72
    const last = a.path.length === 1
    const remain = a.path.reduce((s, q, i, arr) => s + (i === 0 ? dist : Math.hypot(q.x - arr[i - 1].x, q.y - arr[i - 1].y)), 0)
    if (last || remain < 40) target = Math.min(target, Math.max(14, remain * 2.2))
    if (!last && dist < 22) target = Math.min(target, 38)
    if (blockedAhead(a, ux, uy)) { target = 0; a.blocked += dt } else a.blocked = Math.max(0, a.blocked - dt * 2)
    a.v += Math.max(-ACC * 1.6 * dt, Math.min(ACC * dt, target - a.v))
    if (a.v < 0) a.v = 0
    let rem = a.v * dt
    while (rem > 0 && a.path.length) {
      const q = a.path[0], ddx = q.x - a.x, ddy = q.y - a.y, d = Math.hypot(ddx, ddy)
      if (d <= rem) { a.x = q.x; a.y = q.y; rem -= d; a.path.shift() } else { a.x += (ddx / d) * rem; a.y += (ddy / d) * rem; rem = 0 }
    }
    if (a.v > 3) { const ta = Math.atan2(uy, ux); let df = ta - a.ang; while (df > Math.PI) df -= 2 * Math.PI; while (df < -Math.PI) df += 2 * Math.PI; a.ang += df * Math.min(1, dt * 9) }
    a.batt = Math.max(35, a.batt - dt * (a.box ? 0.13 : 0.1))
  }
  function updOffset(a, dt) {
    // 우측 통행: 이동 방향의 오른쪽 차선으로 비켜서 달린다 (정지 시 중앙)
    const moving = a.path.length > 0 && a.state !== 'maint'
    let tx = moving ? -a.diry : 0, ty = moving ? a.dirx : 0
    if (a.kind === 'fl' && a.state === 'fwork') { tx = a.fx * 0.4; ty = a.fy * 0.4 } // 랙 쪽으로 붙는다
    const k = Math.min(1, dt * 5)
    a.ox += (tx - a.ox) * k; a.oy += (ty - a.oy) * k
  }
  function afterTask(a) {
    if (a.maintFlag) { goTo(a, BAY, 'toMaint'); addLog(a.name + ' 작업 완료 → 정비 스테이션 이동', 'crit'); return }
    if (a.release) { a.release = false; a.role = 'spare'; goTo(a, a.home, 'toHome'); addLog(a.name + ' 대기 위치로 복귀', ''); return }
    const t = assign(a)
    if (t) { a.task = t; goTo(a, t.from, 'toPick') } else goTo(a, a.home, 'toHome')
  }
  function arrive(a) {
    a.cur = a.dest; a.v = 0
    if (a.state === 'toPick') { a.state = 'load'; a.timer = a.task.type === 'in' ? 1.4 : 0.9 }
    else if (a.state === 'toDrop') { a.state = 'unload'; a.timer = 0.9 }
    else if (a.state === 'toMaint') { a.state = 'maint'; a.timer = 7; setPhase(4); addLog(a.name + ' 정비 시작 · 구동모터 베어링 교체', 'crit') }
    else if (a.state === 'toHome') { a.state = 'idle'; a.wait = 0.6 }
  }
  // ── 무인 지게차: 위 단 보충 재고 ↔ 바닥 칸 ──
  const upperAt = (r, s, i, lv) => S.upper[r][s][i][lv]
  function floorCount(r) { let n = 0, tot = 0; S.slots[r].forEach((side) => side.forEach((v) => { if (v.st >= 0) { tot++; if (v.st === 1) n++ } })); return [n, tot] }
  function flAssign(a) {
    // 1) 바닥이 비어가는 줄: 위 단 재고를 내려서 바닥 칸을 채운다 (보충)
    // 2) 바닥이 꽉 찬 줄: 바닥 화물을 위 단으로 올려 입고 자리를 만든다 (적치)
    const rows = ROWS.map((_, r) => { const [n, tot] = floorCount(r); return { r, fill: n / tot } }).sort((p, q) => p.fill - q.fill)
    for (const { r, fill } of rows) {
      if (fill > 0.5) break
      const c = []
      S.slots[r].forEach((side, s) => side.forEach((v, i) => { if (v.st === 0) S.upper[r][s][i].forEach((u, lv) => { if (u && u.box && !u.res) c.push({ r, s, i, lv }) }) }))
      if (c.length) { const t = near(a, c); return { type: 'repl', slot: { r: t.r, s: t.s, i: t.i }, lv: t.lv } }
    }
    for (const { r, fill } of rows.slice().reverse()) {
      if (fill < 0.75) break
      const c = []
      S.slots[r].forEach((side, s) => side.forEach((v, i) => { if (v.st === 1) S.upper[r][s][i].forEach((u, lv) => { if (u && !u.box && !u.res) c.push({ r, s, i, lv }) }) }))
      if (c.length) { const t = near(a, c); return { type: 'put', slot: { r: t.r, s: t.s, i: t.i }, lv: t.lv } }
    }
    return null
  }
  function flStart(a) {
    const t = flAssign(a)
    if (!t) { if (a.cur !== a.home) goTo(a, a.home, 'toHome'); return false }
    const sl = slotAt(t.slot), u = upperAt(t.slot.r, t.slot.s, t.slot.i, t.lv)
    u.res = true
    if (t.type === 'repl') { sl.st = 4; t.box = u.box } else { sl.st = 5; t.box = sl.box }
    a.task = t
    goTo(a, slotPt(t.slot), 'toRack')
    return true
  }
  // 작업 단계: 랙 쪽으로 회전 → (포크 올림) → 밀어 넣기 → 들기/놓기 → 빼기 → ...
  function flPlan(a) {
    const t = a.task, H = LEVEL_H[t.lv], up = H / 0.9
    const P = (k, d) => ({ k, d })
    if (t.type === 'repl') return [P('turn', 0.7), P('up', up), P('in', 0.7), P('grab', 0.5), P('out', 0.7), P('down', up), P('in', 0.7), P('drop', 0.4), P('out', 0.7)]
    return [P('turn', 0.7), P('in', 0.7), P('grab', 0.5), P('out', 0.7), P('up', up), P('in', 0.7), P('drop', 0.4), P('out', 0.7), P('down', up)]
  }
  function flWork(a, dt) {
    const fw = a.fw, ph = fw.list[fw.idx], t = a.task
    fw.t += dt
    const k = Math.min(1, fw.t / ph.d)
    const H = LEVEL_H[t.lv]
    if (ph.k === 'turn') { let df = fw.face - a.ang; while (df > Math.PI) df -= 2 * Math.PI; while (df < -Math.PI) df += 2 * Math.PI; a.ang += df * Math.min(1, dt * 6) }
    if (ph.k === 'up') a.forkH = (fw.from || 0) + (H - (fw.from || 0)) * k
    if (ph.k === 'down') a.forkH = fw.from * (1 - k)
    if (ph.k === 'in') a.reach = k
    if (ph.k === 'out') a.reach = 1 - k
    if (fw.t < ph.d) return
    // 단계 끝: 들기 · 놓기 처리
    const sl = slotAt(t.slot), u = upperAt(t.slot.r, t.slot.s, t.slot.i, t.lv), c = t.box
    if (ph.k === 'grab') {
      if (t.type === 'repl') { u.box = null } else { sl.box = null }
      c.loc = { type: 'amr', a }; a.box = c; a.lastBox = c; S.upperVer++
    }
    if (ph.k === 'drop') {
      if (t.type === 'repl') { sl.box = c; sl.st = 1; c.loc = { type: 'slot', ...t.slot }; u.res = false; addLog('FL-01 보충 · ' + ZONES[t.slot.r].tag + '-' + String(t.slot.i + 1).padStart(2, '0') + ' ' + (t.lv + 2) + '단 → 바닥 칸', '') }
      else { u.box = c; u.res = false; sl.st = 0; c.loc = { type: 'upper', ...t.slot, lv: t.lv }; addLog('FL-01 적치 · 바닥 칸 → ' + ZONES[t.slot.r].tag + '-' + String(t.slot.i + 1).padStart(2, '0') + ' ' + (t.lv + 2) + '단', '') }
      a.cumKg += c.w; a.box = null; S.upperVer++
    }
    fw.idx++; fw.t = 0; fw.from = a.forkH
    if (fw.idx >= fw.list.length) { a.fw = null; a.task = null; a.forkH = 0; a.reach = 0; a.state = 'idle'; a.wait = 0.8 }
  }
  function updFork(a, dt) {
    switch (a.state) {
      case 'idle':
        a.batt = Math.min(100, a.batt + dt * 0.6)
        a.wait -= dt
        if (a.wait <= 0) { a.wait = 1.2; flStart(a) }
        break
      case 'toRack': case 'toHome':
        move(a, dt)
        if (!a.path.length) {
          a.cur = a.dest; a.v = 0
          if (a.state === 'toHome') { a.state = 'idle'; a.wait = 1.5; break }
          // 랙 방향 (s=0: 통로 남쪽 랙, s=1: 통로 북쪽 랙)
          const s = a.task.slot.s
          a.fx = 0; a.fy = s === 0 ? 1 : -1
          a.state = 'fwork'; a.fw = { list: flPlan(a), idx: 0, t: 0, from: 0, face: Math.atan2(a.fy, a.fx) }
        }
        break
      case 'fwork': flWork(a, dt); break
    }
    a.fwPhase = a.fw ? a.fw.list[a.fw.idx] && a.fw.list[a.fw.idx].k : null
  }
  function updAmr(a, dt) {
    a.ph += dt
    if (a.kind === 'fl') { updFork(a, dt); sensors(a, dt); return }
    switch (a.state) {
      case 'idle':
        a.batt = Math.min(100, a.batt + dt * 0.8)
        if (a.release) { a.release = false; a.role = 'spare' }
        a.wait -= dt
        if (a.wait <= 0) {
          a.wait = 0.6
          if (a.maintFlag) { goTo(a, BAY, 'toMaint'); addLog(a.name + ' 정비 스테이션 이동', 'crit') }
          else if (a.role === 'active') { const t = assign(a); if (t) { a.task = t; goTo(a, t.from, 'toPick') } }
        }
        break
      case 'toPick': case 'toDrop': case 'toMaint': case 'toHome':
        move(a, dt); if (!a.path.length) arrive(a); break
      case 'load': a.timer -= dt; if (a.timer <= 0) { doPick(a); goTo(a, a.task.to, 'toDrop') } break
      case 'unload': a.timer -= dt; if (a.timer <= 0) { doDrop(a); a.task = null; afterTask(a) } break
      case 'maint': a.timer -= dt; if (a.timer <= 0) repair(a); break
    }
    sensors(a, dt)
  }
  function sensors(a, dt) {
    updOffset(a, dt)
    const moving = a.v > 5 || a.state === 'fwork'
    const loadF = a.box ? a.box.w * 0.012 : 0
    const base = 1.45 + 0.17 * Math.sin(a.ph * 1.3 + a.id) + (Math.random() - 0.5) * 0.16 + (moving ? 0.15 : 0) + (moving ? loadF : 0)
    a.vib = a.state === 'maint' ? 0.2 + Math.random() * 0.05 : base + a.deg
    const tt = a.state === 'maint' ? 32 : 43 + (moving ? 3 : 0) + loadF * 6 + a.deg * 7 + (Math.random() - 0.5) * 0.6
    a.temp += (tt - a.temp) * Math.min(1, dt * 1.2)
    a.health = a.state === 'maint' ? a.health : Math.max(5, Math.min(99, 100 - Math.max(0, a.vib - 1.9) * 15 - Math.max(0, a.temp - 55) * 0.7))
    if (a.state !== 'maint' && (a.role === 'active' || a.state !== 'idle')) {
      a.pktAcc += dt
      if (a.pktAcc > 0.9) { a.pktAcc = 0; const [lv] = status(a); S.packets.push({ x: a.x, y: a.y, t: 0, lv }) }
    }
  }
  function repair(a) {
    a.deg = 0; a.maintFlag = false; a.health = 98; a.temp = 40; a.cumKg = 0
    S.sc.degrading = false; S.sc.rul = null
    addLog(a.name + ' 정비 완료 · 진동 정상, 누적 운반 초기화', 'ok')
    setPhase(5)
    goTo(a, a.home, 'toHome')
    S.amrs[4].release = true
  }

  // ── 예지보전 시나리오 ──
  function slopeOf(h) { const v = h.filter((x) => x != null).slice(-20); if (v.length < 5) return 0; const n = v.length; let sx = 0, sy = 0, sxy = 0, sxx = 0; v.forEach((y, i) => { sx += i; sy += y; sxy += i * y; sxx += i * i }); return (n * sxy - sx * sy) / (n * sxx - sx * sx) / 0.2 }
  function scenario(dt) {
    const sc = S.sc, T = S.amrs[2], SP = S.amrs[4]
    sc.t += dt
    if (sc.phase === 1 && !sc.degrading && sc.t - sc.start > 9) sc.degrading = true
    if (sc.degrading && T.state !== 'maint') T.deg = Math.min(5.2, T.deg + dt * 0.27)
    if (sc.phase === 1 && T.deg > 1.2) { setPhase(2); addLog('AMR-03 진동 이상 감지 · 평소 대비 +' + Math.round((T.vib / 1.55 - 1) * 100) + '%', 'warn') }
    if (sc.phase === 2 && T.deg > 2.5) {
      sc.slope = Math.max(0.08, slopeOf(T.hist))
      sc.rul = Math.max(6, Math.round(((7.1 - T.vib) / sc.slope) * 4))
      setPhase(3)
      addLog('AI 예측 · 베어링 마모(누적 ' + tons(T) + 't 운반), 고장까지 약 ' + sc.rul + '시간', 'crit')
      T.maintFlag = true; SP.role = 'active'; SP.wait = 0.2
      addLog('정비 예약 · 예비기 AMR-05 투입', 'warn')
    }
    if (sc.phase === 3 && sc.rul != null) sc.slope = Math.max(0.08, slopeOf(T.hist) || sc.slope)
    if (sc.phase === 5 && sc.t - sc.start > 9) { setPhase(1); addLog('정상 운영 · 다음 주기 모니터링', '') }
  }

  function step(dt) {
    S.simT += dt
    scenario(dt); updDocks(dt); S.amrs.forEach((a) => updAmr(a, dt))
    S.sampleAcc += dt
    while (S.sampleAcc >= 0.2) { S.sampleAcc -= 0.2; S.amrs.forEach((a) => { a.hist.push(a.state === 'maint' ? null : a.vib); if (a.hist.length > 150) a.hist.shift() }) }
  }

  function locText(c) {
    const L = c.loc
    const loc = (r, i) => ZONES[r].tag + '-' + String(i + 1).padStart(2, '0')
    if (L.type === 'slot') return `랙 ${loc(L.r, L.i)} 바닥 칸${L.r === COLD_ROW ? ' (냉장 창고)' : ''} · 출고 대기`
    if (L.type === 'upper') return `랙 ${loc(L.r, L.i)} ${L.lv + 2}단 · 보충 재고${L.r === COLD_ROW ? ' (냉장 창고)' : ''}`
    if (L.type === 'amr' && L.a.kind === 'fl') return `무인 지게차 FL-01 · ${L.a.task && L.a.task.type === 'repl' ? '위 단 → 바닥 칸 보충 중' : '바닥 칸 → 위 단 적치 중'}`
    if (L.type === 'amr') return `${L.a.name} 운반 중 → ${L.a.task && L.a.task.type === 'out' ? 'D' + (c.hub + 4) + ' 택배차' : '랙 ' + ZONES[zoneRow(c)].tag + ' 구역'}`
    if (L.type === 'outTruck') return `D${L.d + 4} 택배차 적재 · 출발 대기`
    if (L.type === 'inTruck') return `입고 도크 D${L.d + 1} · 하차 대기`
    if (L.type === 'gone') return `${HUBS[L.hub].full}로 출발`
    return '-'
  }

  init()
  return { S, init, step, locText }
}
