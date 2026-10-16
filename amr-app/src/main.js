// ─────────────────────────────────────────────────────────────
// S카고 AMR 관제 앱 (관리자용 · 로그인 없음)
// 웹 관제 화면과 같은 시뮬레이션 · 3D 모듈을 휴대폰 화면 5탭으로 보여준다.
//   홈(요약) · 통계(KPI) · 3D · 물류(입출차 · 상자) · 로봇(예지보전)   + 🔔 알림 · ⚙ 설정
// 26.10.15 관리자가 보기 편하게 정리: 홈은 한 화면 요약만, 자세한 건 각 탭으로
// 서버가 없으면 앱만으로 돌아가고, 서버(DB)에 연결되면 웹 관제 화면과 상태 · 정비 승인을 함께 쓴다.
// ─────────────────────────────────────────────────────────────
import { createSim, HUBS, ZONES, STATE_KO, PHASE_T, CAPTIONS, COLORS, status, lvCol, tons, captionOf, fmtPlate } from './twin/amrSim.js'
import { createAmrScene } from './twin/amrScene3d.js'
import { amrApi } from './twin/amrApi.js'   // DB 연동: 웹 관제 화면과 같은 서버 · 같은 DB
import { LocalNotifications } from '@capacitor/local-notifications' // 휴대폰 알림 (차단 · 정비)
import { mountAuthGate, hoursLeft } from './twin/amrAuthGate.js' // 26.10.16 관리자 잠금 화면 (웹의 앱 연결 QR · 6자리)
import { BarcodeScanner, BarcodeFormat } from '@capacitor-mlkit/barcode-scanning' // QR 찍기 (구글 ML Kit)

const $ = (id) => document.getElementById(id)
const PH_COL = { 1: COLORS.cyan, 2: COLORS.amber, 3: COLORS.amber, 4: COLORS.red, 5: COLORS.green }
const PH_BG = { 1: 'rgba(63,224,204,.08)', 2: 'rgba(255,184,77,.1)', 3: 'rgba(255,184,77,.1)', 4: 'rgba(255,90,95,.1)', 5: 'rgba(79,220,138,.1)' }
const cssLv = (l) => (l === 'crit' ? 'var(--red)' : l === 'warn' ? 'var(--amber)' : l === 'spare' ? 'var(--dim)' : 'var(--green)')
const pad = (n) => String(n).padStart(2, '0')
const clock = (d) => pad(d.getHours()) + ':' + pad(d.getMinutes())
const store = { get(k) { try { return localStorage.getItem(k) } catch { return null } }, set(k, v) { try { localStorage.setItem(k, v) } catch { /* 무시 */ } } }

/* ── 상태 ── */
let tab = 'home', selected = 2, speed = 1, playing = true, cargoFilter = 'all', alertFilter = 'all'
let gate = null // 26.10.16 OTP 잠금 화면 (아래에서 붙임)
// 26.10.16 알림 우선순위: crit 긴급만(기본) · warn 긴급 + 주의 · all 전부 — 고른 단계만 휴대폰 알림 · 팝업, 나머지는 알림 탭에만
let notifyLevel = store.get('amr_app_notify') || 'crit'
const NOTIFY_RANK = { crit: 3, warn: 2, info: 1 }, NOTIFY_NEED = { crit: 3, warn: 2, all: 1 }
const allowAlert = (lv) => NOTIFY_RANK[lv] >= NOTIFY_NEED[notifyLevel]
function alertLevel(text, lv) {
  if (/차단|판독 불가|연결 끊김/.test(text)) return 'crit'              // 긴급: 미등록 차량 · 서버 끊김
  if (/AI 예측|진동 이상|자동 정비 예약|배터리 부족/.test(text)) return 'warn' // 주의: 고장 예측 · 진동 · 배터리
  if (/정비|충전|기준 변경|완료|복귀/.test(text)) return 'info'           // 정보: 정비 진행 · 충전 · 기준
  return lv === 'crit' || lv === 'warn' ? 'warn' : 'info'
}
let vehInit = false // 입출차 기록을 한 번이라도 받았는지
const vQueue = [], vWant = [], lastOk = { IN: null, OUT: null } // 앱 트럭 게이트 OCR (아래 gateWant 참고)
let kpiCache = null, vehCache = [], vFilter = 'all', vPick = null // 통계(KPI) · 입출차 기록 (DB)
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
    const al = alertLevel(it.text, it.lv)
    if (al !== 'info' && allowAlert(al) && !/진입 차단/.test(it.text)) toast(it.text, al === 'crit' ? '' : 'amber')
  })
  if (events.length > 80) events.length = 80
  renderAlerts(); renderBadge()
}
function onPhase(p) {
  phaseAt[p] = new Date()
  if (p === 1) { delete phaseAt[2]; delete phaseAt[3]; delete phaseAt[4]; delete phaseAt[5] }
}

