/* 기획 예시 화면(SCARGO 웹 예시 화면.html)의 권한표·목업·규칙·화면 템플릿을 모듈로 옮김.
   MOCK 은 api.js 에서 서버 응답으로 덮어쓴다. */

import { apiBase } from './base' // fix_1600

/* =========================================================
   1. 권한표 (기획서 v2 W-xx × 권한)
   P = 처리, V = 조회, D = 소속 계통만 처리, '' = 숨김 + API 403
   서버에서도 같은 표로 @PreAuthorize 검사를 해야 한다
   ========================================================= */
// 26.10.20 권한 확정(기획서 v2 p.20): ADMIN · CONTROL · HUB_OP · CENTER_OP + CARRIER(운송사 담당자 = 역할, 권한 값 아님)
const MENU = [
  { group: '공통 컨트롤타워', items: [
    ['W-02', '통합 대시보드', 'dashboard', { ADMIN: 'P', CONTROL: 'P', HUB_OP: 'D', CENTER_OP: 'D', CARRIER: 'D' }],
    ['W-09', '이상 상황 관리', 'exceptions', { ADMIN: 'V', CONTROL: 'P', HUB_OP: 'D', CENTER_OP: 'D', CARRIER: 'D' }],
  ]},
  { group: '항만 → 허브', items: [
    ['W-12', '톨게이트 · 검문소', 'toll', { ADMIN: 'V', CONTROL: 'P' }],
  ]},
  { group: '허브 → 물류센터', items: [
    ['W-04', '허브 관제', 'hub', { ADMIN: 'V', CONTROL: 'V', HUB_OP: 'P' }],
    ['W-05', '물류센터 관제', 'center', { ADMIN: 'V', CONTROL: 'V', CENTER_OP: 'P' }],
  ]},
];
const PERM_LABEL = { P: '처리', V: '조회', D: '소속' };
const FIRST = { ADMIN: 'dashboard', CONTROL: 'dashboard', CARRIER: 'dashboard', HUB_OP: 'hub', CENTER_OP: 'center' };
const permOf = (route, role) => { for (const g of MENU) for (const it of g.items) if (it[2] === route) return it[3][role] || ''; return ''; };

/* =========================================================
   2. 목업 데이터 = API 응답 모양
   예) GET /api/web/hubs/1/monitor 가 MOCK.hub 를 돌려준다고 생각하면 된다
   ========================================================= */
