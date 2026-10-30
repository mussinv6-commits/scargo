<template>
  <!-- 26.10.20 추가: S-Bot 전용 화면. S Cargo 메뉴/사이드바와 분리된 별도 셸. -->
  <!-- 26.10.28 S Cargo 관리자: S Cargo 와 같은 상단바(App.vue) + 사이드바 + 메뉴명 바 -->
  <div v-if="canScargo" class="admin-app sb-admin">
    <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
      <aside class="admin-sidebar">
        <div class="admin-nav-scroll">
          <div class="admin-nav-group-label sb-grp"><span>S-Bot</span></div>
          <RouterLink v-if="sbotAdminView" to="/sbot" class="admin-nav-item admin-nav-home" :class="{ active: route.path === '/sbot' }"><i class="bi bi-speedometer2"></i> 대시보드</RouterLink>
          <RouterLink to="/sbot/hub" class="admin-nav-item admin-nav-home" :class="{ active: route.path.startsWith('/sbot/hub') }"><i class="bi bi-diagram-3"></i> 허브 관제</RouterLink>
          <div class="admin-side-foot"><p>멈추지 않는 허브,<br />AMR 예지보전</p></div><!-- 26.10.28 S-Bot(AMR 전용) 문구 -->
        </div>
      </aside>
      <div class="admin-main sb-main2" :class="{ 'has-title': true }">
        <header class="admin-topbar">
          <button type="button" class="admin-burger" :aria-label="collapsed ? '메뉴 펼치기' : '메뉴 숨기기'" @click="collapsed = !collapsed"><i class="bi bi-list"></i></button>
          <div v-if="route.path === '/sbot'" class="ops-head-copy"><h1>S-Bot 대시보드</h1><p>허브 AMR 운영 현황을 한눈에 확인하세요.</p></div>
          <h1 v-else class="admin-page-title">{{ route.path.startsWith('/sbot/hub') ? '허브 관제' : 'S-Bot' }}</h1>
          <div class="admin-topbar-right">
            <span class="sb-stat" :class="stat" :title="statTip"><i></i>{{ statText }}</span>
            <a v-if="sbotAdminView" href="#" class="sb-demo2" :class="{ run: demo >= 0 }" @click.prevent="demo >= 0 ? stopDemo() : startDemo()">{{ demo >= 0 ? "■ 시연 중지" : "▶ 시연" }}</a>
            <LiveClock />
          </div>
        </header>
        <router-view />
      </div>
    </div>
  </div>
  <div v-else class="sb-shell">
    <header class="sb-top">
      <RouterLink :to="sbotAdmin ? '/sbot' : '/sbot/hub'" class="sb-brand"><img :src="logo" alt="S-Bot" /></RouterLink>
      <span class="sb-title">{{ sbotAdmin ? "S-Bot 대시보드" : "허브 AMR 관제" }}</span>
      <nav class="sb-nav">
        <!-- 26.10.22 통합: 대시보드 · 택배 이동 · 물류센터는 S-Bot 관리자만, 관제사(CONTROL · HUB_OP)는 허브 관제만 -->
        <RouterLink v-if="sbotAdmin" to="/sbot">대시보드</RouterLink>
        <RouterLink to="/sbot/hub">허브 관제</RouterLink>
        <!-- 26.10.25 상단 메뉴에서 숨김: 출입증 감사(/sbot/amr-audit) 화면과 경로는 그대로 남겨 둠
        <RouterLink v-if="canAudit" to="/sbot/amr-audit">출입증 감사</RouterLink> -->
        <RouterLink v-if="canScargo" to="/admin" class="sb-sw">S CARGO</RouterLink><!-- 26.10.28 총관리자 첫 화면(/admin)으로 --><!-- fix_1105 (scargo_1): S Cargo 관리자는 S Cargo 로 전환 -->
        <RouterLink to="/">홈</RouterLink>
        <a v-if="sbotAdmin" href="#" class="sb-demo" :class="{ run: demo >= 0 }" @click.prevent="demo >= 0 ? stopDemo() : startDemo()">{{ demo >= 0 ? "■ 시연 중지" : "▶ 시연" }}</a><!-- 26.10.29 시연: 허브 화면의 AMR 전체 시연만 실행 (화재는 앱·웹 연동 시연에서 따로) -->
        <span class="sb-stat":class="stat" :title="statTip"><i></i>{{ statText }}</span><!-- 26.10.25 추가: 서버 연결 상태 -->
        <span class="sb-user" v-if="authState.user">{{ displayName }}님<template v-if="accessLabel"> · {{ accessLabel }}</template></span>
        <a href="#" @click.prevent="logout">로그아웃</a>
      </nav>
    </header>
    <main class="sb-main"><router-view /></main>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from "vue";
