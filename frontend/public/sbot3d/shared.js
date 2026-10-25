/* S-Bot 화면 공용 데이터 (대시보드 · 택배 이동 · 물류센터가 같은 값을 본다)
   - 택배 이동 화면에서 택배차가 센터에 도착하면 arrive() 로 기록하고, 물류센터 화면과 대시보드가 읽는다.
   - 지금은 브라우저 localStorage(같은 주소끼리 공유). DB API 가 생기면 load()/arrive() 안쪽만 서버 호출로 바꾸면 된다.
   - 하루가 바뀌면 기준값으로 다시 시작한다. */
(function () {
  var KEY = 'sbot_shared_v3'; // 26.10.25 v3: 예전(v2)에 일괄 반값으로 쌓인 통행료 합계를 버리고 새로 시작
  var NAME = { GGI: '경기', SEL: '서울', ICN: '인천' };
  var BASE = { GGI: 3720, SEL: 4530, ICN: 6310 }; // 1회 기본 요금 = 재정 + 민자 (물류이동 화면과 같은 값)
  // 26.10.25 심야할인은 통과한 시각에만: 21~24시 30% · 0~6시 50%, 낮에는 할인 없음 (예전에는 모든 차량에 50%를 일괄 적용했음)
  //  기준 시각은 컴퓨터 시계가 아니라 3D 화면의 하루 시계(sbot_clock). 택배 이동 · 물류센터 화면이 같은 시각을 따른다
  function clockTod() {
    try { var c = JSON.parse(localStorage.getItem('sbot_clock') || 'null'); if (c && typeof c.tod === 'number' && typeof c.rate === 'number') return (((c.tod + (Date.now() - c.at) / 1000 * c.rate) % 24) + 24) % 24; } catch (e) { /* 실제 시각으로 */ }
    var d = new Date(); return d.getHours() + d.getMinutes() / 60;
  }
  // 26.10.25 시간대별 운행량: 택배 간선 운송은 심야에 몰린다 (22~05시 3배 · 05~09시 1.5배 · 낮 0.7배 · 저녁 1.2배). 출발 간격을 이 값으로 나눈다
  function flow() { var h = clockTod(); return (h >= 22 || h < 5) ? 3 : h < 9 ? 1.5 : h < 18 ? .7 : 1.2; }
  function rateNow() { var h = clockTod(); return h >= 21 ? .3 : h < 6 ? .5 : 0; }
  // 센터별 심야할인 누적(오늘): 서버(DB)는 실제 시각만 알아서, 화면 시계 기준 할인액은 여기에 쌓는다
  var DK = KEY + '_disc';
  function discGet() { var d = null; try { d = JSON.parse(localStorage.getItem(DK) || 'null'); } catch (e) { /* 새로 */ } if (!d || d.day !== day()) d = { day: day() }; ['GGI', 'SEL', 'ICN'].forEach(function (k) { if (!d[k] || typeof d[k] !== 'object') d[k] = { n: 0, b: 0, d: 0 }; }); return d; }
  // 도착 1건 기록: 할인이 붙은(심야에 통과한) 건만 횟수 · 기본 요금 · 할인액을 센터별로 쌓는다
  function rec(k, base, disc) { if (!(disc > 0)) return; try { var d = discGet(); d[k].n++; d[k].b += Math.round(base); d[k].d += Math.round(disc); localStorage.setItem(DK, JSON.stringify(d)); } catch (e) { /* 무시 */ } }
  function discReset() { try { localStorage.removeItem(DK); } catch (e) { /* 무시 */ } }
  function day() { var d = new Date(); return d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate(); }
  // DB 대응: key = delivery_regions.code · no = trucks 차량번호 · status = shipment_legs.status(HUB_TO_CENTER) · items = hub_dispatches.parcel_count
  // trips = 센터 도착(ARRIVED) 건수 · base/fin = 통행료 기본/심야할인 후 확정(toll_payments.amount 합계)
  function truck(no, st, n) { return { no: no, state: st, status: st === '입고 완료' ? 'ARRIVED' : 'IN_TRANSIT', items: n }; }
  function seed() {
    var s = { day: day(), centers: {
      GGI: { trips: 6, items: 46, base: BASE.GGI * 6, fin: BASE.GGI * 6, hr: [2, 4, 7, 9, 9, 8, 4, 3], cat: [19, 13, 9, 5], rot: 0,
        trucks: [truck('11가 1001', '입고 완료', 18), truck('11가 1002', '이동 중', 16), truck('11가 1003', '이동 중', 12)] },
      SEL: { trips: 6, items: 30, base: BASE.SEL * 6, fin: BASE.SEL * 6, hr: [1, 2, 4, 6, 7, 5, 3, 2], cat: [12, 9, 6, 3], rot: 0,
        trucks: [truck('12가 3456', '입고 완료', 12), truck('34나 7890', '입고 완료', 10), truck('56다 1234', '이동 중', 8)] },
      ICN: { trips: 4, items: 52, base: BASE.ICN * 4, fin: BASE.ICN * 4, hr: [2, 4, 7, 9, 10, 9, 6, 5], cat: [21, 15, 10, 6], rot: 0,
        trucks: [truck('78라 2001', '입고 완료', 30), truck('90마 2002', '이동 중', 22)] }
    } };
    s.at = Date.now(); return s;
  }
  function save(s) { s.at = Date.now(); try { localStorage.setItem(KEY, JSON.stringify(s)); } catch (e) { /* 저장 불가 환경은 무시 */ } }
  function loadRaw() {
    try { var s = JSON.parse(localStorage.getItem(KEY)); if (s && s.day === day() && s.centers) return s; } catch (e) { /* 새로 만든다 */ }
    var n = seed(); save(n); return n;
  }
  // 센터마다 심야에 통과한 횟수(nn) · 그 기본 요금 합(nb) · 할인액 합(nd)을 붙여 돌려준다 → 화면이 주간 / 심야 금액을 나눠 보여 준다
  function load() {
    var s = loadRaw(), d = discGet();
    Object.keys(s.centers).forEach(function (k) { var c = s.centers[k], x = d[k]; if (c && x) { c.nn = x.n; c.nb = x.b; c.nd = x.d; } });
    return s;
  }
  // 택배차 1대가 센터에 도착: 입고 물품 수 · 기본 요금 · 심야할인 후 확정 요금을 그 센터에 더한다. 도착한 택배차 번호를 돌려준다
  function arriveOn(s, key, info) {
    var c = s.centers[key]; if (!c) return null;
    var items = info.items || 0, h = clockTod(), b = Math.max(0, Math.min(7, Math.floor((h - 6) / 2))); // 시간대 칸도 3D 하루 시계 기준 (통행료와 같은 시각)
    var part = [Math.round(items * .4), Math.round(items * .3), Math.round(items * .2)]; part.push(Math.max(0, items - part[0] - part[1] - part[2]));
    var b0 = info.base || BASE[key], f0 = info.fin != null ? info.fin : Math.round(b0 * (1 - rateNow()));
    rec(key, b0, b0 - f0);
    c.trips++; c.items += items; c.base += b0; c.fin += f0; c.hr[b] += items;
    for (var i = 0; i < 4; i++) c.cat[i] += part[i];
    var t = c.trucks[c.rot % c.trucks.length]; c.rot++; t.state = '입고 완료'; t.status = 'ARRIVED'; t.items = items;
    return t;
  }
  var API_OK = false; // 백엔드(/api/amr/sbot) 와 통신이 되는 동안 true
  function post(path, body) {
    return fetch(apiBase() + '/api/amr/sbot/' + path, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
      .then(function (r) { return r.ok ? r.json() : null; }).catch(function () { return null; });
  }
  // 택배차 출발: 허브 출고 + 허브→센터 운송 구간(운송 중)을 DB 에 만든다. 성공하면 {assignmentId, vehicleNo}, 서버 불가면 null(시뮬레이션으로 계속)
  function dispatch(key, items, cb) {
    if (!API_OK) { cb(null); return; }
    post('dispatch', { region: key, items: items }).then(function (j) { if (j) setTimeout(sync, 300); cb(j); });
  }
  function arrive(key, info) {
    if (info && info.asg) { rec(key, info.base != null ? info.base : BASE[key], info.base != null && info.fin != null ? info.base - info.fin : BASE[key] * rateNow()); post('arrive', { assignmentId: info.asg }).then(function () { setTimeout(sync, 300); }); return info.no || null; } // DB 에 도착 기록
    var s = load();
    if (s.src === 'db') { var c0 = s.centers[key]; return c0 && c0.trucks && c0.trucks.length ? c0.trucks[0].no : null; } // DB 모드: 값은 서버가 정한다 (3D 화면은 보여주기만)
    var t = arriveOn(s, key, info); save(s); return t ? t.no : null;
  }
  function depart(key, no) {
    var s = load(), c = s.centers[key]; if (!c || s.src === 'db') return;
    c.trucks.forEach(function (t) { if (t.no === no) { t.state = '이동 중'; t.status = 'IN_TRANSIT'; } });
    save(s);
  }
  // ── 자동 진행: 택배 이동 화면이 열려 있지 않아도(대시보드·물류센터만 열어도) 택배차 도착이 계속 기록된다 ──
  //  택배 이동 화면이 열려 있으면(live() 호출) 그 화면의 실제 도착만 기록하고 여기서는 멈춘다. 여러 화면이 동시에 돌려도 한 곳만 진행(lease)
  var ID = Math.random().toString(36).slice(2), GAP = { GGI: [40, 70], SEL: [30, 55], ICN: [50, 90] }, MAXTRIPS = 40, HOLD = 8000;
  function rnd(a, b) { return a + Math.random() * (b - a); }
  function live() { try { localStorage.setItem(KEY + '_move', String(Date.now())); } catch (e) { /* 무시 */ } }
  // DB 운행 자동 진행: 권역마다 '출발(dispatch) → 6~12초 뒤 도착(arrive)'을 반복한다. 한 화면(lease 를 가진 쪽)만 실행
  var DBSIM = {}, DBGAP = { GGI: [1, 3], SEL: [1, 3], ICN: [2, 4] }; // 시연용: 이동 12~20초, 도착 후 1~4초 만에 다음 출발(운송 중이 0대로 비지 않게)
  function fastSp() { try { var n = parseInt(localStorage.getItem('sbot_speed'), 10); if (n >= 1 && n <= 16) return n; return localStorage.getItem('sbot_fast') === '1' ? 4 : 1; } catch (e) { return 1; } } // 배속: 대시보드 배속 버튼이 sbot_speed(1·2·4·8)를 저장한다 (예전 sbot_fast='1' 은 4배)
  function dbTick(now) {
    var sp = fastSp();
    ['GGI', 'SEL', 'ICN'].forEach(function (k) {
      var q = DBSIM[k] || (DBSIM[k] = { next: 0 });
      if (q.asg && now >= q.arrAt) {
        rec(k, BASE[k], BASE[k] * rateNow()); post('arrive', { assignmentId: q.asg }).then(function () { setTimeout(sync, 300); });
        q.asg = null; q.next = now + rnd(DBGAP[k][0], DBGAP[k][1]) * 1000 / sp / flow();
      } else if (!q.asg && !q.busy && now >= q.next) {
        q.busy = true;
        post('dispatch', { region: k, items: 4 + Math.floor(Math.random() * 9) }).then(function (j) {
          q.busy = false;
          if (j) { q.asg = j.assignmentId; q.arrAt = Date.now() + rnd(12, 20) * 1000 / sp; setTimeout(sync, 300); }
          else q.next = Date.now() + 3000; // 출발 실패(서버 응답 없음/차량 부족)여도 3초 뒤 다시 시도
        });
      }
    });
  }
  function auto() { try { return localStorage.getItem(KEY + '_auto') !== '0'; } catch (e) { return true; } }
  function setAuto(v) { try { localStorage.setItem(KEY + '_auto', v ? '1' : '0'); } catch (e) { /* 무시 */ } }
  function tick() {
    try {
      if (!auto()) return; // 자동 운행 끔: 수동 조작만 반영
      var now = Date.now();
      if (now - (+localStorage.getItem(KEY + '_move') || 0) < 3000) return;
      var L = JSON.parse(localStorage.getItem(KEY + '_lease') || 'null');
      if (L && L.id !== ID && now - L.at < 5000) return;
      localStorage.setItem(KEY + '_lease', JSON.stringify({ id: ID, at: now }));
      if (API_OK) { dbTick(now); return; } // 서버와 통신되면 DB 에 직접 운행 기록(택배 이동 화면이 안 열려 있어도 값이 계속 바뀐다)
      var s = load();
      if (s.src === 'db') return; // DB 에 오늘 운송 기록이 있으면 시뮬레이션은 멈춘다
      Object.keys(s.centers).forEach(function (k) {
        var c = s.centers[k];
        c.trucks.forEach(function (t) { if (t.until && now >= t.until) { t.state = '이동 중'; t.status = 'IN_TRANSIT'; delete t.until; } });
        if (!c.next) c.next = now + rnd(GAP[k][0], GAP[k][1]) * 1000 / flow();
        if (now >= c.next && c.trips < MAXTRIPS) { // 하루 상한: 숫자가 끝없이 커지지 않게
          var t = arriveOn(s, k, { items: 4 + Math.floor(Math.random() * 9), base: BASE[k], fin: Math.round(BASE[k] * (1 - rateNow())) });
          if (t) t.until = now + HOLD;
          c.next = now + rnd(GAP[k][0], GAP[k][1]) * 1000 / flow();
        }
      });
      save(s);
    } catch (e) { /* 저장 불가 환경은 무시 */ }
  }
  // ── DB 연동: GET /api/amr/sbot/overview (parcels → hub_dispatches → shipment_legs → toll_payments, 권역별 오늘 합계) ──
  //  오늘 운송 기록이 있으면 그 값으로 덮어쓰고(src='db'), 서버가 꺼져 있거나 기록이 없으면 위 시뮬레이션이 계속 돈다.
  //  심야할인은 DB 에 없어서 결제 합계(tollAmount)의 50% 를 확정 통행료로 계산한다.
  function apiBase() {
    try { var b = localStorage.getItem('scargo_amr_api'); if (b) return b; } catch (e) { /* 무시 */ }
    var h = location.hostname;
    return /^\d+\.\d+\.\d+\.\d+$/.test(h) && h !== '127.0.0.1' ? 'http://' + h + ':8080' : 'http://localhost:8080';
  }
  // 수동 조작: 대시보드 버튼에서 호출. 서버 통신이 되면 DB 에 기록, 아니면 시뮬레이션 값에 반영
  function dispatchNow(key, items) {
    items = items || 4 + Math.floor(Math.random() * 9);
    if (API_OK) return post('dispatch', { region: key, items: items }).then(function (j) { sync(); return j; });
    arrive(key, { items: items, base: BASE[key], fin: Math.round(BASE[key] * (1 - rateNow())) }); return Promise.resolve(null);
  }
  function arriveOne(key) {
    var s = load(), c = s.centers[key], t = c && c.trucks.filter(function (x) { return x.status !== 'ARRIVED' && x.assignmentId; })[0];
    if (t && API_OK) { rec(key, BASE[key], BASE[key] * rateNow()); return post('arrive', { assignmentId: t.assignmentId }).then(function (j) { sync(); return j; }); }
    return Promise.resolve(null);
  }
  // 26.10.25 시연 모드: 시연을 시작한 시각 이후의 기록만 가져온다 (DB 는 지우지 않는다). 시연이 끝나면 지워진다
  function demoSince() { try { var v = parseInt(localStorage.getItem('sbot_demo_since'), 10); return v > 0 ? '?since=' + v : ''; } catch (e) { return ''; } }
  function sync() {
    var ctl = new AbortController(), t = setTimeout(function () { ctl.abort(); }, 4000);
    fetch(apiBase() + '/api/amr/sbot/overview' + demoSince(), { credentials: 'include', signal: ctl.signal })
      .then(function (r) { clearTimeout(t); API_OK = r.ok; return r.ok ? r.json() : null; })
      .then(function (j) {
        if (!j || !j.hasData) { var s0 = load(); if (s0.src === 'db') { try { localStorage.removeItem(KEY); } catch (e) { /* 무시 */ } } return; }
        var out = { day: day(), src: 'db', centers: {}, hub: j.hub || {}, amr: j.amr || [] };
        Object.keys(j.centers).forEach(function (k) {
          var c = j.centers[k];
          if (c.cat && c.cat.reduce(function (x, y) { return x + y; }, 0) === 0 && c.items > 0) { // parcels 품목 기록이 없으면 물품 구성은 기본 비율(40/30/20/10)로 추정
            var p = [Math.round(c.items * .4), Math.round(c.items * .3), Math.round(c.items * .2)]; p.push(Math.max(0, c.items - p[0] - p[1] - p[2])); c.cat = p;
          }
          var base = Math.max(c.tollAmount || 0, BASE[k] * c.trips); // 결제 기록(toll_payments)이 있으면 그 합계, 이전 기록처럼 결제가 없는 도착 건은 기본 요금표로 보충
          out.centers[k] = { trips: c.trips, transit: c.transit, items: c.items, base: base, fin: base - Math.min(base, discGet()[k].d), hr: c.hr, cat: c.cat, trucks: c.trucks, rot: 0 };
        });
        save(out);
      })
      .catch(function () { clearTimeout(t); API_OK = false; });
  }
  setInterval(sync, 2000); sync();
  setInterval(tick, 2000); tick();
  window.SBOT = { apiBase: apiBase, KEY: KEY, NAME: NAME, load: load, arrive: arrive, depart: depart, live: live, sync: sync, dispatch: dispatch, dispatchNow: dispatchNow, arriveOne: arriveOne, discReset: discReset, rateNow: rateNow, flow: flow, clockTod: clockTod, auto: auto, setAuto: setAuto };
})();
