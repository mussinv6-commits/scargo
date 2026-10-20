// ─────────────────────────────────────────────────────────────
// 26.10.16 AMR 관리자 OTP 인증 화면 (웹 관제 화면 · 관리자 앱이 같이 쓴다)
//  - 구글 OTP 앱의 6자리 → 서버 확인 → 24시간 출입증
//  - 아직 등록 전이면: 웹(팀 ADMIN 로그인 상태)에서 QR을 띄워 등록
//  - 사용: const gate = mountAuthGate(host, { client: 'WEB' | 'APP', keypad, blockOffline, onSettings })
//          gate.openRegister() → OTP 다시 등록,  gate.destroy()
// ─────────────────────────────────────────────────────────────
import { amrApi } from './amrApi.js'
import { drawQr } from './amrQr.js'

const CSS = `
.otpg{position:absolute;inset:0;z-index:60;display:flex;align-items:center;justify-content:center;background:rgba(5,9,13,.72);backdrop-filter:blur(7px);-webkit-backdrop-filter:blur(7px);font-family:inherit}
.otpg[hidden]{display:none!important}
.otpg.full{position:fixed;background:var(--bg,#070b10);backdrop-filter:none;-webkit-backdrop-filter:none;align-items:stretch}
.otpg-box{width:min(410px,calc(100% - 32px));background:var(--panel,#111a24);border:1px solid var(--edge2,#2b3b4d);border-radius:16px;padding:24px 26px;box-shadow:0 20px 60px rgba(0,0,0,.55);text-align:center;color:var(--ink,#e9f0f6)}
.otpg.full .otpg-box{width:100%;max-width:440px;margin:0 auto;border:0;border-radius:0;box-shadow:none;background:transparent;padding:calc(48px + env(safe-area-inset-top,0px)) 22px calc(20px + env(safe-area-inset-bottom,0px));display:flex;flex-direction:column}
.otpg-lock{width:50px;height:50px;border-radius:14px;margin:0 auto 12px;display:grid;place-items:center;background:rgba(63,224,204,.12);border:1px solid rgba(63,224,204,.4);color:var(--cyan,#3fe0cc)}
.otpg-lock svg{width:24px;height:24px}
.otpg.full .otpg-lock{display:none}
.otpg-brand{display:none;text-align:left;font-weight:900;font-size:22px;letter-spacing:.02em}.otpg-brand span{color:var(--cyan,#3fe0cc)}
.otpg.full .otpg-brand{display:block}
.otpg h2{font-size:19px;margin:0}.otpg.full h2{display:none}
.otpg p.otpg-d{font-size:13px;color:var(--muted,#8a9bb0);margin:6px 0 0;line-height:1.55}
.otpg.full p.otpg-d{text-align:left}
.otpg-code{display:flex;gap:8px;justify-content:center;margin:18px 0 8px;position:relative}
.otpg-code b{width:44px;height:54px;border-radius:10px;background:var(--panel2,#16212d);border:1px solid var(--edge2,#2b3b4d);display:grid;place-items:center;font-size:24px;font-family:var(--mono,ui-monospace,monospace);font-weight:700}
.otpg-code b.f{border-color:var(--cyan,#3fe0cc);color:var(--cyan,#3fe0cc)}.otpg-code b.cur{border:2px solid var(--cyan,#3fe0cc)}
.otpg-code input{position:absolute;inset:0;opacity:0;width:100%;font-size:16px;caret-color:transparent}
.otpg.err .otpg-code b{border-color:var(--red,#ff5a5f);animation:otpgShake .35s}
@keyframes otpgShake{20%{transform:translateX(-5px)}50%{transform:translateX(5px)}80%{transform:translateX(-3px)}}
.otpg-t{display:flex;align-items:center;justify-content:center;gap:8px;font-size:12px;color:var(--dim,#58697e)}
.otpg-t i{width:70px;height:4px;border-radius:2px;background:var(--edge,#1f2c3a);overflow:hidden;display:block}.otpg-t i u{display:block;height:100%;background:var(--amber,#ffb84d)}
.otpg-msg{min-height:18px;font-size:12.5px;margin-top:10px;color:var(--red,#ff5a5f)}.otpg-msg.ok{color:var(--green,#4fdc8a)}
.otpg-keep{font-size:12.5px;color:var(--muted,#8a9bb0);margin-top:4px}.otpg-keep b{color:var(--amber,#ffb84d)}
.otpg-btn{margin-top:14px;width:100%;height:44px;border:0;border-radius:10px;background:var(--cyan,#3fe0cc);color:#05211d;font-weight:800;font-size:15px;cursor:pointer;font-family:inherit}
.otpg-btn:disabled{opacity:.45;cursor:default}
.otpg-btn.sub{background:transparent;border:1px solid var(--edge2,#2b3b4d);color:var(--muted,#8a9bb0);height:38px;font-size:13px;font-weight:600;margin-top:8px}
.otpg-pad{display:none;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:auto;padding-top:18px}
.otpg.full .otpg-pad{display:grid}.otpg.full .otpg-go{display:none}
.otpg-pad button{height:54px;border:0;border-radius:12px;background:var(--panel,#111a24);color:var(--ink,#e9f0f6);font-size:21px;font-family:inherit}
.otpg-pad button:active{background:var(--panel2,#16212d)}
.otpg-pad button.s{font-size:13px;color:var(--muted,#8a9bb0)}
.otpg-srv{font-size:11px;color:var(--dim,#58697e);margin-top:10px;text-align:center}
.otpg-qr{display:flex;gap:16px;align-items:center;text-align:left;margin-top:16px}
.otpg-qr canvas{width:150px;height:150px;border-radius:8px;flex:none;image-rendering:pixelated}
.otpg-steps{font-size:12.5px;color:var(--muted,#8a9bb0);line-height:1.85}.otpg-steps b{color:var(--ink,#e9f0f6);margin-right:4px}
.otpg-key{margin-top:10px;font-size:11px;color:var(--dim,#58697e);word-break:break-all;text-align:left}.otpg-key code{color:var(--muted,#8a9bb0);font-family:var(--mono,ui-monospace,monospace);user-select:all}
.otpg-view[hidden]{display:none!important}
`
const LOCK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>'

