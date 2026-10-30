// 관제 지도 · 차량 관제 · 계측 이력 공통: 접속 토큰, 시연 컨트롤, 서식, 판정 3단계(정상/주의/확정) 표시
const QS = new URLSearchParams(location.search), TOKEN = QS.get('token') || '';
if (QS.get('embed')) document.documentElement.classList.add('embed');
const SUFFIX = location.search;

async function api(url, opt = {}) {
  opt.headers = Object.assign({}, opt.headers || {});
  if (TOKEN) opt.headers['X-Toll-Token'] = TOKEN;
  const r = await fetch(url, opt);
  let d = null;
  try { d = await r.json(); } catch (e) {}
  if (!r.ok) throw new Error((d && d.error) || ('오류 ' + r.status));
  return d;
}
const tons = kg => kg == null ? '—' : (kg / 1000).toFixed(1) + 't';
const esc = s => String(s ?? '').replace(/(과적)?검문소 ?18(?!\d)/g, '인천 과적검문소').replace('관제자(시연)', '관제자').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const p2 = n => String(n).padStart(2, '0');
const hms = iso => { if (!iso) return ''; const d = new Date(iso); return `${p2(d.getHours())}:${p2(d.getMinutes())}:${p2(d.getSeconds())}`; };
const won = n => n == null ? '' : '₩ ' + Number(n).toLocaleString('ko-KR');

function pill(cls, text) { return `<span class="pill ${cls}">${esc(text)}</span>`; }

// 판정 3단계 (정상 / 주의 / 확정). 계측 결과를 보여주는 모든 화면이 같은 말을 쓴다
const LV = { NORMAL: ['grn', '정상'], CAUTION: ['org', '주의'], CONFIRMED: ['red', '확정'], DEVIATION: ['red', '경로 이탈'] };
const lvPill = l => l && LV[l] ? pill(LV[l][0], LV[l][1]) : '';
const LV_HELP = '정상: 기준 이내 · 주의: 기준 초과, 허용 오차(10%) 이내 · 확정: 허용 오차 초과';
const ROUTE_NAME = { BASIC: '기본 경로', DETOUR: '우회 경로(안산 경유)' };
const POS_STATE = { NORMAL: ['grn', '정상 주행'], DEVIATING: ['red', '경로 이탈'], STOPPED: ['red', '정차 (검문소 조치)'], ARRIVED: ['gry', '도착'] };
const ROUTE_KM_TOTAL = 79.8;

// 차량의 현재 상태(위치 시뮬레이션 기준). 위치 기록이 없는 옛 통행은 마지막 계측 지점으로 대신한다
function posOf(t) {
  const p = t.position;
  if (p) return { km: Number(p.km), state: POS_STATE[p.route_status] || POS_STATE.NORMAL, status: p.route_status, dist_m: p.dist_m, type: p.route_type };
  return { km: Number(t.last_km || 0), state: ['gry', '위치 정보 없음'], status: 'NORMAL', dist_m: 0, type: 'BASIC' };
}

// 예상 통행료 (차량 상세 한 곳에서만 보여준다). 경로 이탈로 톨게이트를 못 지나면 다시 계산한 값을 우선한다
function tollOf(t) {
  const q = t.quote || {}, base = t.base_total, exp = t.expected_total;
  const re = q.reroute_total != null ? q.reroute_total : null;
  return { base, exp, re, now: re != null ? re : exp, note: q.reroute_note || '', est: t.has_estimate };
}
function tollText(t) {
  const x = tollOf(t);
  if (x.exp == null) return '—';
  const d = discText(Object.assign({}, t.quote || {}, { night_excluded: (t.quote || {}).night_excluded }));
  return x.re != null ? `${won(x.re)} (경로 이탈로 다시 계산 · ${x.note} · 원래 ${won(x.exp)})` : `${won(x.exp)} (기준 ${won(x.base)}, ${d})${x.est ? ' · 산정 요금 포함' : ''}`;
}

// 지점 통과 예정/실제 시각
function passAt(t, km) {
  const q = t.quote; if (!q) return null;
  const d = new Date(q.departed_at);
  return new Date(d.getTime() + (q.expected_minutes * km / ROUTE_KM_TOTAL) * 60000);
}
const clk = d => d ? `${p2(d.getHours())}:${p2(d.getMinutes())}` : '';

