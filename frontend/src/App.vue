<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount, defineAsyncComponent } from "vue";
import { useRouter, useRoute } from "vue-router";
import axios from "axios";
import Main from "./views/Main.vue";
import NotificationBell from "@/components/common/NotificationBell.vue";
import { authState, clearLogin, isDriverType } from "@/auth/authState.js";
import { API_BASE } from "@/utils/apiBase.js";
// import { ADMIN_MENU } from "@/components/admin/adminMenu.js"; // 26.10.28 상단 역할 드롭다운 삭제로 사용 안 함
// import { COMPANY_MENU } from "@/components/company/companyMenu.js"; // 26.10.28 상단 역할 드롭다운 삭제로 사용 안 함
import safeCargoLogo from "@/assets/scargo_logo_4x.png";
import sbotLogo from "@/assets/sbot_logo.png"; // 26.10.28 S-Bot 화면 상단 로고
import { staffRole, RN, HOME } from "@/scargo2/roles.js"; // 26.10.29 관제사 상단바
import { hubAccess } from "@/components/amr/amrAccess.js";

const router = useRouter();
const route = useRoute();

// 26.10.20: S Cargo 관리자 = ADMIN 이면서 S-Bot 관리자가 아닌 계정
const isAdmin = computed(() => authState.user?.userType === "ADMIN" && authState.user?.adminScope !== "SBOT");
// 26.10.29 관제사(통합 · 허브 · 지역센터)도 총관리자와 같은 상단바 · 사이드바 (메뉴는 권한만큼)
const staff = computed(() => (authState.user?.userType === "ADMIN" ? null : staffRole(authState.user)));
const shellTop = computed(() => isAdmin.value || !!staff.value);
const staffHome = computed(() => (staff.value ? "/w/" + HOME[staff.value] : "/admin"));
const canSbot = computed(() => isAdmin.value || (!!staff.value && hubAccess(authState.user).canView)); // 관제사는 허브 관제(/sbot/hub)만
// 26.10.26 관리자: 사이드바가 없는 화면(메인 등)에서는 "메뉴" 버튼으로 사이드바를 왼쪽에서 열어 준다
const AdminSidebar = defineAsyncComponent(() => import("@/components/admin/AdminSidebar.vue"));
const menuOpen = ref(false);
const hasSidebar = computed(() => route.path.startsWith("/admin") || route.path.startsWith("/w/") || route.path.startsWith("/sbot"));
async function toggleMenu() {
  if (!menuOpen.value) { try { await import("@/components/CSS/admin.css"); } catch { /* 스타일 로드 실패해도 열기는 함 */ } }
  menuOpen.value = !menuOpen.value;
}
watch(() => route.fullPath, () => { menuOpen.value = false; });
// 26.10.26 관리자 상단바 공지 흐름: 최신 공지 5건 (제목만)
const noticeItems = ref([]);
// 26.10.29 긴급 안전 공지(자동): 화재 경보 발령 · 해제 글을 공지 띠 맨 앞에 붉게 표시 (서버 /api/amr/safety/*, 3초마다)
const safetyItems = ref([]);
const safetyLive = ref(null); // 진행 중인 경보 (관리자 아닌 계정은 상단 붉은 띠로 계속 표시)
async function loadSafetyTicker() {
  try {
    const c = await fetch(`${API_BASE}/api/amr/safety/alert`, { credentials: "include" }).then((r) => (r.ok ? r.json() : null));
    safetyLive.value = c && c.active ? c : null;
  } catch { /* 서버 꺼짐 */ }
  if (!isAdmin.value) { safetyItems.value = []; return; }
  try {
    const [cur, his] = await Promise.all([
      fetch(`${API_BASE}/api/amr/safety/alert`, { credentials: "include" }).then((r) => (r.ok ? r.json() : null)),
      fetch(`${API_BASE}/api/amr/safety/history`, { credentials: "include" }).then((r) => (r.ok ? r.json() : [])),
    ]);
    const out = [];
    // 상단 공지 띠에는 진행 중인 경보 한 줄만 (자세한 기록은 공지사항 목록의 긴급 안전 공지 카드)
    if (cur && cur.active) out.push({ id: "sf-live", title: cur.kind === "batt" ? "🚨 AMR 배터리 화재가 발생했습니다" : "🚨 허브에 화재가 발생했습니다", sf: true, live: true });
    safetyItems.value = out;
  } catch { /* 서버 꺼짐 */ }
}
let safetyTimer = 0;
const tickItems = computed(() => [...safetyItems.value, ...noticeItems.value]);
const noticeTitles = computed(() => tickItems.value.map((n) => n.title));
const tickSec = computed(() => Math.max(20, tickItems.value.reduce((s, n) => s + String(n.title).length, 0) * 0.5));
async function loadNoticeTicker() {
  if (!shellTop.value) { noticeItems.value = []; return; }
  try {
    const r = await axios.get(`${API_BASE}/api/v1/posts/category/NOTICE`, { params: { page: 0, size: 5 }, withCredentials: true });
    const list = Array.isArray(r.data) ? r.data : r.data?.content || [];
    noticeItems.value = list.map((p) => ({ id: p.postId ?? p.id, title: String(p.title || "").trim() })).filter((n) => n.id != null && n.title);
  } catch { noticeItems.value = []; }
}
watch(shellTop, loadNoticeTicker, { immediate: true });
const isCorporateApproved = computed(() => authState.user?.userType === "CORPORATE_APPROVED");
const isCorporatePending = computed(() => authState.user?.userType === "CORPORATE_PENDING");
const isDriver = computed(() => isDriverType(authState.user?.userType)); // 26.10.22 통합: CONTAINER_DRIVER · LINEHAUL_DRIVER 도 기사

