// ─────────────────────────────────────────────────────────────
// S카고 AMR 관제 앱 (관리자용 · 로그인 없음)
// 웹 관제 화면과 같은 시뮬레이션 · 3D 모듈을 휴대폰 화면 5탭으로 보여준다.
//   홈 · 로봇(예지보전) · 3D · 화물 · 알림
// 서버가 필요 없어서 와이파이 없이도 앱만으로 돌아간다.
// ─────────────────────────────────────────────────────────────
import { createSim, HUBS, ZONES, STATE_KO, PHASE_T, CAPTIONS, COLORS, status, lvCol, tons } from './twin/amrSim.js'
import { createAmrScene } from './twin/amrScene3d.js'

const $ = (id) => document.getElementById(id)
const PH_COL = { 1: COLORS.cyan, 2: COLORS.amber, 3: COLORS.amber, 4: COLORS.red, 5: COLORS.green }
const PH_BG = { 1: 'rgba(63,224,204,.08)', 2: 'rgba(255,184,77,.1)', 3: 'rgba(255,184,77,.1)', 4: 'rgba(255,90,95,.1)', 5: 'rgba(79,220,138,.1)' }
const cssLv = (l) => (l === 'crit' ? 'var(--red)' : l === 'warn' ? 'var(--amber)' : l === 'spare' ? 'var(--dim)' : 'var(--green)')
const pad = (n) => String(n).padStart(2, '0')
const clock = (d) => pad(d.getHours()) + ':' + pad(d.getMinutes())
const store = { get(k) { try { return localStorage.getItem(k) } catch { return null } }, set(k, v) { try { localStorage.setItem(k, v) } catch { /* 무시 */ } } }

/* ── 상태 ── */
let tab = 'home', selected = 2, speed = 1, playing = true, cargoFilter = 'all', alertFilter = 'all'
const events = [] // { time, text, lv, cat, read }
const phaseAt = {} // 단계별 시작 시각(실제 시계)
let lastLogKey = ''

/* ── 이벤트 분류 ── */
function classify(text) {
  if (/FL-01/.test(text)) return 'fl'
  if (/진동|예측|정비|예비기|AMR-05|복귀|모니터링/.test(text)) return 'pm'
  if (/택배차|허브|출발|OCR/.test(text)) return 'cargo'
  return 'sys'
}
const EV_STYLE = {
  crit: ['!', 'rgba(255,90,95,.15)', 'var(--red)'], warn: ['~', 'rgba(255,184,77,.14)', 'var(--amber)'],
  ok: ['✓', 'rgba(79,220,138,.13)', 'var(--green)'], hub: ['➜', 'rgba(90,168,255,.14)', 'var(--h1)'],
  fl: ['▲', 'rgba(242,183,5,.14)', 'var(--yel)'], '': ['i', 'rgba(138,155,176,.12)', 'var(--muted)'],
}
function onLog(items) {
  const top = items[0]; if (!top) return
  const key = top.t.toFixed(3) + top.text
  if (key === lastLogKey) return
  // 새로 쌓인 항목만 추가 (같은 시각에 여러 개가 들어올 수 있음)
  const fresh = []
  for (const it of items) { const k = it.t.toFixed(3) + it.text; if (k === lastLogKey) break; fresh.push(it) }
  lastLogKey = key
  fresh.reverse().forEach((it) => {
    const cat = classify(it.text)
    const lv = cat === 'fl' && !it.lv ? 'fl' : it.lv
    events.unshift({ time: new Date(), text: it.text, lv, cat, read: tab === 'alert' })
    if (it.lv === 'crit' || it.lv === 'warn') toast(it.text, it.lv === 'crit' ? '' : 'amber')
  })
  if (events.length > 80) events.length = 80
  renderAlerts(); renderBadge()
}
function onPhase(p) {
  phaseAt[p] = new Date()
  if (p === 1) { delete phaseAt[2]; delete phaseAt[3]; delete phaseAt[4]; delete phaseAt[5] }
}

