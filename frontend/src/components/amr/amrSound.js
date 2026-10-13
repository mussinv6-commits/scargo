// ─────────────────────────────────────────────────────────────
// S카고 물류센터 3D 트윈 — 현장 음향 (Web Audio, 음원 파일 없이 합성)
//   - 배경: 창고 공조 웅웅거림 + 넓은 공간 잔향
//   - AMR: 속도에 따라 커지는 모터음 (카메라 위치 기준 3D 입체음향)
//   - 이벤트: 운송장 OCR 삑, 리프트 작동, 장애물 정지 경고, 셔터, 트럭 엔진·에어브레이크,
//             정비 임팩트 렌치, 단계별 알림음
// 브라우저 정책상 사용자가 버튼을 눌러야 소리가 켜진다.
// ─────────────────────────────────────────────────────────────
import { DOCK_Y, BAY } from './amrSim.js'

export function createAmrSound() {
  let ctx = null, enabled = false
  let master, bus, ui, revIn, ambGain, noiseBuf
  const motors = []
  const prev = { amr: [], inDock: [], outDock: [], phase: 0, wrench: 0 }

  function build() {
    const AC = window.AudioContext || window.webkitAudioContext
    if (!AC) return false
    ctx = new AC()
    master = ctx.createGain(); master.gain.value = 0.9
    const comp = ctx.createDynamicsCompressor(); comp.threshold.value = -18; comp.ratio.value = 4
    master.connect(comp).connect(ctx.destination)
    bus = ctx.createGain(); bus.connect(master)
    ui = ctx.createGain(); ui.gain.value = 0.55; ui.connect(master)

    // 노이즈 버퍼 (2초)
    noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate)
    const nd = noiseBuf.getChannelData(0)
    for (let i = 0; i < nd.length; i++) nd[i] = Math.random() * 2 - 1

    // 창고 잔향: 1.8초 감쇠 노이즈 임펄스
    const rev = ctx.createConvolver()
    const len = Math.floor(ctx.sampleRate * 1.8), ir = ctx.createBuffer(2, len, ctx.sampleRate)
    for (let c = 0; c < 2; c++) { const d = ir.getChannelData(c); for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 3.2) }
    rev.buffer = ir
    revIn = ctx.createGain(); revIn.gain.value = 0.32
    const revOut = ctx.createGain(); revOut.gain.value = 0.5
    revIn.connect(rev).connect(revOut).connect(master)

    // 배경: 갈색 노이즈(공조) + 저음 험
    const brown = ctx.createBuffer(1, ctx.sampleRate * 4, ctx.sampleRate), bd = brown.getChannelData(0)
    let last = 0
    for (let i = 0; i < bd.length; i++) { last = (last + 0.02 * (Math.random() * 2 - 1)) / 1.02; bd[i] = last * 3.2 }
    const amb = ctx.createBufferSource(); amb.buffer = brown; amb.loop = true
    const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 520
    ambGain = ctx.createGain(); ambGain.gain.value = 0.07
    amb.connect(lp).connect(ambGain).connect(master); amb.start()
    ;[[100, 0.012], [200, 0.005], [57, 0.01]].forEach(([f, g]) => {
      const o = ctx.createOscillator(); o.frequency.value = f
      const gg = ctx.createGain(); gg.gain.value = g
      o.connect(gg).connect(ambGain); o.start()
    })
    return true
  }

  function panner(x, y, z) {
    const p = ctx.createPanner()
    p.panningModel = 'HRTF'; p.distanceModel = 'inverse'; p.refDistance = 7; p.maxDistance = 140; p.rolloffFactor = 0.9
    setPos(p, x, y, z)
    p.connect(bus)
    const send = ctx.createGain(); send.gain.value = 1; p.connect(send).connect(revIn)
    p._send = send
    return p
  }
  function setPos(p, x, y, z) {
    if (p.positionX) { const t = ctx.currentTime; p.positionX.setTargetAtTime(x, t, 0.05); p.positionY.setTargetAtTime(y, t, 0.05); p.positionZ.setTargetAtTime(z, t, 0.05) }
    else p.setPosition(x, y, z)
  }
  function motorFor(i) {
    if (motors[i]) return motors[i]
    // 구동 모터: 저음 톱니파 + 인버터 고음(가늘게) + 바퀴 구름 노이즈
    const p = panner(0, 0.3, 0)
    const out = ctx.createGain(); out.gain.value = 0; out.connect(p)
    const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 700; lp.connect(out)
    const o1 = ctx.createOscillator(); o1.type = 'sawtooth'; o1.frequency.value = 70
    const g1 = ctx.createGain(); g1.gain.value = 0.18; o1.connect(g1).connect(lp)
    const o2 = ctx.createOscillator(); o2.type = 'sine'; o2.frequency.value = 1400
    const g2 = ctx.createGain(); g2.gain.value = 0.035; o2.connect(g2).connect(out)
    const n = ctx.createBufferSource(); n.buffer = noiseBuf; n.loop = true
    const nb = ctx.createBiquadFilter(); nb.type = 'bandpass'; nb.frequency.value = 900; nb.Q.value = 0.7
    const ng = ctx.createGain(); ng.gain.value = 0.12; n.connect(nb).connect(ng).connect(out)
    o1.start(); o2.start(); n.start()
    motors[i] = { p, out, o1, o2, lp }
    return motors[i]
  }

  /* ── 효과음 ── */
  function tone(dest, f, t0, dur, type = 'sine', vol = 0.3, f2) {
    const o = ctx.createOscillator(); o.type = type; o.frequency.setValueAtTime(f, t0)
    if (f2) o.frequency.exponentialRampToValueAtTime(f2, t0 + dur)
    const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(vol, t0 + 0.012); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur)
    o.connect(g).connect(dest); o.start(t0); o.stop(t0 + dur + 0.05)
  }
  function noise(dest, t0, dur, { type = 'bandpass', f = 1000, f2, q = 1, vol = 0.3, am = 0, attack = 0.02 } = {}) {
    const s = ctx.createBufferSource(); s.buffer = noiseBuf; s.loop = true
    const fl = ctx.createBiquadFilter(); fl.type = type; fl.frequency.setValueAtTime(f, t0); fl.Q.value = q
    if (f2) fl.frequency.exponentialRampToValueAtTime(f2, t0 + dur)
    const g = ctx.createGain(); g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(vol, t0 + attack); g.gain.setValueAtTime(vol, t0 + Math.max(attack, dur - 0.15)); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur)
    let node = s.connect(fl).connect(g)
    if (am) { // 셔터 덜컹거림 같은 진폭 변조
      const amg = ctx.createGain(); amg.gain.value = 0.5
      const lfo = ctx.createOscillator(); lfo.frequency.value = am; const lg = ctx.createGain(); lg.gain.value = 0.5
      lfo.connect(lg).connect(amg.gain); node = node.connect(amg); lfo.start(t0); lfo.stop(t0 + dur + 0.1)
    }
    node.connect(dest); s.start(t0); s.stop(t0 + dur + 0.1)
  }
  function at(x, y, z, life = 4) { const p = panner(x, y, z); setTimeout(() => { p.disconnect(); p._send.disconnect() }, life * 1000); return p }

  const sfx = {
    ocr(x, z) { const p = at(x, 2.6, z), t = ctx.currentTime; tone(p, 2093, t, 0.09, 'square', 0.12); tone(p, 2637, t + 0.1, 0.12, 'square', 0.12) },
    liftUp(x, z) { const p = at(x, 0.4, z), t = ctx.currentTime; noise(p, t, 0.8, { f: 500, f2: 1400, q: 3, vol: 0.18 }); tone(p, 160, t, 0.8, 'sawtooth', 0.05, 260); tone(p, 90, t + 0.78, 0.12, 'sine', 0.35, 50) },
    liftDown(x, z) { const p = at(x, 0.4, z), t = ctx.currentTime; noise(p, t, 0.7, { f: 1300, f2: 450, q: 3, vol: 0.16 }); tone(p, 240, t, 0.7, 'sawtooth', 0.045, 140); tone(p, 80, t + 0.68, 0.14, 'sine', 0.4, 45) },
    obstacle(x, z) { const p = at(x, 0.5, z), t = ctx.currentTime; tone(p, 1568, t, 0.11, 'triangle', 0.25); tone(p, 1568, t + 0.18, 0.11, 'triangle', 0.25) },
    shutter(x, z) { const p = at(x, 3, z, 5), t = ctx.currentTime; noise(p, t, 2.4, { f: 380, q: 1.2, vol: 0.32, am: 17 }); tone(p, 55, t, 2.4, 'sawtooth', 0.05) },
    truckIn(x, z) {
      const p = at(x, 1, z, 6), t = ctx.currentTime
      tone(p, 36, t, 2.6, 'sawtooth', 0.22, 30); noise(p, t, 2.6, { type: 'lowpass', f: 260, vol: 0.25, attack: 0.4 })
      noise(p, t + 2.3, 0.7, { type: 'highpass', f: 2500, vol: 0.22, attack: 0.01 }) // 에어브레이크 '치익'
    },
    truckOut(x, z) { const p = at(x, 1, z, 6), t = ctx.currentTime; tone(p, 30, t, 2.8, 'sawtooth', 0.24, 48); noise(p, t, 2.8, { type: 'lowpass', f: 220, f2: 520, vol: 0.25, attack: 0.3 }) },
    hydraulic(x, z, y, dur, up) { const p = at(x, y, z, dur + 2), t = ctx.currentTime; tone(p, up ? 180 : 260, t, dur, 'sawtooth', 0.07, up ? 260 : 170); noise(p, t, dur, { f: up ? 700 : 900, f2: up ? 1100 : 600, q: 4, vol: 0.12, attack: 0.15 }) },
    backup(x, z) { const p = at(x, 1, z), t = ctx.currentTime; for (let k = 0; k < 3; k++) tone(p, 1046, t + k * 0.32, 0.16, 'square', 0.1) },
    clunk(x, z, y) { const p = at(x, y, z), t = ctx.currentTime; tone(p, 70, t, 0.18, 'sine', 0.45, 40); noise(p, t, 0.12, { type: 'lowpass', f: 600, vol: 0.25, attack: 0.005 }) },
    wrench(x, z) {
      const p = at(x, 0.5, z), t = ctx.currentTime
      for (let k = 0; k < 6; k++) { noise(p, t + k * 0.055, 0.04, { f: 3200, q: 2, vol: 0.28, attack: 0.003 }); tone(p, 120, t + k * 0.055, 0.04, 'square', 0.08) }
    },
    phase(n) {
      const t = ctx.currentTime
      if (n === 2) { tone(ui, 880, t, 0.35, 'sine', 0.3); tone(ui, 659, t + 0.32, 0.5, 'sine', 0.3) }
      else if (n === 3) { for (let k = 0; k < 3; k++) tone(ui, 988, t + k * 0.28, 0.18, 'square', 0.12) }
      else if (n === 4) { tone(ui, 784, t, 0.3, 'triangle', 0.3); tone(ui, 587, t + 0.3, 0.3, 'triangle', 0.3); tone(ui, 440, t + 0.6, 0.55, 'triangle', 0.3) }
      else if (n === 5) { [523, 659, 784, 1047].forEach((f, k) => tone(ui, f, t + k * 0.12, 0.4, 'sine', 0.22)) }
    },
  }

  /* ── 매 프레임: 시뮬레이션 상태 변화를 소리로 ── */
  function update({ S, camera, toWorld, simDt, playing }) {
    if (!enabled || !ctx || ctx.state !== 'running') return
    // 듣는 위치 = 카메라
    const L = ctx.listener, t = ctx.currentTime
    const fw = { x: 0, y: 0, z: -1 }, up = camera.up
    const e = camera.matrixWorld.elements
    fw.x = -e[8]; fw.y = -e[9]; fw.z = -e[10]
    if (L.positionX) {
      L.positionX.setTargetAtTime(camera.position.x, t, 0.05); L.positionY.setTargetAtTime(camera.position.y, t, 0.05); L.positionZ.setTargetAtTime(camera.position.z, t, 0.05)
      L.forwardX.setTargetAtTime(fw.x, t, 0.05); L.forwardY.setTargetAtTime(fw.y, t, 0.05); L.forwardZ.setTargetAtTime(fw.z, t, 0.05)
      L.upX.value = up.x; L.upY.value = up.y; L.upZ.value = up.z
    } else { L.setPosition(camera.position.x, camera.position.y, camera.position.z); L.setOrientation(fw.x, fw.y, fw.z, up.x, up.y, up.z) }

    const live = playing && simDt > 0
    S.amrs.forEach((a, i) => {
      const [x, z] = toWorld(a.x, a.y), m = motorFor(i)
      setPos(m.p, x, 0.3, z)
      const v = live ? a.v : 0, k = Math.min(1, v / 84)
      m.out.gain.setTargetAtTime(k > 0.03 ? 0.08 + k * 0.32 : 0, t, 0.08)
      m.o1.frequency.setTargetAtTime(55 + k * 85, t, 0.1)
      m.o2.frequency.setTargetAtTime(900 + k * 1700, t, 0.1)
      m.lp.frequency.setTargetAtTime(350 + k * 900, t, 0.1)
      // 고장 징후: AMR-03 진동이 커지면 모터에 거친 소리가 섞인다
      m.o1.detune.setTargetAtTime(a.vib > 2.6 ? Math.sin(t * 40) * a.vib * 30 : 0, t, 0.02)

      const pv = prev.amr[i] || {}
      if (a.kind === 'fl') {
        m.o1.frequency.setTargetAtTime(38 + k * 50, t, 0.1)
        m.o2.frequency.setTargetAtTime(500 + k * 600, t, 0.1)
        if (live && a.fwPhase !== pv.fw && a.fw) {
          const ph = a.fw.list[a.fw.idx]
          if (ph && (ph.k === 'up' || ph.k === 'down')) sfx.hydraulic(x, z, 2, ph.d, ph.k === 'up')
          if (ph && ph.k === 'out') sfx.backup(x, z)
          if (pv.fw === 'grab' || pv.fw === 'drop') sfx.clunk(x, z, Math.max(0.3, a.forkH))
        }
        prev.amr[i] = { state: a.state, fw: a.fwPhase }
        return
      }
      if (live) {
        if (a.state === 'load' && pv.state !== 'load') sfx.liftUp(x, z)
        if (a.state === 'unload' && pv.state !== 'unload') sfx.liftDown(x, z)
        if (a.state === 'load' && a.task && a.task.type === 'in') {
          const p = 1 - a.timer / 1.4
          if (p > 0.6 && !pv.scanned) { const [sx, sz] = toWorld(118, DOCK_Y[a.task.dk] ?? a.y); sfx.ocr(sx, sz); pv.scanned = true }
        } else pv.scanned = false
        if (a.blocked > 0.4 && !(pv.blocked > 0.4)) sfx.obstacle(x, z)
      }
      prev.amr[i] = { state: a.state, blocked: a.blocked, scanned: pv.scanned }
    })

    if (live) {
      S.inDocks.forEach((d, i) => {
        const pv = prev.inDock[i]
        if (pv && pv !== d.state) {
          const [x, z] = toWorld(80, DOCK_Y[i])
          if (d.state === 'arriving') sfx.truckIn(x - 6, z)
          if (d.state === 'docked' || d.state === 'leaving') sfx.shutter(x, z)
          if (d.state === 'leaving') sfx.truckOut(x - 6, z)
        }
        prev.inDock[i] = d.state
      })
      S.outDocks.forEach((d, i) => {
        const pv = prev.outDock[i]
        if (pv && pv !== d.state) {
          const [x, z] = toWorld(920, DOCK_Y[i])
          if (d.state === 'arriving') sfx.truckIn(x + 6, z)
          if (d.state === 'docked' || d.state === 'leaving') sfx.shutter(x, z)
          if (d.state === 'leaving') sfx.truckOut(x + 6, z)
        }
        prev.outDock[i] = d.state
      })
      const T = S.amrs[2]
      if (T.state === 'maint') {
        prev.wrench -= simDt
        if (prev.wrench <= 0) { const [x, z] = toWorld(BAY.x, BAY.y); sfx.wrench(x, z); prev.wrench = 1.1 + Math.random() * 0.8 }
      }
    }
    if (S.sc.phase !== prev.phase) { if (prev.phase) sfx.phase(S.sc.phase); prev.phase = S.sc.phase }
  }

  function onVis() { if (!ctx) return; if (document.hidden) ctx.suspend(); else if (enabled) ctx.resume() }
  document.addEventListener('visibilitychange', onVis)

  return {
    get enabled() { return enabled },
    // 반드시 버튼 클릭 같은 사용자 동작 안에서 호출
    setEnabled(on) {
      if (on && !ctx && !build()) return false
      enabled = on
      if (!ctx) return enabled
      if (on) { ctx.resume(); master.gain.setTargetAtTime(0.9, ctx.currentTime, 0.2) }
      else { master.gain.setTargetAtTime(0, ctx.currentTime, 0.1); setTimeout(() => { if (!enabled && ctx) ctx.suspend() }, 400) }
      return enabled
    },
    update,
    dispose() {
      document.removeEventListener('visibilitychange', onVis)
      if (ctx) ctx.close()
      ctx = null; enabled = false
    },
  }
}