// 26.09.30 수정: 로그인 유형 표시가 어색하던 문제 → 이름 옆 작은 배지로 정리
const roleBadge = computed(() => {
  if (isAdmin.value) return { label: "관리자", cls: "role-admin" };
  if (staff.value) return { label: RN[staff.value], cls: "role-admin" }; // 26.10.29
  if (isCorporateApproved.value) return { label: "사업자", cls: "role-biz" };
  if (isCorporatePending.value) return { label: "사업자 승인대기", cls: "role-pending" };
  if (isDriver.value) return { label: "화물차 기사", cls: "role-driver" };
  return null;
});

const myInfoLink = computed(() => {
  if (isAdmin.value) return "/admin/profile";
  if (staff.value) return "/w/my";
  if (authState.user?.userType === "BRANCH_DRIVER") return "/app-only"; // 26.10.29 지선 운송기사 // 26.10.29 관제사 내 정보 (예전에는 기사 화면 주소로 가서 첫 화면으로 되돌아감)
  if (isCorporateApproved.value) return "/company";
  if (isCorporatePending.value) return "/pending"; // 26.10.29 승인 대기 운송사는 승인 대기 화면
  return "/driver/app/my-page";
});

// 26.10.28 상단 역할 드롭다운 삭제: 관리자 · 사업자 · 기사는 사이드바(앱은 하단 탭)에 같은 메뉴가 있어 중복.
//  사이드바가 없는 지역센터 관제사(/center)만 남긴다.
const roleMenu = computed(() => {
  // 26.10.23 지역센터 관제사: 센터 인수 확정
  if (false && authState.user?.userType === "CENTER_OP") { // 26.10.29 센터 인수 확정은 사이드바로 옮겨서 상단 드롭다운 사용 안 함
    return {
      label: "지역센터",
      icon: "bi-box-arrow-in-down",
      base: "/center",
      groups: [[{ to: "/center/receipts", label: "센터 인수 확정" }]],
    };
  }
  return null;
});

// 26.09.30 수정: 부트스트랩 JS(data-bs-toggle)에 의존하던 드롭다운/햄버거 메뉴를 Vue 상태로 직접 제어.
const navOpen = ref(false);
const roleOpen = ref(false);
const roleMenuEl = ref(null);

function closeMenus() {
  navOpen.value = false;
  roleOpen.value = false;
}
watch(() => route.fullPath, closeMenus);

function onDocClick(e) {
  if (roleMenuEl.value && !roleMenuEl.value.contains(e.target)) roleOpen.value = false;
}
onMounted(() => { document.addEventListener("click", onDocClick); loadSafetyTicker(); safetyTimer = setInterval(loadSafetyTicker, 3000); });
onBeforeUnmount(() => { document.removeEventListener("click", onDocClick); clearInterval(safetyTimer); });