/* ── 시뮬레이션 · 3D ── */
const sim = createSim({ onLog, onPhase })
const S = sim.S
for (let i = 0; i < 80; i++) sim.step(0.05)
S.packets.length = 0
onLog(S.logItems); phaseAt[S.sc.phase] = new Date()

const host = $('host3d')
let scene = null
try {
  scene = createAmrScene(host, sim, {
    onPick: (hit) => {
      if (hit.type === 'amr') { selected = hit.index; if (scene.viewMode === 'follow') scene.setView('follow', selected); renderSheet(); renderPick() }
    },
    onViewChange: (m) => { document.querySelectorAll('#v-chips button').forEach((b) => b.classList.toggle('on', b.dataset.v === m || (m === 'free' && b.dataset.v === coldView))) },
  })
  scene.setQuality(store.get('amr_app_quality') === 'normal' ? 'normal' : 'low') // 휴대폰은 저사양이 기본
  scene.setView('auto')
} catch (err) {
  host.innerHTML = '<p style="padding:20px;color:#8a9bb0;font-size:13px">이 기기에서 3D(WebGL)를 시작하지 못했습니다. (' + (err && err.message) + ')</p>'
}
let coldView = ''

// 3D 화면을 홈 미니 칸 ↔ 3D 탭으로 옮긴다 (렌더러는 하나만 쓴다)
function placeHost() {
  const slot = tab === '3d' ? $('full-slot') : tab === 'home' ? $('mini-slot') : null
  if (slot && host.parentElement !== slot) slot.appendChild(host)
  if (!slot && host.parentElement !== $('app')) $('app').appendChild(host)
  if (scene) scene.resize()
}

/* ── 탭 ── */
function setTab(t) {
  tab = t
  document.querySelectorAll('.page').forEach((p) => p.classList.toggle('on', p.dataset.page === t))
  document.querySelectorAll('.tab').forEach((b) => b.classList.toggle('on', b.dataset.tab === t))
  placeHost()
  if (t === 'alert') { events.forEach((e) => (e.read = true)); renderBadge() }
  renderAll()
}
document.querySelectorAll('.tab').forEach((b) => b.addEventListener('click', () => setTab(b.dataset.tab)))
document.addEventListener('click', (e) => {
  const g = e.target.closest('[data-go]'); if (g) setTab(g.dataset.go)
  const r = e.target.closest('[data-go-robot]'); if (r) { selected = +r.dataset.goRobot; setTab('robot') }
})

