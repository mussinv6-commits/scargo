<template>
  <!-- 26.10.22 S-Bot 대시보드 / 26.10.29 팀원 최신본(화재 경보 · 예지보전 · AMR별 운영 · 병목 · 화재 기록 · 보고서)을
       10/28 S Cargo 밝은 디자인(아이콘 KPI 카드 · 머리말 패널 · 새 시간대 그래프 · 큰 가동률 게이지)으로 다시 입힘 -->
  <div class="sbd">
    <!-- 화재 경보 배너: AMR 관제에서 화재(허브·배터리)가 진행 중이면 맨 위에 표시 -->
    <div v-if="fire.active && (srvAlert === null || srvAlert.active)" class="firebar" role="alert"><b>🔥 {{ fire.active.kind === "batt" ? "AMR 배터리 화재" : "허브 화재 경보" }}</b> · {{ fire.active.label }} · {{ fire.active.stage }} · 경과 {{ fmtDur(fireNow) }}<span v-if="fire.active.temp"> · 배터리 {{ fire.active.temp }}℃</span><RouterLink to="/sbot/hub" class="fgo">허브 관제 →</RouterLink></div>

    <!-- 요약 한 줄 + 날짜 · 보고서 -->
    <div class="topbar">
      <div class="sumline"><i class="bi bi-lightning-charge-fill"></i>오늘 허브 처리 <b>{{ kpis[2].v }}</b> · AMR 가동 <b>{{ kpis[1].v }}</b><template v-if="downN"> · 정비·충전 중 <em>AMR {{ downN }}대</em></template></div>
      <div class="tools">
        <label class="dsel"><i class="bi bi-calendar3"></i> <input v-model="pickDate" type="date" :max="todayStr()" /><button v-if="pickDate" type="button" class="dclr" @click="pickDate = ''">오늘</button></label>
        <label class="dsel">보고 구분 <select v-model="pickPeriod"><option value="day">일간</option><option value="week">주간</option><option value="month">월간</option><option value="year">연간</option></select><span v-if="pickPeriod !== 'day'" class="dclr" style="pointer-events:none">{{ pickRange.from }} ~ {{ pickRange.to }}</span></label>
        <button type="button" class="csv" @click="exportAmr"><i class="bi bi-file-earmark-excel"></i> AMR 일일 보고서</button>
        <button type="button" class="csv" @click="exportSafety"><i class="bi bi-file-earmark-excel"></i> 안전사고 보고서</button>
      </div>
    </div>

    <!-- KPI 카드: 26.10.29 3개씩 두 줄 (위: 물량 · 아래: AMR). "AMR 처리 능력"은 그래프 아래 줄에 있어 카드에서 뺌 -->
    <div class="kpis">
      <template v-for="g in KPI_ROWS" :key="g.t">
        <div class="kgrp">{{ g.t }}</div>
        <div v-for="i in g.ids" :key="i" class="kpi" :class="ks(i).c">
          <span class="ic"><i class="bi" :class="ks(i).icon"></i></span>
          <span class="kt">
            <small>{{ kpis[i].t }}</small>
            <b :class="kpis[i].dc">{{ kv(kpis[i].v).num }}<em v-if="kv(kpis[i].v).unit">{{ kv(kpis[i].v).unit }}</em></b>
            <span class="sub">{{ kpis[i].d || ks(i).sub }}</span>
          </span>
        </div>
      </template>
    </div>

    <!-- 26.10.29 카드 아래는 탭으로 (스크롤 없이 한 화면 · S Cargo 운영 관리와 같은 탭 모양) -->
    <div class="stabs" role="tablist">
      <button v-for="t in TABS" :key="t.k" type="button" role="tab" class="stab" :class="{ on: tab === t.k, fire: t.k === 'fire' && fireOn }" @click="setTab(t.k)">
        <i class="bi" :class="t.icon"></i> {{ t.t }}<span v-if="tabN(t.k)" class="n">{{ tabN(t.k) }}</span>
      </button>
    </div>
    <div class="stbody">

    <!-- 탭 ① 처리 현황: 시간대별 처리 현황 | AMR 가동 현황 -->
    <div v-show="tab === 'flow'" class="grid">
      <section class="card panel">
        <header class="ph"><span class="pi"><i class="bi bi-bar-chart-line"></i></span><h3>시간대별 처리 현황</h3><span class="pr">{{ dateLabel }} · 최고 {{ peak }}건</span></header>
        <div class="hc">
          <div class="hc-y"><span v-for="t in yTicks" :key="t">{{ t }}</span></div>
          <div class="hc-plot">
            <div class="hc-grid"><i v-for="t in yTicks" :key="t"></i></div>
            <svg class="hc-line" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
              <defs><linearGradient id="hcArea" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1d4ed8" stop-opacity=".08"/><stop offset="1" stop-color="#1d4ed8" stop-opacity="0"/></linearGradient></defs>
              <polygon :points="areaPts" fill="url(#hcArea)"/>
              <polyline :points="linePts" fill="none" stroke="#1d4ed8" stroke-width="2" vector-effect="non-scaling-stroke" stroke-linejoin="round" stroke-linecap="round"/>
            </svg>
            <div class="hc-cols">
              <div v-for="(v, i) in hours" :key="i" class="hc-col" :class="{ pk: v === peak && peak > 0 }" :title="hourLabels[i] + '시 · ' + v + '건'">
                <span class="hc-v" :style="{ bottom: (v / yMax * 100) + '%' }">{{ v }}</span>
                <span class="hc-bar" :style="{ height: (v / yMax * 100) + '%' }"></span>
                <span class="hc-dot" :style="{ bottom: (v / yMax * 100) + '%' }"></span>
              </div>
            </div>
          </div>
          <div class="hc-x"><span v-for="(h, i) in hourLabels" :key="i" :class="{ pk: hours[i] === peak && peak > 0 }">{{ h }}시</span></div>
        </div>
        <div class="lg"><span><i class="b1"></i>처리 건수</span><span><i class="b2"></i>가장 많은 시간 ({{ peakLabel }})</span><span><i class="b3"></i>추세</span></div>
        <div class="capl">처리 능력 <b>{{ capNum }}건/시</b><template v-if="clearMin !== null"> · 오늘 투입 물량 소진 약 <b>{{ clearMin }}분</b></template></div>
      </section>

      <section class="card panel">
        <header class="ph"><span class="pi g"><i class="bi bi-cpu"></i></span><h3>AMR 가동 현황</h3><span v-if="downN" class="warnb" :title="'정비·충전 중 ' + downN + '대'">정비·충전 {{ downN }}대</span><span class="spd" title="AMR 작동 시뮬레이션 배속"><em class="spl">배속</em><button v-for="x in [1, 2, 4, 8]" :key="x" type="button" :class="{ on: speed === x }" @click="setSpeed(x)">{{ x }}×</button></span></header>
        <div class="amr-wrap">
          <div class="amr-top big">
            <div class="gbox">
              <svg viewBox="0 0 120 80" class="chart">
                <path d="M16,62 A44,44 0 0 1 104,62" pathLength="100" class="g-bg"/>
                <path d="M16,62 A44,44 0 0 1 104,62" pathLength="100" class="g-fg" :stroke-dasharray="`${gauge} 100`"/>
                <text x="60" y="54" class="big">{{ gauge }}%</text>
                <text x="60" y="74" class="tx dim c">AMR 가동률</text>
              </svg>
            </div>
            <small class="gnote">{{ gaugeNote }}</small>
            <p class="dbst"><i :class="(hasDb || sbotDb) && !stale ? 'on' : 'off'"></i>{{ statusText }}<em class="lastu" :class="{ bad: stale }"> · 마지막 갱신 {{ lastText }}<template v-if="stale"> (응답 지연)</template></em></p>
          </div>
          <ul v-if="amrRows.length" class="amrl">
            <li v-for="a in amrRows" :key="a.code" class="amrr">
              <i class="dot" :class="a.cls" :title="a.label"></i><b>{{ a.code }}</b><span class="lb" :class="'s-' + a.cls">{{ a.label }}</span>
              <span class="bar"><i :style="{ width: a.pct + '%' }"></i></span><span class="pct">{{ a.pct }}%</span>
              <span class="gold" :title="'오늘 누적 ' + a.today + '건 · 충전 ' + a.charge + '회 · 정비 ' + a.fault + '회'">{{ a.rate }}</span>
            </li>
          </ul>
          <p v-else class="dim empty">AMR 기록이 없습니다.</p>
        </div>
      </section>
    </div>

    <!-- 탭 ② 확인 · 예지보전: 확인 필요 | 예지보전 | 병목 · 정비 -->
    <div v-show="tab === 'check'" class="tri">
      <section class="card panel">
        <header class="ph"><span class="pi r"><i class="bi bi-exclamation-triangle"></i></span><h3>확인 필요</h3><span class="pr">누르면 해당 화면</span></header>
        <div class="plist">
          <template v-if="alerts.length">
            <RouterLink v-for="a in alerts" :key="a.k" :to="a.to" class="arow" :class="{ done: aLv(a.k) === 3 }"><span class="tag" :class="a.lv">{{ a.lv === "bad" ? "긴급" : "주의" }}</span><span class="atx">{{ a.t }}</span><button type="button" class="ast" :class="'s' + aLv(a.k)" title="누르면 확인 → 조치 중 → 완료 순서로 바뀝니다" @click.prevent.stop="aNext(a.k)">{{ aLv(a.k) ? ASTEP[aLv(a.k)] : "미확인" }}</button></RouterLink>
          </template>
          <div v-else class="okline"><i class="bi bi-check-circle-fill"></i> 지금 확인할 이상이 없습니다</div>
        </div>
      </section>
      <section class="card panel">
        <header class="ph"><span class="pi p"><i class="bi bi-heart-pulse"></i></span><h3>예지보전</h3><span class="pr">건강 점수 · 남은 수명</span></header>
        <div class="plist pm">
          <div v-for="r in pmRows" :key="r.code" class="prow">
            <div class="prow1"><b>{{ r.code }}</b><span class="tag" :class="r.lv">{{ r.tag }}</span><span class="pm-h">{{ r.health == null ? "—" : r.health + "점" }}</span></div>
            <div class="pbar"><i :style="{ width: (r.health == null ? 0 : r.health) + '%' }" :class="r.lv"></i></div>
            <div v-if="r.note" class="pnote">{{ r.note }}</div>
          </div>
          <div v-if="!pmRows.length" class="okline">로봇 상태를 불러오지 못했습니다</div>
        </div>
      </section>
      <section class="card panel">
        <header class="ph"><span class="pi o"><i class="bi bi-stoplights"></i></span><h3>병목 · 정비 현황</h3><span class="pr">{{ dateLabel }}</span></header>
        <div class="plist">
          <div v-for="b in bottle" :key="b.t" class="brow"><span>{{ b.t }}</span><div class="pbar"><i :style="{ width: b.w + '%' }" :class="b.lv"></i></div><b>{{ b.v }}</b></div>
        </div>
      </section>
    </div>

    <!-- 탭 ③ AMR별 운영 현황 -->
    <section v-show="tab === 'robot'" class="card panel">
      <header class="ph"><span class="pi"><i class="bi bi-robot"></i></span><h3>AMR별 운영 현황</h3><span class="pr">상태가 급한 순 · 처리 물품은 허브 처리 건수를 로봇별 작업 비중으로 배분</span></header>
      <table v-if="robotTable.length" class="rtab">
        <thead><tr><th>로봇</th><th>상태</th><th>배터리</th><th>건강</th><th>가동률</th><th>처리 능력</th><th>처리 물품</th><th>충전 / 정비</th></tr></thead>
        <tbody>
          <tr v-for="r in robotTable" :key="r.code">
            <td><b>{{ r.code }}</b></td>
            <td><span class="tg" :class="r.lv">{{ r.label }}</span></td>
            <td :class="{ lowb: r.batt != null && 30 >= r.batt }">{{ r.batt == null ? "—" : r.batt + "%" }}</td>
            <td :class="r.hlv">{{ r.health == null ? "—" : r.health + "점" }}</td>
            <td>{{ r.pct }}%</td>
            <td>{{ r.hour }}건/시</td>
            <td>{{ r.alloc }}건</td>
            <td>{{ r.charge }} / {{ r.fault }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="dim empty">AMR 기록을 불러오지 못했습니다 (서버 연결 확인)</div>
    </section>

    <!-- 탭 ④ 화재 · 안전 기록 -->
    <section v-show="tab === 'fire'" class="card panel">
      <header class="ph"><span class="pi r"><i class="bi bi-fire"></i></span><h3>화재 · 안전 기록</h3><span class="pr">{{ dateLabel }} {{ fireToday.length }}건 · AMR 관제에서 기록</span></header>
      <table v-if="fireToday.length" class="rtab ftab">
        <thead><tr><th>시작</th><th>해제</th><th>구분</th><th>구역·장비</th><th>대피 / 소요</th><th>내용</th></tr></thead>
        <tbody><tr v-for="(f, i) in fireToday" :key="i"><td>{{ hhmm(f.start) }}</td><td>{{ hhmm(f.end) }}</td><td><span class="tg" :class="f.kind === 'batt' ? 'chg' : 'fault'">{{ f.kind === "batt" ? "배터리 화재" : "허브 화재" }}</span></td><td>{{ f.label }}</td><td>{{ f.kind === "hub" ? "AMR " + f.evac + "/" + f.total + "대 대피 · " : "" }}{{ fmtDur(f.secs) }}</td><td class="l is-left">{{ f.note }}</td></tr></tbody>
      </table>
      <div v-else class="dim empty">{{ dateLabel }} 화재 · 안전 기록이 없습니다</div>
    </section>
    </div>
  </div>
</template>


<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { amrApi } from "@/components/amr/amrApi.js";
import { useRouter } from "vue-router";
import { buildXlsx, downloadBlob } from "@/utils/xlsxLite.js";
import { readFire } from "@/utils/fireLog.js"; // 26.10.29 화재 기록 (AMR 관제에서 기록)
import { createReport, nowInfo } from "@/utils/reportXlsx.js"; // 26.10.28 보고서 공통 양식
const router = useRouter();
const go = (p) => router.push(p);

// ── DB 실시간: 허브 AMR 관제와 같은 /api/amr/kpi 를 5초마다 읽는다. 서버가 꺼져 있으면 예시값을 보여 준다 ──
const live = ref(null);
const online = ref(false);
const robots = ref([]); // 로봇별 배터리 · 건강 점수 (/api/amr/state)
const lastOkAt = ref(0); // 서버에서 마지막으로 값을 받은 시각
const nowTick = ref(Date.now());
const maint = ref([]); // 정비 이력 · 예약 (/api/amr/maintenance)
// 진행 중 · 예약 정비는 최근 12시간 안에 잡힌 건만 본다 (예전 시연에서 끝내지 않은 오래된 예약이 지금 상태처럼 보이는 것을 막는다)
const openMaint = computed(() => {
  const now = Date.now(), seen = new Set();
  return maint.value.filter((m) => (m.status === "SCHEDULED" || m.status === "IN_PROGRESS") && now - new Date(m.startedAt || m.scheduledAt || m.approvedAt || 0).getTime() < 12 * 3600 * 1000)
    .sort((a, b) => new Date(b.startedAt || b.scheduledAt || 0) - new Date(a.startedAt || a.scheduledAt || 0)).filter((m) => (seen.has(m.robotCode) ? false : seen.add(m.robotCode)));
});
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
  if (k) { live.value = k; lastOkAt.value = Date.now(); }
  try { const m = await amrApi.maintenanceList(30); if (Array.isArray(m)) maint.value = m; } catch { /* 무시 */ }
  try { const st = await amrApi.state(); if (st && Array.isArray(st.robots)) robots.value = st.robots; } catch { /* 무시 */ }
}
let timer;
let tickTimer;
let offAuth;
onMounted(() => { amrApi.useCookieAuth(); offAuth = amrApi.onAuth((ok) => { locked.value = !ok; }); poll(); timer = setInterval(poll, 5000); tickTimer = setInterval(() => { nowTick.value = Date.now(); }, 1000); });
onBeforeUnmount(() => { clearInterval(timer); clearInterval(tickTimer); if (offAuth) offAuth(); });