const isDriverApp = computed(() => route.path.startsWith("/driver"));
const isAdminApp = computed(() => route.path.startsWith("/admin"));
const isCompanyApp = computed(() => route.path.startsWith("/company"));
// 26.10.28 QA C-3: 통합 관제(/w) 화면도 관리자 화면처럼 창 높이에 맞추고 푸터를 숨긴다 (불필요한 상하 스크롤바 제거)
const isWApp = computed(() => route.path.startsWith("/w/") && route.path !== "/w/W-01");
// 26.10.28 S-Bot 도 S Cargo 관리자는 같은 상단바 + 사이드바로 (S-Bot 전용 계정 · 관제사는 기존 S-Bot 상단바 그대로)
const isSbotApp = computed(() => route.path.startsWith("/sbot") && canSbot.value);
const isGroup = computed(() => route.path === "/" || route.path === "/login" || route.path === "/group" || route.path === "/plan" || route.path.startsWith("/tower") || (route.path.startsWith("/sbot") && !canSbot.value));
const hideChrome = computed(() => isGroup.value || isDriverApp.value || isAdminApp.value || isCompanyApp.value || isWApp.value || isSbotApp.value);

function isSection(prefix) {
  return route.path === prefix || route.path.startsWith(prefix + "/");
}
const isNoticeSection = computed(() => isSection("/notice") || isSection("/admin/notices"));

function handleLogout() {
  axios
    .post(`${API_BASE}/api/accounts/logout`, {}, { withCredentials: true })
    .catch(() => {})
    .finally(() => {
      clearLogin();
      router.push("/login");
    });
}

// 26.09.30 수정: 푸터 Top 버튼이 메인 화면('/')으로 이동하던 문제 → 현재 화면 맨 위로 스크롤
function scrollToTop() {
  window.scrollTo({ top: 0, behavior: "smooth" });
}
</script>