/* ── 시뮬레이션 · 3D ── */
/* ── DB 연동 상태 ── */
let remote = null            // 서버 /api/amr/state 응답
const linked = () => !!(remote && remote.producerLive && amrApi.online)   // 웹 관제(현장)가 돌고 있을 때만 따라간다
let dbHist = null            // 선택 로봇의 DB 진동 기록

const sim = createSim({ onLog, onPhase, autoApproveSec: 0, vehicleGate: true, onEvent: (type, p) => { if (type === 'vehicle') gateWant(p) } })
const S = sim.S
sim.setVehicleGate(false); for (let i = 0; i < 80; i++) sim.step(0.05); sim.setVehicleGate(true) // 미리 돌릴 때는 게이트 OCR 없이
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
  if (store.get('amr_app_2d') === '1') scene.set2D(true)
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
  if (t === 'stats') pollKpi()
  if (t !== 'settings' && gate && !amrApi.authed) gate.refresh() // 서버 설정만 보고 나오면 다시 잠금
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
  $('h-cap').textContent = captionOf(S)
  const amrs = S.amrs.filter((a) => a.kind !== 'fl')
  $('h-act').textContent = amrs.filter((a) => a.role === 'active' && !a.maintFlag && a.state !== 'maint').length + '/' + amrs.length
  // 오늘 처리 · 입출차 · 차단: DB 집계(KPI)가 있으면 그 값, 없으면 이 휴대폰 시뮬레이션 값
  $('h-thru').textContent = kpiCache ? kpiCache.today : S.thru
  $('h-gate').textContent = kpiCache ? kpiCache.entries + kpiCache.exits : vehCache.length
  const blk = kpiCache ? kpiCache.blocks : vehCache.filter((v) => v.decision === 'BLOCK').length
  const hb = $('h-block'); hb.textContent = blk; hb.className = blk ? 'crit' : 'good'
  const T = S.amrs[S.sc.target]
  // 지금 확인할 일 (조치가 필요한 것만 · 누르면 해당 화면으로)
  renderTodo(T)
  // 최근 알림 3줄
  $('h-recent').innerHTML = events.slice(0, 3).map((e) => { const [, , col] = EV_STYLE[e.lv] || EV_STYLE['']; return `<div class="rc"><i style="background:${col}"></i><span>${e.text}</span><em>${clock(e.time)}</em></div>` }).join('') || '<div class="hint">알림이 없습니다</div>'
}
function renderFleet() {
  $('h-fleet').innerHTML = S.amrs.map((a, i) => {
    const [l, t] = status(a), c = a.kind === 'fl' ? 'var(--yel)' : cssLv(l)
    return `<button type="button" class="fr ${l === 'crit' ? 'crit' : ''}" data-go-robot="${i}"><span class="d" style="background:${c};box-shadow:0 0 8px ${c}"></span><span class="n">${a.name}</span><span class="s" style="${l === 'crit' ? 'color:var(--red)' : ''}">${l === 'crit' ? t : robotStateText(a)}</span><span class="bar"><i style="width:${Math.round(a.health)}%;background:${c}"></i></span><span class="h" style="color:${l === 'crit' ? 'var(--red)' : 'var(--ink)'}">${Math.round(a.health)}</span></button>`
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
$('v-2d').addEventListener('click', () => {
  if (!scene) return
  scene.set2D(!scene.is2D); store.set('amr_app_2d', scene.is2D ? '1' : '0'); syncSide()
  toast(scene.is2D ? '2D 모드: 위에서 내려다본 평면 지도 (가볍고 한눈에)' : '3D 모드: 입체 트윈', 'ok')
})
$('v-speed').addEventListener('click', () => { speed = speed === 1 ? 2 : speed === 2 ? 4 : 1; syncSide() })
$('v-pause').addEventListener('click', () => { playing = !playing; syncSide() })
function syncSide() {
  $('v-quality').textContent = scene && scene.quality === 'normal' ? '일반' : '저사양'
  $('v-2d').textContent = scene && scene.is2D ? '3D' : '2D'
  $('v-quality').disabled = !!(scene && scene.is2D)
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
  renderFleet()
}
function renderTimeline(a) {
  const card = $('r-maint-card')
  if (selected !== S.sc.target) {
    card.hidden = false
    const lastMaint = a.kind === 'fl' ? '오늘 08:30 · 마스트 유압 점검' : '오늘 08:30 · 정기 점검'
    $('r-tl').innerHTML = `<div class="tli" style="color:var(--green)"><i></i><span>${lastMaint}</span><em>완료</em></div><div class="tli todo"><i></i><span>다음 정기 점검</span><em>7일 후</em></div>`
    return
  }
  const p = S.sc.phase, at = (k) => (phaseAt[k] ? clock(phaseAt[k]) : '')
  const approved = p >= 3 && !S.sc.await
  const steps = [
    [2, '진동 이상 감지', 'var(--amber)'], [3, 'AI 고장 예측 · 정비 승인 요청', 'var(--red)'],
    [3.5, approved ? '관리자 승인 (' + (S.sc.approvedBy || '관리자') + ') · 예비기 AMR-05 투입' : '관리자 승인 · 예비기 AMR-05 투입', 'var(--cyan)'],
    [4, '정비 스테이션 도착 · 베어링 교체', 'var(--red)'], [5, '정비 완료 · 정상 복귀', 'var(--green)'],
  ]
  $('r-tl').innerHTML = steps.map(([k, txt, c], i) => {
    const done = k === 3.5 ? approved && p !== 1 : p >= k && !(p === 1)
    const doing = !done && ((k === 3.5 && S.sc.await) || (k === 4 && p === 3 && approved) || (k === 5 && p === 4))
    return `<div class="tli ${done ? '' : 'todo'}" style="${done ? 'color:' + c : ''}"><i></i><span>${txt}</span><em>${done ? at(Math.floor(k)) : doing ? (k === 3.5 ? '승인 대기' : '진행 중') : '예정'}</em></div>`
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
  const cs = getComputedStyle(document.documentElement), cv = (n) => cs.getPropertyValue(n).trim()
  cx.font = '10px monospace'; cx.textBaseline = 'middle'
  ;[0, 2, 4, 6, 8].forEach((v) => { cx.strokeStyle = cv('--edge'); cx.lineWidth = 1; cx.beginPath(); cx.moveTo(L, Y(v) + 0.5); cx.lineTo(R, Y(v) + 0.5); cx.stroke(); cx.fillStyle = cv('--dim'); cx.textAlign = 'right'; cx.fillText(v, L - 5, Y(v)) })
  cx.fillStyle = 'rgba(255,90,95,.07)'; cx.fillRect(L, Y(ymax), R - L, Y(7.1) - Y(ymax))
  ;[[4.5, COLORS.amber, '주의 4.5'], [7.1, COLORS.red, '고장 7.1']].forEach(([v, c, t]) => { cx.strokeStyle = c; cx.globalAlpha = 0.75; cx.setLineDash([3, 3]); cx.beginPath(); cx.moveTo(L, Y(v)); cx.lineTo(R, Y(v)); cx.stroke(); cx.setLineDash([]); cx.globalAlpha = 1; cx.fillStyle = c; cx.textAlign = 'left'; cx.fillText(t, L + 4, Y(v) - 7) })
  cx.strokeStyle = cv('--edge2'); cx.beginPath(); cx.moveTo(L + pw + 0.5, Tp); cx.lineTo(L + pw + 0.5, B); cx.stroke()
  cx.fillStyle = cv('--dim'); cx.textAlign = 'right'; cx.fillText('예측 ▸', R, h - 6)
  const hs = linked() && dbHist && dbHist.code === a.name ? dbHist.v : a.hist, N = 150, dx = pw / (N - 1), off = N - hs.length, [lv] = status(a), col = a.kind === 'fl' ? '#f2b705' : lvCol(lv === 'spare' ? 'ok' : lv)
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
  }).join('') + `<div class="hint" style="margin-top:4px">권역별 누적 출고: ${HUBS.map((h, i) => h.name + ' ' + S.hubSent[i]).join(' · ')}건</div>`
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
  $('c-scans').innerHTML = S.scans.length ? S.scans.map((s) => { const c = s.c, h = HUBS[c.hub]; return `<div class="mv"><div><div class="id">${c.ean || c.id}</div><div class="w">${c.id} · ${c.cat} ${c.w}kg${c.handle !== '일반' ? ' · ' + c.handle : ''}</div></div><div class="to">바코드 스캔 완료</div><span class="chip" style="background:${h.col}">${h.name}</span></div>` }).join('') : '<div class="empty">입고 도크에서 첫 상자 바코드를 읽는 중입니다.</div>'
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
  if (tab === 'settings') renderDb()
}
let last = performance.now(), acc = 0
function frame(now) {
  const dt = Math.min(0.05, (now - last) / 1000); last = now
  let simDt = 0
  if (playing && !document.hidden) { let t = dt * speed; simDt = t; while (t > 0) { const s = Math.min(0.025, t); sim.step(s); t -= s } }
  if (linked()) overlayRemote()
  // 3D는 화면에 보일 때(홈 · 3D 탭)만 그린다 → 배터리 절약
  if (scene && (tab === 'home' || tab === '3d') && !document.hidden) {
    const T = S.amrs[S.sc.target], p = S.sc.phase
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

/* ── 다크 / 라이트 테마 (기기에 저장) ── */
function applyTheme(t) {
  const light = t === 'light'
  document.documentElement.dataset.theme = light ? 'light' : 'dark'
  const meta = document.querySelector('meta[name="theme-color"]'); if (meta) meta.content = light ? '#f2f5f8' : '#070b10'
  document.querySelectorAll('[data-theme-toggle]').forEach((b) => { b.textContent = light ? '다크 모드' : '라이트 모드' }) // 웹과 같은 문구
  if (scene) scene.setTheme(light ? 'light' : 'dark')
  store.set('amr_app_theme', light ? 'light' : 'dark')
}
document.querySelectorAll('[data-theme-toggle]').forEach((b) => b.addEventListener('click', () => applyTheme(document.documentElement.dataset.theme === 'light' ? 'dark' : 'light')))
applyTheme(store.get('amr_app_theme') || 'dark')
syncSide()

/* ── DB 연동 (웹 관제 화면과 같은 서버) ──
   - 2초마다 /api/amr/state 를 읽는다
   - 웹 관제(현장)가 돌고 있으면: 로봇 수치는 DB 값으로 보여 주고, 고장 예측 · 승인 단계를 웹과 맞춘다
   - 앱에서 "정비 승인"을 누르면 DB에 기록 → 웹 관제 화면의 고장 로봇이 정비소로 이동한다 */
function overlayRemote() {
  remote.robots.forEach((r) => {
    const a = S.amrs.find((x) => x.name === r.code); if (!a) return
    if (r.vibration != null) a.vib = r.vibration
    if (r.temp != null) a.temp = r.temp
    if (r.battery != null) a.batt = r.battery
    if (r.health != null) a.health = r.health
  })
}
let lastApprover = ''
async function pollRemote() {
  const st = await amrApi.state()
  remote = st
  if (st && st.producerLive) {
    const act = st.active
    const mm = st.pending || act
    if (mm && mm.rulHours) S.sc.rul = Math.round(mm.rulHours)   // 남은 수명은 웹(현장)이 계산해 DB에 남긴 값으로 맞춘다
    if (st.settings && Date.now() - cfgLocalT > 3000) sim.setCfg({ rulAt: st.settings.rulThresholdHours, chargeAt: st.settings.chargeAtPct })
    sim.follow({ pending: !!st.pending, approved: !!(act && act.approvedAt), approvedBy: act && act.approvedBy, code: (st.pending || act || {}).robotCode })
    if (act && act.approvedAt && act.id !== lastApprover) { lastApprover = act.id; if (allowAlert('warn')) toast(act.robotCode + ' 자동 정비 예약 · 예비기 투입 (웹 관제)', 'amber'); phoneNotify(act.robotCode + ' 자동 정비 예약', '예비기 AMR-05 투입 · 남은 수명 약 ' + Math.round(act.rulHours || 0) + '시간', 'warn') }
    const a = S.amrs[selected]
    if (tab === 'robot') {
      const h = await amrApi.history(a.name, 5)
      if (h) dbHist = { code: a.name, v: h.slice(-150).map((p) => (p.state === 'MAINT' ? null : p.vibration)) }
    }
  }
  if (st && st.vehicles) applyVehicles(st.vehicles)
  renderDb(); renderCfg()
}
setInterval(pollRemote, 2000); pollRemote()

/* ── 26.10.15 입구 · 출구 번호판 OCR: 웹(현장)이 인식해 DB에 남긴 결과를 앱 3D 트럭 위에 띄운다 ── */
const VKIND = { CARGO: '화물차', COURIER: '택배차', UNKNOWN: '미등록' }
const VDEC = { ALLOW: ['입차', 'g'], BLOCK: ['차단', 'r'], EXIT_OK: ['출차', 'c'], MISMATCH: ['불일치', 'a'] }
let lastVehicleId = 0
/* 26.10.16 앱 트럭의 게이트 OCR
   앱 3D 트럭은 웹과 따로 움직이므로, 앱 트럭이 게이트에 서면 '인식 중'을 띄우고
   웹(현장)에서 막 나온 OCR 결과를 붙인다. 6초 안에 새 결과가 없으면 최근 결과를 다시 보여 준다 */
const laneOf = (v) => v.lane || (v.kind === 'COURIER' ? 'OUT' : 'IN')
const toRes = (v) => ({ plate: v.plate, photoPlate: v.photoPlate, confidence: v.confidence, kind: v.kind, decision: v.decision })
function gateWant(p) { vWant.push({ side: p.side, dock: p.dock, dir: p.dir, vehicleNo: p.vehicleNo, at: Date.now() }); gateMatch() }
function gateMatch() {
  for (let i = 0; i < vWant.length; i++) {
    const w = vWant[i], lane = w.side === 'out' ? 'OUT' : 'IN'
    const qi = vQueue.findIndex((q) => q.v.direction === w.dir && laneOf(q.v) === lane)
    if (qi < 0) continue
    const q = vQueue.splice(qi, 1)[0]; vWant.splice(i--, 1)
    sim.vehicleResult(w.side, w.dock, toRes(q.v), w.dir)
  }
}
setInterval(() => {
  const now = Date.now()
  while (vQueue.length && now - vQueue[0].at > 60000) vQueue.shift() // 1분 지난 결과는 버림
  for (let i = 0; i < vWant.length; i++) {
    const w = vWant[i]; if (now - w.at < 6000) continue
    vWant.splice(i--, 1)
    if (w.dir === 'EXIT') sim.vehicleResult(w.side, w.dock, { plate: w.vehicleNo, decision: 'EXIT_OK', confidence: null }, 'EXIT')
    else { const v = lastOk[w.side === 'out' ? 'OUT' : 'IN']; sim.vehicleResult(w.side, w.dock, v ? toRes(v) : null, 'ENTRY') }
  }
}, 1000)
function applyVehicles(list) {
  const fresh = list.filter((v) => v.id > lastVehicleId).reverse() // 오래된 것부터
  const first = !vehInit; vehInit = true // 첫 응답(기록이 0건이어도)까지만 '처음'으로 본다
  if (!list.length) return // 앱을 처음 켰을 때도 도크별 최근 결과를 트럭에 바로 붙인다 (알림은 새 기록만)
  fresh.forEach((v) => {
    if (v.dock == null) return
    const out = v.lane ? v.lane === 'OUT' : v.kind === 'COURIER'
    if (v.direction !== 'EXIT' && v.decision !== 'BLOCK') lastOk[out ? 'OUT' : 'IN'] = v // 새 결과가 없을 때 다시 보여 줄 최근 결과
    if (!first) { vQueue.push({ v, at: Date.now() }); gateMatch() }
    if (v.decision === 'BLOCK' && !first) { toast('미등록 차량 차단 · ' + (fmtPlate(v.plate) || '판독 불가'), ''); phoneNotify('미등록 차량 진입 차단', (fmtPlate(v.plate) || '판독 불가') + ' · ' + (v.lane === 'OUT' ? '출고' : '입고') + ' 게이트', 'crit') }
  })
  lastVehicleId = Math.max(lastVehicleId, ...list.map((v) => v.id))
  vehCache = list
  renderVehicles()
}
// 물류 탭 · 입출차 목록 (필터 · 누르면 인식 사진)
function photoUrl(u) {
  // OCR 서버가 준 주소(localhost:8001)는 휴대폰에서 안 열리므로 노트북 주소로 바꾼다
  try { const x = new URL(u), b = new URL(amrApi.base); x.hostname = b.hostname; return x.href } catch { return u }
}
function renderVehicles() {
  const el = $('v-list'); if (!el) return
  const list = vehCache.filter((v) => vFilter === 'all' || (vFilter === 'BLOCK' ? v.decision === 'BLOCK' : v.direction === vFilter && v.decision !== 'BLOCK'))
  el.innerHTML = list.length ? list.map((v) => {
    const [dl, c] = VDEC[v.decision] || [v.decision, 'a']
    const t = v.createdAt ? clock(new Date(v.createdAt)) : ''
    const to = v.decision === 'BLOCK' ? '미등록 · 진입 차단' : v.direction === 'EXIT' ? (VKIND[v.kind] || '') + (v.destHub ? ' · ' + v.destHub + '행' : '') + (v.boxCount ? ' ' + v.boxCount + '건' : '') : (VKIND[v.kind] || '') + ' → ' + (v.lane === 'OUT' ? '출고 D' + (v.dock + 4) : '입고 D' + (v.dock + 1))
    return `<li data-id="${v.id}" class="${vPick === v.id ? 'on' : ''}"><b class="vp">${fmtPlate(v.plate) || '판독불가'}</b><span><strong>${to}</strong>${t}${v.confidence != null ? ' · 인식 ' + v.confidence + '%' : ''}</span><i class="vd ${c}">${v.direction === 'EXIT' ? (v.decision === 'EXIT_OK' ? '출차' : dl) : dl}</i></li>`
  }).join('') : '<li><span>해당 기록이 없습니다</span></li>'
}
$('v-filter').addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; vFilter = b.dataset.f; $('v-filter').querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b)); renderVehicles() })
$('v-list').addEventListener('click', (e) => {
  const li = e.target.closest('li[data-id]'); if (!li) return
  const v = vehCache.find((x) => String(x.id) === li.dataset.id); if (!v) return
  vPick = v.id
  const ph = $('v-photo')
  if (v.imageUrl) { const im = $('v-img'); im.dataset.alt = photoUrl(v.imageUrl); im.src = amrApi.photoUrl(v.id); $('v-cap').textContent = '사진 ' + (fmtPlate(v.photoPlate) || '') + ' → 인식 ' + (fmtPlate(v.plate) || '판독 불가'); ph.hidden = false } else ph.hidden = true
  renderVehicles()
})
// 사진: 백엔드(8080)로 못 받으면 OCR 서버(8001)에서 직접 한 번 더 (웹과 같은 예비 경로)
$('v-img').addEventListener('error', () => { const im = $('v-img'); if (im.dataset.alt && im.src !== im.dataset.alt) { const a = im.dataset.alt; im.dataset.alt = ''; im.src = a } })
$('c-seg').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return
  $('c-seg').querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b))
  $('c-gate').hidden = b.dataset.s !== 'gate'; $('c-box').hidden = b.dataset.s !== 'box'
})