// 지점 한 곳의 계측 요약 문구 (표·타임라인 공통)
function pointNote(p) {
  if (!p.final) return p.skipped ? '경로 이탈로 미통과' : '';
  const f = p.final, out = [];
  if (p.retest) out.push(p.first_level === 'CONFIRMED' && p.level !== 'CONFIRMED' ? '재측정 통과' : '재측정');
  if (f.fine_manwon) out.push(`과태료 ${f.fine_manwon}만원 통보`);
  else if (f.is_reoffense) out.push('재확정(추가 과태료 없음)');
  if (p.level === 'CONFIRMED' && !f.is_passed) out.push('검문소 조치: 운행 중지');
  if (p.pay) out.push(`통행료 ${won(p.pay.amount)} 통보${p.pay.discount_pct ? ` (심야 ${p.pay.discount_pct}%)` : ''}`);
  return out.join(' · ');
}

// 운행 완료(곤지암 허브 도착) 판단과 도착 시각
const isDone = t => t.position && t.position.route_status === 'ARRIVED';
// 검문소 조치로 운행이 중지된 차량 (도로공사·경찰 조치 결과 — 더는 움직이지 않으므로 '운행 중'에서 뺀다)
const isHalted = t => t.position && t.position.route_status === 'STOPPED';
const arrivedAt = t => { const e = (t.route_events || []).find(x => x.kind === 'ARRIVE'); return e ? e.occurred_at : null; };