/* ── 홈 ── */
function robotStateText(a) {
  if (a.kind === 'fl') {
    if (a.state === 'fwork' && a.task) return (a.task.type === 'repl' ? '보충 · ' : '적치 · ') + ZONES[a.task.slot.r].tag + '-' + pad(a.task.slot.i + 1) + ' ' + (a.task.lv + 2) + '단'
    return a.state === 'toRack' ? '랙 이동' : a.state === 'toHome' ? '대기 위치 복귀' : '대기'
  }
  if (a.role === 'spare' && a.state === 'idle') return '예비 대기'
  let s = STATE_KO[a.state]
  if (a.box && a.task) s += a.task.type === 'out' ? ' · → D' + (a.box.hub + 4) + ' ' + HUBS[a.box.hub].name : ' · → 랙 ' + ZONES[a.task.slot.r].tag
  return s
}
function renderHome() {
  const p = S.sc.phase, col = PH_COL[p]
  const st = $('h-step'); st.style.setProperty('--c', col); st.style.setProperty('--cb', PH_BG[p])
  st.querySelector('b').textContent = 'STEP 0' + p; st.querySelector('span').textContent = PHASE_T[p]
  st.querySelectorAll('.dots i').forEach((d, i) => { d.className = i + 1 < p ? 'd' : i + 1 === p ? 'n' : '' })
  $('h-cap').textContent = CAPTIONS[p]
  const amrs = S.amrs.filter((a) => a.kind !== 'fl')
  $('h-act').textContent = amrs.filter((a) => a.role === 'active' && !a.maintFlag && a.state !== 'maint').length + '/' + amrs.length
  $('h-thru').textContent = S.thru
  const T = S.amrs[2], maintN = S.amrs.filter((a) => a.maintFlag || a.state === 'maint').length
  const hm = $('h-maint'); hm.textContent = maintN; hm.className = maintN ? 'crit' : ''
  // 위험 알림 카드
  const al = $('h-alert'), [lv] = status(T)
  if (lv === 'crit' || lv === 'warn') {
    al.hidden = false; al.classList.toggle('amber', lv === 'warn')
    if (T.state === 'maint') { $('h-alert-t').textContent = 'AMR-03 정비 중 · 베어링 교체'; $('h-alert-p').textContent = '예비기 AMR-05가 작업을 대신하고 있어 허브 출고는 멈추지 않습니다.' }
    else if (T.maintFlag) { $('h-alert-t').textContent = 'AMR-03 고장 예측 · 약 ' + S.sc.rul + '시간'; $('h-alert-p').textContent = '구동모터 진동 ' + T.vib.toFixed(1) + ' mm/s, 상승 추세 · 누적 운반 ' + tons(T) + 't. 작업 마친 뒤 정비소 이동 · 예비기 AMR-05 투입' }
    else { $('h-alert-t').textContent = 'AMR-03 진동 이상 ' + T.vib.toFixed(1) + ' mm/s'; $('h-alert-p').textContent = '평소 범위(1.2~2.0)를 벗어났습니다. AI가 추세를 보고 고장 시점을 계산하는 중입니다.' }
  } else al.hidden = true
  $('h-fleet').innerHTML = S.amrs.map((a, i) => {
    const [l, t] = status(a), c = a.kind === 'fl' ? 'var(--yel)' : cssLv(l)
    return `<button type="button" class="fr ${l === 'crit' ? 'crit' : ''}" data-go-robot="${i}"><span class="d" style="background:${c};box-shadow:0 0 8px ${c}"></span><span class="n">${a.name}</span><span class="s" style="${l === 'crit' ? 'color:#ffb3b5' : ''}">${l === 'crit' ? t : robotStateText(a)}</span><span class="bar"><i style="width:${Math.round(a.health)}%;background:${c}"></i></span><span class="h" style="color:${l === 'crit' ? 'var(--red)' : 'var(--ink)'}">${Math.round(a.health)}</span></button>`
  }).join('')
}