const hms = (t) => [t.getHours(), t.getMinutes(), t.getSeconds()].map((n) => String(n).padStart(2, "0")).join(":");
const lastText = computed(() => (lastOkAt.value ? hms(new Date(lastOkAt.value)) : "—"));
const stale = computed(() => !online.value || (lastOkAt.value > 0 && nowTick.value - lastOkAt.value > 15000)); // 15초 넘게 응답이 없으면 지연으로 표시
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
    { t: "AMR 처리 능력", v: db ? `${(util.value.length ? amrRows.value.filter((r) => r.code.startsWith("AMR")).reduce((a, r) => a + r.hour, 0) : 0)}건/시` : "—", d: "최근 1시간 실적" },
    { t: "AMR 작업 (전일 동시간 대비)", v: db && k.yesterdaySameTime > 0 ? `${Math.round(((k.today - k.yesterdaySameTime) / k.yesterdaySameTime) * 1000) / 10 >= 0 ? "▲ " : "▼ "}${Math.abs(Math.round(((k.today - k.yesterdaySameTime) / k.yesterdaySameTime) * 1000) / 10)}%` : "—", dc: db && k.yesterdaySameTime > 0 ? (k.today >= k.yesterdaySameTime ? "up" : "dn") : "", d: db && k.yesterdaySameTime > 0 ? `오늘 ${k.today}건 · 전일 ${k.yesterdaySameTime}건` : "전일 기록 없음" },
    { t: "교차로 평균 대기", v: db && k.avgInterWaitSec != null ? `${Math.round(k.avgInterWaitSec * 10) / 10}초` : "—", d: db && k.interWaits ? `대기 ${k.interWaits}회` : "" },
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
// 남은 수명은 한 숫자가 아니라 범위(±20%)로 보여 준다: 예측에는 오차가 있기 때문
const rulRange = (h) => { const x = Number(h); if (!isFinite(x)) return "?시간"; const lo = Math.max(0, Math.floor(x * 0.8)), hi = Math.max(lo + 1, Math.ceil(x * 1.2)); return `약 ${lo}~${hi}시간`; };
const fire = ref({ active: null, log: [] });
const srvAlert = ref(null); // 서버의 경보 상태 (기준). 서버가 "경보 없음"이면 이 브라우저에 남은 진행 중 표시는 무시 · null = 서버 연결 불가
async function loadSrvAlert() { try { const a = await amrApi.safetyNow(); srvAlert.value = a || null; } catch { srvAlert.value = null; } }
const fireNow = computed(() => { void nowTick.value; const a = fire.value.active; return a ? Math.max(0, Math.round((Date.now() - a.since) / 1000)) : 0; });
const fmtDur = (sec) => (sec >= 60 ? Math.floor(sec / 60) + "분 " + (sec % 60) + "초" : sec + "초");
const hhmm = (t) => { const d = new Date(t); return String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0"); };
const pickDate = ref(""); // 비우면 오늘. 보고서 · 화재 기록을 이 날짜로 본다 (yyyy-MM-dd)
const todayStr = () => { const d = new Date(); return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0"); };
// 26.10.30 보고 구분: 일간 · 주간(월~일) · 월간 · 연간. 고른 날짜(없으면 오늘)가 속한 기간, 끝은 오늘까지만
const pickPeriod = ref("day");
const PNAME = { day: "일일", week: "주간", month: "월간", year: "연간" };
const pickRange = computed(() => {
  const d = pickDate.value ? new Date(pickDate.value + "T00:00:00") : new Date(); d.setHours(0, 0, 0, 0);
  const f = (x) => x.getFullYear() + "-" + String(x.getMonth() + 1).padStart(2, "0") + "-" + String(x.getDate()).padStart(2, "0");
  let s = new Date(d), e = new Date(d);
  if (pickPeriod.value === "week") { s = new Date(d.getFullYear(), d.getMonth(), d.getDate() - ((d.getDay() + 6) % 7)); e = new Date(s.getFullYear(), s.getMonth(), s.getDate() + 6); }
  else if (pickPeriod.value === "month") { s = new Date(d.getFullYear(), d.getMonth(), 1); e = new Date(d.getFullYear(), d.getMonth() + 1, 0); }
  else if (pickPeriod.value === "year") { s = new Date(d.getFullYear(), 0, 1); e = new Date(d.getFullYear(), 11, 31); }
  const t = new Date(); t.setHours(0, 0, 0, 0); if (e > t) e = t; if (e < s) e = new Date(s);
  return { s, e, from: f(s), to: f(e) };
});
const dateLabel = computed(() => (pickPeriod.value !== "day" ? pickRange.value.from + " ~ " + pickRange.value.to : pickDate.value && pickDate.value !== todayStr() ? pickDate.value : "오늘"));
const fireToday = computed(() => { const a = pickRange.value.s.getTime(), b = pickRange.value.e.getTime() + 86400000; return fire.value.log.filter((x) => x.end >= a && x.end < b); });
// 알림 처리 상태: 미확인 → 확인 → 조치 중 → 완료 (이 브라우저에 저장). 완료된 알림은 흐리게 아래로 보낸다
const AKEY = "sbot_alert_state_v1", ASTEP = ["", "확인", "조치 중", "완료"];
const astate = ref((() => { try { const o = JSON.parse(localStorage.getItem(AKEY)); return o && o.day === todayStr() ? o.m || {} : {}; } catch { return {}; } })());
const aLv = (k) => astate.value[k] || 0;
function aNext(k) { const n = (aLv(k) + 1) % ASTEP.length; const m = { ...astate.value }; if (n) m[k] = n; else delete m[k]; astate.value = m; try { localStorage.setItem(AKEY, JSON.stringify({ day: todayStr(), m })); } catch { /* 무시 */ } }
function readShared() {
  fire.value = readFire();
  try { const s = JSON.parse(localStorage.getItem(SKEY)); shared.value = s && s.day === dayKey() && s.centers ? s : null; } catch { shared.value = null; }
}
let sTimer, aTimer;
const onStorage = (e) => { if (!e.key || e.key === SKEY || e.key === "sbot_fire_v1") readShared(); };
onMounted(() => { if (speed.value > 1) setSpeed(speed.value); readShared(); sTimer = setInterval(readShared, 1000); loadSrvAlert(); aTimer = setInterval(loadSrvAlert, 3000); window.addEventListener("storage", onStorage); });
onBeforeUnmount(() => { clearInterval(sTimer); clearInterval(aTimer); window.removeEventListener("storage", onStorage); });
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
  if (fire.value.active) out.push({ k: "fire", t: (fire.value.active.kind === "batt" ? "배터리 화재 " : "화재 경보 ") + fire.value.active.label + " · " + fire.value.active.stage, to: "/sbot/hub", lv: "bad" });
  amrRows.value.filter((r) => r.cls === "fault").forEach((r) => out.push({ k: "f" + r.code, t: r.code + " 정비 중", to: "/sbot/hub", lv: "bad" }));
  amrRows.value.filter((r) => r.cls === "chg").forEach((r) => out.push({ k: "c" + r.code, t: r.code + " 충전 중", to: "/sbot/hub", lv: "warn" }));
  robotTable.value.filter((r) => r.health != null && r.lv !== "fault").forEach((r) => { if (r.health < 50) out.push({ k: "hb" + r.code, t: r.code + " 건강 점수 " + r.health + "점 · 정비 필요", to: "/sbot/hub", lv: "bad" }); else if (r.health < 80) out.push({ k: "hw" + r.code, t: r.code + " 건강 점수 " + r.health + "점 · 상태 주의", to: "/sbot/hub", lv: "warn" }); });
  openMaint.value.forEach((m) => out.push({ k: "m" + m.id, t: m.robotCode + (m.status === "IN_PROGRESS" ? " 정비 진행 중" : " 정비 예약") + (m.rulHours != null ? " · 남은 수명 " + rulRange(m.rulHours) : ""), to: "/sbot/hub", lv: m.status === "IN_PROGRESS" ? "bad" : "warn" }));
  if (!online.value) out.push({ k: "off", t: "서버 연결 확인 필요 (시뮬레이션 값)", to: "/sbot", lv: "warn" });
  return out.sort((a, b) => (aLv(a.k) === 3) - (aLv(b.k) === 3));
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
  const tn = nowInfo(), stamp = tn.stamp;
  // 26.10.28 보고서 양식: 제목 · 문서번호 · 결재란 · 구역 번호 (합계 · 주간 · 심야 칸은 수식)
  const rp = createReport(11);
  rp.header("물류센터별 통행료 정산 보고서", [["문서번호", tn.docNo("SCARGO-TOLL")], ["보고일자", tn.kor], ["보고 대상", "허브(곤지암) → 서울 · 경기 · 인천 물류센터"], ["데이터", ex ? "예시값 (운행 기록 없음)" : "S-Bot 오늘 누적"]]);
  rp.section("1. 센터별 통행료 정산  (단위: 회 / 원)");
  rp.add([H("센터"), H("도착 차량(회)"), H("입고 물품(건)"), H("기본 요금 합(원)"), H("주간 통과(회)"), H("심야 통과(회)"), H("주간 금액(원)"), H("심야 기본(원)"), H("심야 할인(원)"), H("심야 금액(원)"), H("확정 합계(원)")], 32);
  const R0 = rp.rows.length + 1; // 첫 데이터 행 번호 (수식이 이 번호를 쓴다)
  rows.forEach((r, i) => { const n = R0 + i, dn = r.trips - r.nn, da = r.base - r.nb, na = r.nb - r.dis;
    rp.add([{ v: r.nm, s: 7 }, N(r.trips), N(r.items), N(r.base), { v: dn, f: `B${n}-F${n}`, s: 2 }, N(r.nn), { v: da, f: `D${n}-H${n}`, s: 2 }, N(r.nb), N(r.dis), { v: na, f: `H${n}-I${n}`, s: 2 }, { v: da + na, f: `G${n}+J${n}`, s: 2 }], 22); });
  const sum = (col, key) => ({ v: rows.reduce((a, r) => a + (key(r)), 0), f: `SUM(${col}${R0}:${col}${R0 + rows.length - 1})`, s: 3 });
  rp.add([{ v: "합계", s: 4 }, sum("B", (r) => r.trips), sum("C", (r) => r.items), sum("D", (r) => r.base), sum("E", (r) => r.trips - r.nn), sum("F", (r) => r.nn), sum("G", (r) => r.base - r.nb), sum("H", (r) => r.nb), sum("I", (r) => r.dis), sum("J", (r) => r.nb - r.dis), sum("K", (r) => r.base - r.dis)], 24);
  rp.add([], 8);
  rp.section("2. 산출 기준");
  rp.note("주간 금액 = 기본 요금 합 - 심야 기본 · 심야 금액 = 심야 기본 - 심야 할인 · 확정 합계 = 주간 + 심야");
  rp.note("통행료는 통과한 시각의 3D 하루 시계 기준: 06~21시 할인 없음 · 21~24시 30% · 00~06시 50%");
  const s1 = rp.sheet("센터별 통행료 정산", [12, 11, 11, 15, 11, 11, 15, 15, 14, 15, 16]);
  const HRS = ["06~08", "08~10", "10~12", "12~14", "14~16", "16~18", "18~20", "20~22"];
  const rp2 = createReport(4);
  rp2.header("센터별 시간대 입고 보고서", [["문서번호", tn.docNo("SCARGO-TOLL")], ["보고일자", tn.kor], ["보고 대상", "서울 · 경기 · 인천 물류센터"], ["데이터", ex ? "예시값 (운행 기록 없음)" : "S-Bot 오늘 누적"]]);
  rp2.section("1. 센터별 시간대 입고 물품  (단위: 건)");
  rp2.add([H("시간대"), H("서울"), H("경기"), H("인천")], 28);
  const Q0 = rp2.rows.length + 1;
  HRS.forEach((h, i) => rp2.add([{ v: h, s: 7 }, N(rows[0].hr[i] || 0), N(rows[1].hr[i] || 0), N(rows[2].hr[i] || 0)], 20));
  const Q1 = Q0 + HRS.length - 1;
  rp2.add([{ v: "합계", s: 4 }, { v: rows[0].hr.reduce((a, b) => a + b, 0), f: `SUM(B${Q0}:B${Q1})`, s: 3 }, { v: rows[1].hr.reduce((a, b) => a + b, 0), f: `SUM(C${Q0}:C${Q1})`, s: 3 }, { v: rows[2].hr.reduce((a, b) => a + b, 0), f: `SUM(D${Q0}:D${Q1})`, s: 3 }], 24);
  const s2 = rp2.sheet("시간대별 입고", [14, 14, 14, 14, 12, 12, 12]);
  downloadBlob(buildXlsx([s1, s2]), `S카고_센터별_통행료_정산보고서_${stamp.slice(0, 10)}.xlsx`);
}
// 26.10.28 AMR 일일 운영 보고서(.xlsx): 개요 → 일일 요약 → AMR별 현황 → 화물 운반(센터별) → 시간대별 → 정비 이력 → 특이사항 · 종합 의견
//  합계 칸은 수식. 정비 이력은 서버(/api/amr/maintenance)에서 읽어 오늘 건만 담는다. 서버가 꺼져 있으면 이력 표는 "기록 없음"으로 나온다.
// 26.10.29 안전사고 보고서(엑셀): AMR 관제에서 남긴 화재 · 배터리 화재 기록(선택한 날짜)으로 사고 개요 · 목록 · 대응 절차 · 영향 · 원인·재발 방지 작성란 · 후속 조치 확인표를 만든다
function exportSafety() {
  const tn = nowInfo(), list = fireToday.value.slice().sort((a, b) => a.start - b.start), day = dateLabel.value;
  const KIND = { hub: "허브 화재 경보", batt: "AMR 배터리 화재" };
  const secs = list.reduce((a, f) => a + (f.secs || 0), 0), nHub = list.filter((f) => f.kind === "hub").length, nBatt = list.length - nHub;
  const rp = createReport(9);
  rp.header("안전사고 보고서", [["문서번호", tn.docNo("SCARGO-SAFE")], ["보고 대상일", day === "오늘" ? tn.kor : day], ["대상 시설", "곤지암 허브 · AMR 허브"], ["작성자", "S-Bot 자동 작성 (원인·조치는 담당자 기입)"]]);
  rp.note("※ 이 기록은 AMR 관제 화면(시뮬레이션)에서 발생시킨 화재 훈련 기록입니다. 실제 사고 보고에는 현장 확인 내용을 함께 기입할 것.");
  rp.section("1. 사고 개요");
  rp.paragraph(list.length ? `${day} 화재 · 안전 사고 ${list.length}건 (허브 화재 경보 ${nHub}건, AMR 배터리 화재 ${nBatt}건). 총 소요 ${fmtDur(secs)}.` : `${day} 접수된 화재 · 안전 사고가 없음.`, 2);
  rp.section("2. 사고 목록");
  rp.table([{ label: "번호", type: "center" }, { label: "발생", type: "center" }, { label: "종료", type: "center" }, { label: "구분" }, { label: "구역 · 장비" }, { label: "소요(초)", type: "num", sum: true }, { label: "AMR 대피(대)", type: "num" }, { label: "AMR 전체(대)", type: "num" }, { label: "내용" }],
    list.map((f, i) => [i + 1, hhmm(f.start), hhmm(f.end), KIND[f.kind] || f.kind, f.label, f.secs || 0, f.kind === "hub" ? f.evac || 0 : "", f.kind === "hub" ? f.total || 0 : "", f.note || ""]), { total: list.length > 0, totalLabel: "합계", empty: "해당 날짜의 사고 기록이 없습니다." });
  rp.section("3. 대응 절차 기록  (시스템이 자동 수행한 조치)");
  rp.table([{ label: "구분" }, { label: "단계", type: "center" }, { label: "조치" }, { label: "담당" }],
    [["허브 화재", 1, "경보 발령 · 안내방송 · 신규 작업 중단", "시스템"], ["허브 화재", 2, "입차 게이트 차단 · AMR 전원 대피 구역 이동", "시스템"], ["허브 화재", 3, "스프링클러 작동 확인 · 소방차 전용 구역 확보", "시스템 / 담당자"], ["허브 화재", 4, "화재 종료(관리자 확인) · 장비 복귀 · 게이트 재개", "관리자"],
      ["배터리 화재", 1, "온도 이상 감지 · 해당 AMR 작업 중단", "시스템"], ["배터리 화재", 2, "격리 구역 이동 · 충전 차단 · 주변 장비 접근 금지", "시스템"], ["배터리 화재", 3, "소화 설비 작동 · 허브 운영은 유지", "시스템"], ["배터리 화재", 4, "진압 확인 · 관리자 확인 후 배터리 교체 · 복귀", "관리자"]]);
  rp.section("4. 영향 평가");
  const evacRate = nHub && list.filter((f) => f.kind === "hub").reduce((a, f) => a + (f.total || 0), 0) ? Math.round((list.filter((f) => f.kind === "hub").reduce((a, f) => a + (f.evac || 0), 0) / list.filter((f) => f.kind === "hub").reduce((a, f) => a + (f.total || 0), 0)) * 100) : null;
  rp.table([{ label: "항목" }, { label: "내용" }],
    [["총 중단·대응 시간", list.length ? fmtDur(secs) : "-"], ["AMR 대피율", evacRate == null ? "-" : evacRate + "%"], ["입차 게이트", nHub ? `허브 화재 ${nHub}건 동안 차단` : "영향 없음"], ["허브 운영", nBatt && !nHub ? "배터리 화재는 격리 구역에서 처리 · 허브 운영 유지" : nHub ? "허브 화재 중 신규 작업 중단" : "영향 없음"], ["인명 피해", "기록 없음 (담당자 확인)"]]);
  rp.section("5. 원인 분석 · 재발 방지  (담당자 작성)");
  rp.paragraph("원인:\n\n재발 방지 대책:", 4);
  rp.section("6. 후속 조치 확인");
  rp.table([{ label: "조치 항목" }, { label: "담당" }, { label: "기한", type: "center" }, { label: "완료", type: "center" }], [["", "", "", ""], ["", "", "", ""], ["", "", "", ""]]);
  rp.note("※ 이 문서는 S-Bot 에서 자동 작성되었습니다. 원인 · 후속 조치는 담당자가 직접 기입해 결재를 받으십시오.");
  downloadBlob(buildXlsx([rp.sheet("안전사고 보고서", [10, 11, 11, 16, 16, 12, 13, 13, 30])]), `S봇_안전사고보고서_${day === "오늘" ? tn.day : day}.xlsx`);
}
// 26.10.30 건별 운반 내역 시트 (AMR 일일 보고서 둘째 시트 · 오늘 · 지난 날짜 공용)
function taskSheet(det, tn, dk) {
  const when = dk.day === tn.day ? "오늘" : dk.kor, rp2 = createReport(11), TT = { IN: "입고", OUT: "출고" };
  rp2.header("AMR 건별 운반 내역", [["문서번호", tn.docNo("SCARGO-AMR")], ["보고일자", dk.kor], ["대상 시설", "곤지암 허브"], ["작성자", "S-Bot 자동 작성"]]);
  rp2.section(`1. 건별 운반 내역  (${when} 최근 ${(det && det.tasks ? det.tasks.length : 0).toLocaleString("ko-KR")}건 / 전체 ${((det && det.taskTotal) || 0).toLocaleString("ko-KR")}건 · 완료 시각 최신순)`);
  rp2.table([{ label: "번호", type: "center" }, { label: "완료 시각", type: "center" }, { label: "로봇", type: "center" }, { label: "구분", type: "center" }, { label: "운송장" }, { label: "목적 허브" }, { label: "물품 유형" }, { label: "중량(kg)" }, { label: "출발" }, { label: "도착" }, { label: "소요(초)" }],
    ((det && det.tasks) || []).map((t, i) => [i + 1, String(t.at).slice(11, 19), t.code, TT[t.type] || t.type, t.waybill, t.hub, t.handle, t.kg == null ? "" : t.kg, t.from, t.to, t.sec == null ? "" : Math.round(t.sec * 10) / 10]),
    { empty: det ? when + " 운반 기록이 없습니다." : "건별 기록을 불러오지 못했습니다 (백엔드 재시작 후 사용 가능)." });
  rp2.note("※ 건별 기록은 로봇 시뮬레이터가 DB에 남긴 작업 로그이며, 허브 처리 물품(오늘 투입 물량)과 출처가 다릅니다.");
  return rp2.sheet("건별 운반 내역", [8, 12, 11, 8, 18, 12, 12, 11, 14, 14, 10]);
}
async function exportAmrPast(tn, dk) {
  const pn = PNAME[pickPeriod.value], ranged = pickPeriod.value !== "day";
  let det = null; try { det = await amrApi.reportDetail(ranged ? pickRange.value.from : pickDate.value, ranged ? pickRange.value.to : ""); } catch { det = null; }
  const list = fireToday.value.slice().sort((a, b) => a.start - b.start), tasks = (det && det.taskTotal) || 0, chg = (det && det.charges) || [];
  const rp = createReport(9);
  rp.header(`AMR ${pn} 운영 보고서`, [["문서번호", tn.docNo("SCARGO-AMR")], ["보고일자", dk.kor], ["대상 시설", "곤지암 허브 · AMR 허브"], ["작성자", "S-Bot 자동 작성"]]);
  rp.note(ranged ? "※ 기간 보고서: 선택한 기간에 서버에 남은 건별 운반 · 충전 기록과 화재 · 안전 기록만 합산함. 가동률 · 정비 등 실시간 지표는 넣지 않음." : "※ 지난 날짜 보고서: 서버에 남은 건별 운반 · 충전 기록과 화재 · 안전 기록만 담음. 가동률 · 정비 등 실시간 지표는 지난 날짜에는 제공되지 않아 넣지 않음.");
  rp.section("1. 보고 개요");
  rp.paragraph(`${dk.kor} 기준 곤지암 허브 AMR ${pn} 운영 기록을 보고함 (데이터: DB 기록). 건별 운반 ${tasks.toLocaleString("ko-KR")}건, 충전 ${chg.length}회, 화재 · 안전 기록 ${list.length}건임.`, 2);
  rp.section("2. 처리 요약");
  rp.table([{ label: "건별 운반(건)" }, { label: "충전(회)" }, { label: "화재 · 안전 기록(건)" }], [[tasks, chg.length, list.length]]);
  rp.section("3. 충전 이력  (해당 날짜 충전소 이동 기록)");
  rp.table([{ label: "시각", type: "center" }, { label: "로봇", type: "center" }, { label: "내용" }], chg.map((c) => [String(c.at).slice(11, 16), c.code, c.msg]), { empty: det ? "해당 날짜 충전 기록이 없습니다." : "충전 기록을 불러오지 못했습니다 (백엔드 재시작 후 사용 가능)." });
  rp.section(`4. 화재 · 안전 기록  (AMR 관제 화면 기록 · ${dateLabel.value})`);
  rp.table([{ label: "시작", type: "center" }, { label: "해제", type: "center" }, { label: "구분", type: "center" }, { label: "구역·장비" }, { label: "대피(대)", type: "num" }, { label: "소요(초)", type: "num" }, { label: "내용" }],
    list.map((f) => [hhmm(f.start), hhmm(f.end), f.kind === "batt" ? "배터리 화재" : "허브 화재", f.label, f.kind === "hub" ? f.evac : "", f.secs, f.note]), { empty: dateLabel.value + " 화재 · 안전 기록이 없습니다." });
  rp.note("※ 이 문서는 S-Bot 에서 자동 작성되었습니다.");
  downloadBlob(buildXlsx([rp.sheet(`AMR ${pn} 보고서`, [14, 14, 14, 16, 14, 12, 12, 18, 26]), taskSheet(det, tn, dk)]), `S봇_AMR${pn}운영보고서_${dk.day}.xlsx`);
}
async function exportAmr() {
  const tn = nowInfo(), am = amrRows.value, tr = transport.value, al = alerts.value;
  // 26.10.30 날짜를 고르면 보고일자 · 파일명이 그 날짜로 나오게. 지난 날짜는 서버에 남은 기록(건별 운반 · 충전 · 화재)만으로 작성한다 (실시간 지표를 지난 날짜에 붙이지 않음)
  const ranged = pickPeriod.value !== "day", past = ranged || (!!pickDate.value && pickDate.value !== todayStr());
  const dk = ranged ? { ...nowInfo(pickRange.value.s), kor: PNAME[pickPeriod.value] + " · " + pickRange.value.from + " ~ " + pickRange.value.to, day: pickRange.value.from + "~" + pickRange.value.to } : past ? nowInfo(new Date(pickDate.value + "T00:00:00")) : tn;
  if (past) return exportAmrPast(tn, dk);
    const bad = al.filter((a) => a.lv === "bad"), warn = al.filter((a) => a.lv === "warn");
  const db = hasDb.value || sbotDb.value, k = live.value;
  const hr = hourRows.value, hsum = hr.reduce((a, h) => a + h.count, 0);
  const chargeSum = am.reduce((a, r) => a + r.charge, 0), faultSum = am.reduce((a, r) => a + r.fault, 0);
  // 가동 AMR 대수는 실제 AMR(AMR-xx)만 센다 (지게차 · 예비기 대기 제외)
  const robots = am.filter((r) => r.code.startsWith("AMR") && r.cls !== "sp"), fl = am.filter((r) => !r.code.startsWith("AMR"));
  const avgPct = gauge.value; // 가동률은 대시보드 게이지와 같은 값을 쓴다 (앱에서 조정하는 기준을 그대로 따름)
  const actN = robots.filter((r) => r.cls === "ok").length, amrAll = am.filter((r) => r.code.startsWith("AMR")).length;
  const vehN = Number(String(kpis.value[0].v).replace(/[^0-9]/g, "")) || 0; // 허브에 들어온 화물차(차량 수)
  const capN = am.filter((r) => r.code.startsWith("AMR")).reduce((a, r) => a + r.hour, 0); // AMR 처리 능력: 최근 1시간 로봇 작업 실적 합계(건/시)
  const items = Number(String(kpis.value[2].v).replace(/[^0-9]/g, "")) || 0;
  // 허브 처리 건수(공식)를 로봇별 작업 비중으로 배분한다. 로봇 작업 로그(시뮬레이터)는 허브 집계와 출처가 달라 그대로 더하면 숫자가 어긋난다.
  //  배분은 합계가 항상 허브 건수와 같도록 "나머지가 큰 순서"로 1건씩 나눈다.
  const logSum = am.reduce((a, r) => a + r.today, 0);
  const raw = am.map((r) => (logSum ? (items * r.today) / logSum : 0)), alloc = raw.map(Math.floor);
  { let left = items - alloc.reduce((a, b) => a + b, 0); raw.map((v, i) => [v - Math.floor(v), i]).sort((a, b) => b[0] - a[0]).forEach(([, i]) => { if (left > 0 && logSum) { alloc[i]++; left--; } }); }
  const share = (i) => (logSum ? Math.round((am[i].today / logSum) * 1000) / 10 : 0);
  const amrDone = am.reduce((a, r, i) => a + (r.code.startsWith("AMR") ? alloc[i] : 0), 0);
  // 정비 이력: 오늘 시작 · 예약 · 완료된 건
  let mt = []; try { mt = (await amrApi.maintenanceList(50)) || []; } catch { mt = []; }
  const isToday = (v) => { if (!v) return false; const d = new Date(v); return d.getFullYear() === new Date().getFullYear() && d.getMonth() === new Date().getMonth() && d.getDate() === new Date().getDate(); };
  const hm = (v) => { if (!v) return "-"; const d = new Date(v); return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`; };
  const MST = { SCHEDULED: "예약", IN_PROGRESS: "정비 중", DONE: "완료" };
  let det = null; try { det = await amrApi.reportDetail(pickDate.value); } catch { det = null; } // 건별 운반 · 충전 기록 (백엔드 재시작 후 사용 가능)
  const mttrList = mt.filter((m) => m.status === "DONE" && m.startedAt && m.completedAt).map((m) => (new Date(m.completedAt) - new Date(m.startedAt)) / 60000).filter((v) => v >= 0);
  const mttr = mttrList.length ? Math.round((mttrList.reduce((a, b) => a + b, 0) / mttrList.length) * 10) / 10 : "-"; // 평균 정비 복구 시간(분)
  const waitSec = db && k && k.avgInterWaitSec != null ? Math.round(k.avgInterWaitSec * 10) / 10 : "-"; // 교차로 평균 대기(초)
  const clearMin = capN ? Math.round((items / capN) * 600) / 10 : "-"; // 오늘 투입 물량을 처리 능력으로 처리하는 데 걸리는 시간(분)
  const mtToday = mt.filter((m) => m.status !== "DONE" || isToday(m.startedAt) || isToday(m.scheduledAt) || isToday(m.completedAt)); // 진행 중 · 예약 건은 날짜와 관계없이 포함
  const mtRows = mtToday.map((m) => [hm(m.startedAt || m.scheduledAt), m.robotCode || "-", m.partName || "-", MST[m.status] || m.status || "-", m.approvedBy || (m.approvedAt ? "승인" : "승인 대기"), m.startedAt && m.completedAt ? Math.round((new Date(m.completedAt) - new Date(m.startedAt)) / 60000) : "-", m.memo || ""]);

  const rp = createReport(9);
  rp.header("AMR 일일 운영 보고서", [["문서번호", tn.docNo("SCARGO-AMR")], ["보고일자", dk.kor], ["대상 시설", `곤지암 허브 · AMR ${amrAll || am.length}대${fl.length ? " · 지게차 " + fl.length + "대" : ""}`], ["작성자", "S-Bot 자동 작성"]]);
  if (!db) rp.note("※ 예시값 보고서: 서버에 오늘 운행 기록이 없어 시뮬레이션 값으로 작성됨. 공식 보고에는 사용하지 말 것.");

  rp.section("1. 보고 개요");
  rp.paragraph(`${tn.kor} ${tn.time} 기준 곤지암 허브 AMR 운영 실적을 보고함 (데이터: ${db ? "DB 실시간" : "시뮬레이션 예시값"}). 허브 반입 차량 ${vehN}대, 처리 물품 ${items.toLocaleString("ko-KR")}건(공식 집계), AMR 처리 물품 ${amrDone.toLocaleString("ko-KR")}건(로봇별 작업 비중으로 배분), 충전 ${chargeSum}회, 정비 ${faultSum}회이며 AMR 가동률은 ${avgPct}%임 (S-Bot 대시보드 기준). 허브 처리 물품은 오늘 투입된 물량이며, AMR 처리 능력은 최근 1시간 실적으로 ${capN.toLocaleString("ko-KR")}건/시임.`, 3);

  rp.section("2. 물량 요약  (허브 반입은 차량, 처리는 물품 기준)");
  rp.table([{ label: "허브 반입 차량(대)" }, { label: "허브 처리 물품(건)\n오늘 투입 물량" }, { label: "AMR 처리 물품(건)" }, { label: "AMR 처리 능력(건/시)\n최근 1시간 실적" }, { label: "투입 물량 처리 소요(분)\n처리 능력 기준" }],
    [[vehN, items, amrDone, capN, clearMin]]);
  rp.section("3. 운영 지표");
  rp.table([{ label: "AMR 가동률(%)" }, { label: "가동 AMR(대)" }, { label: "총 충전(회)" }, { label: "총 정비(회)" }, { label: "평균 정비 복구(분)" }, { label: "물품 1건 평균 운반" }, { label: "교차로 평균 대기(초)" }],
    [[avgPct, `${actN} / ${amrAll}`, chargeSum, faultSum, mttr, kpis.value[3].v, waitSec]]);

  rp.section("4. AMR별 운영 현황  (가동률은 최근 1시간 · 처리 물품은 허브 처리 물품 건수를 로봇별 작업 비중으로 배분한 값)");
  const NOTE = { fault: "정비 중 · 작업 불가", chg: "충전 중 · 완료 후 복귀", sp: "예비 대기 · 관리자 승인 후 투입", ok: "" };
  rp.table([{ label: "로봇", type: "center" }, { label: "상태", type: "center" }, { label: "가동률(%)", sum: false }, { label: "처리 능력(건/시)", sum: true }, { label: "처리 비중(%)", sum: true }, { label: "처리 물품(건)", sum: true }, { label: "충전(회)", sum: true }, { label: "정비(회)", sum: true }, { label: "비고" }],
    am.map((a, i) => [a.code, { v: a.label, s: a.cls === "fault" ? 12 : a.cls === "chg" ? 11 : a.cls === "ok" ? 10 : 14 }, a.pct, a.hour, share(i), alloc[i], a.charge, a.fault, NOTE[a.cls] || (a.code.startsWith("AMR") ? "" : "지게차")]), { total: true, totalLabel: "합계(지게차 포함)", empty: "AMR 기록을 불러오지 못했습니다 (서버 연결 확인)" });

  rp.section("5. 화물 운반 내역  (허브 → 물류센터 · 차량은 대, 물품은 건)");
  rp.table([{ label: "센터", type: "center" }, { label: "거리(km)" }, { label: "운송 중(대)", sum: true }, { label: "도착(대)", sum: true }, { label: "전자제품(건)", sum: true }, { label: "의류(건)", sum: true }, { label: "식품(건)", sum: true }, { label: "기타(건)", sum: true }, { label: "운송 중 차량" }],
    tr.map((c) => [c.n + "센터", c.km, c.transit.length, c.arrived, ...c.cat, c.transit.map((x) => x.no).join(", ") || "-"]), { total: true, totalLabel: "합계" });

  rp.section("6. 시간대별 처리 건수");
  { const r0 = rp.rows.length + 2, hmax = Math.max(1, ...hr.map((h) => h.count)); // 첫 데이터 행 번호 (막대 수식에 사용)
    rp.table([{ label: "시간대", type: "center" }, { label: "처리 건수", sum: true }, { label: "비중(%)" }, { label: "건수 막대" }],
      hr.map((h, i) => [`${String(h.hour).padStart(2, "0")}~${String(Number(h.hour) + 2).padStart(2, "0")}시`, h.count, hsum ? Math.round((h.count / hsum) * 1000) / 10 : 0,
        { v: "█".repeat(Math.round((h.count / hmax) * 20)), f: `REPT("█",ROUND(B${r0 + i}/MAX(B$${r0}:B$${r0 + hr.length - 1})*20,0))`, s: 16 }]), { total: true, totalLabel: "합계" }); }

  rp.section("7. 정비 이력  (오늘 건 + 진행 중 · 예약 건)");
  rp.table([{ label: "시각", type: "center" }, { label: "로봇", type: "center" }, { label: "정비 부품" }, { label: "상태", type: "center" }, { label: "승인" }, { label: "소요(분)" }, { label: "메모" }], mtRows, { empty: "오늘 정비 기록이 없습니다." });

  rp.section("8. 충전 이력  (오늘 충전소 이동 기록)");
  const chRows = ((det && det.charges) || []).map((c) => [String(c.at).slice(11, 16), c.code, c.msg]);
  rp.table([{ label: "시각", type: "center" }, { label: "로봇", type: "center" }, { label: "내용" }], chRows, { empty: det ? "오늘 충전 기록이 없습니다." : "충전 기록을 불러오지 못했습니다 (백엔드 재시작 후 사용 가능)." });

  rp.section(`9. 화재 · 안전 기록  (AMR 관제 화면 기록 · ${dateLabel.value})`);
  rp.table([{ label: "시작", type: "center" }, { label: "해제", type: "center" }, { label: "구분", type: "center" }, { label: "구역·장비" }, { label: "대피(대)", type: "num" }, { label: "소요(초)", type: "num" }, { label: "내용" }],
    fireToday.value.map((f) => [hhmm(f.start), hhmm(f.end), f.kind === "batt" ? "배터리 화재" : "허브 화재", f.label, f.kind === "hub" ? f.evac : "", f.secs, f.note]), { empty: dateLabel.value + " 화재 · 안전 기록이 없습니다." });

  rp.section("10. 특이사항 및 조치");
  const lines = [];
  bad.forEach((a) => lines.push(`· ${a.t}: 작업 불가 상태, 정비 완료 후 복귀 필요`));
  warn.forEach((a) => lines.push(`· ${a.t}`));
  mtToday.forEach((m) => lines.push(`· ${m.robotCode || "-"} ${m.partName || ""} 정비 ${MST[m.status] || m.status}${m.approvedBy ? " (" + m.approvedBy + ")" : ""}`));
  const spare = am.filter((r) => r.cls === "sp").map((r) => r.code);
  if (spare.length) lines.push(`· ${spare.join(", ")} 예비기: 관리자 승인 전까지 대기 상태 유지`);
  fireToday.value.forEach((f) => lines.push(`· ${hhmm(f.start)} ${f.kind === "batt" ? f.label + " 배터리 화재 · 격리 · 배터리 교체 후 복귀" : f.label + " 화재 경보 훈련 · AMR " + f.evac + "/" + f.total + "대 대피 · " + fmtDur(f.secs)}`));
  const ac = [0, 0, 0, 0]; alerts.value.forEach((a) => { ac[aLv(a.k)]++; });
  if (alerts.value.length) lines.push(`· 알림 처리 현황: 미확인 ${ac[0]} · 확인 ${ac[1]} · 조치 중 ${ac[2]} · 완료 ${ac[3]}`);
  if (!lines.length) lines.push("· 특이사항 없음");
  rp.paragraph([...new Set(lines)].join("\n"), Math.min(lines.length, 8));

  rp.section("11. 종합 의견");
  rp.paragraph(bad.length ? `AMR ${bad.length}대가 정비 중이라 확인이 필요함. 정비 완료 후 복귀 여부와 예비기 투입 여부를 점검할 것.`
    : warn.length ? `충전 · 확인 중인 항목이 있으나 운영에는 지장 없음. 평균 가동률 ${avgPct}%.`
    : `AMR 전체가 정상 가동 중이며 평균 가동률은 ${avgPct}%임. 처리 지연 없음.`, 2);
  rp.note("※ 이 문서는 S-Bot 에서 자동 작성되었습니다. 합계는 엑셀 수식이며, 서버가 꺼져 있으면 예시값으로 표시됩니다.");
  const widths = [14, 14, 14, 16, 14, 12, 12, 18, 26];
  const sheets = [rp.sheet("AMR 일일 보고서", widths), taskSheet(det, tn, dk)];
  downloadBlob(buildXlsx(sheets), `S봇_AMR일일운영보고서_${dk.day}${db ? "" : "_예시값"}.xlsx`);
}
// 26.10.26 운영 현황 보고서(.xlsx): 종합 판단 → 핵심 지표(설명 포함) → 확인 필요 → 센터별 운송 → 입고·통행료 → 시간대별 → AMR 상세. 합계 · 비중은 수식
function exportCsv() {
  const t = new Date(), p = (n) => String(n).padStart(2, "0");
  const day = `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())}`, stamp = `${day} ${p(t.getHours())}:${p(t.getMinutes())}`;
  const src = hasDb.value || sbotDb.value ? "DB 실시간" : "시뮬레이션 예시값 (서버 · 운행 기록 없음)";
  const H = (v) => ({ v, s: 1 }), N = (v) => ({ v, s: 2 }), T = (v) => ({ v, s: 7 }), W = (v) => ({ v, s: 9 }), C = (v) => ({ v, s: 14 }), TT = (v) => ({ v, s: 4 });
  const L = "ABCDEFGHIJ", rp = createReport(10), rows = rp.rows, merges = rp.merges, heights = rp.heights; // 26.10.28 보고서 양식 머리말(제목 · 문서번호 · 결재란)
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
  { const tn = nowInfo(); rp.header("S-Bot 운영 현황 보고서", [["문서번호", tn.docNo("SCARGO-SBOT")], ["보고일자", tn.kor], ["보고 대상", "S카고 허브(곤지암) → 서울 · 경기 · 인천 물류센터"], ["데이터", src]]); }
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
const sc = computed(() => 120 / Math.max(1, peak.value));
const peakLabel = computed(() => `${hourRows.value[hours.value.indexOf(peak.value)].hour} · ${peak.value}건`);
const pcts = computed(() => hours.value.map((v) => (v / Math.max(1, peak.value)) * 78));
// 선은 누적 처리(계속 올라가기만 함): 시간대 값이 0 · 24 · 0 으로 튀어서 뾰족한 산처럼 보이던 것을 완만하게 보여 준다. 막대는 시간대별 건수.
const pline = computed(() => { let c = 0; const tot = Math.max(1, hours.value.reduce((a, b) => a + b, 0)); return hours.value.map((v, i) => { c += v; return `${((i + 0.5) / hours.value.length) * 100},${100 - (c / tot) * 78}`; }).join(" "); });
const line = computed(() => hours.value.map((v, i) => `${29 + i * 58},${160 - v * sc.value}`).join(" "));
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
// 로봇별 운영 표: 상태가 급한 순(정비 → 충전 → 예비 → 정상). 처리 물품은 보고서와 같은 방식으로 허브 처리 건수를 작업 비중으로 배분한다.
const robotTable = computed(() => {
  const am = amrRows.value, items = Number(String(kpis.value[2].v).replace(/[^0-9]/g, "")) || 0;
  const logSum = am.reduce((a, r) => a + r.today, 0);
  const raw = am.map((r) => (logSum ? (items * r.today) / logSum : 0)), al = raw.map(Math.floor);
  { let left = items - al.reduce((a, b) => a + b, 0); raw.map((v, i) => [v - Math.floor(v), i]).sort((a, b) => b[0] - a[0]).forEach(([, i]) => { if (left > 0 && logSum) { al[i]++; left--; } }); }
  const rm = new Map(robots.value.map((x) => [x.code, x]));
  const ORD = { fault: 0, chg: 1, sp: 2, ok: 3 };
  return am.map((r, i) => {
    const x = rm.get(r.code) || {}, h = x.health == null ? null : Math.round(x.health), b = x.battery == null ? null : Math.round(x.battery);
    return { code: r.code, label: r.label, lv: r.cls, batt: b, health: h, hlv: h == null ? "" : h < 50 ? "crit" : h < 80 ? "warnc" : "", pct: r.pct, hour: r.hour, alloc: al[i], charge: r.charge, fault: r.fault };
  }).sort((a, b) => (ORD[a.lv] ?? 9) - (ORD[b.lv] ?? 9) || a.code.localeCompare(b.code));
});
const capNum = computed(() => amrRows.value.filter((r) => r.code.startsWith("AMR")).reduce((a, r) => a + r.hour, 0));
const clearMin = computed(() => { const items = Number(String(kpis.value[2].v).replace(/[^0-9]/g, "")) || 0; return capNum.value ? Math.round((items / capNum.value) * 60) : null; });
// 예지보전 칸: AMR만. 건강 점수 기준은 앱 · 웹과 같다(50 미만 정비 필요, 80 미만 주의). 정비 예약 · 진행 건이 있으면 남은 수명을 보여 준다.
const pmRows = computed(() => robotTable.value.filter((r) => r.code.startsWith("AMR")).map((r) => {
  const m = openMaint.value.find((x) => x.robotCode === r.code);
  const h = r.health, lv = r.lv === "fault" || (m && m.status === "IN_PROGRESS") || (h != null && h < 50) ? "bad" : m || (h != null && h < 80) ? "warn" : "ok";
  const note = m ? `남은 수명 ${m.rulHours != null ? rulRange(m.rulHours) : "?"} · ${m.status === "IN_PROGRESS" ? "정비 진행 중" : "정비 예약"}` : h != null && h < 80 ? "진동 상승 · 추세 관찰" : r.lv === "chg" ? "충전 중" : r.lv === "sp" ? "예비 대기" : "";
  return { code: r.code, health: h, lv, tag: lv === "bad" ? "정비 필요" : lv === "warn" ? "주의" : "정상", note };
}));
const bottle = computed(() => {
  const k = live.value, db = hasDb.value, w = db && k && k.avgInterWaitSec != null ? Math.round(k.avgInterWaitSec * 10) / 10 : null;
  const am = amrRows.value.filter((r) => r.code.startsWith("AMR")), chg = am.filter((r) => r.cls === "chg").length, flt = am.filter((r) => r.cls === "fault").length;
  const mtL = maint.value.filter((m) => m.status === "DONE" && m.startedAt && m.completedAt).map((m) => (new Date(m.completedAt) - new Date(m.startedAt)) / 60000).filter((v) => v >= 0);
  const mttr = mtL.length ? Math.round((mtL.reduce((a, b) => a + b, 0) / mtL.length) * 10) / 10 : null;
  return [
    { t: "교차로 평균 대기", v: w == null ? "—" : w + "초", w: w == null ? 0 : Math.min(100, (w / 4) * 100), lv: w != null && w > 3 ? "warn" : "ok" },
    { t: "충전 중 AMR", v: chg + "대", w: am.length ? (chg / am.length) * 100 : 0, lv: chg >= 2 ? "warn" : "ok" },
    { t: "정비 중 AMR", v: flt + "대", w: am.length ? (flt / am.length) * 100 : 0, lv: flt ? "bad" : "ok" },
    { t: "정비 예약 · 진행", v: openMaint.value.length + "건", w: 0, lv: "ok" },
    { t: "평균 정비 복구", v: mttr == null ? "—" : mttr + "분", w: 0, lv: "ok" },
  ];
});
const gauge = computed(() => (util.value.length ? Math.round(util.value.reduce((s, u) => s + u.pct, 0) / util.value.length) : 67));
const gaugeNote = computed(() => (util.value.length ? `최근 1시간 평균 · 장비 ${util.value.length}대` : "AMR 4 · 예비 1 · 지게차 1 (예시)"));
const rows = computed(() => [{ n: "경기", c: "#4aa3ff", v: val("GGI").items }, { n: "인천", c: "#4cc38a", v: val("ICN").items }, { n: "서울", c: "#f0a800", v: val("SEL").items }]);
const total = computed(() => rows.value.reduce((s, r) => s + r.v, 0));
const donut = computed(() => { let o = 0; return rows.value.map((r) => { const p = (r.v / Math.max(1, total.value)) * 100; const d = { ...r, p, o }; o += p; return d; }); });

// ==== 26.10.29 디자인 합침: KPI 카드 아이콘 · 색 · 설명 (S Cargo 대시보드와 같은 모양) ====
const KPI_STYLE = [
  { c: 'k-gold', icon: 'bi-truck', sub: '오늘 허브에 들어온 화물차' },
  { c: 'k-blue', icon: 'bi-cpu', sub: '지금 가동 중 / 전체 AMR' },
  { c: 'k-green', icon: 'bi-box-seam', sub: '오늘 AMR 이 처리한 택배' },
  { c: 'k-purple', icon: 'bi-stopwatch', sub: '택배 1건 평균 운반 시간' },
  { c: 'k-teal', icon: 'bi-lightning-charge', sub: '최근 1시간 실적' },
  { c: 'k-rose', icon: 'bi-graph-up-arrow', sub: '어제 같은 시간과 비교' },
  { c: 'k-slate', icon: 'bi-stoplights', sub: '교차로에서 기다린 평균 시간' },
]
const ks = (i) => KPI_STYLE[i] || KPI_STYLE[0]
// '20대' → 숫자 20 + 단위 '대' 로 나눠 숫자만 크게 (나눌 수 없으면 그대로)
const kv = (v) => { const m = String(v).match(/^([\d,.]+)\s*([^\d\s]+)$/); return m ? { num: m[1], unit: m[2] } : { num: v, unit: '' } }
// 새 시간대 그래프: 눈금 최대값(보기 좋은 수로 올림) · 눈금 4칸 · 추세선 좌표(0~100)
const yMax = computed(() => { const m = Math.max(4, peak.value) * 1.08; const raw = m / 4; const mag = Math.pow(10, Math.floor(Math.log10(raw))); const step = [1, 2, 3, 4, 5, 6, 8, 10].map((k) => k * mag).find((x) => x >= raw) || raw; return step * 4 })
const yTicks = computed(() => [4, 3, 2, 1, 0].map((k) => +(yMax.value * k / 4).toFixed(1)))
const linePts = computed(() => { const n = hours.value.length || 1; return hours.value.map((v, i) => `${((i + 0.5) / n) * 100},${100 - (v / yMax.value) * 100}`).join(' ') })
const areaPts = computed(() => { const n = hours.value.length || 1; return `${(0.5 / n) * 100},100 ` + linePts.value + ` ${((n - 0.5) / n) * 100},100` })
// ==== 26.10.29 카드 두 줄 묶음 · 탭 ====
const KPI_ROWS = [{ t: '물량', ids: [0, 2, 5] }, { t: 'AMR', ids: [1, 3, 6] }]
const TABS = [
  { k: 'flow', t: '처리 현황', icon: 'bi-bar-chart-line' },
  { k: 'check', t: '확인 · 예지보전', icon: 'bi-exclamation-triangle' },
  { k: 'robot', t: 'AMR별 운영', icon: 'bi-robot' },
  { k: 'fire', t: '화재 · 안전 기록', icon: 'bi-fire' },
]
const TAB_KEY = 'sbot_dash_tab'
const tab = ref((() => { try { const t = localStorage.getItem(TAB_KEY); return TABS.some((x) => x.k === t) ? t : 'flow' } catch { return 'flow' } })())
function setTab(k) { tab.value = k; try { localStorage.setItem(TAB_KEY, k) } catch { /* 무시 */ } }
const fireOn = computed(() => !!(fire.value.active && (srvAlert.value === null || srvAlert.value.active)))
// 탭 숫자: 확인 필요 중 아직 '미확인' 건수 / 오늘 화재·안전 기록 건수
const tabN = (k) => k === 'check' ? alerts.value.filter((a) => !aLv(a.k)).length : k === 'fire' ? fireToday.value.length : 0
</script>

<style scoped>
/* ==== 26.10.29 S Cargo 밝은 디자인 (10/28 정리본 배치 + 팀원 새 기능) ==== */
.sbd { display: flex; flex-direction: column; gap: 14px; color: #112847; padding-bottom: 8px; }
.card.panel { background: #fff; border: 1px solid #e3e8ef; border-radius: 14px; padding: 14px 18px; box-shadow: 0 1px 3px rgba(17,40,71,.06); display: flex; flex-direction: column; min-width: 0; text-align: left; }
.dim { color: #64748b; font-size: 12.5px; } .empty { text-align: center; padding: 16px 0; }

/* 화재 배너 */
.firebar { background: #fdecec; border: 1px solid #f3b4b4; border-left: 5px solid #d32f2f; color: #8f1d1d; border-radius: 12px; padding: 11px 16px; font-size: 14px; animation: fbl 1.1s ease-in-out infinite alternate; }
.firebar b { color: #c62828; }
.firebar .fgo { float: right; color: #c62828; font-weight: 700; text-decoration: none; } .firebar .fgo:hover { text-decoration: underline; }
@keyframes fbl { from { box-shadow: 0 0 0 0 rgba(211,47,47,0); } to { box-shadow: 0 0 0 4px rgba(211,47,47,.18); } }

/* 요약 + 도구 */
.topbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.sumline { flex: 1; min-width: 280px; display: flex; align-items: center; gap: 6px; background: #fff; border: 1px solid #e3e8ef; border-left: 4px solid #c9972f; border-radius: 12px; padding: 10px 16px; font-size: 14px; box-shadow: 0 1px 3px rgba(17,40,71,.06); }
.sumline i { color: #c9972f; } .sumline b { color: #b5862a; } .sumline em { color: #c62f2f; font-style: normal; font-weight: 700; }
.tools { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dsel { display: inline-flex; align-items: center; gap: 4px; background: #fff; border: 1px solid #e3e8ef; border-radius: 10px; padding: 5px 10px; color: #475569; font-size: 13px; box-shadow: 0 1px 3px rgba(17,40,71,.06); }
.dsel input, .dsel select { border: 0; background: transparent; color: #112847; font: inherit; outline: none; }
.dclr { border: 1px solid #cfd6df; background: #fff; color: #112847; border-radius: 7px; padding: 2px 8px; font-size: 12px; cursor: pointer; }
.csv { display: inline-flex; align-items: center; gap: 6px; background: #112847; color: #fff; border: 0; border-radius: 10px; padding: 8px 14px; font-size: 13px; font-weight: 700; cursor: pointer; box-shadow: 0 1px 3px rgba(17,40,71,.15); }
.csv i { color: #EECC83; } .csv:hover { background: #1b3a63; }

/* KPI 카드 */
.kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 12px; }
.kpi { display: flex; align-items: center; gap: 12px; background: #fff; border: 1px solid #e3e8ef; border-radius: 12px; padding: 12px 16px; box-shadow: 0 1px 3px rgba(17,40,71,.06); transition: box-shadow .15s, transform .15s; min-width: 0; }
.kpi:hover { box-shadow: 0 6px 16px rgba(17,40,71,.1); transform: translateY(-1px); }
.kpi .ic { flex: none; width: 42px; height: 42px; border-radius: 11px; display: inline-flex; align-items: center; justify-content: center; font-size: 19px; background: var(--kbg); color: var(--kc); }
.kpi .kt { display: flex; flex-direction: column; align-items: flex-start; text-align: left; min-width: 0; }
.kpi small { color: #334155; font-size: 13px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.kpi b { color: var(--kc); font-size: 24px; font-weight: 800; line-height: 1.2; font-variant-numeric: tabular-nums; white-space: nowrap; }
.kpi b em { font-style: normal; font-size: 13px; font-weight: 600; margin-left: 3px; opacity: .8; }
.kpi b.up { color: #0f9f6e; } .kpi b.dn { color: #dc3b3b; }
.kpi .sub { color: #64748b; font-size: 11.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.k-gold { --kc: #d18a00; --kbg: #fff4dc; } .k-blue { --kc: #1d4ed8; --kbg: #e8f0fe; } .k-green { --kc: #0f9f6e; --kbg: #e3f6ee; } .k-purple { --kc: #7c4dce; --kbg: #f0e9fb; }
.k-teal { --kc: #0e8f9a; --kbg: #e0f5f6; } .k-rose { --kc: #c2416b; --kbg: #fbe9ef; } .k-slate { --kc: #475569; --kbg: #eef1f6; }

/* 패널 머리말 */
.ph { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; min-width: 0; flex-wrap: nowrap; }
.ph h3 { margin: 0; font-size: 16px; font-weight: 800; color: #112847; white-space: nowrap; }
.pi { flex: none; width: 30px; height: 30px; border-radius: 8px; background: #e8f0fe; color: #1d4ed8; display: inline-flex; align-items: center; justify-content: center; font-size: 15px; }
.pi.g { background: #e3f6ee; color: #0f9f6e; } .pi.r { background: #fdecec; color: #d32f2f; } .pi.p { background: #f0e9fb; color: #7c4dce; } .pi.o { background: #fff1d6; color: #b86e00; }
.pr { margin-left: auto; font-size: 12.5px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; min-width: 0; }
.warnb { flex: none; background: #fff3e0; color: #b86e00; border-radius: 99px; padding: 2px 10px; font-size: 12px; font-weight: 700; white-space: nowrap; }
.spd { margin-left: auto; flex: none; display: inline-flex; align-items: center; gap: 3px; font-size: 12px; color: #64748b; white-space: nowrap; }
.spd .spl { font-style: normal; margin-right: 2px; }
.spd button { background: #fff; border: 1px solid #cfd6df; color: #112847; border-radius: 7px; padding: 2px 7px; font-size: 12px; font-weight: 600; cursor: pointer; }
.spd button:hover:not(.on) { border-color: #112847; } .spd button.on { background: #112847; color: #fff; border-color: #112847; }

/* 1줄: 그래프 | 가동 현황 */
.grid { display: grid; grid-template-columns: 1.45fr 1fr; gap: 14px; }
.grid > .panel { min-height: 330px; }
.hc { flex: 1; min-height: 210px; display: grid; grid-template-columns: 34px 1fr; grid-template-rows: minmax(0, 1fr) auto; column-gap: 8px; padding: 18px 4px 0 0; }
.hc-y { grid-row: 1; display: flex; flex-direction: column; justify-content: space-between; align-items: flex-end; font-size: 11px; color: #94a3b8; transform: translateY(-6px); }
.hc-y span { height: 12px; line-height: 12px; }
.hc-plot { position: relative; grid-row: 1; min-height: 0; }
.hc-grid { position: absolute; inset: 0; display: flex; flex-direction: column; justify-content: space-between; }
.hc-grid i { display: block; border-top: 1px dashed #e3e8ef; } .hc-grid i:last-child { border-top: 1px solid #cbd5e1; }
.hc-line { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; pointer-events: none; z-index: 2; }
.hc-cols { position: absolute; inset: 0; display: flex; align-items: flex-end; }
.hc-col { position: relative; flex: 1; height: 100%; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; }
.hc-bar { width: 46%; max-width: 44px; min-height: 2px; border-radius: 8px 8px 3px 3px; background: linear-gradient(180deg, #7ea4f6 0%, #3b6fe0 100%); box-shadow: 0 2px 6px rgba(29,78,216,.18); transition: filter .15s; }
.hc-col.pk .hc-bar { background: linear-gradient(180deg, #f6d991 0%, #d9a640 100%); box-shadow: 0 2px 8px rgba(201,151,47,.35); }
.hc-col:hover .hc-bar { filter: brightness(1.08); }
.hc-v { position: absolute; left: 0; right: 0; text-align: center; font-size: 12px; font-weight: 700; color: #475569; transform: translateY(-14px); z-index: 4; }
.hc-col.pk .hc-v { color: #b5862a; }
.hc-dot { position: absolute; left: 50%; width: 9px; height: 9px; margin: 0 0 -4.5px -4.5px; border-radius: 50%; background: #fff; border: 2px solid #1d4ed8; z-index: 3; }
.hc-col.pk .hc-dot { border-color: #c9972f; }
.hc-x { grid-column: 2; display: flex; padding-top: 6px; }
.hc-x span { flex: 1; text-align: center; font-size: 12px; color: #64748b; } .hc-x span.pk { color: #b5862a; font-weight: 700; }
.lg { display: flex; gap: 16px; justify-content: center; margin-top: 8px; font-size: 12px; color: #64748b; flex-wrap: wrap; }
.lg i { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 6px; vertical-align: -1px; }
.lg .b1 { background: linear-gradient(180deg, #7ea4f6, #3b6fe0); } .lg .b2 { background: linear-gradient(180deg, #f6d991, #d9a640); } .lg .b3 { height: 2px; background: #1d4ed8; vertical-align: 3px; }
.capl { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #e3e8ef; text-align: center; font-size: 12.5px; color: #64748b; } .capl b { color: #b5862a; }

.amr-wrap { flex: 1; display: flex; flex-direction: column; gap: 8px; min-height: 0; }
.amr-top.big { flex: none; display: flex; flex-direction: column; align-items: center; gap: 2px; padding-bottom: 8px; border-bottom: 1px solid #e3e8ef; }
.gbox svg { width: clamp(150px, 22vh, 210px); height: auto; display: block; }
.g-bg { fill: none; stroke: #e3e8ef; stroke-width: 10; stroke-linecap: round; } .g-fg { fill: none; stroke: #c9972f; stroke-width: 10; stroke-linecap: round; }
.big { fill: #112847; font-size: 20px; font-weight: 800; text-anchor: middle; } .tx { font-size: 10px; } .tx.dim { fill: #64748b; font-size: 9px; } .tx.c { text-anchor: middle; }
.gnote { font-size: 12px; color: #64748b; }
.dbst { margin: 0; font-size: 11.5px; color: #64748b; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; justify-content: center; }
.dbst i { width: 7px; height: 7px; border-radius: 50%; background: #94a3b8; flex: none; } .dbst i.on { background: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,.18); }
.lastu { font-style: normal; } .lastu.bad { color: #c62f2f; font-weight: 700; }
.amrl { list-style: none; margin: 0; padding: 2px 0 0; display: flex; flex-direction: column; justify-content: space-evenly; gap: 4px; flex: 1; font-size: 12.5px; }
.amrr { display: grid; grid-template-columns: 9px 58px 100px 1fr 38px 64px; align-items: center; gap: 8px; white-space: nowrap; min-height: 22px; }
.amrr b { color: #112847; font-weight: 700; font-size: 13px; }
.amrr .lb { justify-self: start; max-width: 100%; padding: 1px 9px; border-radius: 99px; font-size: 12px; font-weight: 700; color: #0f7a5c; background: #e3f6ee; overflow: hidden; text-overflow: ellipsis; }
.amrr .lb.s-sp { color: #1d4ed8; background: #e8f0fe; } .amrr .lb.s-chg { color: #a15c00; background: #fff1d6; } .amrr .lb.s-fault { color: #c62f2f; background: #fdecec; }
.amrr .bar { height: 7px; background: #eef1f6; border-radius: 4px; overflow: hidden; } .amrr .bar i { display: block; height: 100%; background: #0f9f6e; border-radius: 4px; }
.amrr .pct { color: #334155; text-align: right; font-weight: 600; font-variant-numeric: tabular-nums; }
.amrr .gold { color: #b5862a; font-weight: 700; text-align: right; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; } .dot.chg { background: #f0a800; } .dot.fault { background: #ef4444; } .dot.sp { background: #3b82f6; }

/* 2줄: 확인 필요 | 예지보전 | 병목 */
.tri { display: grid; grid-template-columns: 1.1fr 1.3fr 1fr; gap: 14px; }
.plist { display: flex; flex-direction: column; max-height: 260px; overflow: auto; }
.tag { flex: none; border-radius: 6px; padding: 1px 8px; font-size: 11.5px; font-weight: 700; background: #e3f6ee; color: #0f7a5c; } .tag.bad { background: #fdecec; color: #c62f2f; } .tag.warn { background: #fff1d6; color: #a15c00; }
.arow { display: flex; gap: 8px; align-items: center; padding: 8px 6px; border-top: 1px solid #eef1f6; color: #112847; text-decoration: none; font-size: 13px; border-radius: 8px; }
.arow:first-child { border-top: 0; } .arow:hover { background: #f6f8fb; }
.arow .atx { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.arow.done { opacity: .5; } .arow.done .atx { text-decoration: line-through; }
.ast { flex: none; border: 1px solid #cfd6df; background: #fff; color: #475569; border-radius: 99px; padding: 1px 10px; font-size: 11.5px; font-weight: 600; cursor: pointer; }
.ast.s1 { border-color: #93b4f5; color: #1d4ed8; background: #eef4ff; } .ast.s2 { border-color: #f3cf8a; color: #a15c00; background: #fff7e8; } .ast.s3 { border-color: #9bd8bd; color: #0f7a5c; background: #ecf8f2; }
.okline { display: flex; align-items: center; gap: 6px; color: #0f7a5c; font-size: 13px; padding: 10px 4px; } .okline i { font-size: 15px; }
.prow { padding: 7px 0; border-top: 1px solid #eef1f6; } .prow:first-child { border-top: 0; }
.prow1 { display: flex; gap: 8px; align-items: center; font-size: 13px; } .prow1 b { color: #112847; }
.pm-h { margin-left: auto; color: #334155; font-weight: 700; font-variant-numeric: tabular-nums; }
.pbar { flex: 1; height: 7px; background: #eef1f6; border-radius: 4px; overflow: hidden; margin-top: 5px; }
.pbar i { display: block; height: 100%; border-radius: 4px; background: #0f9f6e; } .pbar i.warn { background: #f0a800; } .pbar i.bad { background: #ef4444; }
.pnote { font-size: 11.5px; color: #64748b; margin-top: 4px; }
.brow { display: flex; align-items: center; gap: 10px; padding: 9px 0; border-top: 1px solid #eef1f6; font-size: 13px; } .brow:first-child { border-top: 0; }
.brow > span { width: 110px; flex: none; color: #334155; } .brow .pbar { margin-top: 0; } .brow b { width: 56px; text-align: right; flex: none; color: #112847; }

/* 표 (AMR별 운영 · 화재 기록) */
.rtab { width: 100%; border-collapse: separate; border-spacing: 0; font-size: 13px; }
.rtab th { background: #f4f7fb; color: #475569; font-weight: 700; padding: 9px 8px; text-align: center; border-bottom: 1px solid #e3e8ef; white-space: nowrap; }
.rtab th:first-child { border-top-left-radius: 8px; } .rtab th:last-child { border-top-right-radius: 8px; }
.rtab td { padding: 9px 8px; text-align: center; border-bottom: 1px solid #eef1f6; color: #112847; font-variant-numeric: tabular-nums; }
.rtab td.l { text-align: left; } .rtab tbody tr:hover td { background: #f9fbfd; } .rtab tbody tr:last-child td { border-bottom: 0; }
.rtab td.lowb, .rtab td.warnc { color: #b86e00; font-weight: 700; } .rtab td.crit { color: #c62f2f; font-weight: 700; }
.tg { display: inline-block; border-radius: 99px; padding: 2px 10px; font-size: 12px; font-weight: 700; color: #0f7a5c; background: #e3f6ee; }
.tg.fault { color: #c62f2f; background: #fdecec; } .tg.chg { color: #a15c00; background: #fff1d6; } .tg.sp { color: #1d4ed8; background: #e8f0fe; }

@media (max-width: 1400px) { .tri { grid-template-columns: 1fr 1fr; } .tri > .panel:last-child { grid-column: 1 / -1; } }
@media (max-width: 1100px) { .grid, .tri { grid-template-columns: 1fr; } .tri > .panel:last-child { grid-column: auto; } .pr { display: none; } }
/* 26.10.29 카드 3개씩 두 줄 + 탭 */
.kpis { grid-template-columns: repeat(3, 1fr); }
.kgrp { grid-column: 1 / -1; font-size: 12.5px; font-weight: 700; color: #64748b; margin: 2px 0 -4px 2px; }
.stabs { display: flex; gap: 6px; flex-wrap: wrap; background: #fff; border: 1px solid #e3e8ef; border-bottom: 0; border-radius: 12px 12px 0 0; padding: 10px 12px 0; margin-bottom: -14px; }
.stab { border: 1px solid #d5dbe5; border-bottom: 0; background: #eef1f6; color: #334155; border-radius: 10px 10px 0 0; padding: 9px 16px; font-size: 14px; font-weight: 700; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; }
.stab:hover { background: #e3e8f0; }
.stab.on { background: #1d4ed8; color: #fff; border-color: #1d4ed8; }
.stab .n { background: rgba(0,0,0,.12); border-radius: 99px; padding: 0 7px; font-size: 12px; } .stab.on .n { background: rgba(255,255,255,.25); }
.stab.fire { background: #fdecec; color: #c62828; border-color: #f3b4b4; } .stab.fire.on { background: #d32f2f; color: #fff; border-color: #d32f2f; }
.stbody { background: #fff; border: 1px solid #e3e8ef; border-radius: 0 12px 12px 12px; padding: 14px; box-shadow: 0 1px 3px rgba(17,40,71,.06); }
.stbody .card.panel { box-shadow: none; }
@media (max-width: 900px) { .kpis { grid-template-columns: repeat(2, 1fr); } }
</style>