// 상단 내비게이션 + 시연 컨트롤. opts.selectedPid() -> 선택 차량의 trip_id, opts.refresh() -> 화면 새로고침
// 관제 지도(map)에서는 전체 시연 컨트롤(시연 시작 · 일시정지/재생 · 초기화 · 속도 · 고급), 다른 화면에서는 일시정지/재생과 이탈 안내만 보인다
function setupChrome(active, opts) {
  document.getElementById('nav').innerHTML =
    `<span class="brand">SCARGO</span><a class="${active === 'map' ? 'on' : ''}" href="/${SUFFIX}">관제 지도</a>` +
    `<a class="${active === 'ck' ? 'on' : ''}" href="/checkpoint${SUFFIX}">차량 관제</a>` +
    `<a class="${active === 'gate' ? 'on' : ''}" href="/gate${SUFFIX}">계측 이력</a>` +
    `<span class="sub" style="margin-left:auto">마지막 갱신 <b id="upd">-</b> <span title="톨게이트·검문소의 계측·통행료·과태료 결과는 도로공사 연계로 받은 내용입니다. SCARGO는 안내·권고만 하며 단속·차단은 하지 않습니다." style="cursor:help;border:1px solid #9aa6c4;border-radius:99px;padding:0 6px;margin-left:6px">ⓘ</span></span>`;
  const el = document.getElementById('demo');
  if (!el) return;
  if (!QS.get('dev')) { autoChrome(active); return; }   // 26.10.29 기본은 자동 운행 (시연·재생 버튼 없음). 주소 끝에 ?dev=1 을 붙이면 예전 시연 컨트롤
  const full = active === 'map';
  const sc = { A: '정상', B: '톨게이트 확정', C: '검문소 운행 중지', D: '축하중 확정', E: '재측정 후 통과', F: '주의 통과' };
  const DEPARTS = [['now', '지금'], ['14:00', '14:00 낮 (할인 없음)'], ['22:30', '22:30 (청계TG만 50%)'], ['23:00', '23:00 (모두 50%)'], ['04:40', '04:40 (남인천TG만 50%)']];
  let scen = 'A';
  el.style.flexDirection = 'column'; el.style.alignItems = 'stretch';
  el.innerHTML = `<div style="display:flex;flex-wrap:wrap;align-items:center;gap:8px"><b style="font-size:12px;color:#475069">시연</b>` +
    (full ? `<button id="bBatch" class="primary" title="정상 차량 2대 + 우회(이탈) 차량 1대(주의 판정)를 시간차로 출차시키고 자동으로 진행합니다 (과적 없음)">▶ 시연 시작</button>` : '') +
    `<button id="bAuto">▶ 재생</button>` +
    (full ? `<button id="bReset" title="화면만 비웁니다. DB 기록은 남습니다">초기화</button>` +
      `<label class="sub" style="display:flex;align-items:center;gap:4px">속도 <select id="speed" style="font-size:12px;padding:3px 4px"><option value="2000">천천히</option><option value="1200" selected>보통</option><option value="500">빠르게</option></select></label>` +
      `<button id="bAdv" style="margin-left:4px">고급 ▾</button>` : '') +
    `<span id="demoMsg" class="sub" style="margin-left:auto"></span></div>` +
    `<div id="simNotice"></div>` +
    (full ? `<div id="demoAdv" style="display:none;flex-wrap:wrap;align-items:center;gap:8px;border-top:1px dashed #c9cfdf;padding-top:8px">` +
      `<span class="sub">차량 1대 출차:</span>` + Object.entries(sc).map(([k, v]) => `<button class="sc ${k === 'A' ? 'on' : ''}" data-k="${k}">${v}</button>`).join('') +
      `<label class="sub" style="display:flex;align-items:center;gap:4px" title="실제 시스템에서는 배차(운송 계획)의 계획 경로가 자동으로 적용됩니다">경로 <select id="routeSel" style="font-size:12px;padding:3px 4px"><option value="BASIC">마스터 경로 그대로</option><option value="DETOUR">안산 우회 (이탈 시연)</option></select></label>` +
      `<label class="sub" style="display:flex;align-items:center;gap:4px">출발 시각 <select id="depSel" style="font-size:12px;padding:3px 4px">` + DEPARTS.map(([v, l]) => `<option value="${v}">${l}</option>`).join('') + `</select></label>` +
      `<button id="bStart">인천항 출차 (OCR)</button><button id="bNext">선택 차량 다음 지점</button><button id="bAll">전체 다음 지점</button><button id="bStep">선택 차량 한 칸</button><button id="bSkip">선택 차량 이탈 직전으로</button>` +
      `<label class="sub" style="display:flex;align-items:center;gap:4px"><input type="checkbox" id="podChk" checked> 이탈 감지 시 자동 일시정지</label></div>` : '');
  const msg = (t, bad) => { const m = document.getElementById('demoMsg'); m.textContent = t || ''; m.style.color = bad ? '#b42318' : ''; };
  const run = async (fn, busy) => {
    msg(busy || '');
    try { await fn(); msg(''); } catch (e) { msg(e.message, true); }
    opts.refresh();
  };
  const on = (id, fn) => { const x = document.getElementById(id); if (x) x.onclick = fn; };
  const post = u => api(u, { method: 'POST' });
  // 자동 진행은 서버가 시계를 돌린다 (다른 페이지로 옮겨도 멈추지 않음). 화면은 상태만 보여준다.
  on('bAuto', () => run(async () => setSim(await post(window.__simPlaying ? '/api/sim/pause' : '/api/sim/play'))));
  api('/api/sim').then(setSim).catch(() => {});
  if (!full) return;
  on('bAdv', () => { const d = document.getElementById('demoAdv'); d.style.display = d.style.display === 'none' ? 'flex' : 'none'; });
  el.querySelectorAll('.sc').forEach(b => b.onclick = () => {
    scen = b.dataset.k; el.querySelectorAll('.sc').forEach(x => x.classList.toggle('on', x === b));
  });
  on('bStart', () => run(async () => {
    const t = await api('/api/trip/start', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ scenario: scen, dep: document.getElementById('depSel').value, route: document.getElementById('routeSel').value }) });
    if (opts.onStarted) opts.onStarted(t.trip_id);
  }, '출차 번호판 인식 중…'));
  on('bNext', () => run(async () => {
    const pid = opts.selectedPid();
    if (!pid) throw new Error('먼저 차량을 선택하세요.');
    await post(`/api/db/trip/${pid}/next`);
  }));
  on('bAll', () => run(() => post('/api/db/next_all')));
  on('bReset', () => run(() => post('/api/db/reset')));
  on('bStep', () => run(async () => { const pid = opts.selectedPid(); if (!pid) throw new Error('먼저 차량을 선택하세요.'); await post(`/api/watch/${pid}/step?n=1`); }));
  on('bSkip', () => run(async () => { const pid = opts.selectedPid(); if (!pid) throw new Error('먼저 차량을 선택하세요.'); await post(`/api/watch/${pid}/skip`); }));
  document.getElementById('speed').onchange = e => run(async () => setSim(await post('/api/sim/speed?ms=' + e.target.value)));
  document.getElementById('podChk').onchange = e => run(async () => setSim(await post('/api/sim/pause_on_deviate?on=' + (e.target.checked ? 1 : 0))));
  // 시연 시작: 정상 차량 2대(시간차) + 우회 차량 1대 출차 -> 자동 진행. 이전 운행 기록은 지우지 않는다 (지우려면 '초기화' 버튼)
  on('bBatch', () => run(async () => {
    const dep = 'now';
    const go = (scenario, route) => api('/api/trip/start', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ scenario, dep, route }) });
    msg('차량 3대 출차 중…');
    const a = await go('A', 'BASIC'); await post(`/api/watch/${a.trip_id}/step?n=14`);       // 앞서 가는 정상 차량 (시연에서는 과적을 보여주지 않으므로 3대 모두 정상 시나리오)
    const b = await go('A', 'BASIC'); await post(`/api/watch/${b.trip_id}/step?n=7`);        // 뒤따르는 정상 차량
    const c = await go('F', 'DETOUR');                                                       // 우회(이탈) 차량: 주의 판정(기준 초과, 허용 오차 이내, 벌금 없음). 이탈 중 검문소 접근 시 전방 안내가 나온다
    if (opts.onStarted) opts.onStarted(c.trip_id);
    setSim(await post('/api/sim/play'));
  }, '차량 3대 출차 중…'));
}
// ── 26.10.29 자동 운행 표시: 시연 시작 · 재생 · 속도 버튼 없이 '공용 시계 + 자동 운행 중' 만 보여 준다 ──
//  공용 시계 = 택배 이동 · 물류센터 관제와 같은 하루 시계(sbot_clock). 이 화면은 다른 주소(8002)라 저장소를 같이 못 쓰므로
//  S카고 웹 화면(부모 창)이 1초마다 postMessage({type:'sbot-clock', tod, rate}) 로 보내 준다. 부모가 없으면(직접 열었을 때) PC 시각.
//  이 시각을 서버(/api/clock)에도 알려 새 차량의 출차 시각 · 심야할인 판정이 택배 이동과 같은 시각을 따른다.
function todText(t) { const h = Math.floor(t) % 24, m = Math.floor((t % 1) * 60); return `${p2(h)}:${p2(m)} · ${h >= 6 && h < 18 ? '낮' : '밤'}`; }
// 자동 운행 표시줄: 서버가 알려 준 상태를 그대로 보여 준다 (원인을 알 수 없는 '준비 중' 같은 말은 쓰지 않는다)
function paintAuto() {
  const ac = document.getElementById('autoChip'), am = document.getElementById('autoMsg'); if (!ac) return;
  const A = window.__auto || {}, playing = !!window.__simPlaying;
  let t, bg = '#e3f6ec', fg = '#12814a', m = '';
  if (A.ok === false) {
    t = A.old ? '⚠ 서버 재시작 필요' : '⚠ 서버 연결 실패'; bg = '#fde8e6'; fg = '#b42318';
    m = A.old ? '구간 관제 서버가 예전 버전입니다. run_toll.bat 창을 닫고 다시 실행해 주세요.' : '구간 관제 서버(8002)에 연결할 수 없습니다. run_toll.bat 이 켜져 있는지 확인해 주세요.';
  } else if (A.state === 'model_loading') { t = '● 번호판 인식 모델 불러오는 중'; bg = '#fff0d0'; fg = '#8a4b00'; m = '10~30초 걸립니다. 끝나면 차량이 자동으로 출차합니다.'; }
  else if (A.state === 'model_error' || A.state === 'no_db') { t = '⚠ 자동 운행 불가'; bg = '#fde8e6'; fg = '#b42318'; m = A.state === 'no_db' ? 'DB 주소(db_url.txt)가 비어 있습니다.' : '번호판 인식 모델 오류: ' + (A.err || ''); }
  else if (A.state === 'off') { t = '○ 자동 운행 꺼짐'; bg = '#eceff5'; fg = '#475069'; m = '서버에서 자동 운행을 껐습니다 (TOLL_AUTO=0).'; }
  else { t = playing ? '● 자동 운행 중' : '● 첫 차량 출차 중'; if (A.err) { bg = '#fff0d0'; fg = '#8a4b00'; m = '출차 재시도 중: ' + A.err; } }
  ac.textContent = t; ac.style.background = bg; ac.style.color = fg; if (am) am.textContent = m;
}
function autoChrome(active) {
  const el = document.getElementById('demo');
  el.style.cssText = 'display:flex;flex-wrap:wrap;align-items:center;gap:8px;border:0;padding:0;background:transparent';
  el.innerHTML = `<span style="background:#0e1726;color:#ffb830;border-radius:20px;padding:6px 14px;font-size:14px;font-weight:700">🕗 시각 <span id="clkT">--:--</span> <span style="font-weight:400;font-size:12px;color:#d9dfef">택배 이동 · 물류센터 관제와 같은 시계</span></span>` +
    `<span id="autoChip" style="border-radius:20px;padding:6px 14px;font-size:13px;font-weight:700;background:#eceff5;color:#475069">… 서버 확인 중</span>` +
    `<span id="autoMsg" class="sub"></span><div id="simNotice" style="flex-basis:100%"></div>`;
  let base = null;                                                    // {tod, at, rate}: 부모가 보낸 공용 시계
  const cur = () => base ? (base.tod + (Date.now() - base.at) / 1000 * base.rate) % 24 : (() => { const d = new Date(); return d.getHours() + d.getMinutes() / 60 + d.getSeconds() / 3600; })();
  const paint = () => { const t = document.getElementById('clkT'); if (t) t.textContent = todText(cur()); };
  window.addEventListener('message', e => { const d = e.data; if (d && d.type === 'sbot-clock' && typeof d.tod === 'number') { base = { tod: d.tod, at: Date.now(), rate: typeof d.rate === 'number' ? d.rate : 1 / 3600 }; paint(); } });
  const ping = async () => {
    try {
      const r = await api('/api/clock', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ tod: cur(), rate: base ? base.rate : 1 / 3600 }) });
      window.__auto = { ok: true, state: r && r.state, live: r && r.live, err: r && r.err };
    } catch (e) {
      // 404 = 서버가 자동 운행 기능이 없는 예전 버전(재시작 전). 그 밖에는 연결 실패
      window.__auto = { ok: false, old: /404/.test(String(e.message)), msg: String(e.message) };
    }
    paintAuto();
  };
  paint(); ping();
  setInterval(paint, 1000); setInterval(ping, 5000);
  try { if (parent !== window) { parent.postMessage({ type: 'toll-page', page: active }, '*'); parent.postMessage({ type: 'toll-ready' }, '*'); } } catch (e) { /* 부모 없음 */ }
  api('/api/sim').then(setSim).catch(() => {});
  // 부모(W12)가 iframe 높이를 내용에 맞추도록 높이를 알려 줌 → 잘림 없이 스크롤바는 바깥 화면 하나만
  if (parent !== window) { let lh = 0; const sh = () => { const h = Math.ceil(document.querySelector('main').getBoundingClientRect().bottom + window.scrollY) + 4; if (h !== lh) { lh = h; try { parent.postMessage({ type: 'toll-height', h }, '*'); } catch (e) { /* 무시 */ } } }; setInterval(sh, 400); sh(); }
}