/* ── 지금 확인할 일: 차단 차량 · 정비 · 배터리 부족 ── */
const ackBlock = new Set()
function renderTodo(T) {
  const items = []
  vehCache.filter((v) => v.decision === 'BLOCK' && !ackBlock.has(v.id) && v.createdAt && Date.now() - new Date(v.createdAt).getTime() < 15 * 60 * 1000).slice(0, 2).forEach((v) =>
    items.push({ lv: 'r', title: '미등록 차량 진입 차단', body: `<span class="vp">${fmtPlate(v.plate) || '판독 불가'}</span> ${v.lane === 'OUT' ? '출고' : '입고'} D${v.dock + (v.lane === 'OUT' ? 4 : 1)} · ${clock(new Date(v.createdAt))}`, go: 'gate', id: v.id, btn: '기록 보기' }))
  const [lv] = status(T)
  if (T.state === 'maint') items.push({ lv: 'a', title: T.name + ' 정비 중 · 베어링 교체', body: '예비기 AMR-05 가 작업을 대신하는 중', robot: S.sc.target, btn: '로봇 보기' })
  else if (T.maintFlag) items.push({ lv: 'a', title: T.name + ' 자동 정비 예약됨', body: '남은 수명 약 ' + S.sc.rul + '시간 · 기준 ' + S.cfg.rulAt + '시간 · 예비기 투입', robot: S.sc.target, btn: '로봇 보기' })
  else if (S.sc.phase === 2 && S.sc.rul != null) items.push({ lv: 'a', title: T.name + ' 진동 이상 · 남은 수명 약 ' + S.sc.rul + '시간', body: '정비 기준 ' + S.cfg.rulAt + '시간 이하가 되면 자동 정비', robot: S.sc.target, btn: '로봇 보기' })
  else if (lv === 'warn' || lv === 'crit') items.push({ lv: 'a', title: T.name + ' 진동 ' + T.vib.toFixed(1) + ' mm/s', body: 'AI 가 추세를 계산하는 중', robot: S.sc.target, btn: '로봇 보기' })
  S.amrs.forEach((a, i) => { if (a.kind !== 'fl' && a.role === 'active' && a.batt < Math.min(25, S.cfg.chargeAt) && a.state !== 'charge') items.push({ lv: 'a', title: a.name + ' 배터리 ' + Math.round(a.batt) + '%', body: '작업이 끝나면 충전소로 이동', robot: i, btn: '로봇 보기' }) })
  const el = $('h-todo')
  if (!items.length) { el.innerHTML = '<div class="todo-ok"><i></i>지금 확인할 일이 없습니다</div>'; return }
  el.innerHTML = `<div class="lbl" style="margin:0 2px 6px">지금 확인할 일 · ${items.length}</div>` + items.slice(0, 3).map((it) => `<div class="tcard ${it.lv}"><div><b>${it.title}</b><p>${it.body}</p></div><button type="button" class="btn sm" ${it.go ? `data-todo-gate="${it.id}"` : `data-go-robot="${it.robot}"`}>${it.btn}</button></div>`).join('')
}
$('h-todo').addEventListener('click', (e) => {
  const b = e.target.closest('[data-todo-gate]'); if (!b) return
  ackBlock.add(+b.dataset.todoGate)
  setTab('cargo'); vFilter = 'BLOCK'; $('v-filter').querySelectorAll('button').forEach((x) => x.classList.toggle('on', x.dataset.f === 'BLOCK')); renderVehicles()
})

