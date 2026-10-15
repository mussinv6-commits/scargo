// ─────────────────────────────────────────────────────────────
// S카고 허브 AMR 디지털 트윈 — 화면 조립
// 시뮬레이션 + 3D 씬 + 관제 패널(차트·화물·로그)을 하나의 루트 요소에 붙인다.
// 사용: const app = mountAmrTwin(rootEl)  →  화면을 떠날 때 app.destroy()
// ─────────────────────────────────────────────────────────────
import { createSim, HUBS, ZONES, STATE_KO, PHASE_T, CAPTIONS, PIPE_ON, PH_COL, COLORS, status, lvCol, tons, zoneRow, captionOf, fmtPlate } from './amrSim.js'
// ZONES: 0 D 냉장 창고 · 1 A 인천 · 2 B 서울 · 3 C 경기
import { createAmrScene } from './amrScene3d.js'
import { createAmrSound } from './amrSound.js'
import { amrApi } from './amrApi.js'   // DB 연동 (서버가 꺼져 있으면 시뮬레이션만으로 동작)

export function mountAmrTwin(root) {
  const $ = (id) => root.querySelector('#tw-' + id)
  let selected = 2, cargoPin = null, speed = 1, playing = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  let bannerTimer = 0, raf = 0, scene = null, destroyed = false
  let lastScanVer = -1
  let simRef = null   // createSim 도중에도 단계 콜백이 불릴 수 있어서 따로 둔다

  /* ── 단계 · 배너 · 로그 ── */
  function applyPhase(p) {
    root.querySelectorAll('.stage').forEach((el) => { const q = +el.dataset.p; el.classList.toggle('now', q === p); el.classList.toggle('done', q < p) })
    $('caption').textContent = simRef ? captionOf(simRef.S) : CAPTIONS[p]
    root.querySelectorAll('.pipe .node').forEach((n) => n.classList.toggle('on', PIPE_ON[p].includes(+n.dataset.k)))
    const b = $('banner')
    b.style.setProperty('--c', PH_COL[p])
    b.querySelector('span').textContent = 'STEP 0' + p + '  ' + PHASE_T[p]
    b.classList.add('show')
    clearTimeout(bannerTimer)
    bannerTimer = setTimeout(() => b.classList.remove('show'), 3000)
    if (p === 2) { selected = simRef ? simRef.S.sc.target : 2; cargoPin = null }
  }
  function fmt(t) { const s = Math.floor(t); return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0') }
  function renderLog(items) {
    $('log').innerHTML = items.map((l) => `<li class="${l.lv}"><time>${fmt(l.t)}</time><span>${l.text}</span></li>`).join('')
  }

  /* ── DB 연동: 이 화면이 현장(시뮬레이터) 역할 → 센서 · 예측 · 정비 진행을 저장, 승인은 앱 · 웹 어디서든 ── */
  let maintId = null, remote = null, approveFlash = 0, pendingBy = null, lastCfgBy = '', cfgLocalT = 0
  // 입구 · 출구 번호판 OCR: 트럭이 게이트에 오면 서버(→ gate_api.py)로 인식 요청, 결과로 3D 트럭이 들어오거나 돌아간다
  let ocrOk = null
  function setOcr(ok, msg) {
    if (ok === ocrOk && ok) return
    ocrOk = ok
    ocrBadge.textContent = ok ? '● OCR 연결' : '○ OCR 오프라인'
    ocrBadge.title = ok ? '번호판 인식 서버(gate_api.py :8001) 연결됨' : (msg || '') + ' · python 폴더에서 gate_api.py 실행 (uvicorn gate_api:app --port 8001)'
    ocrBadge.style.color = ok ? '#4fdc8a' : '#8a9bb0'; ocrBadge.style.borderColor = ok ? '#4fdc8a' : '#3a4a5c'
  }
  function onVehicle(p) {
    amrApi.vehicleScan({ direction: p.dir, kind: p.kind, dock: p.dock, vehicleNo: p.vehicleNo, boxCount: p.boxCount, barcodes: p.barcodes, destHub: p.destHub })
      .then((res) => {
        if (destroyed || !simRef) return
        if (res.data) { setOcr(true); simRef.vehicleResult(p.side, p.dock, res.data, p.dir) }
        else { setOcr(false, res.error); simRef.vehicleResult(p.side, p.dock, null, p.dir) }
      })
  }
  function onSimEvent(type, p) {
    if (type === 'vehicle') { onVehicle(p); return }
    if (type === 'charge') { amrApi.event(p.code, 'INFO', S.sc.phase, p.code + ' 배터리 ' + Math.round(p.battery) + '% · 기준 ' + S.cfg.chargeAt + '% 이하 → 충전소 이동'); return }
    if (type !== 'maint') return
    if (p.eventType === 'WARN') amrApi.event(p.code, 'WARN', 2, p.code + ' 진동 이상 감지 · ' + (p.vibration || 0).toFixed(1) + ' mm/s')
    else if (p.eventType === 'PREDICT') { pendingBy = null; amrApi.predict(p).then((m) => { if (m) { maintId = m.id; if (pendingBy) amrApi.approve(m.id, pendingBy) } }) }
    else if (p.eventType === 'APPROVE') { approveFlash = 8; if (maintId) amrApi.approve(maintId, p.by); else pendingBy = p.by }
    else if (p.eventType === 'START') { if (maintId) amrApi.start(maintId) }
    else if (p.eventType === 'DONE') { const id = maintId; maintId = null; if (id) amrApi.done(id) }
  }
  const sim = createSim({ onLog: renderLog, onPhase: applyPhase, onEvent: onSimEvent, autoApproveSec: 0 })
  simRef = sim
  // 2초마다 DB 상태를 읽어서 앱에서 누른 승인을 반영
  async function pollState() {
    const st = await amrApi.state(); if (!st || destroyed) return
    remote = st
    renderGates(st.vehicles)
    // 관리자 운영 기준 (앱에서 바꾸면 2초 안에 여기 3D 로봇이 그 기준대로 움직인다)
    const cs = st.settings
    if (cs && Date.now() - cfgLocalT > 3000) {
      const changed = cs.rulThresholdHours !== S.cfg.rulAt || cs.chargeAtPct !== S.cfg.chargeAt
      sim.setCfg({ rulAt: cs.rulThresholdHours, chargeAt: cs.chargeAtPct })
      const key = (cs.updatedAt || '') + cs.updatedBy
      if (changed && key !== lastCfgBy && cs.updatedBy && !/웹/.test(cs.updatedBy)) notice('운영 기준 변경 · ' + cs.updatedBy + ' → 정비 ' + cs.rulThresholdHours + '시간 · 충전 ' + cs.chargeAtPct + '% (DB 연동)', COLORS.cyan)
      lastCfgBy = key
      syncCfgInputs()
    }
  }
  /* ── DB 연결 표시 ── */
  const dbBadge = document.createElement('span')
  dbBadge.style.cssText = 'margin-left:10px;font-size:12px;font-weight:800;padding:3px 10px;border-radius:999px;border:1px solid;white-space:nowrap'
  ;(root.querySelector('.twin-head') || root).appendChild(dbBadge)
  const offDb = amrApi.onStatus((on) => { dbBadge.textContent = on ? '● DB 연결 · 앱 연동' : '○ DB 오프라인 · 시뮬레이션만'; dbBadge.style.color = on ? '#4fdc8a' : '#8a9bb0'; dbBadge.style.borderColor = on ? '#4fdc8a' : '#3a4a5c' })
  amrApi.check()
  const ocrBadge = document.createElement('span')
  ocrBadge.style.cssText = dbBadge.style.cssText
  ;(root.querySelector('.twin-head') || root).appendChild(ocrBadge)
  setOcr(false, '아직 인식 요청 없음')
  let dbAcc = 0
  const S = sim.S
  const warm = () => { sim.setVehicleGate(false); for (let i = 0; i < 80; i++) sim.step(0.05); S.packets.length = 0; sim.setVehicleGate(true) }
  warm()

  /* ── 패널 ── */
  const fleetEl = $('fleet')
  function buildFleet() {
    fleetEl.innerHTML = S.amrs.map((a, i) => `<button type="button" class="frow" data-i="${i}"><span class="dot"></span><span class="nm">${a.name}</span><span class="st"></span><span class="bar"><i></i></span><span class="hs"></span></button>`).join('')
  }
  fleetEl.addEventListener('click', (e) => { const b = e.target.closest('.frow'); if (!b) return; select(+b.dataset.i) })
  function select(i) { selected = i; cargoPin = null; if (scene && scene.viewMode === 'follow') scene.setView('follow', i); updPanel(); drawChart() }

  const cssCol = (l) => (l === 'crit' ? 'var(--red)' : l === 'warn' ? 'var(--amber)' : l === 'spare' ? '#4a5a6b' : 'var(--green)')
  function currentCargo() { if (cargoPin) return cargoPin; const a = S.amrs[selected]; return a.box || a.lastBox || null }
  function updCargo() {
    const c = currentCargo()
    if (!c) { $('cId').textContent = '-'; return }
    const h = HUBS[c.hub]
    $('cId').textContent = c.id
    const hc = $('cHub'); hc.textContent = '→ ' + h.full; hc.style.background = h.col
    $('cCat').textContent = c.cat
    $('cW').textContent = c.w + ' kg'
    $('cSize').textContent = { S: '소형 (S)', M: '중형 (M)', L: '대형 (L)' }[c.size]
    const HANDLE_NOTE = { 냉장: ' · D 냉장 구역 보관', 파손주의: ' · 저속 운반 (0.6배)', 취급주의: ' · 감속 운반', 중량물: ' · 감속 운반 · 하단 적재' }
    const hd = $('cHandle'); hd.textContent = c.handle + (HANDLE_NOTE[c.handle] || ''); hd.className = c.handle === '냉장' ? 'cold' : c.handle === '일반' ? '' : 'fragile'
    $('cDock').textContent = 'D' + (c.hub + 4) + ' · ' + h.name + '행 택배차'
    $('cLoc').textContent = '현재 위치 · ' + sim.locText(c)
    $('cHint').textContent = cargoPin ? '고른 화물을 따라가는 중입니다. 로봇을 클릭하면 해제됩니다.' : '선택한 로봇이 옮기는 화물을 따라갑니다. 랙의 박스(위 단 포함)를 클릭하면 그 화물로 고정됩니다.'
  }
  // 입출차 OCR 기록 (DB)
  const KIND = { CARGO: '화물차', COURIER: '택배차', UNKNOWN: '미등록' }
  const DEC = { ALLOW: ['입차', '#4fdc8a'], BLOCK: ['차단', '#ff5a5f'], EXIT_OK: ['출차', '#3fe0cc'], MISMATCH: ['불일치', '#ffb84d'] }
  let lastGateKey = ''
  function renderGates(list) {
    if (!list || !list.length) return
    const key = list.map((v) => v.id).join(',')
    if (key === lastGateKey) return
    lastGateKey = key
    $('gates').innerHTML = list.slice(0, 6).map((v) => {
      const [dl, col] = DEC[v.decision] || [v.decision, '#8597aa']
      return `<li><span class="sid">${fmtPlate(v.plate) || '판독불가'}</span><span class="sd">${KIND[v.kind] || ''}${v.confidence != null ? ' · ' + v.confidence + '%' : ''}${v.boxCount ? ' · ' + v.boxCount + '건' : ''}</span><span class="hubchip" style="background:${col}">${dl}</span></li>`
    }).join('')
  }
  function renderScans() {
    if (S.scanVer === lastScanVer) return
    lastScanVer = S.scanVer
    $('scans').innerHTML = S.scans.length
      ? S.scans.map((s) => { const c = s.c, h = HUBS[c.hub]; return `<li><span class="sid">${c.ean || c.id}</span><span class="sd">${c.id} · ${c.cat} · ${c.w}kg${c.handle !== '일반' ? ' · ' + c.handle : ''}</span><span class="hubchip" style="background:${h.col}">${h.name}</span></li>` }).join('')
      : '<li><span class="sd" style="grid-column:1/-1">입고 도크에서 첫 운송장을 읽는 중입니다.</span></li>'
  }
  // 보관 현황: 구역별 바닥 칸(AMR) · 위 단(무인 지게차) 채움
  function renderStock() {
    $('stock').innerHTML = ZONES.map((z, r) => {
      let fl = 0, flT = 0, up = 0, upT = 0
      S.slots[r].forEach((side) => side.forEach((v) => { if (v.st >= 0) { flT++; if (v.box) fl++ } }))
      S.upper[r].forEach((side) => side.forEach((col) => col.forEach((u) => { if (u) { upT++; if (u.box) up++ } })))
      const bar = (n, tot) => `<span class="sbar"><i style="width:${Math.round((n / tot) * 100)}%;background:${z.col}"></i></span><span class="sn">${n}/${tot}</span>`
      return `<div class="srow"><span class="sz" style="color:${z.col}">${z.tag} ${z.name}</span><span class="sk">바닥</span>${bar(fl, flT)}<span class="sk">위 단</span>${bar(up, upT)}</div>`
    }).join('')
  }
  function updPanel() {
    const a = S.amrs[selected], [lv, lt] = status(a)
    $('selName').textContent = a.name
    $('selState').textContent = (a.role === 'spare' && a.state === 'idle' ? '예비 대기' : STATE_KO[a.state]) + (a.box ? ' · ' + a.box.id + ' → ' + HUBS[a.box.hub].name : '')
    const p = $('selPill'); p.className = 'pill ' + lv; p.textContent = lt
    const mv = $('mVib'); mv.textContent = a.vib.toFixed(1); mv.className = 'v' + (a.vib >= 4.5 ? ' crit' : a.vib >= 2.6 ? ' warn' : '')
    const mt = $('mTemp'); mt.textContent = Math.round(a.temp); mt.className = 'v' + (a.temp >= 70 ? ' crit' : a.temp >= 58 ? ' warn' : '')
    const mh = $('mHealth'); mh.textContent = Math.round(a.health); mh.className = 'v' + (a.health < 50 ? ' crit' : a.health < 80 ? ' warn' : '')
    const mc = $('mCum'); mc.textContent = tons(a); mc.className = 'v' + (a.cumKg >= 12000 ? ' warn' : '')
    $('mLoad').textContent = a.box ? a.box.w : 0
    $('mBatt').textContent = Math.round(a.batt)
    $('rul').innerHTML = selected === 2 && a.maintFlag && S.sc.rul != null && a.state !== 'maint' ? `AI 예측: 고장까지 약 <b>${S.sc.rul}시간</b> · 고장 전 정비 예약됨` : a.state === 'maint' ? '정비 진행 중 · 예비기가 작업 대체' : ''
    fleetEl.querySelectorAll('.frow').forEach((r, i) => {
      const b = S.amrs[i], [l, t] = status(b)
      r.classList.toggle('sel', i === selected)
      const dot = r.querySelector('.dot'); dot.style.background = cssCol(l); dot.style.boxShadow = l === 'spare' ? 'none' : '0 0 8px ' + cssCol(l)
      r.querySelector('.st').textContent = b.role === 'spare' && b.state === 'idle' ? '예비 대기' : STATE_KO[b.state]
      const bar = r.querySelector('.bar i'); bar.style.width = Math.round(b.health) + '%'; bar.style.background = cssCol(l)
      r.querySelector('.hs').textContent = t === '정상' ? Math.round(b.health) + '점' : t
    })
    $('kThru').textContent = S.thru
    const amrOnly = S.amrs.filter((b) => b.kind !== 'fl')
    $('kAct').textContent = amrOnly.filter((b) => b.role === 'active' && !b.maintFlag && b.state !== 'maint').length + '/' + amrOnly.length
    renderStock()
    $('hubcount').innerHTML = HUBS.map((h, i) => `<div style="border-color:${h.col}">${h.name} 출고<b>${S.hubSent[i]}건</b></div>`).join('')
    updCargo(); renderScans()
  }

  /* ── 진동 차트 ── */
  const ch = $('chart'), cx = ch.getContext('2d')
  const CH = { edge: '#1f2c3a', dim: '#566679' }
  const readCH = () => { const cs = getComputedStyle(root); CH.edge = cs.getPropertyValue('--edge').trim() || CH.edge; CH.dim = cs.getPropertyValue('--dim').trim() || CH.dim }
  function sizeChart() { const dpr = Math.min(2, window.devicePixelRatio || 1); ch.width = Math.round(ch.clientWidth * dpr); ch.height = Math.round(130 * dpr) }
  const chartRO = new ResizeObserver(() => { sizeChart(); drawChart() }); chartRO.observe(ch)
  function drawChart() {
    const a = S.amrs[selected], dpr = Math.min(2, window.devicePixelRatio || 1), w = ch.width / dpr, h = 130
    if (!w) return
    cx.setTransform(dpr, 0, 0, dpr, 0, 0); cx.clearRect(0, 0, w, h)
    const L = 24, R = w - 4, Tp = 10, B = h - 20, pw = (R - L) * 0.74, ymax = 8
    const Y = (v) => B - (v / ymax) * (B - Tp)
    cx.font = "10px 'JetBrains Mono',monospace"; cx.textBaseline = 'middle'
    ;[0, 2, 4, 6, 8].forEach((v) => { cx.strokeStyle = CH.edge; cx.lineWidth = 1; cx.beginPath(); cx.moveTo(L, Y(v) + 0.5); cx.lineTo(R, Y(v) + 0.5); cx.stroke(); cx.fillStyle = CH.dim; cx.textAlign = 'right'; cx.fillText(v, L - 5, Y(v)) })
    cx.fillStyle = 'rgba(255,90,95,.06)'; cx.fillRect(L, Y(ymax), R - L, Y(7.1) - Y(ymax))
    cx.fillStyle = 'rgba(255,184,77,.05)'; cx.fillRect(L, Y(7.1), R - L, Y(4.5) - Y(7.1))
    ;[[4.5, COLORS.amber, '주의 4.5'], [7.1, COLORS.red, '고장 7.1']].forEach(([v, c, l]) => { cx.strokeStyle = c; cx.globalAlpha = 0.7; cx.setLineDash([3, 3]); cx.beginPath(); cx.moveTo(L, Y(v)); cx.lineTo(R, Y(v)); cx.stroke(); cx.setLineDash([]); cx.globalAlpha = 1; cx.fillStyle = c; cx.textAlign = 'left'; cx.fillText(l, L + 4, Y(v) - 7) })
    cx.fillStyle = CH.dim; cx.textAlign = 'left'; cx.fillText('진동 · 최근 30초', L, h - 7); cx.textAlign = 'right'; cx.fillText('예측 구간', R, h - 7)
    cx.strokeStyle = CH.edge; cx.beginPath(); cx.moveTo(L + pw + 0.5, Tp); cx.lineTo(L + pw + 0.5, B); cx.stroke()
    const hs = a.hist, N = 150, dx = pw / (N - 1), off = N - hs.length
    const [lv] = status(a), col = lvCol(lv === 'spare' ? 'ok' : lv)
    const segs = []; let cur = []
    hs.forEach((v, i) => { if (v == null) { if (cur.length) segs.push(cur); cur = []; return } cur.push([L + (off + i) * dx, Y(Math.min(ymax, v)), v]) })
    if (cur.length) segs.push(cur)
    segs.forEach((sg) => {
      const gr = cx.createLinearGradient(0, Tp, 0, B); gr.addColorStop(0, col + '55'); gr.addColorStop(1, col + '00')
      cx.fillStyle = gr; cx.beginPath(); cx.moveTo(sg[0][0], B); sg.forEach((p) => cx.lineTo(p[0], p[1])); cx.lineTo(sg[sg.length - 1][0], B); cx.closePath(); cx.fill()
      cx.strokeStyle = col; cx.lineWidth = 1.8; cx.beginPath(); sg.forEach((p, k) => (k ? cx.lineTo(p[0], p[1]) : cx.moveTo(p[0], p[1]))); cx.stroke()
    })
    const lastSeg = segs[segs.length - 1], lastPt = lastSeg && lastSeg[lastSeg.length - 1]
    if (lastPt && lastPt[0] > L + pw - dx * 2) {
      cx.fillStyle = col; cx.shadowColor = col; cx.shadowBlur = 8; cx.beginPath(); cx.arc(lastPt[0], lastPt[1], 3.2, 0, 7); cx.fill(); cx.shadowBlur = 0
      if (selected === 2 && a.maintFlag && S.sc.rul != null && a.state !== 'maint') {
        const sl = Math.max(0.08, S.sc.slope), ly = lastPt[2], secs = (7.1 - ly) / sl, px = Math.min(R, lastPt[0] + secs * 5 * dx)
        const py = px >= R ? Y(ly + (sl * (R - lastPt[0])) / (5 * dx)) : Y(7.1)
        cx.strokeStyle = COLORS.red; cx.setLineDash([2, 3]); cx.lineWidth = 1.5; cx.beginPath(); cx.moveTo(lastPt[0], lastPt[1]); cx.lineTo(px, py); cx.stroke(); cx.setLineDash([])
        if (px < R) { cx.beginPath(); cx.arc(px, py, 4, 0, 7); cx.stroke() }
      }
    }
  }

  /* ── 말풍선 (고장 대상 로봇) ── */
  function calloutInfo() {
    const T = S.amrs[S.sc.target], p = S.sc.phase
    if (p < 2 || p > 4) return null
    if (T.state === 'maint') return { title: '정비 중', line: '예비기 AMR-05가 작업 대체', col: COLORS.red }
    if (T.maintFlag) return { title: '고장 예측 · 약 ' + S.sc.rul + '시간', line: '누적 ' + tons(T) + 't · 작업 후 정비 이동', col: COLORS.red }
    return { title: '진동 이상 ' + T.vib.toFixed(1) + ' mm/s ▲', line: '누적 운반 ' + tons(T) + 't · 플릿 최다', col: COLORS.amber }
  }

  /* ── 3D 씬 ── */
  const viewport = $('viewport')
  const viewBtns = root.querySelectorAll('[data-view]')
  const syncViewBtns = (mode) => viewBtns.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.view === mode)))
  try {
    scene = createAmrScene(viewport, sim, {
      onPick: (hit) => {
        if (hit.type === 'amr') select(hit.index)
        else if (hit.type === 'cargo') { cargoPin = hit.cargo; updPanel() }
      },
      onViewChange: syncViewBtns,
    })
  } catch (err) {
    const fb = document.createElement('div'); fb.className = 'twin-fallback'
    fb.textContent = '이 브라우저에서 3D(WebGL)를 시작하지 못했습니다. 하드웨어 가속이 켜져 있는지 확인해 주세요. (' + (err && err.message) + ')'
    viewport.appendChild(fb)
  }
  viewBtns.forEach((b) => b.addEventListener('click', () => { if (scene) scene.setView(b.dataset.view, selected) }))

  /* ── 음향 ── */
  const SOUND_KEY = 'scargo_twin_sound'
  const sound = createAmrSound()
  const sndBtn = $('sound')
  const syncSnd = () => { sndBtn.setAttribute('aria-pressed', String(sound.enabled)); sndBtn.querySelector('.lbl').textContent = sound.enabled ? '소리 끄기' : '소리 켜기' }
  const savePref = (on) => { try { localStorage.setItem(SOUND_KEY, on ? '1' : '0') } catch { /* 저장 불가 환경 무시 */ } }
  sndBtn.addEventListener('click', () => { sound.setEnabled(!sound.enabled); savePref(sound.enabled); syncSnd() })
  // 지난번에 켜 두었으면, 화면을 처음 누르는 순간 다시 켠다 (브라우저 자동재생 정책)
  let wantSound = false
  try { wantSound = localStorage.getItem(SOUND_KEY) === '1' } catch { wantSound = false }
  const resumeOnGesture = (e) => { if (e.target.closest && e.target.closest('#tw-sound')) return; if (wantSound && !sound.enabled) { sound.setEnabled(true); syncSnd() } root.removeEventListener('pointerdown', resumeOnGesture) }
  if (wantSound) root.addEventListener('pointerdown', resumeOnGesture)
  syncSnd()

  /* ── 한 화면 맞춤: 관리자 상단바 아래 남은 높이를 계산해 화면 전체를 쓴다 ── */
  function fitHeight() {
    const top = root.getBoundingClientRect().top + window.scrollY
    root.style.setProperty('--twin-h', Math.max(600, window.innerHeight - top - 12) + 'px')
  }
  fitHeight()
  window.addEventListener('resize', fitHeight)
  const fitTimer = setTimeout(fitHeight, 300) // 관리자 레이아웃이 자리 잡은 뒤 한 번 더

  /* ── 설명 창 ── */
  const notesBtn = $('notes')
  const setNotes = (open) => { root.classList.toggle('notes-open', open); notesBtn.setAttribute('aria-expanded', String(open)) }
  notesBtn.addEventListener('click', () => setNotes(!root.classList.contains('notes-open')))
  $('notesClose').addEventListener('click', () => setNotes(false))
  const onKey = (e) => { if (e.key === 'Escape') setNotes(false) }
  window.addEventListener('keydown', onKey)
  root.addEventListener('click', (e) => { if (root.classList.contains('notes-open') && !e.target.closest('#tw-notesPanel') && !e.target.closest('#tw-notes')) setNotes(false) })

  /* ── 화질 모드: 일반 / 저사양 (선택은 브라우저에 기억) ── */
  const QUAL_KEY = 'scargo_twin_quality'
  const qualBtns = root.querySelectorAll('.qual [data-q]')
  function setQuality(q, save) {
    if (scene) scene.setQuality(q)
    qualBtns.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.q === q)))
    if (save) { try { localStorage.setItem(QUAL_KEY, q) } catch { /* 저장 불가 환경 무시 */ } }
  }
  let savedQ = null
  try { savedQ = localStorage.getItem(QUAL_KEY) } catch { savedQ = null }
  setQuality(savedQ === 'low' ? 'low' : 'normal', false)
  qualBtns.forEach((b) => b.addEventListener('click', () => { autoChecked = true; setQuality(b.dataset.q, true) }))
  /* ── 2D 모드 · 다크/라이트 (선택은 브라우저에 기억) ── */
  const VIEW2D_KEY = 'scargo_twin_2d', THEME_KEY = 'scargo_twin_theme'
  const store = { get: (k) => { try { return localStorage.getItem(k) } catch { return null } }, set: (k, v) => { try { localStorage.setItem(k, v) } catch { /* 무시 */ } } }
  const btn2d = $('2d'), btnTheme = $('theme')
  function set2D(on, save) {
    if (scene) scene.set2D(on)
    if (btn2d) { btn2d.setAttribute('aria-pressed', String(!!on)); btn2d.textContent = on ? '3D' : '2D' }
    if (save) store.set(VIEW2D_KEY, on ? '1' : '0')
  }
  function setTheme(t, save) {
    const light = t === 'light'
    root.classList.toggle('light', light)
    if (scene) scene.setTheme(light ? 'light' : 'dark')
    if (btnTheme) btnTheme.textContent = light ? '다크 모드' : '라이트 모드'
    if (save) store.set(THEME_KEY, light ? 'light' : 'dark')
    readCH(); drawChart()
  }
  if (btn2d) btn2d.addEventListener('click', () => set2D(!(scene && scene.is2D), true))
  if (btnTheme) btnTheme.addEventListener('click', () => setTheme(root.classList.contains('light') ? 'dark' : 'light', true))
  set2D(store.get(VIEW2D_KEY) === '1', false)
  setTheme(store.get(THEME_KEY) === 'light' ? 'light' : 'dark', false)

  function notice(text, col) {
    const b = $('banner')
    b.style.setProperty('--c', col)
    b.querySelector('span').textContent = text
    b.classList.add('show')
    clearTimeout(bannerTimer)
    bannerTimer = setTimeout(() => b.classList.remove('show'), 4500)
  }
  // 처음 실행했을 때 화면이 느리면(초당 22장 미만) 한 번만 자동으로 저사양 전환
  let autoChecked = savedQ != null, fpsT = 0, fpsN = 0, warmT = 0

  /* ── 재생 컨트롤 ── */
  const playBtn = $('play')
  const syncPlay = () => { playBtn.textContent = playing ? '일시정지' : '재생' }
  playBtn.addEventListener('click', () => { playing = !playing; syncPlay() })
  root.querySelectorAll('.spd').forEach((b) => b.addEventListener('click', () => { speed = +b.dataset.s; root.querySelectorAll('.spd').forEach((x) => x.setAttribute('aria-pressed', String(x === b))) }))
  $('restart').addEventListener('click', () => { sim.init(); warm(); maintId = null; selected = 2; cargoPin = null; lastScanVer = -1; buildFleet(); updPanel(); playing = true; syncPlay() })

  /* ── 루프 ── */
  let last = performance.now(), panelAcc = 0
  function frame(now) {
    if (destroyed) return
    const dt = Math.min(0.05, (now - last) / 1000); last = now
    let simDt = 0
    if (playing) { let t = dt * speed; simDt = t; while (t > 0) { const s = Math.min(0.025, t); sim.step(s); t -= s } }
    if (scene) {
      scene.update(now, simDt, dt, { selected, cargo: currentCargo(), callout: calloutInfo(), phase: S.sc.phase })
      scene.render()
      sound.update({ S, camera: scene.camera, toWorld: scene.toWorld, simDt, playing })
    }
    panelAcc += dt
    if (panelAcc > 0.15) { approveFlash = Math.max(0, approveFlash - panelAcc); panelAcc = 0; updPanel(); drawChart(); renderApprove() }
    if (!autoChecked && scene && !document.hidden) {
      const real = (now - (frame.prev || now)) / 1000
      warmT += real
      if (warmT > 3) {
        fpsT += real; fpsN++
        if (fpsT > 4) {
          const fps = fpsN / fpsT
          autoChecked = true
          if (fps < 22) { setQuality('low', true); notice('화면이 느려 저사양 모드로 바꿨습니다 (초당 ' + Math.max(1, Math.round(fps)) + '장)', COLORS.amber) }
        }
      }
    }
    frame.prev = now
    raf = requestAnimationFrame(frame)
  }

  /* ── 관리자 운영 기준 (정비 · 충전) + 자동 정비 상태 ── */
  const cfgCard = document.createElement('div')
  cfgCard.className = 'card cfg-card'
  cfgCard.innerHTML = '<div class="grouphead"><span class="gtitle">운영 기준 · 관리자</span><span class="gmeta cfg-by">-</span></div>'
    + '<div class="cfg-row"><span>정비 기준 <em>남은 수명</em></span><div class="cfg-step"><button type="button" data-k="rul" data-d="-6">−</button><b class="cfg-rul">24</b><i>시간 이하</i><button type="button" data-k="rul" data-d="6">+</button></div></div>'
    + '<div class="cfg-row"><span>충전 기준 <em>배터리</em></span><div class="cfg-step"><button type="button" data-k="chg" data-d="-10">−</button><b class="cfg-chg">30</b><i>% 이하</i><button type="button" data-k="chg" data-d="10">+</button></div></div>'
    + '<p class="cfg-note">기준에 걸리면 정비 예약 · 예비기 투입 · 충전 이동은 자동입니다. 앱에서 바꿔도 여기 반영됩니다.</p>'
  const apCard = document.createElement('div')
  apCard.className = 'card approve-card done'; apCard.hidden = true
  apCard.innerHTML = '<div class="ap-t"><i>!</i><b class="ap-h"></b></div><p class="ap-p"></p>'
  const sideL = root.querySelector('.side-l') || root
  sideL.prepend(apCard); sideL.prepend(cfgCard)
  function syncCfgInputs() {
    cfgCard.querySelector('.cfg-rul').textContent = S.cfg.rulAt
    cfgCard.querySelector('.cfg-chg').textContent = S.cfg.chargeAt
    const by = remote && remote.settings && remote.settings.updatedBy
    cfgCard.querySelector('.cfg-by').textContent = by ? '최근 변경: ' + by : (amrApi.online ? 'DB 저장' : '이 화면만')
  }
  let cfgTimer = 0
  cfgCard.addEventListener('click', (e) => {
    const b = e.target.closest('button[data-k]'); if (!b) return
    const d = +b.dataset.d
    if (b.dataset.k === 'rul') sim.setCfg({ rulAt: S.cfg.rulAt + d }); else sim.setCfg({ chargeAt: S.cfg.chargeAt + d })
    syncCfgInputs(); cfgLocalT = Date.now()
    clearTimeout(cfgTimer)
    cfgTimer = setTimeout(() => amrApi.saveSettings(S.cfg.rulAt, S.cfg.chargeAt, '웹 · 관제').then((v) => { if (v) lastCfgBy = (v.updatedAt || '') + v.updatedBy }), 400)
  })
  function renderApprove() {
    const sc = S.sc, T = S.amrs[sc.target]
    if (sc.phase === 2 && sc.rul != null) {
      apCard.hidden = false; apCard.classList.remove('done')
      apCard.querySelector('.ap-h').textContent = T.name + ' 진동 이상 · 남은 수명 약 ' + sc.rul + '시간'
      apCard.querySelector('.ap-p').textContent = '정비 기준 ' + S.cfg.rulAt + '시간 이하가 되면 자동으로 정비를 예약하고 예비기 AMR-05를 투입합니다.'
    } else if (sc.phase === 3 || sc.phase === 4) {
      apCard.hidden = false; apCard.classList.add('done')
      apCard.querySelector('.ap-h').textContent = sc.phase === 3 ? '자동 정비 예약 · ' + T.name : T.name + ' 정비 중'
      apCard.querySelector('.ap-p').textContent = '기준 ' + S.cfg.rulAt + '시간 이하 → ' + (sc.approvedBy || '자동') + ' · 예비기 AMR-05 투입' + (amrApi.online ? ' · DB 기록' : '')
    } else apCard.hidden = true
  }
  syncCfgInputs()

  // DB: 실제 시간 2초마다 센서 저장 + 상태 읽기 (화면이 느려도 간격 유지)
  const dbTimer = setInterval(() => { if (playing) amrApi.telemetry(S.sc.phase, S.amrs); pollState() }, 2000)

  buildFleet(); sizeChart(); updPanel(); drawChart(); syncPlay()
  applyPhase(S.sc.phase)
  raf = requestAnimationFrame(frame)

  return {
    sim,
    get scene() { return scene },
    destroy() {
      destroyed = true
      offDb()
      clearInterval(dbTimer)
      cancelAnimationFrame(raf)
      clearTimeout(bannerTimer)
      chartRO.disconnect()
      root.removeEventListener('pointerdown', resumeOnGesture)
      window.removeEventListener('resize', fitHeight)
      window.removeEventListener('keydown', onKey)
      clearTimeout(fitTimer)
      sound.dispose()
      if (scene) scene.dispose()
    },
  }
}