/* ── 3D 탭: 시점 · 아래 카드 ── */
document.querySelectorAll('#v-chips button').forEach((b) => b.addEventListener('click', () => {
  if (!scene) return
  const v = b.dataset.v
  coldView = ''
  if (v === 'cold') {
    coldView = 'cold'; scene.setView('free')
    scene.camera.position.set(-1, 9.5, -3.5); scene.controls.target.set(-3, 1.2, -12.5)
  } else scene.setView(v, selected)
  document.querySelectorAll('#v-chips button').forEach((x) => x.classList.toggle('on', x === b))
}))
$('v-quality').addEventListener('click', () => {
  if (!scene) return
  const q = scene.quality === 'low' ? 'normal' : 'low'
  scene.setQuality(q); store.set('amr_app_quality', q); syncSide()
  toast(q === 'low' ? '저사양 모드: 그림자 · 빛 효과를 끄고 가볍게' : '일반 화질: 그림자 · 빛 효과 켬 (배터리 사용 증가)', 'ok')
})
$('v-speed').addEventListener('click', () => { speed = speed === 1 ? 2 : speed === 2 ? 4 : 1; syncSide() })
$('v-pause').addEventListener('click', () => { playing = !playing; syncSide() })
function syncSide() {
  $('v-quality').textContent = scene && scene.quality === 'normal' ? '일반' : '저사양'
  $('v-speed').textContent = speed + 'x'
  $('v-pause').textContent = playing ? 'Ⅱ' : '▶'
}
function renderPick() {
  $('s-pick').innerHTML = S.amrs.map((a, i) => { const [l] = status(a); return `<button type="button" data-i="${i}" class="${i === selected ? 'on' : ''}"><span class="d" style="background:${a.kind === 'fl' ? 'var(--yel)' : cssLv(l)}"></span>${a.name}</button>` }).join('')
}
$('s-pick').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return
  selected = +b.dataset.i
  if (scene) { coldView = ''; scene.setView('follow', selected); document.querySelectorAll('#v-chips button').forEach((x) => x.classList.toggle('on', x.dataset.v === 'follow')) }
  renderPick(); renderSheet()
})
$('v-sheet').addEventListener('click', (e) => { if (!e.target.closest('#s-pick')) setTab('robot') })
function renderSheet() {
  const a = S.amrs[selected], [l, t] = status(a)
  $('v-sheet').className = 'sheet ' + (l === 'crit' ? 'crit' : l === 'warn' ? 'warn' : '')
  $('s-name').textContent = a.name
  const p = $('s-pill'); p.className = 'pill ' + l; p.textContent = t
  $('s-state').textContent = robotStateText(a) + (a.box ? ' · ' + a.box.id + (a.box.handle !== '일반' ? ' ' + a.box.handle : '') : '')
  const pred = selected === 2 && a.maintFlag && S.sc.rul != null && a.state !== 'maint'
  $('s-rul').textContent = pred ? S.sc.rul + 'h' : Math.round(a.health)
  $('s-rul').style.color = pred ? 'var(--red)' : cssLv(l)
  $('s-rul-k').textContent = pred ? '남은 수명' : '건강점수'
  setMet($('s-vib'), a.vib.toFixed(1), a.vib >= 4.5 ? 'crit' : a.vib >= 2.6 ? 'warn' : '')
  setMet($('s-temp'), Math.round(a.temp), a.temp >= 70 ? 'crit' : a.temp >= 58 ? 'warn' : '')
  setMet($('s-batt'), Math.round(a.batt), a.batt < 40 ? 'warn' : '')
}
function setMet(el, v, cls) { el.textContent = v; el.className = cls }