const MOCK = {
  sites: [ { id: 1, type: 'HUB', name: '곤지암 허브' }, { id: 11, type: 'CENTER', code: 'INCHEON', name: '인천 센터' }, { id: 12, type: 'CENTER', code: 'SEOUL', name: '서울 센터' }, { id: 13, type: 'CENTER', code: 'GYEONGGI', name: '경기 센터' } ],
  // 차량별 현재 지연(분). 상태 색 계산에 쓴다
  delays: [ { siteId: 1, vehicleNo: '인천80사5678', delayMin: 35 }, { siteId: 12, vehicleNo: '경기82자1001', delayMin: 10 } ],
  exceptions: [
    { id: 901, type: 'SEAL_MISMATCH', label: '씰 불일치 (개봉 보류)', severity: 'CRITICAL', siteId: 1, target: 'MSCU4788258 · D3', at: '09:42', status: 'OPEN', owner: null },
    { id: 902, type: 'SORT_FAILED', label: '박스 ≠ 롤테이너 목적지', severity: 'CRITICAL', siteId: 1, target: 'BX-0418 → RT-GG-07', at: '09:51', status: 'IN_PROGRESS', owner: '김허브' },
    { id: 903, type: 'OTHER', label: '도착 지연 30분+', severity: 'WARNING', siteId: 1, target: 'TO-261020-005 (+35분)', at: '09:20', status: 'OPEN', owner: null },
    { id: 904, type: 'OVERLOAD', label: '톨게이트 과적 (과태료)', severity: 'WARNING', siteId: null, target: '인천81아2211 · 톨게이트 입구', at: '08:58', status: 'RESOLVED', owner: '박항만' },
    { id: 905, type: 'ITEM_DAMAGED', label: '센터 부족·파손', severity: 'WARNING', siteId: 12, target: 'LH-261020-02 (박스 1 파손)', at: '09:55', status: 'OPEN', owner: null },
  ],
  hub: {
    inbound: [
      { orderId: 'TO-261020-004', vehicleNo: '인천80사1234', containerNo: 'SCGU1234565', eta: '도착', delayMin: 0, arrived: true, boxes: 240 },
      { orderId: 'TO-261020-005', vehicleNo: '인천80사5678', containerNo: 'APLU2251273', eta: '10:35', delayMin: 35, arrived: false, boxes: 120 },
      { orderId: 'TO-261020-006', vehicleNo: '인천81아2211', containerNo: 'HJSU4451408', eta: '11:10', delayMin: 0, arrived: false, boxes: 260 },
    ],
    // seal: SEAL_PENDING | SEAL_RECEIVED, openHold/reclassRequired 는 자동 해제하지 않는다
    docks: [
      { no: 'D1', kind: 'IN', status: 'UNLOADING', containerNo: 'MSCU1998688', seal: { result: 'MATCH', receipt: 'SEAL_RECEIVED', openHold: false }, progress: [180, 260] },
      { no: 'D2', kind: 'IN', status: 'UNLOADING', containerNo: 'CMAU5986467', seal: { result: 'MATCH', receipt: 'SEAL_RECEIVED', openHold: false }, progress: [60, 240] },
      { no: 'D3', kind: 'IN', status: 'RESERVED', containerNo: 'MSCU4788258', seal: { result: 'MISMATCH', receipt: 'SEAL_PENDING', openHold: true }, progress: [0, 210] },
      { no: 'D4', kind: 'IN', status: 'IDLE' },
      { no: 'D11', kind: 'OUT', status: 'LOADING', tripId: 'LH-261020-07', dest: 'SEOUL', units: [12, 20] },
      { no: 'D12', kind: 'OUT', status: 'LOADING', tripId: 'LH-261020-08', dest: 'INCHEON', units: [15, 16] },
      { no: 'D13', kind: 'OUT', status: 'RESERVED', tripId: 'LH-261020-09', dest: 'GYEONGGI', units: [0, 18] },
      { no: 'D14', kind: 'OUT', status: 'IDLE' },
    ],
    work: { unload: [240, 500], scanned: [236, 500], sorted: [221, 500], amr: { running: 12, charging: 2, stopped: 0 } },
    ports: [ { dest: 'INCHEON', name: '인천', fill: 45 }, { dest: 'SEOUL', name: '서울', fill: 82 }, { dest: 'GYEONGGI', name: '경기', fill: 96 }, { dest: 'EXCEPTION', name: '재분류', fill: 8 } ],
    branches: [
      { tripId: 'LH-261020-07', dest: '서울', depart: '10:30', status: 'LOADING', units: [12, 20], late: true },
      { tripId: 'LH-261020-08', dest: '인천', depart: '11:00', status: 'LOADING', units: [15, 16] },
      { tripId: 'LH-261020-09', dest: '경기', depart: '12:00', status: 'ASSIGNED', units: [0, 18] },
    ],
    returns: [
      { orderId: 'TO-261020-001', containerNo: 'SUDU9939088', place: '선광 CY (선사 지정)', due: '10/22 18:00', status: 'RETURNING_EMPTY', dueSoon: false },
      { orderId: 'TO-261019-014', containerNo: 'PONU3791467', place: 'HJIT 반납장', due: '10/20 18:00', status: 'UNLOADED', dueSoon: true },
    ],
  },
  centers: {
    SEOUL: { name: '서울 센터', incoming: [ { tripId: 'LH-261020-02', vehicleNo: '경기82자1001', status: 'UNLOADED', eta: '도착 09:40', units: 18 }, { tripId: 'LH-261020-07', vehicleNo: '경기85바3030', status: 'LOADING', eta: '허브 상차 중', units: 20 } ],
      units: [ { unitId: 'RT-SE-01', boxes: 14, scanned: 14, damaged: 0, wrongDest: 0 }, { unitId: 'RT-SE-02', boxes: 12, scanned: 12, damaged: 1, wrongDest: 0 }, { unitId: 'RT-SE-03', boxes: 13, scanned: 13, damaged: 0, wrongDest: 1 } ] },
    INCHEON: { name: '인천 센터', incoming: [ { tripId: 'LH-261020-01', vehicleNo: '인천86사1100', status: 'RECEIVED', eta: '인수 08:55', units: 16 } ], units: [ { unitId: 'RT-IC-01', boxes: 15, scanned: 15, damaged: 0, wrongDest: 0 } ] },
    GYEONGGI: { name: '경기 센터', incoming: [ { tripId: 'LH-261020-09', vehicleNo: '[미배차]', status: 'ASSIGNED', eta: '12:00 출발 예정', units: 18 } ], units: [] },
  },
  toll: {
    // 가상 요율: 기본 + km 당. 확정 전이라 W-11 기준정보에서 바꿀 수 있게 둔다
    feeRules: [ { cls: 1, desc: '2축 · 2.5t 미만', base: 900, perKm: 44.3 }, { cls: 2, desc: '2축 · 2.5~5.5t', base: 900, perKm: 45.2 }, { cls: 3, desc: '2축 · 5.5t 이상', base: 900, perKm: 47.0 }, { cls: 4, desc: '3축', base: 900, perKm: 62.9 }, { cls: 5, desc: '4축 이상', base: 900, perKm: 74.4 } ],
    checkpoints: [ { id: 1, type: 'TOLL_IN', name: '톨게이트 입구' }, { id: 2, type: 'CHECKPOINT', name: '검문소 A' }, { id: 3, type: 'CHECKPOINT', name: '검문소 B' }, { id: 4, type: 'CHECKPOINT', name: '검문소 C' }, { id: 5, type: 'TOLL_OUT', name: '톨게이트 출구' } ],
    passes: [
      { vehicleNo: '인천80사1234', checkpointId: 1, cls: 5, axles: [6.1, 9.4, 9.8, 8.7, 8.9], km: 0, ocr: 0.98 },
      { vehicleNo: '인천81아2211', checkpointId: 1, cls: 5, axles: [6.4, 11.6, 11.2, 9.3, 9.1], km: 0, ocr: 0.96 },
      { vehicleNo: '인천80바9090', checkpointId: 3, cls: 5, axles: [6.0, 11.4, 10.9, 9.6, 9.9], km: 0, ocr: 0.91, recheck: [6.0, 10.6, 10.4, 9.4, 9.6] },
    ],
  },
};