/* ── 3D 바로가기 시점 · 게이트 자동 따라가기 ── */
$('v-presets').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b || !scene) return
  if (b.id === 'v-autogate') { scene.setAutoGate(!scene.autoGate); b.classList.toggle('on', scene.autoGate); store.set('amr_app_autogate', scene.autoGate ? '1' : '0'); toast(scene.autoGate ? '게이트 자동 켜짐 · 다음 트럭이 입구 · 출구 OCR에 오면 그 게이트를 6초 보여 줍니다' : '게이트 자동 꺼짐', 'ok'); return }
  scene.setPreset(b.dataset.p); coldView = ''
  $('v-presets').querySelectorAll('button[data-p]').forEach((x) => x.classList.toggle('on', x === b))
  document.querySelectorAll('#v-chips button').forEach((x) => x.classList.remove('on'))
})
if (scene && store.get('amr_app_autogate') === '1') { scene.setAutoGate(true); $('v-autogate').classList.add('on') }
// 26.10.16 게이트 자동 상태를 버튼에 (대기 중 · 입구/출구 보는 중)
let agTxt = ''
setInterval(() => {
  if (!scene) return
  const side = scene.autoGateSide
  const t = !scene.autoGate ? '게이트 자동' : side ? '게이트 자동 ▶ ' + (side === 'in' ? '입구 보는 중' : '출구 보는 중') : '게이트 자동 ● 대기' // 웹과 같은 문구
  if (t !== agTxt) { agTxt = t; $('v-autogate').textContent = t }
}, 400)