// 서버 시뮬레이션 상태를 버튼과 이탈 안내에 반영 (각 화면이 overview 의 sim 값을 넘겨준다)
function setSim(s) {
  if (!s) return;
  window.__simPlaying = s.playing;
  if (document.getElementById('autoChip')) paintAuto();
  const b = document.getElementById('bAuto');
  if (b) { b.classList.toggle('on', s.playing); b.textContent = s.playing ? '⏸ 일시정지' : '▶ 재생'; }
  const pc = document.getElementById('podChk'); if (pc) pc.checked = !!s.pause_on_dev;
  const n = document.getElementById('simNotice');
  if (n) {
    const key = s.notice ? s.notice.trip_id : '';
    if (n.dataset.k !== String(key) || (!s.notice && n.innerHTML)) {
      n.dataset.k = String(key);
      const here = location.pathname.startsWith('/checkpoint');
      n.innerHTML = s.notice ? `<div style="border:1px solid #e5483b;background:#fdf1f0;border-radius:8px;padding:8px 12px;font-size:13px;color:#8a1a10;display:flex;flex-wrap:wrap;gap:10px;align-items:center"><b>⚠ 시연 일시정지</b><span>${esc(s.notice.text)}</span>` +
        `${here ? '' : `<a href="/checkpoint${SUFFIX}">차량 관제에서 보기</a>`}<button id="bResume" style="margin-left:auto;padding:5px 14px;border:1px solid #13203f;border-radius:6px;background:#13203f;color:#fff;font-weight:700;cursor:pointer;font-family:inherit">▶ 재개</button></div>` : '';
      const r = document.getElementById('bResume');
      if (r) r.onclick = async () => { try { setSim(await api('/api/sim/play', { method: 'POST' })); } catch (e) {} };
    }
  }
}
function stamp() { const u = document.getElementById('upd'); if (u) u.textContent = hms(new Date().toISOString()); }

