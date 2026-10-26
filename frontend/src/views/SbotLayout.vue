<template>
  <!-- 26.10.20 추가: S-Bot 전용 화면. S Cargo 메뉴/사이드바와 분리된 별도 셸. -->
  <div class="sb-shell">
    <header class="sb-top">
      <RouterLink :to="sbotAdmin ? '/sbot' : '/sbot/hub'" class="sb-brand"><img :src="logo" alt="S-Bot" /></RouterLink>
      <span class="sb-title">{{ sbotAdmin ? "S-Bot 대시보드" : "허브 AMR 관제" }}</span>
      <nav class="sb-nav">
        <!-- 26.10.22 통합: 대시보드 · 택배 이동 · 물류센터는 S-Bot 관리자만, 관제사(CONTROL · HUB_OP)는 허브 관제만 -->
        <RouterLink v-if="sbotAdmin" to="/sbot">대시보드</RouterLink>
        <RouterLink to="/sbot/hub">허브 관제</RouterLink>
        <!-- 26.10.25 상단 메뉴에서 숨김: 출입증 감사(/sbot/amr-audit) 화면과 경로는 그대로 남겨 둠
        <RouterLink v-if="canAudit" to="/sbot/amr-audit">출입증 감사</RouterLink> -->
        <RouterLink v-if="canScargo" to="/w/W-02" class="sb-sw">S CARGO</RouterLink><!-- fix_1105 (scargo_1): S Cargo 관리자는 S Cargo 로 전환 -->
        <RouterLink to="/">홈</RouterLink>
        <a v-if="sbotAdmin" href="#" class="sb-demo" :class="{ run: demo >= 0 }" @click.prevent="demo >= 0 ? stopDemo() : startDemo()">{{ demo >= 0 ? "■ 시연 중지" : "▶ 시연" }}</a><!-- 26.10.25 시연 시나리오 -->
        <span class="sb-stat":class="stat" :title="statTip"><i></i>{{ statText }}</span><!-- 26.10.25 추가: 서버 연결 상태 -->
        <span class="sb-user" v-if="authState.user">{{ displayName }}님<template v-if="accessLabel"> · {{ accessLabel }}</template></span>
        <a href="#" @click.prevent="logout">로그아웃</a>
      </nav>
    </header>
    <main class="sb-main"><router-view /></main>
    <div v-if="demo >= 0" class="sb-cap">
      <b>{{ demo + 1 }} / {{ DEMO.length }} · {{ DEMO[demo].t }}</b> {{ DEMO[demo].cap }}<em v-if="liveTxt" class="sb-live">{{ liveTxt }}</em>
      <span class="sb-ctl"><button type="button" @click="demoGo(-1)">◀</button><button type="button" @click="demoPause">{{ paused ? "▶" : "❚❚" }}</button><button type="button" @click="demoGo(1)">▶▶</button></span>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from "vue";
import { amrApi } from "@/components/amr/amrApi.js";
import { useRouter } from "vue-router";
import axios from "axios";
import { authState, clearLogin } from "@/auth/authState.js";
import { API_BASE } from "@/utils/apiBase.js";
import { hubAccess, isSbotAdmin, isMaster } from "@/components/amr/amrAccess.js"; // 26.10.22 통합: 화면별 권한
import logo from "@/assets/sbot_logo.png";