<template>
  <div id="app" :class="{ 'admin-top-on': shellTop }">
    <!-- 26.10.29 긴급 경보 중에는 관리자가 화재 종료를 누를 때까지 상단에 붉은 띠가 계속 표시됨 (관리자는 공지 띠에 표시) -->
    <div v-if="safetyLive && !isAdmin" class="safety-bar">🚨 {{ safetyLive.kind === "batt" ? "AMR 배터리 화재가 발생했습니다" : "허브에 화재가 발생했습니다" }}</div>
    <nav v-if="!isGroup" class="navbar navbar-expand-lg navbar-dark bg-brand sticky-top site-nav" :class="{ 'admin-top': shellTop }">
      <div class="container-fluid nav-grid">
        <!-- 26.10.28 S-Bot 화면에서는 맨 왼쪽 로고가 S-Bot 로고 (누르면 S-Bot 대시보드) -->
        <RouterLink :to="isSbotApp ? (isAdmin ? '/sbot' : '/sbot/hub') : '/scargo'" class="navbar-brand brand-logo">
          <img :src="isSbotApp ? sbotLogo : safeCargoLogo" :alt="isSbotApp ? 'S-Bot' : 'S Cargo'" />
        </RouterLink>

        <button
          class="navbar-toggler"
          type="button"
          :aria-expanded="navOpen"
          aria-controls="mainNavbar"
          aria-label="메뉴 열기/닫기"
          @click="navOpen = !navOpen"
        >
          <span class="navbar-toggler-icon"></span>
        </button>

        <div id="mainNavbar" class="collapse navbar-collapse" :class="{ show: navOpen }">
          <ul class="navbar-nav main-menu">
            <li class="nav-item">
              <RouterLink to="/" class="nav-link" :class="{ 'home-btn': shellTop }">{{ shellTop ? "⌂ 본사 홈" : "홈" }}</RouterLink><!-- 26.10.26 관리자는 사이드바에 메뉴가 있어서 상단은 본사 홈 + 공지 흐름 -->
            </li>
            <li v-if="shellTop && !hasSidebar" class="nav-item">
              <a href="#" class="nav-link menu-btn" :class="{ on: menuOpen }" @click.prevent="toggleMenu">☰ 메뉴 {{ menuOpen ? "▴" : "▾" }}</a>
            </li>
            <!-- 26.10.26 관리자 상단바: 최신 공지 제목이 흐르는 띠 (마우스를 올리면 멈춤, 누르면 공지 상세) -->
            <li v-if="shellTop" class="nav-item notice-tick" :title="noticeTitles.length ? '' : '등록된 공지사항이 없습니다'">
              <b class="nt-label">📢 공지</b>
              <div class="nt-view">
                <div v-if="tickItems.length" class="nt-track" :style="{ animationDuration: tickSec + 's' }">
                  <template v-for="k in 2" :key="k">
                    <RouterLink v-for="n in tickItems" :key="k + '-' + n.id" :to="n.sf ? (isAdmin ? '/admin/notices' : '/notice') : (isAdmin ? '/admin/notices/' : '/notice/') + n.id" class="nt-it" :class="{ 'nt-sf': n.sf, 'nt-live': n.live }">{{ n.title }}</RouterLink>
                  </template>
                </div>
                <span v-else class="nt-empty">등록된 공지사항이 없습니다</span>
              </div>
            </li>
            <li v-if="!shellTop" class="nav-item">
              <RouterLink :to="isAdmin ? '/admin/notices' : '/notice'" class="nav-link" :class="{ 'is-active': isNoticeSection }">공지사항</RouterLink>
            </li>
            <!-- 26.10.01: 게이트 OCR 검사는 관리자 메뉴(/admin/gate-ocr)로 이동 -->

            <!-- 역할별 메인 메뉴 (관리자 / 사업자 / 화물차 기사) -->
            <li v-if="roleMenu && !isAdmin" ref="roleMenuEl" class="nav-item dropdown">
              <a
                href="#"
                class="nav-link dropdown-toggle"
                :class="{ 'is-active': isSection(roleMenu.base) }"
                :aria-expanded="roleOpen"
                @click.prevent="roleOpen = !roleOpen"
              >
                {{ roleMenu.label }}{{ isDriver ? '' : ' 메뉴' }}
              </a>
              <ul class="dropdown-menu" :class="{ show: roleOpen }">
                <template v-for="(group, gi) in roleMenu.groups" :key="gi">
                  <li v-if="gi > 0"><hr class="dropdown-divider" /></li>
                  <li v-for="m in group" :key="m.to">
                    <RouterLink :to="m.to" class="dropdown-item" :class="{ 'is-current': route.path === m.to }">{{ m.label }}</RouterLink>
                  </li>
                </template>
              </ul>
            </li>
          </ul>

          <ul v-if="!authState.user" class="navbar-nav auth-menu">
            <li class="nav-item">
              <RouterLink to="/login" class="nav-link auth-link"><i class="bi bi-box-arrow-in-right"></i> 로그인</RouterLink>
            </li>
            <li class="nav-item">
              <RouterLink to="/regi" class="nav-link auth-link auth-cta"><i class="bi bi-person-plus"></i> 회원가입</RouterLink>
            </li>
          </ul>

          <ul v-else class="navbar-nav auth-menu">
            <!-- 26.10.28 QA C-1: S-Bot 이동 버튼. S-Bot 화면의 'S CARGO' 버튼과 같은 자리(오른쪽 메뉴 앞) · 같은 모양 -->
            <li v-if="canSbot" class="nav-item d-flex align-items-center">
              <RouterLink :to="isSbotApp ? staffHome : (isAdmin ? '/sbot' : '/sbot/hub')" class="sbot-sw">{{ isSbotApp ? "S CARGO" : "S-BOT" }}</RouterLink><!-- 26.10.28 S-Bot 화면에서는 같은 자리에서 S Cargo 로 -->
            </li>
            <NotificationBell />
            <li class="nav-item auth-user">
              <span v-if="roleBadge" class="role-badge" :class="roleBadge.cls">{{ roleBadge.label }}</span>
              <span class="auth-name">{{ authState.user.userName }}님</span>
            </li>
            <li class="nav-item">
              <RouterLink :to="myInfoLink" class="nav-link auth-link"><i class="bi bi-person-circle"></i> 내정보</RouterLink>
            </li>
            <li class="nav-item">
              <a href="#" class="nav-link auth-link" @click.prevent="handleLogout"><i class="bi bi-box-arrow-right"></i> 로그아웃</a>
            </li>
          </ul>
        </div>
      </div>
    </nav>

    <!-- 26.10.26 관리자 메뉴(사이드바) 서랍: 누르면 왼쪽에서 열리고, 바깥을 누르면 닫힘 -->
    <div v-if="shellTop && menuOpen" class="admin-drawer-bg" @click="menuOpen = false">
      <div class="admin-drawer" @click.stop><div class="admin-app"><div class="admin-shell"><AdminSidebar /></div></div></div>
    </div>

    <div class="wrapper" :class="{ 'wrapper-app': isDriverApp, 'wrapper-admin': isAdminApp || isCompanyApp || isWApp || isSbotApp }">
      <Main />
    </div>

    <footer v-if="!hideChrome" class="py-4 bg-brand mt-auto">
      <div class="container text-center">
        <button type="button" class="footer-top" @click="scrollToTop">
          <i class="bi bi-arrow-up"></i> Top
        </button>
        <p class="mb-0 mt-2">
          <small>Copyright &copy; 못먹어도S카고</small>
        </p>
      </div>
    </footer>
  </div>