/* =========================================================
   3. 규칙 함수 (서버 서비스 로직으로 옮길 부분)
   ========================================================= */
// 거점 상태: 정상 = 미해결 예외 0 & 30분 이상 지연 0 (30분 미만 지연은 정상)
function siteStatus(siteId) {
  const open = MOCK.exceptions.filter(e => e.siteId === siteId && e.status !== 'RESOLVED');
  if (open.some(e => e.severity === 'CRITICAL')) return 'crit';
  const late = MOCK.delays.some(d => d.siteId === siteId && d.delayMin >= 30);
  if (open.length || late) return 'warn';
  return 'ok';
}
const STATUS_TEXT = { ok: '정상', warn: '주의', crit: '긴급' };
// 과적: 축하중 10t, 총중량 40t, 측정 오차 10% 허용 → 11t / 44t 초과면 과적
const LIMIT = { axle: 10, total: 40, tolerance: 1.1 };
function overload(axles) {
  const total = axles.reduce((a, b) => a + b, 0);
  const badAxles = axles.map((w, i) => w > LIMIT.axle * LIMIT.tolerance ? i : -1).filter(i => i >= 0);
  return { total: +total.toFixed(1), badAxles, over: badAxles.length > 0 || total > LIMIT.total * LIMIT.tolerance };
}
// 처리 규칙은 "횟수"가 아니라 "위치"로 정한다
function overloadAction(cpType, first, second) {
  if (!first.over) return { code: 'PASS', text: '통과' };
  if (cpType !== 'CHECKPOINT') return { code: 'FINE_PASS', text: '과태료 부과 후 통과' };
  if (!second) return { code: 'RECHECK', text: '대기 공간으로 이동 · 재측정' };
  return second.over ? { code: 'BAN', text: '운행 금지' } : { code: 'PASS', text: '재측정 정상 · 통과 (과태료 없음)' };
}
const fee = (cls, km) => { const r = MOCK.toll.feeRules.find(x => x.cls === cls); return Math.round((r.base + r.perKm * km) / 10) * 10; };

/* =========================================================
   4. 화면
   ========================================================= */
const state = { role: 'CONTROL', route: 'hub', center: 'SEOUL', exFilter: 'ALL', passIdx: 1, km: 62 };
const $ = (s) => document.querySelector(s);
export const toastBus = { fn: null };
const tagOf = (k, t) => `<span class="tag t-${k}">${t}</span>`;
const can = (route) => permOf(route, state.role) === 'P' || permOf(route, state.role) === 'D';
function toast(msg) { toastBus.fn && toastBus.fn(msg); }
function head(id, title, api, extra = '') { return `<div class="head"><span class="mono muted">${id}</span><h1>${title}</h1>${extra}<span class="api">${api}</span></div>`; }

