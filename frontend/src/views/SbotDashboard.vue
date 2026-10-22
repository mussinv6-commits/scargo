<template>
  <!-- 26.10.22 추가: S-Bot 대시보드. 허브 → 택배 이동 → 물류센터 흐름 글자를 누르면 해당 화면으로 이동.
       숫자는 예시 데이터(DB 연결은 다음 단계). 3D 화면은 각 화면에서 따로 띄운다. -->
  <div class="sbd">
    <div class="sech">허브 처리 현황 <small>AMR 작업 기준 · 센터 현황과 같은 데이터</small></div>
    <div class="kpis">
      <div v-for="k in kpis" :key="k.t" class="kpi"><small>{{ k.t }}</small><b>{{ k.v }}</b></div>
    </div>

    <div class="flow">
      <span class="flow-t">운송 흐름</span>
      <RouterLink v-for="(f, i) in flow" :key="f.t" :to="f.to" class="chip">{{ f.t }}<i v-if="i < flow.length - 1" class="ar">→</i></RouterLink>
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
              <tr v-for="t in transport" :key="t.q" @click="go('/sbot/center?c=' + t.q)">
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
                <text x="60" y="52" class="big">{{ gauge }}%</text>
              </svg>
              <small class="dim">AMR 가동률</small>
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
  const k = await amrApi.kpi();
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
// 허브 처리 현황: 센터 현황과 숫자가 어긋나지 않도록, 반입 차량·처리 건수는 센터로 나간 물량(공용 데이터)에서 계산한다.
// AMR 가동·택배 1건 운반시간은 DB(/api/amr/kpi)에 값이 있으면 DB를 쓴다.
const kpis = computed(() => {
  const k = live.value, db = hasDb.value;
  const cs = Object.values(shared.value?.centers || EXC);
  const out = cs.reduce((a, c) => a + c.trips, 0), items = cs.reduce((a, c) => a + c.items, 0);
  const inTransit = transport.value.reduce((a, t) => a + t.transit.length, 0);
  const hub = shared.value?.src === "db" && shared.value.hub?.items > 0 ? shared.value.hub : null; // DB 모드: 허브 입고(오늘 들어온 화물차·물품)
  const act = db ? util.value.filter((u) => u.pct > 0).length : 4, all = db ? util.value.length : 6;
  const sec = db && k.avgTransportSec != null ? Math.round(k.avgTransportSec) : 192;
  return [
    { t: "허브 반입 차량", v: `${hub ? hub.trucks : out + inTransit}대` },
    { t: "AMR 가동", v: `${act} / ${all}` },
    { t: "허브 처리 건수", v: `${hub ? hub.items : items}건` },
    { t: "AMR 택배 1건 평균 운반", v: sec >= 60 ? `${Math.floor(sec / 60)}분 ${sec % 60}초` : `${sec}초` },
  ];
});
const flow = [{ t: "허브", to: "/sbot/hub" }, { t: "택배 이동", to: "/sbot/move" }, { t: "물류센터", to: "/sbot/center" }];
// 센터별 값은 택배 이동 화면이 센터 도착 때마다 기록하는 공용 데이터(public/sbot3d/shared.js 와 같은 키)를 읽는다.
// 같은 값을 물류센터 관제 화면도 읽는다. 기록이 없으면 기준값(예시)을 보여 준다.
const SKEY = "sbot_shared_v2";
const shared = ref(null);
const dayKey = () => { const d = new Date(); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`; };
function readShared() {
  try { const s = JSON.parse(localStorage.getItem(SKEY)); shared.value = s && s.day === dayKey() && s.centers ? s : null; } catch { shared.value = null; }
}
let sTimer;
const onStorage = (e) => { if (!e.key || e.key === SKEY) readShared(); };
onMounted(() => { readShared(); sTimer = setInterval(readShared, 1000); window.addEventListener("storage", onStorage); });
onBeforeUnmount(() => { clearInterval(sTimer); window.removeEventListener("storage", onStorage); });
const CATL = ["전자제품", "의류", "식품", "기타"];
// 허브 → 물류센터 운송 현황: 택배 이동 화면의 택배차 상태(이동 중/입고 완료)와 물품 구성을 그대로 읽는다 (DB: shipment_legs.status, hub_dispatches)
const transport = computed(() => [["SEL", "서울", 38, "#f0a800"], ["GGI", "경기", 55, "#4aa3ff"], ["ICN", "인천", 75, "#4cc38a"]].map(([q, n, km, c]) => {
  const s = shared.value?.centers?.[q];
  const tk = s?.trucks || [];
  return { q, n, km, c, transit: tk.filter((x) => x.status !== "ARRIVED"), arrived: tk.filter((x) => x.status === "ARRIVED").length, cat: s?.cat || [0, 0, 0, 0] };
}));
const CATC = ["#4aa3ff", "#f0a800", "#4cc38a", "#a78bfa"];
const tbars = computed(() => {
  const mx = Math.max(1, ...transport.value.map((t) => t.cat.reduce((a, b) => a + b, 0)));
  return transport.value.map((t) => {
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
const gauge = computed(() => (util.value.length ? Math.round(util.value.reduce((s, u) => s + u.pct, 0) / util.value.length) : 67));
const gaugeNote = computed(() => (util.value.length ? `최근 1시간 평균 · 장비 ${util.value.length}대` : "AMR 4 · 예비 1 · 지게차 1 (예시)"));
const rows = computed(() => [{ n: "경기", c: "#4aa3ff", v: val("GGI").items }, { n: "인천", c: "#4cc38a", v: val("ICN").items }, { n: "서울", c: "#f0a800", v: val("SEL").items }]);
const total = computed(() => rows.value.reduce((s, r) => s + r.v, 0));
const donut = computed(() => { let o = 0; return rows.value.map((r) => { const p = (r.v / Math.max(1, total.value)) * 100; const d = { ...r, p, o }; o += p; return d; }); });
</script>

<style scoped>
.sbd { display: flex; flex-direction: column; gap: 10px; color: #e6eff7; height: calc(100vh - 122px); min-height: 480px; } /* 한 화면에 맞춤 (헤더 64 + 여백) */
.card, .kpi { background: #13243f; border: 1px solid #2a3a58; border-radius: 10px; }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.kpi { padding: 6px 14px; } .kpi small { display: block; color: #98a0ad; font-size: 12px; } .kpi b { color: #f3d27a; font-size: 20px; }
.flow { display: flex; align-items: center; gap: 8px; background: #13243f; border: 1px solid #2a3a58; border-radius: 999px; padding: 6px 16px; }
.flow-t { color: #98a0ad; font-size: 13px; margin-right: 6px; } .flow-h { margin-left: auto; color: #98a0ad; font-size: 12px; }
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
.tr table { table-layout: fixed; } .tr th, .tr td { text-align: left !important; vertical-align: middle; }
.tr th:nth-child(1) { width: 17%; } .tr th:nth-child(2) { width: 28%; } .tr th:nth-child(3) { width: 8%; } .tr th:nth-child(4) { width: 47%; }
.tr td { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; padding: 2px 6px !important; } .tr td:nth-child(2) small { margin-left: 6px; } .tr h3 small { margin-left: 8px; font-weight: 400; }
.tr table { width: 100%; border-collapse: collapse; font-size: 12px; } .tr th { color: #98a0ad; font-weight: 400; text-align: left; padding: 2px 6px; border-bottom: 1px solid #2a3a58; }
.tr td { padding: 3px 6px; border-bottom: 1px solid #1d2f4d; } .tr tbody tr { cursor: pointer; } .tr tbody tr:hover { background: #1a2f52; }
.sq { display: inline-block; width: 8px; height: 8px; margin-right: 6px; border-radius: 2px; } .gold { color: #f3d27a; }
.cats span { margin-right: 6px; white-space: nowrap; font-size: 11px; }
.sech { color: #f3d27a; font-weight: 700; font-size: 14px; margin-bottom: -4px; } .sech small { color: #98a0ad; font-weight: 400; font-size: 12px; margin-left: 8px; }
.lab.r { text-anchor: end; fill: #98a0ad; font-size: 7px; } .right h3 small { font-weight: 400; margin-left: 6px; }
.two { grid-template-columns: .7fr 1.3fr !important; }
.row2 { display: flex; gap: 10px; flex: 1; min-height: 0; align-items: center; } .row2 .c1 { flex: 1.8; } .gbox { flex: 1; display: flex; flex-direction: column; align-items: center; min-height: 0; } .gbox svg { flex: 1; min-height: 0; width: 100%; } .gbox small { text-align: center; }
.tx { fill: #e9ebef; font-size: 10px; } .tx.dim { fill: #98a0ad; font-size: 9px; } .tx.gold { fill: #f3d27a; font-weight: 700; } .tx.r { text-anchor: end; } .tx.seg { fill: #0b1d36; font-size: 10px; font-weight: 700; text-anchor: middle; }
.ctr { text-align: center; } .cwrap { flex: 1.8; display: flex; flex-direction: column; align-items: center; min-height: 0; align-self: stretch; } .cwrap svg { flex: 1; min-height: 0; width: 100%; } .row2 .c1 { flex: none; } .scene h3.ctr small { display: block; margin: 2px 0 6px; }
.tx.c { text-anchor: middle; }
.gbox svg { max-height: 130px; }
</style>