const BASE_CSS = `
html.embed,html.embed body{overflow:hidden}
html.embed #nav{display:none}
html.embed main>h1{display:none}
html.embed main{padding:6px 14px 14px;gap:10px}
body{margin:0;font-family:'Pretendard','Malgun Gothic','Apple SD Gothic Neo',system-ui,sans-serif;color:#1c2540;background:#f4f5f9}
#nav{display:flex;align-items:center;gap:6px;background:#13203f;padding:0 20px;height:46px;color:#fff}
#nav .brand{font-weight:800;font-size:18px;margin-right:18px}
#nav a{color:#e6eaf5;text-decoration:none;font-size:14px;padding:6px 14px;border-radius:8px}
#nav a.on{background:#f5a300;color:#14213d;font-weight:700}
#nav .sub{color:#9aa6c4}#nav b{color:#fff}
main{padding:16px 24px 24px;display:flex;flex-direction:column;gap:14px;max-width:1500px;margin:0 auto}
.card{background:#fff;border-radius:12px;padding:16px;box-shadow:0 1px 3px rgba(20,33,61,.08);min-width:0}
.card h3{margin:0 0 10px;font-size:14px}
.sub{color:#5b6479;font-size:12px}
.pill{display:inline-block;padding:2px 9px;border-radius:99px;font-size:12px;font-weight:600;white-space:nowrap}
.red{background:#fde8e6;color:#b42318}.grn{background:#def5e7;color:#0b6b3a}.org{background:#fff0d0;color:#8a4b00}.gry{background:#eceff5;color:#475069}
.kv{display:grid;grid-template-columns:108px 1fr;gap:6px 8px;font-size:13px;margin:0}.kv dt{color:#5b6479}.kv dd{margin:0}
.plate{display:inline-block;border:2px solid #1c2540;border-radius:6px;padding:2px 10px;font-weight:800;background:#fff;letter-spacing:1px}
.tab:disabled{opacity:.5}
input[type=date]{font:inherit;font-size:13px;padding:4px 8px;border:1px solid #cfd5e4;border-radius:8px;background:#fff;color:#13203f}
.tab{border:0;cursor:pointer;font-family:inherit;font-size:13px;padding:6px 16px;border-radius:99px;font-weight:600;background:#eceff5;color:#475069}
.tab.on{background:#13203f;color:#fff}
#demo{display:flex;flex-wrap:wrap;align-items:center;gap:8px;border:1px dashed #9aa6c4;border-radius:10px;padding:8px 12px;background:#fff}
#demo button{padding:6px 12px;border:1px solid #9aa6c4;border-radius:6px;background:#fff;color:#1c2540;font-size:12px;cursor:pointer;font-family:inherit}
#demo button.primary,#demo button.on{background:#13203f;color:#fff;border-color:#13203f;font-weight:700}
#demo button.sc{border-radius:99px;padding:3px 10px}
.bars{position:relative;display:flex;align-items:flex-end;gap:10px;height:84px;border-bottom:1px solid #c9cfdf;padding:0 6px}
.bars .lim{position:absolute;left:0;right:0;border-top:2px dashed #b42318}
.bars i{flex:1;background:#6b7ba8;border-radius:4px 4px 0 0;display:block}.bars i.bad{background:#b42318}
.barlab{display:flex;gap:10px;padding:2px 6px;font-size:11px;color:#5b6479}.barlab span{flex:1;text-align:center}
.row{display:grid;align-items:center;gap:8px;padding:7px 10px;border-bottom:1px solid #eceff5;font-size:12px;width:100%;box-sizing:border-box;border-left:0;border-right:0;border-top:0;font-family:inherit;color:inherit;text-align:left;background:#fff}
.row.h{background:#f4f5f9;font-weight:600;color:#475069;position:sticky;top:0;z-index:1}
button.row{cursor:pointer}button.row.sel{background:#fff7e0}
.scroll{overflow-y:auto;overflow-x:hidden;border:1px solid #eceff5;border-radius:8px}
.empty{padding:28px;text-align:center;color:#5b6479;font-size:13px}
img.crop{max-width:100%;height:46px;object-fit:contain;border:1px solid #c9cfdf;border-radius:4px;background:#fff}
`;
document.head.insertAdjacentHTML('beforeend', `<style>${BASE_CSS}</style>`);

