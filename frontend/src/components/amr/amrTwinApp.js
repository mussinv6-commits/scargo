// ─────────────────────────────────────────────────────────────
// S카고 물류센터 AMR 디지털 트윈 — 화면 조립
// 시뮬레이션 + 3D 씬 + 관제 패널(차트·화물·로그)을 하나의 루트 요소에 붙인다.
// 사용: const app = mountAmrTwin(rootEl)  →  화면을 떠날 때 app.destroy()
// ─────────────────────────────────────────────────────────────
import { createSim, HUBS, ZONES, STATE_KO, PHASE_T, CAPTIONS, PIPE_ON, PH_COL, COLORS, status, lvCol, tons, zoneRow } from './amrSim.js'
// ZONES: 0 D 냉장 창고 · 1 A 호남 · 2 B 강원 · 3 C 영남
import { createAmrScene } from './amrScene3d.js'
import { createAmrSound } from './amrSound.js'

export function mountAmrTwin(root) {
  const $ = (id) => root.querySelector('#tw-' + id)
  let selected = 2, cargoPin = null, speed = 1, playing = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  let bannerTimer = 0, raf = 0, scene = null, destroyed = false
  let lastScanVer = -1

  /* ── 단계 · 배너 · 로그 ── */
  function applyPhase(p) {
    root.querySelectorAll('.stage').forEach((el) => { const q = +el.dataset.p; el.classList.toggle('now', q === p); el.classList.toggle('done', q < p) })
    $('caption').textContent = CAPTIONS[p]
    root.querySelectorAll('.pipe .node').forEach((n) => n.classList.toggle('on', PIPE_ON[p].includes(+n.dataset.k)))
    const b = $('banner')
    b.style.setProperty('--c', PH_COL[p])
    b.querySelector('span').textContent = 'STEP 0' + p + '  ' + PHASE_T[p]
    b.classList.add('show')
    clearTimeout(bannerTimer)
    bannerTimer = setTimeout(() => b.classList.remove('show'), 3000)
    if (p === 2) { selected = 2; cargoPin = null }
  }
  function fmt(t) { const s = Math.floor(t); return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0') }
  function renderLog(items) {
    $('log').innerHTML = items.map((l) => `<li class="${l.lv}"><time>${fmt(l.t)}</time><span>${l.text}</span></li>`).join('')
  }

  const sim = createSim({ onLog: renderLog, onPhase: applyPhase })
  const S = sim.S
  const warm = () => { for (let i = 0; i < 80; i++) sim.step(0.05); S.packets.length = 0 }
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
    $('cDock').textContent = 'D' + (c.hub + 4) + ' · ' + h.name + ' 허브행 택배차'
    $('cLoc').textContent = '현재 위치 · ' + sim.locText(c)
    $('cHint').textContent = cargoPin ? '고른 화물을 따라가는 중입니다. 로봇을 클릭하면 해제됩니다.' : '선택한 로봇이 옮기는 화물을 따라갑니다. 랙의 박스(위 단 포함)를 클릭하면 그 화물로 고정됩니다.'
  }
  function renderScans() {
    if (S.scanVer === lastScanVer) return
    lastScanVer = S.scanVer
    $('scans').innerHTML = S.scans.length
      ? S.scans.map((s) => { const c = s.c, h = HUBS[c.hub]; return `<li><span class="sid">${c.id}</span><span class="sd">${c.cat} · ${c.w}kg${c.handle !== '일반' ? ' · ' + c.handle : ''}</span><span class="hubchip" style="background:${h.col}">${h.name}</span></li>` }).join('')
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

  /* ── 말풍선 (AMR-03) ── */
  function calloutInfo() {
    const T = S.amrs[2], p = S.sc.phase
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
  $('restart').addEventListener('click', () => { sim.init(); warm(); selected = 2; cargoPin = null; lastScanVer = -1; buildFleet(); updPanel(); playing = true; syncPlay() })

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
    if (panelAcc > 0.15) { panelAcc = 0; updPanel(); drawChart() }
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

  buildFleet(); sizeChart(); updPanel(); drawChart(); syncPlay()
  applyPhase(S.sc.phase)
  raf = requestAnimationFrame(frame)

  return {
    sim,
    get scene() { return scene },
    destroy() {
      destroyed = true
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