const SCREENS = {
  dashboard() {
    const open = MOCK.exceptions.filter(e => e.status !== 'RESOLVED');
    const sites = MOCK.sites.map(s => { const st = siteStatus(s.id); const c = { ok: 'var(--ok)', warn: 'var(--warn)', crit: 'var(--crit)' }[st];
      return `<a class="site" href="#${s.type === 'HUB' ? 'hub' : 'center'}" data-center="${s.code || ''}"><div class="row"><span class="dot" style="background:${c}"></span><b>${s.name}</b><span style="margin-left:auto">${tagOf(st, STATUS_TEXT[st])}</span></div>
      <div class="faint">미해결 예외 ${MOCK.exceptions.filter(e => e.siteId === s.id && e.status !== 'RESOLVED').length}건 · 30분+ 지연 ${MOCK.delays.filter(d => d.siteId === s.id && d.delayMin >= 30).length}대</div></a>`; }).join('');
    return head('W-02', '통합 대시보드', 'GET /api/web/dashboard/summary') + `
    <div class="grid kpis">
      <div class="panel kpi"><div class="l">오늘 입고 컨테이너</div><div class="v">12/18</div><div class="s">도착 예정 6대</div></div>
      <div class="panel kpi"><div class="l">분류 박스</div><div class="v">${MOCK.hub.work.sorted[0]}</div><div class="s">스캔 ${MOCK.hub.work.scanned[0]} · 하역 ${MOCK.hub.work.unload[0]}</div></div>
      <div class="panel kpi"><div class="l">지선 정시 출발률</div><div class="v">92%</div><div class="s">지연 1건</div></div>
      <div class="panel kpi"><div class="l">미해결 예외</div><div class="v" style="color:var(--crit)">${open.length}</div><div class="s">긴급 ${open.filter(e => e.severity === 'CRITICAL').length} · 경고 ${open.filter(e => e.severity === 'WARNING').length}</div></div>
    </div>
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(300px,1fr));margin-top:12px">
      <div class="panel"><h2>거점 상태 <span class="right">정상 · 주의 · 긴급</span></h2><div class="grid" style="gap:8px">${sites}</div>
        <p class="faint" style="margin:10px 0 0">정상 = 미해결 예외 0건, 30분 이상 지연 0대. 10분 지연은 정상으로 본다.</p></div>
      <div class="panel"><h2>최근 예외 <a href="#exceptions" class="right">이상 상황 관리 →</a></h2><div class="scroll"><table>
        <tr><th>심각도</th><th>유형</th><th>대상</th><th>시각</th></tr>
        ${MOCK.exceptions.slice(0, 5).map(e => `<tr><td>${tagOf(e.severity === 'CRITICAL' ? 'crit' : 'warn', e.severity === 'CRITICAL' ? '긴급' : '경고')}</td><td>${e.label}</td><td class="mono">${e.target}</td><td class="mono">${e.at}</td></tr>`).join('')}
      </table></div></div>
    </div>`;
  },

  hub() {
    const H = MOCK.hub, edit = permOf('hub', state.role) === 'P';
    const dockCard = (d) => {
      if (d.status === 'IDLE') return `<div class="dock idle"><b class="mono">${d.no}</b><span class="faint">비어 있음</span></div>`;
      if (d.kind === 'IN') {
        const hold = d.seal.openHold, cls = hold ? 'hold' : d.status === 'RESERVED' ? 'res' : 'busy';
        const sealTag = hold ? tagOf('crit', '개봉 보류') : d.seal.receipt === 'SEAL_RECEIVED' ? tagOf('ok', '씰 인수') : tagOf('warn', '씰 확인 대기');
        return `<div class="dock ${cls}"><div class="row"><b class="mono">${d.no}</b><span style="margin-left:auto">${sealTag}</span></div>
          <span class="mono">${d.containerNo}</span>
          ${hold ? `<span class="faint">씰 ${d.seal.result} · 처리는 허브 앱</span><button class="btn" data-act="release" data-dock="${d.no}" ${edit ? '' : 'disabled'}>보류 해제 (관리자 확인)</button>`
                 : `<div class="bar"><i style="width:${Math.round(d.progress[0] / d.progress[1] * 100)}%"></i></div><span class="faint">하역 ${d.progress[0]}/${d.progress[1]} 박스</span>`}</div>`;
      }
      return `<div class="dock ${d.status === 'LOADING' ? 'busy' : 'res'}"><div class="row"><b class="mono">${d.no}</b><span style="margin-left:auto">${tagOf('info', d.dest)}</span></div><span class="mono">${d.tripId}</span><div class="bar"><i style="width:${Math.round(d.units[0] / d.units[1] * 100)}%"></i></div><span class="faint">상차 ${d.units[0]}/${d.units[1]} 롤테이너</span></div>`;
    };
    const pct = (a) => Math.round(a[0] / a[1] * 100);
    return head('W-04', '곤지암 허브 관제', 'GET /api/web/hubs/1/monitor', tagOf(siteStatus(1), STATUS_TEXT[siteStatus(1)])) + `
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(280px,1fr))">
      <div class="panel"><h2><span class="n">①</span>들어올 컨테이너 <span class="right">${H.inbound.length}대</span></h2>
        ${H.inbound.map(v => `<div class="lane"><div class="row"><b>${v.vehicleNo}</b><span style="margin-left:auto">${v.arrived ? tagOf('warn', '도착 · 도크 대기') : v.delayMin >= 30 ? tagOf('crit', 'ETA ' + v.eta + ' +' + v.delayMin + '분') : tagOf('mute', 'ETA ' + v.eta)}</span></div>
          <div class="faint mono">${v.containerNo} · 박스 ${v.boxes}</div>
          ${v.arrived ? `<button class="btn acc" style="margin-top:6px" data-act="assign" data-order="${v.orderId}" ${edit ? '' : 'disabled'}>도크 배정</button>` : ''}</div>`).join('')}
        <p class="faint">POST /api/web/docks/{no}/assign → 기사 앱 푸시</p></div>
      <div class="panel" style="grid-column:span 2;min-width:0"><h2><span class="n">②</span>도크 현황판 <span class="right">입고 D1~D4 · 출고 D11~D14</span></h2>
        <div class="grid" style="grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:8px">${H.docks.map(dockCard).join('')}</div></div>
    </div>
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(280px,1fr));margin-top:12px">
      <div class="panel"><h2><span class="n">③</span>처리 중 <span class="right">작업자 + AMR 보조</span></h2>
        ${[['하역 (작업자)', H.work.unload], ['식별 스캔', H.work.scanned], ['분류 완료', H.work.sorted]].map(([l, a]) => `<div style="margin:6px 0"><div class="row faint"><span>${l}</span><span class="mono" style="margin-left:auto">${a[0]}/${a[1]}</span></div><div class="bar"><i style="width:${pct(a)}%"></i></div></div>`).join('')}
        <div class="row" style="margin:10px 0 6px"><b>AMR 운반</b><span class="faint" style="margin-left:auto">가동 ${H.work.amr.running} · 충전 ${H.work.amr.charging} · 정지 ${H.work.amr.stopped}</span></div>
        ${H.ports.map(p => `<div class="row" style="margin:4px 0"><span style="width:52px">${p.name}</span><div class="bar" style="flex:1"><i style="width:${p.fill}%;background:${p.fill >= 95 ? 'var(--warn)' : 'var(--info)'}"></i></div><span class="mono" style="width:40px;text-align:right">${p.fill}%</span></div>`).join('')}
      </div>
      <div class="panel"><h2><span class="n">④</span>나갈 것 <span class="right">운행 유형을 나눠 표시</span></h2>
        <div class="faint" style="margin-bottom:4px">지선 출발 (센터별 1건)</div>
        ${H.branches.map(l => `<div class="lane"><div class="row"><b class="mono">${l.depart}</b><span>${l.dest} 센터</span><span style="margin-left:auto">${l.late ? tagOf('warn', '출발 지연') : tagOf('mute', l.status)}</span></div><div class="faint mono">${l.tripId} · 롤테이너 ${l.units[0]}/${l.units[1]}</div></div>`).join('')}
        <div class="faint" style="margin:10px 0 4px">공컨테이너 반납 (하역 완료 후 별도 운행)</div>
        ${H.returns.map(r => `<div class="lane"><div class="row"><b class="mono">${r.containerNo}</b><span style="margin-left:auto">${r.dueSoon ? tagOf('warn', '기한 임박') : tagOf('mute', r.status)}</span></div><div class="faint">${r.place} · 기한 ${r.due} (선사별)</div></div>`).join('')}
      </div>
      <div class="panel"><h2><span class="n">⑤</span>허브 예외</h2>
        ${MOCK.exceptions.filter(e => e.siteId === 1 && e.status !== 'RESOLVED').map(e => `<div class="lane"><div class="row">${tagOf(e.severity === 'CRITICAL' ? 'crit' : 'warn', e.severity === 'CRITICAL' ? '긴급' : '경고')}<b>${e.label}</b></div><div class="faint mono">${e.target} · ${e.at}</div></div>`).join('') || '<p class="faint">미해결 예외 없음</p>'}
      </div>
    </div>`;
  },

  center() {
    const C = MOCK.centers[state.center], edit = permOf('center', state.role) === 'P';
    const issues = C.units.filter(u => u.damaged || u.wrongDest || u.scanned !== u.boxes).length;
    return head('W-05', '물류센터 관제', `GET /api/web/centers/${state.center}/monitor`,
      `<div class="seg" role="tablist" aria-label="센터 선택">${['INCHEON', 'SEOUL', 'GYEONGGI'].map(k => `<button role="tab" aria-selected="${k === state.center}" class="${k === state.center ? 'on' : ''}" data-center="${k}">${MOCK.centers[k].name.replace(' 센터', '')}</button>`).join('')}</div>`) + `
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(300px,1fr))">
      <div class="panel"><h2><span class="n">①</span>들어올 지선 <span class="right">허브 입고 전 화물은 "해외 배송 중"</span></h2>
        ${C.incoming.map(t => `<div class="lane"><div class="row"><b class="mono">${t.tripId}</b><span style="margin-left:auto">${tagOf(t.status === 'RECEIVED' ? 'ok' : t.status === 'UNLOADED' ? 'warn' : 'mute', t.status)}</span></div><div class="faint">${t.vehicleNo} · 롤테이너 ${t.units} · ${t.eta}</div></div>`).join('')}
        <div class="note">허브 → 센터 경로는 운송망 관제(W-03)에서 3D로 보여준다.</div></div>
      <div class="panel" style="grid-column:span 2;min-width:0"><h2><span class="n">③</span>검수 · 인수 확정 <span class="right">하차 → 식별 → 수량·파손 → 차이 처리 → 인수 확정</span></h2>
        ${C.units.length ? `<div class="scroll"><table><tr><th>롤테이너</th><th>박스(예정)</th><th>스캔</th><th>파손</th><th>오배송</th><th>처리</th></tr>
        ${C.units.map(u => `<tr><td class="mono">${u.unitId}</td><td class="mono">${u.boxes}</td><td class="mono">${u.scanned}</td><td>${u.damaged ? tagOf('warn', u.damaged + '건') : '-'}</td><td>${u.wrongDest ? tagOf('crit', u.wrongDest + '건') : '-'}</td>
          <td>${u.wrongDest ? `<button class="btn" data-act="misdelivery" data-unit="${u.unitId}" ${edit ? '' : 'disabled'}>직원 이동 지시</button>` : u.damaged ? `<button class="btn" data-act="record" data-unit="${u.unitId}" ${edit ? '' : 'disabled'}>차이 기록</button>` : '<span class="faint">일치</span>'}</td></tr>`).join('')}
        </table></div>
        <div class="row" style="margin-top:12px"><span class="faint">미처리 차이 ${issues}건 · 오배송은 담당 물류센터 직원이 직접 옮긴다 (지선차량·퀵 제외)</span><button class="btn pri" style="margin-left:auto" data-act="receive" ${edit && issues === 0 ? '' : 'disabled'}>인수 확정</button></div>`
        : '<p class="faint">아직 하차한 롤테이너가 없습니다.</p>'}
      </div>
    </div>`;
  },

  toll() {
    const T = MOCK.toll, p = T.passes[state.passIdx], cp = T.checkpoints.find(c => c.id === p.checkpointId);
    const r1 = overload(p.axles), r2 = p.recheckDone && p.recheck ? overload(p.recheck) : null, act = overloadAction(cp.type, r1, r2);
    const edit = permOf('toll', state.role) === 'P';
    const axRows = (ax, res) => ax.map((w, i) => `<div class="axle"><span>${i + 1}축</span><div class="bar"><i style="width:${Math.min(100, w / 12 * 100)}%;background:${res.badAxles.includes(i) ? 'var(--crit)' : 'var(--info)'}"></i></div><span class="mono">${w.toFixed(1)} t</span></div>`).join('');
    return head('W-12', '톨게이트 · 검문소', 'GET /api/web/checkpoints/{id}/passes', '<span class="faint">차단기 없음 · 천천히 통과</span>') + `
    <div class="panel" style="margin-bottom:12px"><div class="row" style="flex-wrap:wrap;gap:6px">
      ${T.checkpoints.map(c => `<span class="tag ${c.id === p.checkpointId ? 't-info' : 't-mute'}">${c.name}</span>`).join('<span class="faint">—</span>')}
      <span class="faint" style="margin-left:auto">통과 차량</span>
      ${T.passes.map((x, i) => `<button class="btn ${i === state.passIdx ? 'pri' : ''}" data-pass="${i}">${x.vehicleNo}</button>`).join('')}
    </div></div>
    <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(280px,1fr))">
      <div class="panel"><h2>OCR 인식 <span class="right mono">신뢰도 ${(p.ocr * 100).toFixed(0)}%</span></h2>
        <div class="plate"><em>${cp.name} · 카메라 1</em><span>${p.vehicleNo}</span></div>
        <p class="faint">1차 게이트 OCR 그대로. 인식한 차량번호로 사전 데이터(축 무게)를 조회한다.</p></div>
      <div class="panel"><h2>과적 체크 <span class="right">${r1.over ? tagOf('crit', '과적') : tagOf('ok', '정상')}</span></h2>
        ${axRows(p.axles, r1)}
        <div class="row" style="margin-top:8px"><span class="faint">총중량</span><b class="mono" style="margin-left:auto;color:${r1.total > 44 ? 'var(--crit)' : 'inherit'}">${r1.total} t</b></div>
        <p class="faint">기준 축 10t · 총 40t, 오차 10% 허용 (11t / 44t 초과면 과적)</p>
        ${r2 ? `<div class="note"><b>재측정</b> 최대 축 ${Math.max(...p.recheck).toFixed(1)}t · 총 ${r2.total}t → ${r2.over ? tagOf('crit', '과적') : tagOf('ok', '정상')}</div>` : ''}
        <div class="row" style="margin-top:10px"><span>처리</span><b style="margin-left:auto">${act.text}</b></div>
        ${act.code === 'RECHECK' ? `<button class="btn acc" style="margin-top:8px;width:100%" data-act="recheck" ${edit ? '' : 'disabled'}>재측정 실행</button>` : ''}
        <p class="faint mono" style="margin-top:8px">overload_checks.checkpoint_id=${cp.id} · action=${act.code}</p></div>
      <div class="panel"><h2>요금표 <span class="right">가상 요율 · 확정 전</span></h2>
        <table><tr><th>종</th><th>기준</th><th>km당</th></tr>${T.feeRules.map(r => `<tr style="${r.cls === p.cls ? 'background:#fff7df' : ''}"><td class="mono">${r.cls}</td><td>${r.desc}</td><td class="mono">${r.perKm}</td></tr>`).join('')}</table>
        <div class="row" style="margin-top:10px"><label for="km" class="faint">주행 거리</label><input id="km" type="number" min="0" value="${state.km}" style="width:80px;margin-left:auto;padding:4px 6px;border:1px solid var(--line);border-radius:3px" aria-label="주행 거리 km"> km</div>
        <div class="row" style="margin-top:8px"><span>예상 통행료 (${p.cls}종)</span><b class="mono" style="margin-left:auto;font-size:18px">${fee(p.cls, state.km).toLocaleString()}원</b></div>
        ${edit ? '<button class="btn acc" style="margin-top:8px;width:100%" data-act="confirmPass">통과 확정 (저장)</button>' : ''}
        <p class="faint">기본 900원 + km × 요율. 요금은 OCR·과적 다음 순서로 붙인다.</p></div>
    </div>`;
  },

  exceptions() {
    const list = MOCK.exceptions.filter(e => state.exFilter === 'ALL' || e.severity === state.exFilter || (state.exFilter === 'OPEN' && e.status !== 'RESOLVED'));
    const site = (id) => (MOCK.sites.find(s => s.id === id) || { name: '도로' }).name;
    const next = { OPEN: '담당 지정', IN_PROGRESS: '처리 완료', RESOLVED: '' };
    return head('W-09', '이상 상황 관리', 'GET /api/web/exceptions · PUT /api/web/exceptions/{id}', `<div class="seg">${[['ALL', '전체'], ['OPEN', '미해결'], ['CRITICAL', '긴급'], ['WARNING', '경고']].map(([k, l]) => `<button class="${state.exFilter === k ? 'on' : ''}" data-filter="${k}">${l}</button>`).join('')}</div>`) + `
    <div class="panel"><div class="scroll"><table>
      <tr><th>심각도</th><th>유형 코드</th><th>내용</th><th>거점</th><th>대상</th><th>시각</th><th>상태</th><th>담당</th><th></th></tr>
      ${list.map(e => `<tr><td>${tagOf(e.severity === 'CRITICAL' ? 'crit' : 'warn', e.severity === 'CRITICAL' ? '긴급' : '경고')}</td><td class="mono">${e.type}</td><td>${e.label}</td><td>${site(e.siteId)}</td><td class="mono">${e.target}</td><td class="mono">${e.at}</td>
        <td>${tagOf(e.status === 'RESOLVED' ? 'ok' : e.status === 'IN_PROGRESS' ? 'info' : 'mute', e.status)}</td><td>${e.owner || '-'}</td>
        <td>${next[e.status] ? `<button class="btn" data-act="advance" data-ex="${e.id}" ${can('exceptions') ? '' : 'disabled'}>${next[e.status]}</button>` : ''}</td></tr>`).join('')}
    </table></div>
    <p class="faint">상태 흐름 OPEN → IN_PROGRESS → RESOLVED. 처리 주체는 기획서 v2 "예외 처리표"를 따른다 (예: 씰 불일치 → 허브 관제사).</p></div>`;
  },
};