/* ── 휴대폰 알림 (차단 차량 · 정비 예약만) ── */
let notifyOk = false, notifySeq = 1
;(async () => {
  try {
    const st = await LocalNotifications.checkPermissions()
    notifyOk = st.display === 'granted' || (await LocalNotifications.requestPermissions()).display === 'granted'
  } catch { notifyOk = false }
})()
function phoneNotify(title, body, lv = 'crit') {
  if (!notifyOk || !allowAlert(lv)) return
  try { LocalNotifications.schedule({ notifications: [{ id: notifySeq++, title, body }] }) } catch { /* 무시 */ }
}

/* ── 통계 탭: DB 집계 KPI (10초마다 · 통계 탭을 열면 바로) ── */
async function pollKpi() {
  const k = await amrApi.kpi(); if (!k) { if (tab === 'stats') $('k-note').textContent = '서버(DB)에 연결되면 오늘 지표가 나옵니다 · ⚙ 설정에서 노트북 주소 확인'; return }
  kpiCache = k
  if (tab === 'stats') renderStats()
}
function renderStats() {
  const k = kpiCache; if (!k) return
  $('k-today').textContent = k.today
  const d = $('k-delta')
  if (k.yesterdaySameTime > 0) { const pct = Math.round((k.today / k.yesterdaySameTime - 1) * 100); d.textContent = (pct >= 0 ? '▲ ' : '▼ ') + Math.abs(pct) + '% 어제 대비'; d.className = pct >= 0 ? 'up' : 'down' } else { d.textContent = ''; d.className = '' }
  const mx = Math.max(1, ...k.hourly.map((h) => h.count))
  $('k-bars').innerHTML = k.hourly.map((h, i) => `<div><i class="${i === k.hourly.length - 1 ? 'now' : ''}" style="height:${Math.max(4, (h.count / mx) * 100)}%"></i><span>${i === 0 || i === 5 || i === k.hourly.length - 1 ? (i === k.hourly.length - 1 ? '지금' : h.hour) : ''}</span></div>`).join('')
  $('k-util').innerHTML = k.utilization.map((u) => `<div><span>${u.code}</span><i><em style="width:${u.pct}%;background:${u.code.startsWith('FL') ? 'var(--yel)' : u.pct < 70 ? 'var(--amber)' : 'var(--cyan)'}"></em></i><b>${u.pct}%</b></div>`).join('') || '<div class="hint">최근 1시간 센서 기록이 쌓이면 나옵니다</div>'
  $('k-dur').textContent = k.avgTransportSec != null ? k.avgTransportSec + '초' : '-'
  $('k-wait').textContent = (k.avgInterWaitSec != null ? k.avgInterWaitSec : 0) + '초'
  $('k-gate').textContent = (k.entries + k.exits) + ' / ' + k.blocks
  $('k-ocr').textContent = k.avgOcrConfidence != null ? k.avgOcrConfidence + '%' : '-'
  const t = new Date(k.at); $('k-at').textContent = clock(t) + ' 기준'
  $('k-note').textContent = '교차로 대기 ' + k.interWaits + '회 · 웹 관제 화면이 켜져 있는 동안 기록이 쌓입니다'
}
setInterval(pollKpi, 10000); setTimeout(pollKpi, 1200)

