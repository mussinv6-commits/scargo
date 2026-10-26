<template>
  <!-- 26.10.22 추가: S-Bot 대시보드. 허브 → 택배 이동 → 물류센터 흐름 글자를 누르면 해당 화면으로 이동.
       숫자는 예시 데이터(DB 연결은 다음 단계). 3D 화면은 각 화면에서 따로 띄운다. -->
  <div class="sbd">
    <!-- 26.10.25 추가: 맨 위 한 줄 요약 -->
    <div class="sumline">오늘 허브 처리 <b>{{ kpis[2].v }}</b> · AMR 가동 <b>{{ kpis[1].v }}</b> · 운송 중 <b>{{ inTransitN }}대</b><template v-if="downN"> · 정비·충전 중 <em>AMR {{ downN }}대</em></template></div>
    <!-- 26.10.25 추가: 경보 알림(누르면 해당 화면) · 센터 필터 · CSV -->
    <div class="alerts" v-if="alerts.length"><span class="al-t">⚠ 확인 필요</span><RouterLink v-for="a in alerts" :key="a.k" :to="a.to" class="al" :class="a.lv">{{ a.t }}</RouterLink></div>
    <div class="tools">
      <span class="tl">센터</span><button v-for="f in FILTERS" :key="f.q" type="button" :class="{ on: fc === f.q }" @click="fc = f.q">{{ f.n }}</button>
      <button type="button" class="csv" @click="exportCsv">⭳ 운영 현황 보고서(엑셀)</button>
      <button type="button" class="csv" @click="exportXlsx">⭳ 통행료 정산 보고서(엑셀)</button>
    </div>
    <div class="sech">허브 처리 현황 <small>AMR 작업 기준 · 센터 현황과 같은 데이터</small></div>
    <div class="kpis">
      <div v-for="k in kpis" :key="k.t" class="kpi"><small>{{ k.t }}</small><b>{{ k.v }}</b></div>
    </div>

    <div class="flow">
      <span class="flow-t">운송 흐름</span>
      <RouterLink v-for="(f, i) in flow" :key="f.t" :to="f.to" class="chip">{{ f.t }}<i v-if="i < flow.length - 1" class="ar">→</i></RouterLink>
      <span class="spd">배속<button v-for="x in [1, 2, 4, 8]" :key="x" type="button" :class="{ on: speed === x }" @click="setSpeed(x)">{{ x }}×</button></span>
      <span class="flow-h"><i class="dot" :class="hasDb || sbotDb ? 'on' : 'off'"></i>{{ statusText }}</span>
    </div>

    <div class="grid">
      <section class="card scene">
        <h3 class="ctr">허브 → 물류센터 운송 현황 <small class="dim">건물을 누르면 해당 센터 관제</small></h3>
        <svg viewBox="0 0 330 280" role="img" aria-label="허브와 물류센터 위치">
          <defs>
            <linearGradient id="sbdL" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3f64a3"/><stop offset="1" stop-color="#2a4676"/></linearGradient>
            <linearGradient id="sbdR" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5279b8"/><stop offset="1" stop-color="#37598f"/></linearGradient>
            <linearGradient id="sbdT" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#9db8e8"/><stop offset="1" stop-color="#6d8fcf"/></linearGradient>
            <linearGradient id="sbdhL" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#d9a53a"/><stop offset="1" stop-color="#a97d22"/></linearGradient>
            <linearGradient id="sbdhR" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f0c35a"/><stop offset="1" stop-color="#c9962f"/></linearGradient>
            <linearGradient id="sbdhT" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#ffe9a8"/><stop offset="1" stop-color="#f1cf7a"/></linearGradient>
            <g id="sbdCb"><polygon points="-60,-50 0,-20 0,30 -60,0" fill="url(#sbdL)"/><polygon points="0,-20 60,-50 60,0 0,30" fill="url(#sbdR)"/><polygon points="0,-80 60,-50 0,-20 -60,-50" fill="url(#sbdT)"/><polygon points="10,-3 24,-10 24,16 10,23" fill="#14203a"/><polygon points="32,-14 46,-21 46,5 32,12" fill="#14203a"/><polygon points="-50,-35 -40,-30 -40,-20 -50,-25" fill="#cfe0ff" opacity=".85"/><polygon points="-32,-26 -22,-21 -22,-11 -32,-16" fill="#cfe0ff" opacity=".85"/></g>
            <g id="sbdHb"><polygon points="-60,-50 0,-20 0,30 -60,0" fill="url(#sbdhL)"/><polygon points="0,-20 60,-50 60,0 0,30" fill="url(#sbdhR)"/><polygon points="0,-80 60,-50 0,-20 -60,-50" fill="url(#sbdhT)"/><polygon points="10,-3 24,-10 24,16 10,23" fill="#14203a"/><polygon points="32,-14 46,-21 46,5 32,12" fill="#14203a"/></g>
          </defs>
          <!-- 도로: 허브 게이트에서 거리순(서울 38 · 경기 55 · 인천 75km)으로 뻗는다 -->
          <path d="M232,150 C205,150 190,185 168,198" class="road"/>
          <path d="M232,150 C195,146 150,132 118,120" class="road"/>
          <path d="M232,150 C175,140 108,84 64,52" class="road"/>
          <g class="clk" @click="go('/sbot/hub')"><use href="#sbdHb" transform="translate(272,138) scale(.5)"/><text x="272" y="90" class="lab gold">허브 · 곤지암</text></g>
          <rect x="222" y="134" width="20" height="4" fill="#e9eef8"/><rect x="223" y="137" width="2" height="13" fill="#c9962f"/><rect x="239" y="137" width="2" height="13" fill="#c9962f"/>
          <g v-for="c in centers" :key="c.n" class="clk" @click="go('/sbot/center?c=' + c.q)">
            <use href="#sbdCb" :transform="`translate(${c.x},${c.y}) scale(.36)`"/>
            <text :x="c.x" :y="c.y - 35" class="lab">{{ c.n }} {{ c.v }}건</text>
          </g>
        </svg>
        <div class="tr">
          
          <table>
            <thead><tr><th>센터(거리)</th><th>운송 중</th><th>도착</th><th>운송 물품 구성 (건)</th></tr></thead>
            <tbody>
              <tr v-for="t in transportV" :key="t.q" @click="go('/sbot/center?c=' + t.q)">
                <td><i class="sq" :style="{ background: t.c }"></i>{{ t.n }} <small class="dim">{{ t.km }}km</small></td>
                <td><b class="gold">{{ t.transit.length }}대</b> <small class="dim">{{ t.transit.length ? t.transit[0].no + (t.transit.length > 1 ? ' 외 ' + (t.transit.length - 1) + '대' : '') : '-' }}</small></td>
                <td>{{ t.arrived }}대</td>
                <td class="cats"><span v-for="(n, i) in t.cat" :key="i">{{ CATL[i] }} {{ n }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div class="right">
        <section class="card">
          <h3 class="ctr">허브 처리 현황</h3>
          <div class="row2">
            <div class="cwrap">
            <svg viewBox="0 0 340 110" class="chart c1">
              <line x1="6" y1="90" x2="334" y2="90" class="ax"/>
              <rect v-for="(v, i) in hours" :key="i" :x="12 + i * 40" :y="90 - v * sc" width="24" :height="v * sc" rx="2" :class="v === peak ? 'pk' : 'bar'"/>
              <polyline :points="line" fill="none" stroke="#e9ebef" stroke-width="1.5"/>
              <text v-for="(v, i) in hours" :key="'v' + i" :x="24 + i * 40" :y="86 - v * sc" class="tx dim c">{{ v }}</text>
              <text v-for="(h, i) in hourLabels" :key="i" :x="24 + i * 40" y="102" class="tx dim c">{{ h }}시</text>
            </svg>
            <small class="dim">시간대별 처리 건수</small>
            </div>
            <div class="gbox">
              <svg viewBox="0 0 120 80" class="chart">
                <path d="M16,62 A44,44 0 0 1 104,62" pathLength="100" class="g-bg"/>
                <path d="M16,62 A44,44 0 0 1 104,62" pathLength="100" class="g-fg" :stroke-dasharray="`${gauge} 100`"/>
                <text x="60" y="48" class="big">{{ gauge }}%</text>
                <text x="60" y="74" class="tx dim c">AMR 가동률</text>
              </svg>
            </div>
          </div>
          <div v-if="amrRows.length" class="amrl">
            <div v-for="a in amrRows" :key="a.code" class="amrr">
              <i class="dot" :class="a.cls" :title="a.label"></i><b>{{ a.code }}</b><span>{{ a.pct }}%</span><span class="gold" :title="'오늘 누적 ' + a.today + '건 · 충전 ' + a.charge + '회 · 정비 ' + a.fault + '회'">{{ a.rate }}</span><em v-if="a.charge || a.fault" class="cnt">{{ a.charge ? "충" + a.charge : "" }}{{ a.fault ? " 정" + a.fault : "" }}</em>
            </div>
          </div>
        </section>
        <section class="card">
          <h3 class="ctr">센터 운송 현황</h3>
          <svg viewBox="0 0 400 118" class="chart">
            <g v-for="(c, j) in CATL" :key="'k' + j"><rect :x="40 + j * 62" y="4" width="8" height="8" rx="1" :fill="CATC[j]"/><text :x="52 + j * 62" y="11.5" class="tx dim">{{ c }}</text></g>
            <text x="388" y="11.5" class="tx dim r">운송 중 / 도착</text>
            <g v-for="(t, i) in tbars" :key="t.q" class="clk" @click="go('/sbot/center?c=' + t.q)">
              <text x="4" :y="38 + i * 33" class="tx">{{ t.n }}</text>
              <g v-for="(s, j) in t.segs" :key="j">
                <rect :x="40 + s.x * 1.9" :y="22 + i * 33" :width="Math.max(0, s.w * 1.9)" height="22" :fill="CATC[j]"/>
                <text v-if="s.w >= 7" :x="40 + (s.x + s.w / 2) * 1.9" :y="37.5 + i * 33" class="tx seg">{{ t.cat[j] }}</text>
              </g>
              <text :x="46 + t.w * 1.9" :y="38 + i * 33" class="tx gold">합계 {{ t.total }}건</text>
              <text x="388" :y="38 + i * 33" class="tx r"><tspan class="gold">{{ t.transit.length }}대</tspan> / {{ t.arrived }}대</text>
            </g>
          </svg>
        </section>
      </div>
    </div>

    <div class="btns">
      <RouterLink to="/sbot/hub" class="btn"><b>허브 관제 →</b><span>AMR 실시간 위치 · 작업 지시</span></RouterLink>
      <RouterLink to="/sbot/move" class="btn"><b>택배 이동 →</b><span>톨게이트 통과 · 통행료 정산</span></RouterLink>
      <RouterLink to="/sbot/center" class="btn"><b>물류센터 관제 →</b><span>센터별 입고 · 택배차 · 통행료</span></RouterLink>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { amrApi } from "@/components/amr/amrApi.js";
import { useRouter } from "vue-router";
import { buildXlsx, downloadBlob } from "@/utils/xlsxLite.js";
const router = useRouter();
const go = (p) => router.push(p);

// ── DB 실시간: 허브 AMR 관제와 같은 /api/amr/kpi 를 5초마다 읽는다. 서버가 꺼져 있으면 예시값을 보여 준다 ──
const live = ref(null);
const online = ref(false);
const locked = ref(false); // 서버가 로그인(인증)을 요구하는 경우
// DB에 오늘 기록이 실제로 있을 때만 DB값을 쓰고, 비어 있으면 공용 시뮬레이션 값(변동)을 쓴다
const hasDb = computed(() => !!live.value && ((live.value.today || 0) > 0 || (live.value.entries || 0) > 0));
const sbotDb = computed(() => shared.value?.src === "db"); // 허브→센터 운송 데이터가 DB(shipment_legs·hub_dispatches)에서 온 경우
const statusText = computed(() => sbotDb.value ? "DB 실시간 · 허브→센터 운송" + (hasDb.value ? " · AMR " + atText.value : " · AMR 기록 없음") : hasDb.value ? "DB 실시간 · " + atText.value : online.value ? "DB 연결 · 오늘 기록 없음 → 시뮬레이션 값" : locked.value ? "DB 로그인 필요 → 시뮬레이션 값" : "DB 오프라인 → 시뮬레이션 값");
async function poll() {
  let k = await amrApi.kpi();
  if (!k) { // 26.10.25 8080 직접 연결이 안 되면 같은 주소(/api 프록시)로 한 번 더 확인
    try { const r = await fetch("/api/amr/kpi", { credentials: "include" }); if (r.ok && (r.headers.get("content-type") || "").includes("json")) k = await r.json(); } catch { /* 무시 */ }
  }
  online.value = !!k;
  if (k) live.value = k;
}
let timer;
let offAuth;
onMounted(() => { amrApi.useCookieAuth(); offAuth = amrApi.onAuth((ok) => { locked.value = !ok; }); poll(); timer = setInterval(poll, 5000); });
onBeforeUnmount(() => { clearInterval(timer); if (offAuth) offAuth(); });

const atText = computed(() => { const t = new Date(live.value?.at || Date.now()); return [t.getHours(), t.getMinutes(), t.getSeconds()].map((n) => String(n).padStart(2, "0")).join(":") + " 기준"; });
const EX_HOURS = [{ hour: "6", count: 11 }, { hour: "8", count: 19 }, { hour: "10", count: 30 }, { hour: "12", count: 46 }, { hour: "14", count: 35 }, { hour: "16", count: 25 }, { hour: "18", count: 16 }, { hour: "20", count: 9 }];
const util = computed(() => (hasDb.value ? live.value?.utilization : null) || []);
const amrState = computed(() => { const m = new Map(); (shared.value?.src === "db" ? shared.value.amr || [] : []).forEach((x) => m.set(x.code, x)); return m; });
// 허브 처리 현황: 센터 현황과 숫자가 어긋나지 않도록, 반입 차량·처리 건수는 센터로 나간 물량(공용 데이터)에서 계산한다.
// AMR 가동·택배 1건 운반시간은 DB(/api/amr/kpi)에 값이 있으면 DB를 쓴다.
const kpis = computed(() => {
  const k = live.value, db = hasDb.value;
  const cs = Object.values(shared.value?.centers || EXC);
  const out = cs.reduce((a, c) => a + c.trips, 0), items = cs.reduce((a, c) => a + c.items, 0);
  const inTransit = transport.value.reduce((a, t) => a + t.transit.length, 0);
  const hub = shared.value?.src === "db" && shared.value.hub?.items > 0 ? shared.value.hub : null; // DB 모드: 허브 입고(오늘 들어온 화물차·물품)
  // AMR 가동: 최근 1시간 평균(%)이 아니라 지금 상태로 센다. 충전·정비 중이거나 예비 대기면 가동으로 치지 않는다.
  const act = db ? util.value.filter((u) => { const r = amrState.value.get(u.code); const st = r?.state || ""; return st !== "CHARGING" && st !== "MAINT" && st !== "TO_MAINT" && !(r?.spare && !r?.hour); }).length : 4;
  const all = db ? util.value.length : 6;
  const sec = db && k.avgTransportSec != null ? Math.round(k.avgTransportSec) : 192;
  return [
    { t: "허브 반입 차량", v: `${hub ? hub.trucks : out + inTransit}대` },
    { t: "AMR 가동", v: `${act} / ${all}` },
    { t: "허브 처리 건수", v: `${hub ? hub.items : items}건` },
    { t: "AMR 택배 1건 평균 운반", v: sec >= 60 ? `${Math.floor(sec / 60)}분 ${sec % 60}초` : `${sec}초` },
  ];
});
// 배속: AMR 처리(서버 시뮬레이터)와 허브→센터 운송 자동 운행을 같이 빠르게 한다. 값은 이 브라우저에 저장.
const apiBase = () => { try { const b = localStorage.getItem("scargo_amr_api"); if (b) return b; } catch { /* 무시 */ } const h = location.hostname; return /^\d+\.\d+\.\d+\.\d+$/.test(h) && h !== "127.0.0.1" ? `http://${h}:8080` : "http://localhost:8080"; };
const speed = ref((() => { try { const n = parseInt(localStorage.getItem("sbot_speed"), 10); return [1, 2, 4, 8].includes(n) ? n : 1; } catch { return 1; } })());
async function setSpeed(x) {
  speed.value = x;
  try { localStorage.setItem("sbot_speed", String(x)); } catch { /* 무시 */ }
  try { await fetch(apiBase() + "/api/amr/sbot/speed", { method: "POST", credentials: "include", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ x }) }); } catch { /* 서버 꺼짐 */ }
}
const flow = [{ t: "허브", to: "/sbot/hub" }, { t: "택배 이동", to: "/sbot/move" }, { t: "물류센터", to: "/sbot/center" }];
// 센터별 값은 택배 이동 화면이 센터 도착 때마다 기록하는 공용 데이터(public/sbot3d/shared.js 와 같은 키)를 읽는다.
// 같은 값을 물류센터 관제 화면도 읽는다. 기록이 없으면 기준값(예시)을 보여 준다.
const SKEY = "sbot_shared_v3";
const shared = ref(null);
const dayKey = () => { const d = new Date(); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`; };
function readShared() {
  try { const s = JSON.parse(localStorage.getItem(SKEY)); shared.value = s && s.day === dayKey() && s.centers ? s : null; } catch { shared.value = null; }
}
let sTimer;
const onStorage = (e) => { if (!e.key || e.key === SKEY) readShared(); };
onMounted(() => { if (speed.value > 1) setSpeed(speed.value); readShared(); sTimer = setInterval(readShared, 1000); window.addEventListener("storage", onStorage); });
onBeforeUnmount(() => { clearInterval(sTimer); window.removeEventListener("storage", onStorage); });
const CATL = ["전자제품", "의류", "식품", "기타"];
// 허브 → 물류센터 운송 현황: 택배 이동 화면의 택배차 상태(이동 중/입고 완료)와 물품 구성을 그대로 읽는다 (DB: shipment_legs.status, hub_dispatches)
const transport = computed(() => [["SEL", "서울", 38, "#f0a800"], ["GGI", "경기", 55, "#4aa3ff"], ["ICN", "인천", 75, "#4cc38a"]].map(([q, n, km, c]) => {
  const s = shared.value?.centers?.[q];
  const tk = s?.trucks || [];
  return { q, n, km, c, transit: tk.filter((x) => x.status !== "ARRIVED"), arrived: tk.filter((x) => x.status === "ARRIVED").length, cat: s?.cat || [0, 0, 0, 0] };
}));
const CATC = ["#4aa3ff", "#f0a800", "#4cc38a", "#a78bfa"];
// 26.10.25 센터 필터 · 경보 · CSV
const FILTERS = [{ q: "ALL", n: "전체" }, { q: "SEL", n: "서울" }, { q: "GGI", n: "경기" }, { q: "ICN", n: "인천" }];
const fc = ref("ALL");
const transportV = computed(() => (fc.value === "ALL" ? transport.value : transport.value.filter((t) => t.q === fc.value)));
const alerts = computed(() => {
  const out = [];
  amrRows.value.filter((r) => r.cls === "fault").forEach((r) => out.push({ k: "f" + r.code, t: r.code + " 정비 중", to: "/sbot/hub", lv: "bad" }));
  amrRows.value.filter((r) => r.cls === "chg").forEach((r) => out.push({ k: "c" + r.code, t: r.code + " 충전 중", to: "/sbot/hub", lv: "warn" }));
  transport.value.filter((t) => t.transit.length >= 5).forEach((t) => out.push({ k: "t" + t.q, t: t.n + "센터 운송 중 " + t.transit.length + "대", to: "/sbot/center?c=" + t.q, lv: "warn" }));
  if (!online.value) out.push({ k: "off", t: "서버 연결 확인 필요 (시뮬레이션 값)", to: "/sbot", lv: "warn" });
  return out;
});
// 26.10.25 물류센터별 통행료 정산 보고서를 엑셀(.xlsx)로: 센터별 도착 · 주간 / 심야 금액 · 할인 · 확정 합계 (합계 · 주간 · 심야 칸은 수식)
function exportXlsx() {
  const NM = { SEL: "서울", GGI: "경기", ICN: "인천" }, FARE = { SEL: 4530, GGI: 3720, ICN: 6310 }, ex = !shared.value;
  let led = {}; try { const d = JSON.parse(localStorage.getItem(SKEY + "_disc")); if (d && d.day === dayKey()) led = d; } catch { /* 없음 */ }
  const rows = ["SEL", "GGI", "ICN"].map((k) => {
    const c = shared.value?.centers?.[k] || { trips: EXC[k].trips, items: EXC[k].items, base: FARE[k] * EXC[k].trips, fin: FARE[k] * EXC[k].trips, hr: [0, 0, 0, 0, 0, 0, 0, 0] }, l = led[k] || {};
    const base = Math.round(c.base || 0), trips = c.trips || 0, nn = Math.min(trips, l.n || 0), nb = Math.min(base, Math.round(l.b || 0)), dis = Math.min(nb, Math.max(0, base - Math.round(c.fin ?? base)));
    return { k, nm: NM[k], trips, items: c.items || 0, base, nn, nb, dis, hr: c.hr || [] };
  });
  const H = (t) => ({ v: t, s: 1 }), N = (v) => ({ v, s: 2 });
  const t = new Date(), stamp = `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, "0")}-${String(t.getDate()).padStart(2, "0")} ${String(t.getHours()).padStart(2, "0")}:${String(t.getMinutes()).padStart(2, "0")}`;
  const body = rows.map((r, i) => { const n = i + 5, dn = r.trips - r.nn, da = r.base - r.nb, na = r.nb - r.dis;
    return [{ v: r.nm, s: 7 }, N(r.trips), N(r.items), N(r.base), { v: dn, f: `B${n}-F${n}`, s: 2 }, N(r.nn), { v: da, f: `D${n}-H${n}`, s: 2 }, N(r.nb), N(r.dis), { v: na, f: `H${n}-I${n}`, s: 2 }, { v: da + na, f: `G${n}+J${n}`, s: 2 }]; });
  const sum = (col, key) => ({ v: rows.reduce((a, r, i) => a + (key(r)), 0), f: `SUM(${col}5:${col}7)`, s: 3 });
  const tot = [{ v: "합계", s: 4 }, sum("B", (r) => r.trips), sum("C", (r) => r.items), sum("D", (r) => r.base), sum("E", (r) => r.trips - r.nn), sum("F", (r) => r.nn), sum("G", (r) => r.base - r.nb), sum("H", (r) => r.nb), sum("I", (r) => r.dis), sum("J", (r) => r.nb - r.dis), sum("K", (r) => r.base - r.dis)];
  const s1 = { name: "센터별 통행료 정산", widths: [12, 11, 11, 15, 11, 11, 15, 15, 14, 15, 16], heights: { 3: 32 }, rows: [
    [{ v: "S카고 물류센터별 통행료 정산 보고서", s: 5 }],
    [{ v: `기준 ${stamp} · ${ex ? "예시값(운행 기록 없음)" : "S-Bot 오늘 누적"} · 단위: 회 / 원`, s: 6 }],
    [],
    [H("센터"), H("도착 차량(회)"), H("입고 물품(건)"), H("기본 요금 합(원)"), H("주간 통과(회)"), H("심야 통과(회)"), H("주간 금액(원)"), H("심야 기본(원)"), H("심야 할인(원)"), H("심야 금액(원)"), H("확정 합계(원)")],
    ...body, tot, [],
    [{ v: "주간 금액 = 기본 요금 합 - 심야 기본 · 심야 금액 = 심야 기본 - 심야 할인 · 확정 합계 = 주간 + 심야", s: 6 }],
    [{ v: "통행료는 통과한 시각의 3D 하루 시계 기준: 06~21시 할인 없음 · 21~24시 30% · 00~06시 50%", s: 6 }],
  ] };
  const HRS = ["06~08", "08~10", "10~12", "12~14", "14~16", "16~18", "18~20", "20~22"];
  const s2 = { name: "시간대별 입고", widths: [12, 12, 12, 12], rows: [[{ v: "센터별 시간대 입고 물품(건)", s: 5 }], [], [H("시간대"), H("서울"), H("경기"), H("인천")],
    ...HRS.map((h, i) => [{ v: h, s: 7 }, N(rows[0].hr[i] || 0), N(rows[1].hr[i] || 0), N(rows[2].hr[i] || 0)]),
    [{ v: "합계", s: 4 }, { v: rows[0].hr.reduce((a, b) => a + b, 0), f: "SUM(B4:B11)", s: 3 }, { v: rows[1].hr.reduce((a, b) => a + b, 0), f: "SUM(C4:C11)", s: 3 }, { v: rows[2].hr.reduce((a, b) => a + b, 0), f: "SUM(D4:D11)", s: 3 }]] };
  downloadBlob(buildXlsx([s1, s2]), `S카고_센터별_통행료_정산보고서_${stamp.slice(0, 10)}.xlsx`);
}
// 26.10.26 운영 현황 보고서(.xlsx): 종합 판단 → 핵심 지표(설명 포함) → 확인 필요 → 센터별 운송 → 입고·통행료 → 시간대별 → AMR 상세. 합계 · 비중은 수식
function exportCsv() {
  const t = new Date(), p = (n) => String(n).padStart(2, "0");
  const day = `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())}`, stamp = `${day} ${p(t.getHours())}:${p(t.getMinutes())}`;
  const src = hasDb.value || sbotDb.value ? "DB 실시간" : "시뮬레이션 예시값 (서버 · 운행 기록 없음)";
  const H = (v) => ({ v, s: 1 }), N = (v) => ({ v, s: 2 }), T = (v) => ({ v, s: 7 }), W = (v) => ({ v, s: 9 }), C = (v) => ({ v, s: 14 }), TT = (v) => ({ v, s: 4 });
  const L = "ABCDEFGHIJ", rows = [], merges = [], heights = {};
  const add = (cells, h) => { rows.push(cells); if (h) heights[rows.length - 1] = h; return rows.length; };
  const span = (cells, c0, c1, s) => { for (let i = c0 + 1; i <= c1; i++) cells[i] = { v: "", s }; const n = rows.length + 1; merges.push(`${L[c0]}${n}:${L[c1]}${n}`); return cells; };
  const sec = (title) => { add([]); add(span([{ v: title, s: 8 }], 0, 9, 8), 24); };
  const ROUTE = { "/sbot/hub": "허브 관제", "/sbot/move": "택배 이동", "/sbot/center": "물류센터 관제", "/sbot": "대시보드" };

  // ── 데이터 정리 ──
  const tr = transport.value, am = amrRows.value, al = alerts.value;
  const bad = al.filter((a) => a.lv === "bad"), warn = al.filter((a) => a.lv === "warn");
  const lvl = bad.length ? "확인 필요" : warn.length ? "주의" : "정상";
  const NM = { SEL: "서울", GGI: "경기", ICN: "인천" }, FARE = { SEL: 4530, GGI: 3720, ICN: 6310 }, ex = !shared.value;
  const toll = ["SEL", "GGI", "ICN"].map((k) => {
    const c = shared.value?.centers?.[k] || { trips: EXC[k].trips, items: EXC[k].items, base: FARE[k] * EXC[k].trips, fin: FARE[k] * EXC[k].trips };
    const base = Math.round(c.base || 0), fin = Math.round(c.fin ?? base);
    return { nm: NM[k], trips: c.trips || 0, items: c.items || 0, base, fin };
  });
  const arrivedN = tr.reduce((a, c) => a + c.arrived, 0), fareSum = toll.reduce((a, r) => a + r.fin, 0);
  const hr = hourRows.value, hmax = Math.max(1, ...hr.map((h) => h.count)), hsum = hr.reduce((a, h) => a + h.count, 0);

  // ── 제목 · 종합 판단 ──
  add(span([{ v: "S-Bot 운영 현황 보고서", s: 5 }], 0, 9, 5), 30);
  add(span([{ v: `기준 ${stamp}  ·  데이터: ${src}  ·  대상: S카고 허브(곤지암) → 서울 · 경기 · 인천 물류센터`, s: 6 }], 0, 9, 6));
  add([]);
  const bs = bad.length ? 12 : warn.length ? 11 : 10;
  const sumTxt = `허브  화물차 ${kpis.value[0].v} 반입 · 택배 ${kpis.value[2].v} 처리 · AMR 가동 ${kpis.value[1].v} · 택배 1건 평균 운반 ${kpis.value[3].v}\n운송  허브→센터 운송 중 ${inTransitN.value}대 · 센터 도착 완료 ${arrivedN}대 · 오늘 통행료 확정 ${fareSum.toLocaleString("ko-KR")}원\n점검  ${al.length ? al.map((a) => a.t).join(" · ") : "확인이 필요한 항목 없음"}`;
  const n0 = rows.length + 1; merges.push(`A${n0}:B${n0}`, `C${n0}:J${n0}`);
  add([{ v: `종합 상태\n${lvl}`, s: bs }, { v: "", s: bs }, { v: sumTxt, s: 15 }, ...Array(7).fill({ v: "", s: 15 })], 66);

  // ── 1. 핵심 지표 ──
  sec("1. 핵심 지표  (각 항목이 무엇을 뜻하는지 함께 표시)");
  add(span([H("항목"), H("현황"), H("무엇을 보여주는 지표인가")], 2, 9, 1), 22);
  const DESC = ["오늘 허브에 들어온 화물차 수 (센터로 나간 차량 + 현재 운송 중인 차량)", "지금 가동 중인 AMR 수 / 전체 AMR 수 (충전 · 정비 · 예비 대기 중인 로봇은 가동에서 제외)", "오늘 허브에서 처리한 택배 건수 (센터로 보낸 물품 합계)", "AMR이 택배 1건을 옮기는 데 걸린 평균 시간 (짧을수록 처리 속도가 빠름)"];
  kpis.value.forEach((k, i) => add(span([T(k.t), { v: k.v, s: 14 }, W(DESC[i] || "")], 2, 9, 9), 22));
  add(span([T("허브→센터 운송 중"), C(`${inTransitN.value}대`), W("지금 도로 위에서 센터로 이동 중인 택배차 수 (아래 3번 표에서 센터별 · 차량번호 확인)")], 2, 9, 9), 22);
  add(span([T("센터 도착 완료"), C(`${arrivedN}대`), W("오늘 센터에 입고를 마친 택배차 수")], 2, 9, 9), 22);
  add(span([T("통행료 확정 합계"), C(`${fareSum.toLocaleString("ko-KR")}원`), W("오늘 센터 3곳 통행료 합계 (심야 할인 반영 후 금액, 4번 표 참고)")], 2, 9, 9), 22);

  // ── 2. 확인 필요 ──
  sec("2. 확인 필요 사항  (위험: 즉시 조치 · 주의: 확인)");
  { const n = rows.length + 1; merges.push(`B${n}:H${n}`, `I${n}:J${n}`);
    add([H("구분"), H("내용"), ...Array(6).fill(H("")), H("이동할 화면"), H("")], 22); }
  const alRows = al.length ? al : [{ lv: "ok", t: "확인이 필요한 항목이 없습니다 (AMR 정상 · 운송 지연 없음)", to: "" }];
  alRows.forEach((a) => {
    const st = a.lv === "bad" ? 12 : a.lv === "warn" ? 11 : 10, n = rows.length + 1;
    merges.push(`B${n}:H${n}`, `I${n}:J${n}`);
    add([{ v: a.lv === "bad" ? "위험" : a.lv === "warn" ? "주의" : "정상", s: st }, W(a.t), ...Array(6).fill(W("")), C(ROUTE[String(a.to).split("?")[0]] || "-"), C("")], 22);
  });

  // ── 3. 센터별 운송 ──
  sec("3. 센터별 운송 현황  (허브 → 물류센터, 거리 순)");
  add([H("센터"), H("거리(km)"), H("운송 중(대)"), H("도착(대)"), H("전자제품(건)"), H("의류(건)"), H("식품(건)"), H("기타(건)"), H("물품 합계(건)"), H("운송 중 차량")], 32);
  const a3 = rows.length + 1;
  tr.forEach((c) => { const n = rows.length + 1, tot = c.cat.reduce((a, b) => a + b, 0);
    add([T(c.n + "센터"), N(c.km), N(c.transit.length), N(c.arrived), ...c.cat.map(N), { v: tot, f: `SUM(E${n}:H${n})`, s: 2 }, W(c.transit.map((x) => x.no).join(", ") || "-")], 22); });
  const b3 = rows.length, S3 = (col, v) => ({ v, f: `SUM(${col}${a3}:${col}${b3})`, s: 3 });
  add([TT("합계"), TT(""), S3("C", tr.reduce((a, c) => a + c.transit.length, 0)), S3("D", arrivedN), ...[0, 1, 2, 3].map((j) => S3("EFGH"[j], tr.reduce((a, c) => a + c.cat[j], 0))), S3("I", tr.reduce((a, c) => a + c.cat.reduce((x, y) => x + y, 0), 0)), TT("")], 22);

  // ── 4. 입고 · 통행료 ──
  sec(`4. 센터별 입고 · 통행료  ${ex ? "(예시값: 오늘 운행 기록 없음)" : "(S-Bot 오늘 누적)"}`);
  add([H("센터"), H("도착 차량(회)"), H("입고 물품(건)"), H("기본 요금(원)"), H("할인(원)"), H("확정 금액(원)"), H("할인율(%)"), H("차량당 요금(원)")], 32);
  const a4 = rows.length + 1;
  toll.forEach((r) => { const n = rows.length + 1, dis = r.base - r.fin;
    add([T(r.nm + "센터"), N(r.trips), N(r.items), N(r.base), { v: dis, f: `D${n}-F${n}`, s: 2 }, N(r.fin), { v: r.base ? Math.round((dis / r.base) * 1000) / 10 : 0, f: `IF(D${n}=0,0,E${n}/D${n}*100)`, s: 13 }, { v: r.trips ? Math.round(r.fin / r.trips) : 0, f: `IF(B${n}=0,0,F${n}/B${n})`, s: 2 }], 22); });
  const b4 = rows.length, S4 = (col, v) => ({ v, f: `SUM(${col}${a4}:${col}${b4})`, s: 3 }), n4 = rows.length + 1, tb = toll.reduce((a, r) => a + r.base, 0), tf = toll.reduce((a, r) => a + r.fin, 0), tt = toll.reduce((a, r) => a + r.trips, 0);
  add([TT("합계"), S4("B", tt), S4("C", toll.reduce((a, r) => a + r.items, 0)), S4("D", tb), S4("E", tb - tf), S4("F", tf), { v: tb ? Math.round(((tb - tf) / tb) * 1000) / 10 : 0, f: `IF(D${n4}=0,0,E${n4}/D${n4}*100)`, s: 18 }, { v: tt ? Math.round(tf / tt) : 0, f: `IF(B${n4}=0,0,F${n4}/B${n4})`, s: 3 }], 22);
  add(span([{ v: "할인 = 기본 요금 - 확정 금액 · 통행료는 통과 시각 기준 (06~21시 할인 없음 · 21~24시 30% · 00~06시 50%)", s: 17 }], 0, 9, 17));

  // ── 5. 시간대별 ──
  sec("5. 시간대별 처리 건수  (허브 처리 현황 그래프와 같은 데이터)");
  add(span([H("시간대"), H("처리 건수"), H("비중(%)"), H("건수 막대")], 3, 8, 1).concat([H("비고")]), 22);
  const a5 = rows.length + 1;
  hr.forEach((h) => { const n = rows.length + 1, hh = Number(h.hour), bar = "█".repeat(Math.round((h.count / hmax) * 24));
    add(span([T(`${p(hh)}~${p(hh + 2)}시`), N(h.count), { v: hsum ? Math.round((h.count / hsum) * 1000) / 10 : 0, f: `IF(B$${a5 + hr.length}=0,0,B${n}/B$${a5 + hr.length}*100)`, s: 13 }, { v: bar, f: `REPT("█",ROUND(B${n}/MAX(B$${a5}:B$${a5 + hr.length - 1})*24,0))`, s: 16 }], 3, 8, 16).concat([C(h.count === hmax && hmax > 0 ? "▲ 피크" : "")]), 22); });
  add(span([TT("합계"), { v: hsum, f: `SUM(B${a5}:B${a5 + hr.length - 1})`, s: 3 }, { v: 100, s: 18 }, TT("")], 3, 8, 4).concat([TT("")]), 22);

  // ── 6. AMR 상세 ──
  sec("6. AMR 로봇 상세  (가동률은 최근 1시간 기준 · 처리 건수는 오늘 누적)");
  add(span([H("로봇"), H("상태"), H("가동률(%)"), H("시간당 처리(건)"), H("오늘 처리(건)"), H("충전(회)"), H("정비(회)"), H("비고")], 7, 9, 1), 32);
  const NOTE = { fault: "정비 중 · 작업 불가, 정비 완료 후 복귀", chg: "충전 중 · 충전 완료 후 복귀", sp: "예비 대기 · 관리자 승인 후 투입", ok: "" };
  const a6 = rows.length + 1;
  am.forEach((a) => { const n = rows.length + 1, st = a.cls === "fault" ? 12 : a.cls === "chg" ? 11 : a.cls === "ok" ? 10 : 14;
    add(span([T(a.code), { v: a.label, s: st }, { v: a.pct, s: 13 }, N(a.hour), N(a.today), N(a.charge), N(a.fault), W(NOTE[a.cls] || "")], 7, 9, 9), 22); });
  if (!am.length) add(span([T("-"), C("데이터 없음"), W("AMR 기록을 불러오지 못했습니다 (서버 연결 확인)")], 2, 9, 9), 22);
  else { const b6 = rows.length, n6 = rows.length + 1, SA = (col, v) => ({ v, f: `SUM(${col}${a6}:${col}${b6})`, s: 3 });
    add(span([TT("합계 · 평균"), TT(""), { v: Math.round((am.reduce((a, r) => a + r.pct, 0) / am.length) * 10) / 10, f: `AVERAGE(C${a6}:C${b6})`, s: 18 }, SA("D", am.reduce((a, r) => a + r.hour, 0)), SA("E", am.reduce((a, r) => a + r.today, 0)), SA("F", am.reduce((a, r) => a + r.charge, 0)), SA("G", am.reduce((a, r) => a + r.fault, 0)), TT("")], 7, 9, 4), 22); }

  add([]);
  add(span([{ v: "판단 기준: AMR 정비 중 = 위험 · AMR 충전 중 / 센터 운송 중 5대 이상 / 서버 연결 불가 = 주의. 그 외는 정상. 서버가 꺼져 있으면 시뮬레이션 예시값으로 표시됩니다.", s: 17 }], 0, 9, 17), 30);

  const sheet = { name: "운영 현황 보고서", widths: [16, 13, 13, 13, 14, 13, 13, 13, 14, 24], landscape: true, heights, merges, rows };
  downloadBlob(buildXlsx([sheet]), `S봇_운영현황보고서_${day}_${p(t.getHours())}${p(t.getMinutes())}.xlsx`);
}
const inTransitN = computed(() => transport.value.reduce((a, t) => a + t.transit.length, 0)); // 26.10.25 한 줄 요약용
const downN = computed(() => amrRows.value.filter((r) => r.cls === "fault" || r.cls === "chg").length);
const tbars = computed(() => {
  const mx = Math.max(1, ...transport.value.map((t) => t.cat.reduce((a, b) => a + b, 0)));
  return transportV.value.map((t) => {
    const total = t.cat.reduce((a, b) => a + b, 0); let x = 0;
    const segs = t.cat.map((v) => { const w = (v / mx) * 100; const s = { x, w }; x += w; return s; });
    return { ...t, total, segs, w: x };
  });
});
const EXC = { SEL: { trips: 6, items: 30 }, GGI: { trips: 6, items: 46 }, ICN: { trips: 4, items: 52 } };
const EX = { SEL: { items: 30, fin: 13590 }, GGI: { items: 46, fin: 11160 }, ICN: { items: 52, fin: 12620 } };
const val = (q) => shared.value?.centers?.[q] || EX[q];
const centers = computed(() => [
  { n: "서울", q: "SEL", v: val("SEL").items, x: 168, y: 205 },
  { n: "경기", q: "GGI", v: val("GGI").items, x: 118, y: 128 },
  { n: "인천", q: "ICN", v: val("ICN").items, x: 64, y: 58 },
]);
const fareTotal = computed(() => ["GGI", "SEL", "ICN"].reduce((a, q) => a + val(q).fin, 0));
const hourRows = computed(() => {
  const cs = Object.values(shared.value?.centers || {});
  if (!cs.length) return EX_HOURS;
  return ["6", "8", "10", "12", "14", "16", "18", "20"].map((h, i) => ({ hour: h, count: cs.reduce((a, c) => a + ((c.hr || [])[i] || 0), 0) }));
});
const hours = computed(() => hourRows.value.map((h) => h.count));
const hourLabels = computed(() => hourRows.value.map((h) => h.hour));
const peak = computed(() => Math.max(...hours.value));
const sc = computed(() => 60 / Math.max(1, peak.value));
const peakLabel = computed(() => `${hourRows.value[hours.value.indexOf(peak.value)].hour} · ${peak.value}건`);
const line = computed(() => hours.value.map((v, i) => `${24 + i * 40},${90 - v * sc.value}`).join(" "));
// AMR 장비별 처리 현황: 가동률은 KPI(최근 1시간 센서), 시간당/오늘 건수는 DB 운반 기록(amr_task_logs)
const amrRows = computed(() => {
  const a = shared.value?.src === "db" ? shared.value.amr || [] : [];
  const um = new Map(util.value.map((u) => [u.code, u.pct]));
  const codes = [...new Set([...a.map((x) => x.code), ...util.value.map((u) => u.code)])].filter((c) => c !== "-").sort();
  return codes.map((c) => {
    const r = a.find((x) => x.code === c) || {};
    const st = r.state || "";
    const down = st === "MAINT" || st === "TO_MAINT" ? "fault" : st === "CHARGING" ? "chg" : "";
    const spareCode = c === "AMR-05"; // 예비기: 관리자가 정비를 승인해 투입(ACTIVE)하기 전에는 대기
    const label = down === "fault" ? "정비 중" : down === "chg" ? "충전 중" : r.spare ? "예비 대기" : spareCode ? "예비기 투입 중" : "정상 가동";
    const rate = down === "fault" ? "정비중" : down === "chg" ? "충전중" : r.spare ? "예비 대기" : r.hour ? r.hour + "건/시" : "—";
    return { code: c, hour: r.hour || 0, today: r.today || 0, charge: r.charge || 0, fault: r.fault || 0, pct: um.get(c) ?? 0, cls: down || (r.spare ? "sp" : "ok"), label, rate };
  });
});
const gauge = computed(() => (util.value.length ? Math.round(util.value.reduce((s, u) => s + u.pct, 0) / util.value.length) : 67));
const gaugeNote = computed(() => (util.value.length ? `최근 1시간 평균 · 장비 ${util.value.length}대` : "AMR 4 · 예비 1 · 지게차 1 (예시)"));
const rows = computed(() => [{ n: "경기", c: "#4aa3ff", v: val("GGI").items }, { n: "인천", c: "#4cc38a", v: val("ICN").items }, { n: "서울", c: "#f0a800", v: val("SEL").items }]);
const total = computed(() => rows.value.reduce((s, r) => s + r.v, 0));
const donut = computed(() => { let o = 0; return rows.value.map((r) => { const p = (r.v / Math.max(1, total.value)) * 100; const d = { ...r, p, o }; o += p; return d; }); });
</script>

<style scoped>
.sumline { background: #123a46; border: 1px solid #1f5c66; border-radius: 8px; padding: 9px 14px; font-size: 14px; margin-bottom: 12px; }
.sumline b { color: #ffc857; }
.alerts { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; background: #3a1b22; border: 1px solid #7a2e3a; border-radius: 8px; padding: 7px 12px; margin-bottom: 10px; font-size: 13px; }
.al-t { color: #ff9aa5; font-weight: 700; }
.al { text-decoration: none; padding: 2px 10px; border-radius: 99px; font-weight: 700; }
.al.bad { background: #ff6b6b; color: #2a0b10; } .al.warn { background: #ffc857; color: #2a2108; }
.tools { display: flex; align-items: center; gap: 6px; margin-bottom: 10px; font-size: 12px; color: #98a0ad; }
.tools button { background: #1c2f50; border: 1px solid #2a3a58; color: #c9d1e0; border-radius: 6px; padding: 3px 12px; font-size: 12px; cursor: pointer; }
.tools button.on { background: #f3d27a; color: #1a2540; border-color: #f3d27a; font-weight: 700; }
.tools .csv { margin-left: auto; }
.sumline em { color: #ff7b7b; font-style: normal; font-weight: 700; }
.sbd { display: flex; flex-direction: column; gap: 10px; color: #e6eff7; height: calc(100vh - 122px); min-height: 520px; } /* 한 화면에 맞춤 (헤더 64 + 여백) */
.card, .kpi { background: #13243f; border: 1px solid #2a3a58; border-radius: 10px; }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.kpi { padding: 6px 14px; } .kpi small { display: block; color: #98a0ad; font-size: 12px; } .kpi b { color: #f3d27a; font-size: 20px; }
.flow { display: flex; align-items: center; gap: 8px; background: #13243f; border: 1px solid #2a3a58; border-radius: 999px; padding: 6px 16px; }
.flow-t { color: #98a0ad; font-size: 13px; margin-right: 6px; } .spd { margin-left: auto; color: #98a0ad; font-size: 12px; display: flex; align-items: center; gap: 4px; } .spd button { background: #1c2f50; border: 1px solid #2a3a58; color: #c9d1e0; border-radius: 6px; padding: 1px 8px; font-size: 12px; cursor: pointer; } .spd button.on { background: #f3d27a; color: #1a2540; border-color: #f3d27a; font-weight: 700; } .flow-h { margin-left: 14px; color: #98a0ad; font-size: 12px; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; } .dot.on { background: #4fdc8a; } .dot.off { background: #8a9bb0; }
.chip { color: #f3d27a; font-weight: 700; text-decoration: none; padding: 4px 12px; border: 1px solid #f3d27a; border-radius: 999px; }
.chip:hover { background: #f3d27a; color: #14203a; } .ar { font-style: normal; margin-left: 10px; color: #f3d27a; }
.grid { display: grid; grid-template-columns: 1.15fr 1fr; grid-template-rows: minmax(0, 1fr); gap: 10px; flex: 1; min-height: 0; }
.scene { padding: 8px 12px 8px; display: flex; flex-direction: column; min-height: 0; } .scene svg { flex: 1; min-height: 0; width: 100%; background: #0d2040; border-radius: 8px; }
.road { fill: none; stroke: #2b3140; stroke-width: 8; stroke-linecap: round; }
.clk { cursor: pointer; } .clk:hover { filter: brightness(1.2); }
.lab { fill: #e9ebef; font-size: 9px; text-anchor: middle; } .lab.gold { fill: #f3d27a; font-weight: 700; } .lab.dim { fill: #98a0ad; } .lab.l { text-anchor: start; }
.note { margin: 6px 0 0; color: #98a0ad; font-size: 12px; }
.right { display: flex; flex-direction: column; gap: 10px; min-height: 0; } .right > .card { flex: 1; min-height: 0; } .two { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; flex: 1; min-height: 0; }
.right .card { padding: 8px 14px; display: flex; flex-direction: column; min-height: 0; } h3 { margin: 0 0 4px; color: #f3d27a; font-size: 14px; } .chart { flex: 1; min-height: 0; width: 100%; }
.ax { stroke: #2a3a58; } .bar { fill: #5b82c4; } .pk { fill: #f3d27a; }
.g-bg { fill: none; stroke: #2a3a58; stroke-width: 10; stroke-linecap: round; } .g-fg { fill: none; stroke: #f3d27a; stroke-width: 10; stroke-linecap: round; }
.big { fill: #e9ebef; font-size: 18px; font-weight: 700; text-anchor: middle; } .big.sm { font-size: 14px; } .dim { color: #98a0ad; font-size: 12px; }
.btns { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.btn { display: flex; flex-direction: column; gap: 2px; background: #13243f; border: 1px solid #f3d27a; border-radius: 10px; padding: 8px 16px; text-decoration: none; }
.btn b { color: #f3d27a; font-size: 15px; } .btn span { color: #98a0ad; font-size: 12px; } .btn:hover { background: #1a3254; }
@media (max-width: 900px) { .sbd { height: auto; } .grid { grid-template-columns: 1fr; } .kpis { grid-template-columns: repeat(2, 1fr); } .btns { grid-template-columns: 1fr; } }
.tr { margin-top: 6px; text-align: left; } .tr h3 { text-align: left; }
.tr table, .tr thead, .tr tbody, .tr tr, .tr th, .tr td { background: transparent !important; color: #e6eff7 !important; }
.tr th { color: #98a0ad !important; } .tr td .dim, .tr th .dim { color: #98a0ad !important; } .tr .gold { color: #f3d27a !important; }
.tr tbody tr:hover { background: #1a2f52 !important; }
.tr table { table-layout: fixed; } .tr th, .tr td { text-align: center !important; vertical-align: middle; }
.tr th:nth-child(1) { width: 17%; } .tr th:nth-child(2) { width: 28%; } .tr th:nth-child(3) { width: 8%; } .tr th:nth-child(4) { width: 47%; }
.tr td { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; padding: 2px 6px !important; } .tr td:nth-child(2) small { margin-left: 6px; } .tr h3 small { margin-left: 8px; font-weight: 400; }
.tr table { width: 100%; border-collapse: collapse; font-size: 12px; } .tr th { color: #98a0ad; font-weight: 400; text-align: center; padding: 2px 6px; border-bottom: 1px solid #2a3a58; }
.tr td { padding: 3px 6px; border-bottom: 1px solid #1d2f4d; } .tr tbody tr { cursor: pointer; } .tr tbody tr:hover { background: #1a2f52; }
.sq { display: inline-block; width: 8px; height: 8px; margin-right: 6px; border-radius: 2px; } .gold { color: #f3d27a; }
.cats span { margin-right: 6px; white-space: nowrap; font-size: 11px; }
.sech { color: #f3d27a; font-weight: 700; font-size: 14px; margin-bottom: -4px; } .sech small { color: #98a0ad; font-weight: 400; font-size: 12px; margin-left: 8px; }
.lab.r { text-anchor: end; fill: #98a0ad; font-size: 7px; } .right h3 small { font-weight: 400; margin-left: 6px; }
.two { grid-template-columns: .7fr 1.3fr !important; }
.row2 { display: flex; gap: 10px; flex: 1; min-height: 0; align-items: center; } .row2 .c1 { flex: 1.8; } .gbox { flex: 1; display: flex; flex-direction: column; align-items: center; min-height: 0; } .gbox svg { flex: 1; min-height: 0; width: 100%; } .gbox small { text-align: center; }
.tx { fill: #e9ebef; font-size: 10px; } .tx.dim { fill: #98a0ad; font-size: 9px; } .tx.gold { fill: #f3d27a; font-weight: 700; } .tx.r { text-anchor: end; } .tx.seg { fill: #0b1d36; font-size: 10px; font-weight: 700; text-anchor: middle; }
.ctr { text-align: center; } .cwrap { flex: 1.8; display: flex; flex-direction: column; align-items: center; min-height: 0; align-self: stretch; } .cwrap svg { flex: 1; min-height: 0; width: 100%; } .row2 .c1 { flex: none; } .scene h3.ctr small { display: block; margin: 2px 0 6px; }
.amrl { flex: none; display: grid; grid-template-columns: repeat(3, 1fr); gap: 2px 12px; margin-top: 4px; padding-top: 4px; border-top: 1px solid rgba(255,255,255,.08); font-size: 11px; line-height: 1.35; } .amrr { display: flex; gap: 5px; align-items: baseline; white-space: nowrap; } .amrr b { color: #e9ebef; font-weight: 600; min-width: 42px; } .amrr span { color: #c9d1e0 !important; } .amrr span.gold { color: #f3d27a !important; } .amrr .gold { color: #f3d27a; font-weight: 700; } .amrr .dot { width: 7px; height: 7px; border-radius: 50%; align-self: center; flex: none; background: #4cc38a; } .amrr .dot.chg { background: #f0a800; } .amrr .dot.fault { background: #ff5a5a; } .amrr .dot.sp { background: #5aa8ff; } .amrr .cnt { font-style: normal; color: #98a0ad; font-size: 10px; } .amrr .dim { color: #98a0ad; } .right > .card:first-child { flex: 1.5; overflow: hidden; } .row2 { overflow: hidden; min-height: 96px; } .row2 .cwrap, .row2 .gbox { min-height: 0; overflow: hidden; }
.tx.c { text-anchor: middle; }
.gbox svg { max-height: 96px; }
</style>