// 요금 근거 표시: OFFICIAL 공시값 / ESTIMATE 추정값 (톨게이트는 우리가 운영하지 않으므로 추정값은 반드시 '추정'으로 표시)
const basisPill = b => b === 'OFFICIAL' ? pill('grn', '공시') : pill('org', '산정');

// 예상 통행료 문구: 요금 구간(남인천TG / 청계TG, 둘 다 개방식)마다 심야할인을 따로 판정한 결과를 한 줄로
const SEC_SHORT = { NAMINCHEON_TG: '남인천', CHEONGGYE_TG: '청계' };
function discText(q) {
  if (q.night_excluded) return `심야할인 제외: 1년 이내 과태료 ${q.fined_trips_1y}회`;
  const ls = q.lines || [], ps = [...new Set(ls.map(l => l.discount_pct))];
  if (!ls.length || (ps.length === 1 && ps[0] === 0)) return '할인 없음';
  if (ps.length === 1) return `심야할인 ${ps[0]}%`;
  return '심야할인 ' + ls.map(l => `${SEC_SHORT[l.section] || l.section} ${l.discount_pct}%`).join(' · ');
}
function quoteText(q) {
  return `예상 통행료 ${won(q.expected_total)} (기준 ${won(q.base_total)}, ${discText(q)})${q.has_estimate ? ' · 산정 요금 포함' : ''}`;
}
const hm = s => String(s || '').slice(11, 16);