export function hoursLeft() {
  const ms = amrApi.tokenExpiresAt - Date.now()
  if (ms <= 0) return 0
  return Math.max(1, Math.round(ms / 3600000))
}

export function mountAuthGate(host, opt = {}) {
  const client = opt.client === 'APP' ? 'APP' : 'WEB'
  if (!document.getElementById('otpg-style')) { const st = document.createElement('style'); st.id = 'otpg-style'; st.textContent = CSS; document.head.appendChild(st) }
  const el = document.createElement('div')
  el.className = 'otpg' + (opt.keypad ? ' full' : ''); el.hidden = true
  el.innerHTML = `<div class="otpg-box">
    <div class="otpg-lock">${LOCK}</div><div class="otpg-brand">S-BOT <span>AMR</span></div>
    <div class="otpg-view" data-v="login">
      <h2>AMR 관제 인증</h2>
      <p class="otpg-d">휴대폰 Google Authenticator의 <b>S-BOT AMR</b> 6자리 코드를 입력하세요</p>
      <div class="otpg-code"><b></b><b></b><b></b><b></b><b></b><b></b><input inputmode="numeric" autocomplete="one-time-code" maxlength="6" aria-label="OTP 6자리"></div>
      <div class="otpg-t">코드는 30초마다 바뀝니다 <i><u></u></i></div>
      <div class="otpg-keep">통과하면 이 기기에서 <b>24시간</b> 로그인 유지</div>
      <div class="otpg-msg"></div>
      <button class="otpg-btn otpg-go" type="button" disabled>확인</button>
    </div>
    <div class="otpg-view" data-v="register" hidden>
      <h2>AMR 관리자 OTP 등록</h2>
      <p class="otpg-d">처음 한 번만 하면 됩니다. 등록하면 예전에 로그인한 기기는 모두 로그아웃돼요.</p>
      <div class="otpg-qr"><canvas></canvas><div class="otpg-steps"><div><b>1</b>Google Authenticator 설치</div><div><b>2</b>앱에서 <b>+</b> → QR 코드 스캔</div><div><b>3</b>뜬 6자리를 아래에 입력</div></div></div>
      <div class="otpg-key">QR이 안 되면 키 직접 입력: <code></code></div>
      <div class="otpg-code"><b></b><b></b><b></b><b></b><b></b><b></b><input inputmode="numeric" autocomplete="one-time-code" maxlength="6" aria-label="OTP 6자리"></div>
      <div class="otpg-msg"></div>
      <button class="otpg-btn otpg-go" type="button" disabled>등록 완료</button>
      <button class="otpg-btn sub otpg-cancel" type="button" hidden>취소</button>
    </div>
    <div class="otpg-view" data-v="info" hidden>
      <h2 class="otpg-it"></h2>
      <p class="otpg-d otpg-id"></p>
      <div class="otpg-msg"></div>
      <button class="otpg-btn otpg-retry" type="button">다시 확인</button>
    </div>
    <div class="otpg-pad">${[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => `<button type="button" data-k="${n}">${n}</button>`).join('')}<button type="button" class="s" data-k="set">서버 설정</button><button type="button" data-k="0">0</button><button type="button" data-k="del" aria-label="지우기">⌫</button></div>
    <div class="otpg-srv"></div>
  </div>`
  host.appendChild(el)
  const q = (s) => el.querySelector(s)
  const views = { login: q('[data-v=login]'), register: q('[data-v=register]'), info: q('[data-v=info]') }
  let view = 'login', busy = false, destroyed = false, wasAuthed = amrApi.authed

  // ── 6자리 입력 칸 ──
  function codeBox(v) {
    const inp = v.querySelector('input'), boxes = v.querySelectorAll('.otpg-code b'), go = v.querySelector('.otpg-go')
    if (opt.keypad) { inp.readOnly = true; inp.setAttribute('inputmode', 'none') } // 앱: 화면 키패드만 사용 (휴대폰 키보드가 가리지 않게)
    const paint = () => {
      const s = inp.value.replace(/\D/g, '').slice(0, 6); if (s !== inp.value) inp.value = s
      boxes.forEach((b, i) => { b.textContent = s[i] || ''; b.classList.toggle('f', i < s.length); b.classList.toggle('cur', i === s.length) })
      go.disabled = s.length !== 6 || busy
      return s
    }
    inp.addEventListener('input', () => { el.classList.remove('err'); msg(v, ''); if (paint().length === 6) submit() })
    inp.addEventListener('keydown', (e) => { if (e.key === 'Enter' && paint().length === 6) submit() })
    go.addEventListener('click', () => submit())
    return { inp, paint, clear() { inp.value = ''; paint() } }
  }
  const boxes = { login: codeBox(views.login), register: codeBox(views.register) }
  const msg = (v, t, ok = false) => { const m = v.querySelector('.otpg-msg'); m.textContent = t || ''; m.classList.toggle('ok', ok) }

  // ── 앱 키패드 ──
  el.querySelectorAll('.otpg-pad button').forEach((b) => b.addEventListener('click', () => {
    const k = b.dataset.k
    if (k === 'set') { if (opt.onSettings) opt.onSettings(); return }
    const box = boxes[view]; if (!box) return
    box.inp.value = k === 'del' ? box.inp.value.slice(0, -1) : (box.inp.value + k).slice(0, 6)
    box.inp.dispatchEvent(new Event('input'))
  }))

  // 30초 막대
  const bar = q('.otpg-t u')
  const tick = setInterval(() => { if (!el.hidden) bar.style.width = (100 - ((Date.now() / 1000) % 30) / 30 * 100) + '%' }, 250)

  function show(v) {
    view = v
    Object.entries(views).forEach(([k, n]) => { n.hidden = k !== v })
    el.hidden = false; el.classList.remove('err')
    q('.otpg-srv').textContent = '서버 ' + amrApi.base.replace(/^https?:\/\//, '')
    if (boxes[v]) { boxes[v].clear(); if (!opt.keypad) setTimeout(() => boxes[v].inp.focus(), 50) }
  }
  function hide() { el.hidden = true; if (opt.onChange) opt.onChange(true) }
  function info(title, text, m) { show('info'); q('.otpg-it').textContent = title; q('.otpg-id').textContent = text; msg(views.info, m || '') }

  async function submit() {
    if (busy) return
    const box = boxes[view]; const code = box.paint(); if (code.length !== 6) return
    busy = true; box.paint()
    const r = view === 'register' ? await amrApi.confirmSetup(code) : await amrApi.login(code, client)
    busy = false
    if (destroyed) return
    if (r.data) { msg(views[view], '인증 완료', true); setTimeout(hide, 350); return }
    el.classList.remove('err'); void el.offsetWidth; el.classList.add('err')
    box.clear(); msg(views[view], r.error || '인증 실패')
  }

  async function openRegister(canCancel = false) {
    const r = await amrApi.setup()
    if (destroyed) return
    if (!r.data) { info('OTP 등록 불가', r.status === 403 ? '웹에서 관리자(ADMIN) 계정으로 로그인한 상태에서만 등록할 수 있습니다.' : '', r.error); return }
    show('register')
    drawQr(views.register.querySelector('canvas'), r.data.uri, 300)
    views.register.querySelector('.otpg-key code').textContent = r.data.secret.replace(/(.{4})/g, '$1 ').trim()
    views.register.querySelector('.otpg-cancel').hidden = !canCancel
  }
  views.register.querySelector('.otpg-cancel').addEventListener('click', () => { if (amrApi.authed) hide() })

  async function refresh() {
    const s = await amrApi.authStatus()
    if (destroyed) return
    if (s.offline) {
      if (amrApi.authed || !opt.blockOffline) { el.hidden = true; return } // 웹: 서버가 꺼져 있으면 시뮬레이션만 계속
      info('서버에 연결할 수 없습니다', '노트북 서버가 켜져 있는지, 같은 WiFi인지, 서버 주소가 맞는지 확인하세요.', '')
      return
    }
    const st = s.data || {}
    if (st.valid) { hide(); return }
    if (!st.registered) {
      if (client === 'WEB' && st.admin) { openRegister(false); return }
      info('OTP가 아직 등록되지 않았습니다', client === 'WEB' ? '팀 관리자(ADMIN) 계정으로 로그인한 뒤 다시 들어오세요.' : '웹 AMR 관제 화면에서 관리자 OTP를 먼저 등록하세요.', '')
      return
    }
    show('login')
  }
  q('.otpg-retry').addEventListener('click', () => refresh())

  // 토큰이 만료되거나 서버가 401을 주면 다시 잠금 화면
  const offAuth = amrApi.onAuth((ok) => {
    if (opt.onChange) opt.onChange(ok)
    if (!ok && el.hidden) { wasAuthed = false; refresh() }
    else if (ok) wasAuthed = true
  })
  const expT = setInterval(() => { if (wasAuthed && !amrApi.authed) { wasAuthed = false; refresh() } }, 30000)

  refresh()
  return {
    el,
    refresh,
    openRegister: () => openRegister(true),
    logout: () => amrApi.logout(),
    destroy() { destroyed = true; clearInterval(tick); clearInterval(expT); offAuth(); el.remove() },
  }
}