import { amrApi } from "@/components/amr/amrApi.js";
import { useRoute, useRouter } from "vue-router";
import LiveClock from "@/components/common/LiveClock.vue";
import axios from "axios";
import { authState, clearLogin } from "@/auth/authState.js";
import { API_BASE } from "@/utils/apiBase.js";
import { hubAccess, isSbotAdmin, isMaster } from "@/components/amr/amrAccess.js"; // 26.10.22 통합: 화면별 권한
import logo from "@/assets/sbot_logo.png";
import { staffRole } from "@/scargo2/roles.js";

const router = useRouter();
const route = useRoute();
const collapsed = ref(false); // 26.10.28 사이드바 숨김/펼침 (S Cargo 와 같은 삼선 버튼)
const sbotAdmin = computed(() => isSbotAdmin(authState.user));
// 26.10.22 통합: 상단에 권한 범위 표시 (관리자 · 전체 허브 / 통합 관제사 · 조회 전용 / 허브 관제사 · 소속 허브)
const displayName = computed(() => (isMaster(authState.user) ? "마스터" : authState.user?.userName || "")); // 26.10.26 마스터 계정은 '마스터님'
const accessLabel = computed(() => hubAccess(authState.user).label || "");
// 26.10.23 출입증 감사는 앱 연결 코드를 다루는 계정(S-Bot 관리자 · HUB_OP)만
const canAudit = computed(() => hubAccess(authState.user).canManage);
// fix_1105 (scargo_1): S Cargo 로 전환 (S Cargo 관리자만, sbot 계정은 숨김)
const canScargo = computed(() => (authState.user?.userType === "ADMIN" && authState.user?.adminScope !== "SBOT") || (!!staffRole(authState.user) && hubAccess(authState.user).canView)); // 26.10.29 관제사(허브 관제)도 S Cargo 와 같은 상단바 · 사이드바
const sbotAdminView = computed(() => authState.user?.userType === "ADMIN"); // 대시보드 · 시연은 관리자만
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
// 26.10.29 시연: 상단 버튼은 허브 관제 화면(/sbot/hub)으로 가서 그 화면 아래의 "전체 시연"(AMR 장면만)을 눌러 준다.
//  예전 9단계(대시보드 · 택배 이동 · 물류센터로 이동) 시연은 없앴다. 화재 상황은 앱 · 웹 연동 시연에서 따로 보여 준다
const demo = ref(-1); // -1 멈춤 · 0 진행 중 (허브 시연 바의 버튼 글자로 판단)
const hubBtn = (i) => document.querySelectorAll(".tw-demo button")[i];
const hubDemoRunning = () => /처음부터/.test(hubBtn(0)?.textContent || "");
const syncDemo = () => { demo.value = hubDemoRunning() ? 0 : -1; };
let demoPoll;
onMounted(() => { demoPoll = setInterval(syncDemo, 800); });
onBeforeUnmount(() => clearInterval(demoPoll));
async function startDemo() {
  if (router.currentRoute.value.path !== "/sbot/hub") router.push("/sbot/hub");
  for (let n = 0; n < 40 && !hubBtn(0); n++) await new Promise((r) => setTimeout(r, 250)); // 허브 화면이 뜰 때까지 최대 10초
  hubBtn(0)?.click();
  syncDemo();
}
function stopDemo() { hubBtn(2)?.click(); syncDemo(); }
function logout() {
  axios.post(`${API_BASE}/api/accounts/logout`, {}, { withCredentials: true })
    .catch(() => {})
    .finally(() => { clearLogin(); router.push("/"); });
}
</script>