/* 26.10.20 씰 번호 앱 → 웹: GET /api/web/seals (5초 폴링). 컨테이너 기사 T-02·허브 앱 씰 확인·지선 출발 봉인 */
function sealPanel(k) {
  const S = MOCK.seals;
  if (!S) return `<div class="panel"><h2>씰 현황</h2><p class="faint">서버(/api/web/seals) 연결 시 앱에서 입력한 씰번호가 표시됩니다.</p></div>`;
  const cs = S.containerSeals || [], lh = S.branchSeals || [];
  // 26.10.21 수정: 서버는 시각을 epoch 초(숫자)로 보냄 → 문자열 slice 에러로 패널이 안 그려지던 문제. 숫자·ISO 모두 KST HH:MM:SS 로
  const t = (v) => { if (v == null || v === '') return '-'; const d = new Date(typeof v === 'number' ? v * 1000 : v); return isNaN(d) ? String(v) : d.toLocaleTimeString('ko-KR', { hour12: false, timeZone: 'Asia/Seoul' }); };
  const rows = cs.map(x => `<tr><td class="mono">${x.containerNo}</td><td class="mono">${x.expectedSealNo}</td><td class="mono">${x.lastObservedSealNo || (x.lastCondition === 'MISSING' ? '(없음)' : '-')}</td>
    <td>${x.lastMatches === false || x.lastCondition === 'MISSING' || x.lastCondition === 'DAMAGED' ? tagOf('crit', x.lastCondition === 'MISSING' ? '분실' : x.lastCondition === 'DAMAGED' ? '훼손' : '불일치') : x.lastMatches ? tagOf('ok', '일치') : tagOf('mute', '미확인')}</td>
    <td>${x.status === 'LOST' ? tagOf('crit', 'LOST') : tagOf('mute', x.status)} ${x.openHold ? tagOf('crit', '개봉 보류') : ''}</td><td>${x.lastBy || '-'}</td><td class="mono">${t(x.lastAt)}</td></tr>`).join('');
  const lrows = lh.map(l => `<tr><td class="mono">LH#${l.tripId}</td><td class="mono">${l.vehicleNo}</td><td class="mono">${l.domesticSealNo}</td><td>${tagOf('info', l.status)}</td><td class="mono">${t(l.departedAt)}</td></tr>`).join('');
  return `<div class="panel"><h2>컨테이너 씰 (앱 입력값) <span class="right faint">${t(S.serverTime)} 기준</span></h2><div class="scroll"><table>
    <tr><th>컨테이너</th><th>사전 씰</th><th>현장 입력</th><th>판정</th><th>씰 상태</th><th>입력자</th><th>시각</th></tr>${rows || '<tr><td colspan="7" class="faint">없음</td></tr>'}</table></div>
    ${k === 'exceptions' ? '' : `<h2 style="margin-top:12px">지선 국내 봉인 (shipment_legs.domestic_seal_no)</h2><div class="scroll"><table><tr><th>운행</th><th>차량</th><th>봉인번호</th><th>상태</th><th>출발</th></tr>${lrows || '<tr><td colspan="5" class="faint">없음</td></tr>'}</table></div>`}</div>`;
}
for (const k of ['dashboard', 'hub', 'exceptions']) { const f = SCREENS[k]; SCREENS[k] = () => f() + sealPanel(k); }