const router = useRouter();
const sbotAdmin = computed(() => isSbotAdmin(authState.user));
// 26.10.22 통합: 상단에 권한 범위 표시 (관리자 · 전체 허브 / 통합 관제사 · 조회 전용 / 허브 관제사 · 소속 허브)
const displayName = computed(() => (isMaster(authState.user) ? "마스터" : authState.user?.userName || "")); // 26.10.26 마스터 계정은 '마스터님'
const accessLabel = computed(() => hubAccess(authState.user).label || "");
// 26.10.23 출입증 감사는 앱 연결 코드를 다루는 계정(S-Bot 관리자 · HUB_OP)만
const canAudit = computed(() => hubAccess(authState.user).canManage);
// fix_1105 (scargo_1): S Cargo 로 전환 (S Cargo 관리자만, sbot 계정은 숨김)
const canScargo = computed(() => authState.user?.userType === "ADMIN" && authState.user?.adminScope !== "SBOT");
// 26.10.22: 센터별 입고 · 통행료 공용 데이터(대시보드 · 택배 이동 · 물류센터 공유). S-Bot 화면이 열려 있는 동안 자동으로 진행된다
//  26.10.22 통합: S-Bot 관리자 전용 API(/api/amr/sbot/**)를 2초마다 부르므로 S-Bot 관리자일 때만 불러온다 (관제사는 403)
onMounted(() => {
  if (!sbotAdmin.value) return;
  if (window.SBOT) return;
  const s = document.createElement("script");
  s.src = "/sbot3d/shared.js";
  document.head.appendChild(s);
});
// 26.10.25 추가: 상단 서버 연결 상태 배지 (10초마다 확인). 정상 / 확인 중 / 연결 끊김
const stat = ref("wait");
const statText = computed(() => (stat.value === "ok" ? "시스템 정상" : stat.value === "auth" ? "서버 정상 · 로그인 필요" : stat.value === "off" ? "서버 연결 안 됨" : "확인 중"));
const statTip = computed(() => (stat.value === "ok" ? "서버·DB 응답 정상" : stat.value === "auth" ? "서버는 켜져 있습니다. 로그인(관리자 권한)이 필요해 값은 시뮬레이션으로 표시됩니다" : stat.value === "off" ? "서버(8080)에 연결되지 않습니다 (시뮬레이션 값 표시)" : "연결 확인 중"));
let statTimer;
// 26.10.25 수정: 서버가 켜져 있어도 401/403(로그인·권한)이면 '꺼짐'으로 보이던 문제.
//  같은 주소(/api, vite 프록시)로 먼저 확인하고, 안 되면 amrApi 주소(8080)로 확인. 401/403은 '서버 정상 · 로그인 필요'로 구분
async function checkStat() {
  amrApi.useCookieAuth();
  try {
    const r = await fetch("/api/amr/kpi", { credentials: "include" });
    if ((r.headers.get("content-type") || "").includes("json")) {
      stat.value = r.ok ? "ok" : (r.status === 401 || r.status === 403) ? "auth" : "off";
      return;
    }
  } catch { /* 같은 주소에 프록시가 없으면 아래로 */ }
  try {
    const k = await amrApi.kpi();
    stat.value = k ? "ok" : amrApi.online ? "auth" : "off";
  } catch { stat.value = "off"; }
}
onMounted(() => { checkStat(); statTimer = setInterval(checkStat, 10000); });
onBeforeUnmount(() => clearInterval(statTimer));
// 26.10.25 시연 시나리오: 대시보드 → 허브 → 택배 이동 → 물류센터 → 대시보드 순서로 자동 이동하며 자막을 보여 준다
// clock: 통행료 낮/밤 비교용 (sbot_clock). 없으면 그대로 둔다
const DEMO = [
  { to: "/sbot", sec: 7, t: "오늘의 현황", cap: "허브 처리 건수 · AMR 가동 · 운송 중 택배차를 한눈에 확인합니다" },
  { to: "/sbot/hub", sec: 9, restart: true, pre: "gateIn", spd: 2, t: "허브 입고", cap: "항만에서 온 화물차가 허브에 들어와 입고됩니다" },
  { to: "/sbot/hub", sec: 9, pre: "rack", spd: 2, t: "상하차", cap: "AMR이 택배를 센터별로 나눠 택배차에 적재합니다" },
  { to: "/sbot/hub", sec: 22, pre: "station", spd: 4, t: "예지보전", cap: "AMR 이상 징후 감지부터 고장 예측, 정비·교대, 복귀까지 단계별로 확인합니다" },
  { to: "/sbot/hub", sec: 8, act: "depart", pre: "dockOut", spd: 2, t: "택배차 출발", cap: "적재가 끝난 택배차가 허브를 출발합니다" },
  { to: "/sbot/move", sec: 9, t: "통행료 · 낮", cap: "톨게이트 통행료는 낮 요금이 적용됩니다", clock: "day" },
  { to: "/sbot/move", sec: 9, t: "통행료 · 밤", cap: "밤에는 심야 할인으로 통행료가 달라집니다", clock: "night" },
  { to: "/sbot/center", sec: 10, act: "arrive", t: "센터 도착", cap: "배정된 택배차와 물류가 얼마나 도착했는지 확인합니다" },
  { to: "/sbot", sec: 6, t: "결과 확인", cap: "센터별 입고 결과를 대시보드에서 다시 확인합니다" },
];
const demo = ref(-1);
const paused = ref(false);
let demoTimer;
// 26.10.25 시연 전용 데이터: 시작 시각 이후의 기록만 보여 주고(DB 는 지우지 않음) 정해진 값으로 출발 · 도착을 만든다. 끝나면 실시간 모드로 되돌린다
const SEED = { GGI: [18, 16, 12], SEL: [12, 10, 8], ICN: [30, 22] }; // 시연 시작 때 이미 도착해 있는 택배차(물품 수)
const GO = { GGI: 14, SEL: 10, ICN: 20 }; // 4단계에서 출발해 8단계에서 도착하는 택배차
let pend = [];
const sb = () => window.SBOT;
async function demoSeed() {
  const S = sb(); if (!S) return;
  try { localStorage.setItem("sbot_demo_since", String(Date.now())); } catch { /* 무시 */ }
  S.setAuto(false); S.discReset(); pend = [];
  for (const k of Object.keys(SEED)) for (const n of SEED[k]) { const j = await S.dispatchNow(k, n); if (j && j.assignmentId) await S.arrive(k, { asg: j.assignmentId, no: j.vehicleNo }); }
  S.sync();
}
async function demoDepart() {
  const S = sb(); if (!S) return;
  for (const k of Object.keys(GO)) { const j = await S.dispatchNow(k, GO[k]); if (j && j.assignmentId) pend.push({ k, asg: j.assignmentId, no: j.vehicleNo }); }
  S.sync();
}
async function demoArrive() {
  const S = sb(); if (!S) return;
  for (const p of pend) await S.arrive(p.k, { asg: p.asg, no: p.no });
  pend = []; S.sync();
}
function demoEnd() {
  try { localStorage.removeItem("sbot_demo_since"); } catch { /* 무시 */ }
  const S = sb(); if (S) { S.setAuto(true); S.sync(); }
}
// 허브 화면의 카메라 · 배속 버튼을 눌러 자막과 같은 장면을 보여 준다 (허브 화면이 뜬 뒤에 적용)
function applyView(s) {
  const q = (sel) => document.querySelector(sel);
  if (s.restart) q("#tw-restart")?.click();
  if (s.spd) q('.spd[data-s="' + s.spd + '"]')?.click();
  if (s.pre) q('[data-preset="' + s.pre + '"]')?.click();
}
// 자막 끝에 화면의 실제 상태(허브 단계 · 택배 이동 시각)를 붙인다
const liveTxt = ref("");
let liveTimer;
function readLive() {
  try {
    const s = demo.value >= 0 ? DEMO[demo.value] : null;
    if (!s) { liveTxt.value = ""; return; }
    if (s.to === "/sbot/hub") liveTxt.value = (document.querySelector(".stage.now")?.innerText || "").replace(/\s+/g, " ").trim();
    else if (s.to === "/sbot/move") liveTxt.value = document.querySelector("iframe.sb-frame")?.contentDocument?.getElementById("clk")?.textContent || "";
    else liveTxt.value = "";
  } catch { liveTxt.value = ""; }
}
onMounted(() => { liveTimer = setInterval(readLive, 1000); });
onBeforeUnmount(() => clearInterval(liveTimer));
function runDemo(i) {
  clearTimeout(demoTimer);
  if (i >= DEMO.length || i < 0) { demo.value = -1; paused.value = false; demoEnd(); return; }
  demo.value = i;
  const s = DEMO[i];
  if (s.act === "depart") demoDepart(); else if (s.act === "arrive") demoArrive();
  if (s.pre || s.spd || s.restart) setTimeout(() => applyView(s), router.currentRoute.value.path === s.to ? 300 : 2200);
  if (router.currentRoute.value.path !== s.to) router.push(s.to);
  if (s.clock) { try { localStorage.setItem("sbot_clock", JSON.stringify({ tod: s.clock === "night" ? 23 : 12, at: Date.now(), rate: 0.2 })); } catch { /* 무시 */ } }
  if (!paused.value) demoTimer = setTimeout(() => runDemo(i + 1), s.sec * 1000);
}
async function startDemo() { paused.value = false; demo.value = 0; await demoSeed(); runDemo(0); }
function stopDemo() { clearTimeout(demoTimer); demo.value = -1; paused.value = false; demoEnd(); }
function demoGo(d) { runDemo(Math.min(DEMO.length - 1, Math.max(0, demo.value + d))); }
function demoPause() { paused.value = !paused.value; if (paused.value) clearTimeout(demoTimer); else runDemo(demo.value); }
onBeforeUnmount(() => clearTimeout(demoTimer));
function logout() {
  axios.post(`${API_BASE}/api/accounts/logout`, {}, { withCredentials: true })
    .catch(() => {})
    .finally(() => { clearLogin(); router.push("/"); });
}
</script>