<style src="@/components/CSS/admin.css"></style>
<style scoped>
.sb-shell { min-height: 100vh; background: #0b1d36; color: #e6eff7; }
.sb-top { height: 64px; display: flex; align-items: center; gap: 18px; padding: 0 24px; background: #0f2a4a; border-bottom: 1px solid #1d3b60; position: sticky; top: 0; z-index: 50; }
.sb-brand img { height: 44px; border-radius: 8px; display: block; }
.sb-title { font-weight: 700; font-size: 17px; letter-spacing: .04em; }
.sb-nav { margin-left: auto; display: flex; align-items: center; gap: 18px; font-size: 14px; }
.sb-nav a { color: #c9d8ea; text-decoration: none; }
.sb-nav a:hover { color: #ff9f1c; }
.sb-user { color: #9fb0c6; }
.sb-main { padding: 18px 24px 16px; } /* 26.10.28 아래 여백 줄여 대시보드가 한 화면에 들어가게 */
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

/* 26.10.28 S Cargo 와 같은 밝은 틀 (바탕 · 상단 바). 허브 관제 3D 화면은 어둡게 그대로 */
.sb-shell { background: #e8eef6; color: #112847; }
.sb-top { background: #112847; border-bottom: 0; box-shadow: 0 2px 8px rgba(17,40,71,.18); }
.sb-title { color: #fff; }
.sb-nav a { color: #e6eff7; }
.sb-nav a:hover { color: #EECC83; }
.sb-nav a.router-link-exact-active { color: #EECC83; border-bottom-color: #EECC83; }
.sb-user { color: #c9d8ea; }
</style>
<style scoped>
/* 26.10.28 S Cargo 관리자용 S-Bot 셸 */
.sb-grp { cursor: default; }
.sb-admin .admin-side-foot { padding-top: 12px; border-top: 1px solid rgba(255, 255, 255, 0.1); } /* S Cargo 사이드바 문구와 같은 구분선 */
.sb-main2 { background: #e8eef6; padding-bottom: 12px !important; } /* 26.10.28 아래 여백을 줄여 스크롤바가 생기지 않게 */
/* 허브 관제 3D 화면이 한 화면에 들어오게 (상단바 · 메뉴명 바 높이만큼 줄임) */
.sb-admin :deep(.amr-twin .wrap) { padding-block: 14px 10px; }
/* 26.10.28 S Cargo 사이드바 스타일(admin.css)의 '.admin-shell .kpi-card'(S Cargo 대시보드 카드)가 허브 관제 KPI 카드에도 걸려서
   카드가 가로 정렬로 바뀌고 시간대 막대 · AMR 가동률 막대 폭이 0 이 되던 문제 → 허브 관제 원래 카드 모양으로 되돌림 */
.sb-admin :deep(.amr-twin .kpi-card) { display: block; align-items: normal; gap: normal; padding: 12px 14px; border-radius: 10px; background: linear-gradient(180deg, var(--panel2), var(--panel)); border: 1px solid var(--edge); transition: none; }
.sb-admin :deep(.amr-twin .twin-viewport) { max-height: calc(100vh / var(--sc-zoom, 1) - var(--nav-h, 60px) - 306px); aspect-ratio: auto; height: calc(100vh / var(--sc-zoom, 1) - var(--nav-h, 60px) - 306px); }
.admin-topbar-right .sb-stat { margin-right: 2px; }
.sb-demo2 { display: inline-flex; align-items: center; height: 36px; padding: 0 14px; border-radius: 10px; background: #112847; color: #fff; font-size: 13px; font-weight: 700; text-decoration: none; }
.sb-demo2:hover { color: #EECC83; }
.sb-demo2.run { background: #e5484d; color: #fff; }
.sb-admin .sb-stat { background: #fff; color: #0f7a5c; border-color: #bfe3d6; height: 36px; padding: 0 12px; border-radius: 10px; }
.sb-admin .sb-stat.off { background: #fff4e8; color: #b86e00; border-color: #f3d2a8; }
.sb-admin .sb-stat.auth { background: #fff9e6; color: #8a6410; border-color: #eedc9a; }
.sb-admin .sb-stat.wait { background: #fff; color: #64748b; border-color: #e3e8ef; }
</style>