export function act(a, b) {
  /* 원본 클릭 핸들러의 action 처리부 */

  if (a === 'assign') { const d = MOCK.hub.docks.find(x => x.no === 'D4'); Object.assign(d, { status: 'RESERVED', containerNo: 'SCGU1234565', seal: { result: 'MATCH', receipt: 'SEAL_PENDING', openHold: false }, progress: [0, 240] }); MOCK.hub.inbound[0].arrived = false; MOCK.hub.inbound[0].eta = 'D4 배정'; toast('D4 배정 → 기사 앱에 "4번 도크" 푸시'); }
  if (a === 'release') { const d = MOCK.hub.docks.find(x => x.no === b.dataset.dock); d.seal.openHold = false; d.seal.receipt = 'SEAL_RECEIVED'; MOCK.exceptions.find(x => x.id === 901).status = 'RESOLVED'; toast('POST /api/web/seals/{id}/confirm — 보류 해제는 관리자 확인으로만'); }
  if (a === 'misdelivery') { const u = MOCK.centers[state.center].units.find(x => x.unitId === b.dataset.unit); u.wrongDest = 0; toast('오배송 박스 → 담당 센터 직원 이동 지시 등록'); }
  if (a === 'record') { const u = MOCK.centers[state.center].units.find(x => x.unitId === b.dataset.unit); u.damaged = 0; toast('파손 차이 기록 완료 (인수는 진행)'); }
  if (a === 'receive') { MOCK.centers[state.center].incoming[0].status = 'RECEIVED'; toast('인수 확정 → shipment_legs.received_at 저장'); }
  // fix_1600: 통과 확정 → POST /api/web/toll/passages (같은 통과는 simKey 로 한 번만)
  if (a === 'confirmPass') { const p = MOCK.toll.passes[state.passIdx]; p.simKey = p.simKey || (Date.now().toString(36) + state.passIdx); fetch(apiBase + '/api/web/toll/passages', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ vehicleNo: p.vehicleNo, tollgateId: 1, cls: p.cls, km: state.km, simKey: p.simKey }) }).then(r => r.json()).then(d => toast(d.duplicate ? '이미 저장된 통과' : '통과 저장 · ' + d.fare + '원')).catch(() => toast('저장 실패')); }
  if (a === 'recheck') { MOCK.toll.passes[state.passIdx].recheckDone = true; toast('재측정 완료'); }
  if (a === 'advance') { const x = MOCK.exceptions.find(y => y.id === +b.dataset.ex); if (x.status === 'OPEN') { x.status = 'IN_PROGRESS'; x.owner = '나'; } else x.status = 'RESOLVED'; }

}
export { MENU, PERM_LABEL, FIRST, permOf, MOCK, siteStatus, STATUS_TEXT, overload, overloadAction, fee, state, SCREENS, can };
