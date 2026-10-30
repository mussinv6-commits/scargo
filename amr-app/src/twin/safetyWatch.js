// 긴급 경보(화재) 감시 모듈 — 웹(S-Bot · S Cargo)과 폰 앱이 같이 쓴다. 바닐라 JS (Vue 불필요)
//  사용: import { startSafetyWatch } from './safetyWatch.js'; const stop = startSafetyWatch({ base: '' })
//  서버: GET  {base}/api/amr/safety/alert → { active:false } 또는 { active:true, kind:'hub'|'batt', label, stage, since, evac, total, temp }
//  화면: 활성이면 화면 전체가 붉게(배터리 화재는 주황) 번쩍이고 가운데에 큰 글씨. 종료는 웹 관제의 "화재 종료" 버튼만 가능(서버 값이 꺼지면 같이 꺼짐).
//  소리 · 진동: 작게. 작은 알림음 3초 간격, 짧은 진동 4초 간격, 처음 한 번만 조금 또렷하게. 🔇 로 소리만 끈다.
const KIND = {
  hub: { title: '허브에 화재가 발생했습니다', sub: '즉시 대피하십시오', col: '255,26,26', steps: ['작업 중단', '비상구 방향으로 이동'] },
  batt: { title: 'AMR 배터리 화재', sub: '접근 금지', col: '255,138,26', steps: ['격리 구역 반경 진입 금지', '허브는 정상 운영 중'] },
}
// 웹(S Cargo) 팝업 문구: 화물차 운전자 · 업체 대상이라 '대피' 가 아니라 차량 운행 주의 안내
const KIND_CARGO = {
  hub: { title: '허브에 화재가 발생했습니다', sub: '차량 운행에 주의하십시오', col: '255,26,26', steps: ['허브 진입·운행 시 서행', '관제·안전요원 안내에 따라 이동'] },
  batt: { title: 'AMR 배터리 화재', sub: '허브 내 운행에 주의하십시오', col: '255,138,26', steps: ['격리 구역 인근 접근 금지', '허브는 정상 운영 중 · 안내에 따라 운행'] },
}
export function startSafetyWatch({ base = '', pollMs = 3000, fetchFn = fetch, getFn, skip, mode = 'full', onChange } = {}) {
  let timer = 0, el = null, last = null, actx = null, muted = false, beepT = 0, vibT = 0, firstDone = false
  const css = document.createElement('style')
  css.textContent = '@keyframes sw-pulse{from{opacity:.55}to{opacity:1}}'
  document.head.appendChild(css)
  function beep(loud) {
    if (muted) return
    try {
      actx = actx || new (window.AudioContext || window.webkitAudioContext)()
      const o = actx.createOscillator(), g = actx.createGain()
      o.type = 'sine'; o.frequency.value = 660; g.gain.value = loud ? 0.08 : 0.03 // 작게
      o.connect(g); g.connect(actx.destination); o.start(); o.stop(actx.currentTime + (loud ? 0.35 : 0.18))
    } catch { /* 소리 허용 전에는 무시 */ }
  }
  const vib = (ms) => { try { navigator.vibrate && navigator.vibrate(ms) } catch { /* 무시 */ } }
  function show(a) {
    const k = KIND[a.kind] || KIND.hub
    if (!el) {
      el = document.createElement('div')
      el.style.cssText = 'position:fixed;inset:0;z-index:99999;pointer-events:none;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;color:#fff;font-family:"Malgun Gothic",sans-serif;padding:24px'
      document.body.appendChild(el)
    }
    const m = a.since ? Math.max(0, Math.floor((Date.now() - a.since) / 1000)) : 0
    const t = String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0')
    el.style.boxShadow = `inset 0 0 120px 50px rgba(${k.col},.95), inset 0 0 40px 8px rgba(${k.col},.8)`
    el.style.background = `radial-gradient(ellipse at center, rgba(0,0,0,.35) 30%, rgba(${k.col},.45) 100%)`
    el.style.animation = 'sw-pulse 1s ease-in-out infinite alternate'
    el.innerHTML = `<div style="font-size:72px">🔥</div><div style="font-size:40px;font-weight:700;text-shadow:0 0 20px rgb(${k.col})">${k.title}</div>`
      + `<div style="font-size:18px;margin-top:6px">${a.label || ''} · 경과 ${t}</div><div style="font-size:26px;font-weight:700;margin:10px 0">${k.sub}</div>`
      + `<div style="background:rgba(0,0,0,.45);border-radius:12px;padding:12px 20px;font-size:18px;line-height:1.8">${k.steps.join('<br>')}</div>`
      + (a.kind === 'hub' && a.total ? `<div style="margin-top:12px;font-size:15px">AMR 대피 ${a.evac || 0} / ${a.total}대</div>` : '')
      + '<div style="margin-top:16px;font-size:14px;opacity:.8">관리자가 화재 종료를 누르면 꺼집니다</div>'
      + '<button id="sw-mute" style="pointer-events:auto;position:absolute;right:14px;bottom:14px;font-size:20px;background:rgba(0,0,0,.5);color:#fff;border:0;border-radius:20px;padding:6px 12px">' + (muted ? '🔇' : '🔊') + '</button>'
    const mb = el.querySelector('#sw-mute'); if (mb) mb.onclick = () => { muted = !muted; mb.textContent = muted ? '🔇' : '🔊' }
    if (!beepT) {
      if (!firstDone) { beep(true); vib([200, 100, 200]); firstDone = true }
      beepT = setInterval(() => beep(false), 3000); vibT = setInterval(() => vib(60), 4000)
    }
  }
  // 웹(S Cargo)용: 화면 전체가 아니라 가운데 작은 팝업 한 번 (확인을 누르면 닫힘, 같은 경보에는 다시 안 뜸). 소리 · 진동 없음
  let popKey = '', popEl = null
  function popup(a) {
    const k = KIND_CARGO[a.kind] || KIND_CARGO.hub, key = a.kind + ':' + (a.since || '')
    if (key === popKey) return
    popKey = key
    popEl && popEl.remove()
    popEl = document.createElement('div')
    popEl.style.cssText = 'position:fixed;inset:0;z-index:99999;background:rgba(0,0,0,.45);display:flex;align-items:center;justify-content:center;font-family:"Malgun Gothic",sans-serif'
    popEl.innerHTML = `<div style="background:#fff;border-radius:14px;border:3px solid rgb(${k.col});max-width:420px;width:88%;padding:22px 24px;text-align:center;box-shadow:0 12px 40px rgba(0,0,0,.4)">`
      + `<div style="font-size:44px">🚨</div><div style="font-size:24px;font-weight:700;color:rgb(${k.col});margin:4px 0">${k.title}</div>`
      + `<div style="font-size:19px;font-weight:700;margin:10px 0;color:#222">${k.sub}</div>`
      + `<div style="background:#f6f6f6;border-radius:10px;padding:10px 14px;font-size:14px;line-height:1.8;color:#333">${k.steps.join('<br>')}</div>`
      + `<button id="sw-ok" style="margin-top:16px;border:0;border-radius:8px;background:rgb(${k.col});color:#fff;font-size:16px;font-weight:700;padding:9px 34px;cursor:pointer">확인</button></div>`
    popEl.querySelector('#sw-ok').onclick = () => { popEl.remove(); popEl = null }
    document.body.appendChild(popEl)
  }
  function hide() { if (mode === 'popup') { if (!last || !last.active) { popEl && popEl.remove(); popEl = null; popKey = '' } return } if (el) { el.remove(); el = null } clearInterval(beepT); clearInterval(vibT); beepT = vibT = 0; firstDone = false }
  async function poll() {
    if (skip && skip()) { hide(); return } // 허브 관제 화면(종료 버튼이 있는 곳)에서는 가리지 않음
    try {
      let a
      if (getFn) { a = await getFn(); if (!a) return } // 앱: amrApi(출입증 토큰) 로 조회
      else { const r = await fetchFn(base + '/api/amr/safety/alert', { credentials: 'include' }); if (!r.ok) return; a = await r.json() }
      if (a && a.active) { if (mode === 'popup') popup(a); else show(a) } else { if (mode === 'popup') { popEl && popEl.remove(); popEl = null; popKey = '' } else hide() }
      if (onChange && JSON.stringify(a) !== JSON.stringify(last)) onChange(a)
      last = a
    } catch { /* 서버 연결 실패 시 직전 상태 유지 */ }
  }
  poll(); timer = setInterval(poll, pollMs)
  return () => { clearInterval(timer); hide(); css.remove() }
}
// 허브 관제(웹)에서 화재 시작 · 단계 · 종료를 서버에 알린다 (관리 권한 필요)
export async function postSafetyAlert(body, { base = '', fetchFn = fetch } = {}) {
  try { await fetchFn(base + '/api/amr/safety/alert', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) } catch { /* 서버 꺼짐 */ }
}