/* ── 로봇 탭 ── */
$('r-tabs').addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; selected = +b.dataset.i; renderRobot() })
$('r-see3d').addEventListener('click', () => { setTab('3d'); if (scene) { coldView = ''; scene.setView('follow', selected) } })
function renderRobot() {
  const a = S.amrs[selected], [l, t] = status(a)
  $('r-tabs').innerHTML = S.amrs.map((b, i) => { const [bl] = status(b); return `<button type="button" data-i="${i}" class="${i === selected ? 'on' : ''}"><span class="d" style="background:${b.kind === 'fl' ? 'var(--yel)' : cssLv(bl)}"></span>${b.name}</button>` }).join('')
  $('r-name').textContent = a.name + (a.kind === 'fl' ? ' · 무인 지게차' : '')
  const p = $('r-pill'); p.className = 'pill ' + l; p.textContent = t
  const h = Math.round(a.health), col = a.kind === 'fl' && l === 'ok' ? '#f2b705' : lvCol(l === 'spare' ? 'ok' : l)
  $('r-health').textContent = a.state === 'maint' ? '-' : h
  const ring = $('r-ring'); ring.setAttribute('stroke-dasharray', (2.513 * (a.state === 'maint' ? 0 : h)).toFixed(0) + ' 252'); ring.setAttribute('stroke', col)
  const pred = selected === 2 && a.maintFlag && S.sc.rul != null && a.state !== 'maint'
  if (pred) { $('r-rul-l').textContent = 'AI 예측 · 남은 수명'; $('r-rul').textContent = S.sc.rul; $('r-rul').style.color = 'var(--red)'; $('r-rul-u').textContent = '시간 후 고장 예상' }
  else if (a.state === 'maint') { $('r-rul-l').textContent = '정비 진행'; $('r-rul').textContent = Math.round((1 - a.timer / 7) * 100) + '%'; $('r-rul').style.color = 'var(--red)'; $('r-rul-u').textContent = '베어링 교체 중' }
  else { $('r-rul-l').textContent = 'AI 판단'; $('r-rul').textContent = l === 'warn' ? '주의' : '정상'; $('r-rul').style.color = cssLv(l); $('r-rul-u').textContent = l === 'warn' ? '추세 분석 중' : '고장 징후 없음' }
  $('r-sub').textContent = selected === 2 && (l === 'crit' || l === 'warn') ? '원인 추정: 구동모터 베어링 마모 · 누적 운반 ' + tons(a) + 't (플릿 최다)' : a.kind === 'fl' ? '위 단 보충 · 적치 전담 · 누적 ' + tons(a) + 't' : '누적 운반 ' + tons(a) + 't · 진동 기준 4.5 / 7.1 mm/s'
  setMet($('r-vib'), a.state === 'maint' ? '-' : a.vib.toFixed(1), a.vib >= 4.5 ? 'crit' : a.vib >= 2.6 ? 'warn' : '')
  setMet($('r-temp'), Math.round(a.temp), a.temp >= 70 ? 'crit' : a.temp >= 58 ? 'warn' : '')
  setMet($('r-batt'), Math.round(a.batt), a.batt < 40 ? 'warn' : '')
  setMet($('r-cum'), tons(a), a.cumKg >= 12000 ? 'warn' : '')
  setMet($('r-load'), a.box ? a.box.w : 0, '')
  setMet($('r-state'), a.kind === 'fl' ? (STATE_KO[a.state] || a.state) : a.role === 'spare' && a.state === 'idle' ? '예비' : STATE_KO[a.state], '')
  renderTimeline(a)
  drawChart()
}
function renderTimeline(a) {
  const card = $('r-maint-card')
  if (selected !== 2) {
    card.hidden = false
    const lastMaint = a.kind === 'fl' ? '오늘 08:30 · 마스트 유압 점검' : '오늘 08:30 · 정기 점검'
    $('r-tl').innerHTML = `<div class="tli" style="color:var(--green)"><i></i><span>${lastMaint}</span><em>완료</em></div><div class="tli todo"><i></i><span>다음 정기 점검</span><em>7일 후</em></div>`
    return
  }
  const p = S.sc.phase, at = (k) => (phaseAt[k] ? clock(phaseAt[k]) : '')
  const steps = [
    [2, '진동 이상 감지', 'var(--amber)'], [3, 'AI 고장 예측 · 정비 예약', 'var(--red)'], [3, '예비기 AMR-05 투입', 'var(--cyan)'],
    [4, '정비 스테이션 도착 · 베어링 교체', 'var(--red)'], [5, '정비 완료 · 정상 복귀', 'var(--green)'],
  ]
  $('r-tl').innerHTML = steps.map(([k, txt, c], i) => {
    const done = p >= k && !(p === 1)
    const doing = !done && ((k === 4 && p === 3) || (k === 5 && p === 4))
    return `<div class="tli ${done ? '' : 'todo'}" style="${done ? 'color:' + c : ''}"><i></i><span>${txt}</span><em>${done ? at(k) : doing ? '진행 중' : '예정'}</em></div>`
  }).join('')
}
// 진동 차트 (최근 30초 + 예측선)
const ch = $('r-chart'), cx = ch.getContext('2d')
function drawChart() {
  if (tab !== 'robot') return
  const dpr = Math.min(2, window.devicePixelRatio || 1), w = ch.clientWidth, h = 150
  if (!w) return
  if (ch.width !== Math.round(w * dpr)) { ch.width = Math.round(w * dpr); ch.height = Math.round(h * dpr) }
  const a = S.amrs[selected]
  cx.setTransform(dpr, 0, 0, dpr, 0, 0); cx.clearRect(0, 0, w, h)
  const L = 22, R = w - 4, Tp = 8, B = h - 18, pw = (R - L) * 0.76, ymax = 8, Y = (v) => B - (v / ymax) * (B - Tp)
  cx.font = '10px monospace'; cx.textBaseline = 'middle'
  ;[0, 2, 4, 6, 8].forEach((v) => { cx.strokeStyle = '#1f2c3a'; cx.lineWidth = 1; cx.beginPath(); cx.moveTo(L, Y(v) + 0.5); cx.lineTo(R, Y(v) + 0.5); cx.stroke(); cx.fillStyle = '#58697e'; cx.textAlign = 'right'; cx.fillText(v, L - 5, Y(v)) })
  cx.fillStyle = 'rgba(255,90,95,.07)'; cx.fillRect(L, Y(ymax), R - L, Y(7.1) - Y(ymax))
  ;[[4.5, COLORS.amber, '주의 4.5'], [7.1, COLORS.red, '고장 7.1']].forEach(([v, c, t]) => { cx.strokeStyle = c; cx.globalAlpha = 0.75; cx.setLineDash([3, 3]); cx.beginPath(); cx.moveTo(L, Y(v)); cx.lineTo(R, Y(v)); cx.stroke(); cx.setLineDash([]); cx.globalAlpha = 1; cx.fillStyle = c; cx.textAlign = 'left'; cx.fillText(t, L + 4, Y(v) - 7) })
  cx.strokeStyle = '#2b3b4d'; cx.beginPath(); cx.moveTo(L + pw + 0.5, Tp); cx.lineTo(L + pw + 0.5, B); cx.stroke()
  cx.fillStyle = '#58697e'; cx.textAlign = 'right'; cx.fillText('예측 ▸', R, h - 6)
  const hs = a.hist, N = 150, dx = pw / (N - 1), off = N - hs.length, [lv] = status(a), col = a.kind === 'fl' ? '#f2b705' : lvCol(lv === 'spare' ? 'ok' : lv)
  const segs = []; let cur = []
  hs.forEach((v, i) => { if (v == null) { if (cur.length) segs.push(cur); cur = []; return } cur.push([L + (off + i) * dx, Y(Math.min(ymax, v)), v]) })
  if (cur.length) segs.push(cur)
  segs.forEach((sg) => {
    const gr = cx.createLinearGradient(0, Tp, 0, B); gr.addColorStop(0, col + '55'); gr.addColorStop(1, col + '00')
    cx.fillStyle = gr; cx.beginPath(); cx.moveTo(sg[0][0], B); sg.forEach((p) => cx.lineTo(p[0], p[1])); cx.lineTo(sg[sg.length - 1][0], B); cx.closePath(); cx.fill()
    cx.strokeStyle = col; cx.lineWidth = 2; cx.beginPath(); sg.forEach((p, k) => (k ? cx.lineTo(p[0], p[1]) : cx.moveTo(p[0], p[1]))); cx.stroke()
  })
  const ls = segs[segs.length - 1], lp = ls && ls[ls.length - 1]
  if (lp && lp[0] > L + pw - dx * 2) {
    cx.fillStyle = col; cx.beginPath(); cx.arc(lp[0], lp[1], 3.4, 0, 7); cx.fill()
    if (selected === 2 && a.maintFlag && S.sc.rul != null && a.state !== 'maint') {
      const sl = Math.max(0.08, S.sc.slope), secs = (7.1 - lp[2]) / sl, px = Math.min(R, lp[0] + secs * 5 * dx)
      const py = px >= R ? Y(lp[2] + (sl * (R - lp[0])) / (5 * dx)) : Y(7.1)
      cx.strokeStyle = COLORS.red; cx.setLineDash([3, 3]); cx.lineWidth = 1.6; cx.beginPath(); cx.moveTo(lp[0], lp[1]); cx.lineTo(px, py); cx.stroke(); cx.setLineDash([])
      if (px < R) { cx.beginPath(); cx.arc(px, py, 4, 0, 7); cx.stroke() }
    }
  }
}