/* ── 관리자 운영 기준 (정비 · 충전): 앱에서 바꾸면 DB → 웹 관제 3D 로봇이 바로 그 기준으로 움직인다 ── */
let cfgTimer = 0, cfgLocalT = 0
$('cfg-toggle').addEventListener('click', () => { const b = $('cfg-body'); b.hidden = !b.hidden; $('cfg-toggle').textContent = b.hidden ? '변경' : '닫기' })
function renderCfg() {
  $('cfg-sum-rul').textContent = S.cfg.rulAt; $('cfg-sum-chg').textContent = S.cfg.chargeAt
  $('cfg-rul').textContent = S.cfg.rulAt
  $('cfg-chg').textContent = S.cfg.chargeAt
  const st = remote && remote.settings
  $('cfg-by').textContent = st && st.updatedBy ? '최근 변경: ' + st.updatedBy : linked() ? 'DB 연동' : '앱 단독'
}
document.getElementById('cfg-card').addEventListener('click', (e) => {
  const b = e.target.closest('button[data-k]'); if (!b) return
  const d = +b.dataset.d
  if (b.dataset.k === 'rul') sim.setCfg({ rulAt: S.cfg.rulAt + d }); else sim.setCfg({ chargeAt: S.cfg.chargeAt + d })
  cfgLocalT = Date.now(); renderCfg(); renderAll()
  clearTimeout(cfgTimer)
  cfgTimer = setTimeout(async () => {
    if (!amrApi.online) { toast('앱 단독 모드 · 이 휴대폰에만 적용', 'amber'); return }
    const v = await amrApi.saveSettings(S.cfg.rulAt, S.cfg.chargeAt, '앱 · 관리자')
    toast(v ? '운영 기준 저장 · 정비 ' + v.rulThresholdHours + '시간 · 충전 ' + v.chargeAtPct + '% → 웹 관제 반영' : '서버에 저장하지 못했습니다', v ? 'ok' : '')
  }, 500)
})