</template>

<style src="@/components/CSS/App.css" scoped></style>

<style>
/* 26.10.26 관리자 상단바: 본사 홈(로고 옆) + 공지 흐름 띠. 다른 계정 상단바는 그대로 */
@media (min-width: 992px) {
  .admin-top .main-menu { position: static !important; transform: none !important; flex: 1 1 auto !important; justify-content: flex-start !important; min-width: 0; }
  .admin-top .navbar-collapse { justify-content: space-between !important; }
}
.sbot-sw { background: #EECC83; color: #102644 !important; font-weight: 800; border-radius: 14px; padding: 3px 12px; font-size: 13px; letter-spacing: .03em; text-decoration: none; white-space: nowrap; margin-right: 6px; } /* 26.10.28 S-Bot 의 S CARGO 버튼과 같은 모양 */
.sbot-sw:hover { filter: brightness(1.05); }
.admin-top .home-btn { border: 1px solid rgba(255,255,255,.4); border-radius: 16px; padding: 4px 14px !important; font-weight: 700; font-size: 14px; }
.admin-top .notice-tick { flex: 1 1 auto; min-width: 0; display: flex; align-items: center; gap: 10px; background: rgba(255,255,255,.08); border-radius: 16px; padding: 4px 14px !important; margin-left: 8px; max-width: 760px; }
.admin-top .menu-btn { border: 1px solid rgba(255,255,255,.4); border-radius: 16px; padding: 4px 14px !important; font-weight: 700; font-size: 14px; white-space: nowrap; }
.admin-top .menu-btn.on { background: #c9a03a; border-color: #c9a03a; color: #fff; }
.admin-drawer-bg { position: fixed; inset: 0; background: rgba(8,16,30,.45); z-index: 1090; }
.admin-drawer { position: fixed; left: 0; top: var(--nav-h, 60px); bottom: 0; width: 270px; z-index: 1100; box-shadow: 4px 0 18px rgba(0,0,0,.35); overflow: hidden; }
.admin-drawer .admin-app, .admin-drawer .admin-shell { height: 100%; }
.admin-drawer .admin-shell .admin-sidebar { width: 100% !important; height: 100% !important; }
.admin-top .nt-label { color: #f0c35a; font-size: 12.5px; white-space: nowrap; }
.admin-top .nt-view { flex: 1; overflow: hidden; min-width: 0; }
.admin-top .nt-track { display: inline-flex; gap: 48px; white-space: nowrap; animation: nt-move linear infinite; padding-left: 100%; }
.admin-top .nt-view:hover .nt-track { animation-play-state: paused; }
.admin-top .nt-it { color: #e9eef8; font-size: 14px; text-decoration: none; }
.admin-top .nt-it:hover { color: #f0c35a; text-decoration: underline; }
/* 홈 화면 아래쪽 노란 공지 줄은 관리자에게는 숨김 (공지는 상단바에서 흐름) */
.admin-top-on .notice-slide { display: none !important; }
.admin-top .nt-empty { color: rgba(233,238,248,.65); font-size: 13.5px; }
@keyframes nt-move { from { transform: translateX(0); } to { transform: translateX(-50%); } }
.safety-bar { position: sticky; top: 0; z-index: 2000; background: #c62828; color: #fff; font-weight: 700; text-align: center; padding: 7px 12px; animation: nt-blink 1s ease-in-out infinite alternate; }
.admin-top .nt-sf { color: #fff3b0 !important; font-weight: 700; }
.admin-top .nt-live { color: #fff !important; background: rgba(255,0,0,.55); border-radius: 10px; padding: 0 8px; animation: nt-blink 1s ease-in-out infinite alternate; }
@keyframes nt-blink { from { opacity: .6 } to { opacity: 1 } }
</style>