/* ── 화물 탭 ── */
$('c-filter').addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; cargoFilter = b.dataset.f; $('c-filter').querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b)); renderCargo() })
let inCount = 0, lastScanVer = -1
function renderCargo() {
  if (S.scanVer !== lastScanVer) { if (lastScanVer >= 0) inCount += S.scanVer - lastScanVer; else inCount = S.scanVer; lastScanVer = S.scanVer }
  let store = 0
  S.slots.forEach((r) => r.forEach((sd) => sd.forEach((v) => { if (v.box) store++ })))
  S.upper.forEach((r) => r.forEach((sd) => sd.forEach((col) => col.forEach((u) => { if (u && u.box) store++ }))))
  const moving = S.amrs.filter((a) => a.box)
  const shipped = S.hubSent.reduce((s, n) => s + n, 0)
  $('c-in').textContent = inCount; $('c-store').textContent = store; $('c-move').textContent = moving.length; $('c-out').textContent = shipped
  $('c-hubs').innerHTML = S.outDocks.map((d, i) => {
    const h = HUBS[i], n = d.boxes.length, st = d.state === 'docked' ? n + '/' + d.cap : d.state === 'leaving' ? '출발' : d.state === 'arriving' ? '도착 중' : '대기'
    return `<div class="hub"><b style="color:${h.col}">D${i + 4} ${h.name}</b><span class="bar"><i style="width:${d.state === 'docked' ? (n / d.cap) * 100 : d.state === 'leaving' ? 100 : 0}%;background:${h.col}"></i></span><em style="${d.state === 'leaving' ? 'color:var(--green)' : ''}">${st}</em></div>`
  }).join('') + `<div class="hint" style="margin-top:4px">허브별 누적 출고: ${HUBS.map((h, i) => h.name + ' ' + S.hubSent[i]).join(' · ')}건</div>`
  $('c-stock').innerHTML = '<div class="stk-h"><span></span><span>바닥 칸 (AMR)</span><span>위 단 (지게차)</span></div>' + ZONES.map((z, r) => {
    let fl = 0, flT = 0, up = 0, upT = 0
    S.slots[r].forEach((sd) => sd.forEach((v) => { if (v.st >= 0) { flT++; if (v.box) fl++ } }))
    S.upper[r].forEach((sd) => sd.forEach((col) => col.forEach((u) => { if (u) { upT++; if (u.box) up++ } })))
    const bb = (n, t) => `<span class="bb"><span class="bar"><i style="width:${(n / t) * 100}%;background:${z.col}"></i></span><em>${n}/${t}</em></span>`
    return `<div class="stk"><span class="z" style="color:${z.col}">${z.tag} ${z.name}</span>${bb(fl, flT)}${bb(up, upT)}</div>`
  }).join('')
  const list = moving.filter((a) => cargoFilter === 'all' || a.box.handle === '냉장')
  $('c-moving').innerHTML = list.length ? list.map((a) => {
    const c = a.box, h = HUBS[c.hub], cold = c.handle === '냉장'
    return `<div class="mv"><div><div class="id">${c.id}</div><div class="w">${c.cat} ${c.w}kg${c.handle !== '일반' ? ' · ' + c.handle : ''}</div></div><div class="to">${a.name}<br>${sim.locText(c).replace(/^.*?·\s*/, '').replace(a.name + ' 운반 중 ', '')}</div><span class="chip" style="background:${cold ? 'var(--sky)' : h.col}">${cold ? '냉장' : h.name}</span></div>`
  }).join('') : '<div class="empty">지금 옮기는 화물이 없습니다.</div>'
  $('c-scans').innerHTML = S.scans.length ? S.scans.map((s) => { const c = s.c, h = HUBS[c.hub]; return `<div class="mv"><div><div class="id">${c.id}</div><div class="w">${c.cat} ${c.w}kg${c.handle !== '일반' ? ' · ' + c.handle : ''}</div></div><div class="to">OCR 판독 완료</div><span class="chip" style="background:${h.col}">${h.name}</span></div>` }).join('') : '<div class="empty">입고 도크에서 첫 운송장을 읽는 중입니다.</div>'
}