// 조회 기간: 시작일 ~ 종료일을 직접 고른다 (기본 오늘). 선택값은 탭을 닫기 전까지 화면 사이에서 유지된다
const ymd = d => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
function getRange() {
  const t = ymd(new Date());
  try { const r = JSON.parse(sessionStorage.getItem('tollRange2')); if (r && r.from) return r; } catch (e) {}
  const d = new Date(); d.setDate(d.getDate() - 6);          // 처음 열면 최근 7일 (누적 기록이 바로 보이도록)
  return { from: ymd(d), to: t };
}
function setRange(r) { try { sessionStorage.setItem('tollRange2', JSON.stringify(r)); } catch (e) {} }
function rangeQuery() { const r = getRange(); return 'from=' + encodeURIComponent(r.from) + '&to=' + encodeURIComponent(r.to || ''); }
function mountRange(el, onChange) {
  const draw = () => {
    const r = getRange(), all = r.from === 'all', t = ymd(new Date());
    el.innerHTML = `<span class="sub">기간</span> <input type="date" id="rgF" value="${all ? '' : r.from}" max="${t}"> ~ <input type="date" id="rgT" value="${all ? '' : (r.to || '')}" max="${t}">
      <button class="tab ${r.from === t && r.to === t ? 'on' : ''}" id="rgToday">오늘</button><button class="tab ${all ? 'on' : ''}" id="rgAll">전체</button>`;
    const apply = () => {
      let f = el.querySelector('#rgF').value, to = el.querySelector('#rgT').value;
      if (!f) return;
      if (to && to < f) to = f;
      setRange({ from: f, to: to || '' }); draw(); onChange();
    };
    el.querySelector('#rgF').onchange = apply; el.querySelector('#rgT').onchange = apply;
    el.querySelector('#rgToday').onclick = () => { setRange({ from: t, to: t }); draw(); onChange(); };
    el.querySelector('#rgAll').onclick = () => { setRange({ from: 'all', to: '' }); draw(); onChange(); };
  };
  draw();
}