function renderDb() {
  const on = amrApi.online, live = linked()
  const el = $('db-status')
  el.textContent = live ? '● 웹 관제와 연동 중 (DB)' : on ? '● 서버 연결됨 · 웹 관제 화면이 꺼져 있음' : '○ 서버 연결 안 됨 · 앱 단독 시뮬레이션'
  el.style.color = live ? 'var(--green)' : on ? 'var(--amber)' : 'var(--muted)'
  $('db-chip').textContent = live ? 'DB 연동' : on ? '서버만' : '단독'
  $('db-chip').className = 'db-chip ' + (live ? 'on' : on ? 'mid' : '')
  const lbl = $('r-chart-lbl'); if (lbl) lbl.textContent = live ? 'Vibration · DB 기록 (최근 5분)' : 'Vibration · 최근 30초'
}
$('db-url').value = amrApi.base
$('db-save').addEventListener('click', async () => {
  amrApi.setBase($('db-url').value)
  $('db-url').value = amrApi.base
  toast('서버 주소 저장: ' + amrApi.base, 'ok')
  if (!amrApi.authed) { gate.refresh(); return }
  await pollRemote()
})
$('db-chip').addEventListener('click', () => setTab('settings'))

/* ── 26.10.16 관리자 인증: 앱을 켜면 웹의 [앱 연결] QR을 찍거나 6자리 입력 (통과하면 24시간 유지) ── */
async function scanQr() {
  // 구글 코드 스캐너: 카메라 권한 없이 구글 화면으로 찍고 글자만 돌려받는다 (처음 한 번 모듈 설치)
  try {
    const m = await BarcodeScanner.isGoogleBarcodeScannerModuleAvailable()
    if (m && m.available === false) {
      await BarcodeScanner.installGoogleBarcodeScannerModule()
      throw new Error('QR 기능을 내려받는 중입니다. 10초쯤 뒤 다시 눌러 주세요')
    }
  } catch (e) { if (e && /내려받는/.test(e.message)) throw e /* 확인 기능이 없는 기기는 그냥 찍기 시도 */ }
  try {
    const { barcodes } = await BarcodeScanner.scan({ formats: [BarcodeFormat.QrCode] })
    return barcodes && barcodes[0] ? barcodes[0].rawValue : ''
  } catch (e) {
    if (e && /cancel/i.test(String(e.message || e))) return ''
    throw new Error('QR을 읽지 못했습니다. 6자리를 입력하세요')
  }
}
function renderAuth() {
  const ok = amrApi.authed
  $('au-status').textContent = ok ? '● 인증됨 · ' + hoursLeft() + '시간 남음' : '○ 인증 필요'
  $('au-status').style.color = ok ? 'var(--green)' : 'var(--amber)'
  $('au-out').hidden = !ok
}
gate = mountAuthGate(document.body, {
  client: 'APP', keypad: true, blockOffline: true, onScan: scanQr,
  onServer: (b) => { $('db-url').value = b; toast('QR로 서버 주소 설정: ' + b.replace(/^https?:\/\//, ''), 'ok') },
  onSettings: () => { gate.el.hidden = true; setTab('settings'); toast('서버 주소를 바꾸고 [연결]을 누르면 인증 화면으로 돌아갑니다', 'amber') },
  onChange: () => renderAuth(),
})
$('au-out').addEventListener('click', async () => { await gate.logout(); renderAuth() })
renderAuth(); setInterval(renderAuth, 60000)

/* ── 26.10.16 알림 설정: 어떤 단계까지 휴대폰에 알릴지 ── */
function renderNotify() { $('nt-seg').querySelectorAll('button').forEach((b) => b.classList.toggle('on', b.dataset.n === notifyLevel)) }
$('nt-seg').addEventListener('click', (e) => {
  const b = e.target.closest('button[data-n]'); if (!b) return
  notifyLevel = b.dataset.n; store.set('amr_app_notify', notifyLevel); renderNotify()
  toast('휴대폰 알림: ' + b.textContent, 'ok')
})
renderNotify()
// 서버가 15초 넘게 끊기면 긴급 알림 (잠깐 끊긴 건 무시)
let offTimer = 0, offNotified = false, wasOnline = false
amrApi.onStatus((on) => {
  if (on) { wasOnline = true; clearTimeout(offTimer); offTimer = 0; if (offNotified) { offNotified = false; toast('서버 다시 연결됨', 'ok') } return }
  if (!wasOnline || offTimer || offNotified) return
  offTimer = setTimeout(() => {
    offTimer = 0; if (amrApi.online) return
    offNotified = true
    events.unshift({ time: new Date(), text: '노트북 서버 연결 끊김 · 웹 관제 · DB 확인', lv: 'crit', cat: '', read: tab === 'alert' }); renderAlerts(); renderBadge()
    toast('노트북 서버 연결 끊김', ''); phoneNotify('AMR 서버 연결 끊김', '노트북 서버(웹 관제 · DB)가 15초 넘게 응답하지 않습니다', 'crit')
  }, 15000)
})