/* ── 알림 탭 ── */
$('a-filter').addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; alertFilter = b.dataset.f; $('a-filter').querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b)); renderAlerts() })
$('a-read').addEventListener('click', () => { events.forEach((e) => (e.read = true)); renderAlerts(); renderBadge() })
function renderAlerts() {
  const list = events.filter((e) => alertFilter === 'all' || e.cat === alertFilter)
  $('a-list').innerHTML = list.length ? list.map((e) => {
    const [ic, bg, col] = EV_STYLE[e.lv] || EV_STYLE['']
    const [title, ...rest] = e.text.split(' · ')
    return `<div class="ev ${e.read ? '' : 'new'}"><span class="ic" style="background:${bg};color:${col}">${ic}</span><div><em>${clock(e.time)}</em><b>${title}</b>${rest.length ? '<p>' + rest.join(' · ') + '</p>' : ''}</div></div>`
  }).join('') : '<div class="empty">알림이 없습니다.</div>'
}
function renderBadge() {
  const n = events.filter((e) => !e.read && (e.lv === 'crit' || e.lv === 'warn' || e.lv === 'ok')).length
  const b = $('badge'); b.hidden = !n; b.textContent = n > 9 ? '9+' : n
}

/* ── 토스트 (앱 안 알림) ── */
let toastT = 0
function toast(text, cls) {
  const t = $('toast'); t.className = 'toast ' + (cls || ''); t.textContent = text
  requestAnimationFrame(() => t.classList.add('show'))
  clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove('show'), 3200)
}
$('toast').addEventListener('click', () => { $('toast').classList.remove('show'); setTab('alert') })