<style scoped>
.sb-shell { min-height: 100vh; background: #0b1d36; color: #e6eff7; }
.sb-top { height: 64px; display: flex; align-items: center; gap: 18px; padding: 0 24px; background: #0f2a4a; border-bottom: 1px solid #1d3b60; position: sticky; top: 0; z-index: 50; }
.sb-brand img { height: 44px; border-radius: 8px; display: block; }
.sb-title { font-weight: 700; font-size: 17px; letter-spacing: .04em; }
.sb-nav { margin-left: auto; display: flex; align-items: center; gap: 18px; font-size: 14px; }
.sb-nav a { color: #c9d8ea; text-decoration: none; }
.sb-nav a:hover { color: #ff9f1c; }
.sb-user { color: #9fb0c6; }
.sb-main { padding: 18px 24px 40px; }
.sb-nav a, .sb-user { white-space: nowrap; }
/* 26.10.25 추가: 지금 보고 있는 메뉴 강조 + 서버 상태 배지 */
.sb-nav a.router-link-exact-active { color: #ff9f1c; font-weight: 700; border-bottom: 2px solid #ff9f1c; padding-bottom: 3px; }
.sb-nav a.sb-demo { color: #1a2540; background: #f3d27a; border-radius: 99px; padding: 3px 12px; font-weight: 700; font-size: 13px; }
.sb-nav a.sb-demo.run { background: #ff6b6b; color: #2a0b10; }
.sb-cap { position: fixed; left: 50%; bottom: 22px; transform: translateX(-50%); z-index: 80; max-width: 92vw; background: rgba(8,16,32,.88); border: 1px solid #f3d27a; border-radius: 12px; padding: 10px 20px; font-size: 15px; box-shadow: 0 6px 24px rgba(0,0,0,.45); }
.sb-cap b { color: #f3d27a; margin-right: 8px; }
.sb-live { margin-left: 10px; color: #9fe3d2; font-style: normal; font-size: 13px; border-left: 1px solid #3a5278; padding-left: 10px; }
.sb-ctl { margin-left: 14px; display: inline-flex; gap: 6px; }
.sb-ctl button { background: #1b2b45; color: #e6eff7; border: 1px solid #3a5278; border-radius: 6px; padding: 2px 9px; cursor: pointer; font-size: 12px; }
.sb-ctl button:hover { border-color: #f3d27a; }
.sb-stat { display: inline-flex; align-items: center; gap: 6px; padding: 3px 11px; border-radius: 99px; font-size: 12px; font-weight: 700; white-space: nowrap; background: #123a36; color: #4fe3c1; border: 1px solid #1f6b5c; }
.sb-stat i { width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
.sb-stat.off { background: #4a2a12; color: #ffb25c; border-color: #8a5a22; }
.sb-stat.auth { background: #3a3414; color: #f3d27a; border-color: #7a6a22; }
.sb-stat.wait { background: #1b2b45; color: #9fb0c6; border-color: #2f4468; }
/* 좁은 화면: 상단 메뉴가 세로로 깨지지 않게 줄바꿈 */
@media (max-width: 900px) {
  .sb-top { height: auto; flex-wrap: wrap; gap: 8px 14px; padding: 8px 14px; }
  .sb-brand img { height: 36px; }
  .sb-title { font-size: 15px; }
  .sb-nav { margin-left: 0; width: 100%; flex-wrap: wrap; gap: 6px 14px; font-size: 13px; }
  .sb-main { padding: 12px 12px 32px; }
}
.sb-nav .sb-sw{background:#EECC83;color:#102644 !important;font-weight:800;border-radius:14px;padding:3px 12px;font-size:13px;letter-spacing:.03em;text-decoration:none} /* scargo_1 fix_1105 */
</style>