/* ── 루프 ── */
function renderAll() {
  if (tab === 'home') renderHome()
  if (tab === '3d') { renderSheet(); renderPick() }
  if (tab === 'robot') renderRobot()
  if (tab === 'cargo') renderCargo()
  if (tab === 'alert') renderAlerts()
}
let last = performance.now(), acc = 0
function frame(now) {
  const dt = Math.min(0.05, (now - last) / 1000); last = now
  let simDt = 0
  if (playing && !document.hidden) { let t = dt * speed; simDt = t; while (t > 0) { const s = Math.min(0.025, t); sim.step(s); t -= s } }
  // 3D는 화면에 보일 때(홈 · 3D 탭)만 그린다 → 배터리 절약
  if (scene && (tab === 'home' || tab === '3d') && !document.hidden) {
    const T = S.amrs[2], p = S.sc.phase
    const callout = p >= 2 && p <= 4 ? (T.state === 'maint' ? { title: '정비 중', line: '예비기 AMR-05가 작업 대체', col: COLORS.red } : T.maintFlag ? { title: '고장 예측 · 약 ' + S.sc.rul + '시간', line: '누적 ' + tons(T) + 't · 작업 후 정비 이동', col: COLORS.red } : { title: '진동 이상 ' + T.vib.toFixed(1) + ' mm/s ▲', line: '누적 운반 ' + tons(T) + 't', col: COLORS.amber }) : null
    scene.update(now, simDt, dt, { selected, cargo: S.amrs[selected].box || null, callout, phase: p })
    scene.render()
  }
  acc += dt
  if (acc > 0.25) { acc = 0; renderAll() }
  requestAnimationFrame(frame)
}
syncSide(); renderPick(); renderBadge(); setTab('home')
requestAnimationFrame(frame)

// 안드로이드 뒤로가기: 홈이 아니면 홈으로
window.addEventListener('popstate', () => { if (tab !== 'home') { setTab('home'); history.pushState({}, '') } })
history.pushState({}, '')
window.__amrApp = { sim, setTab } // 개발용 확인 핸들
