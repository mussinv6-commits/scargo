<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from "vue";
import { useRouter, useRoute } from "vue-router";
import axios from "axios";
import Main from "./views/Main.vue";
import NotificationBell from "@/components/common/NotificationBell.vue";
import { authState, clearLogin } from "@/auth/authState.js";
import { API_BASE } from "@/utils/apiBase.js";
import { ADMIN_MENU } from "@/components/admin/adminMenu.js";
import { COMPANY_MENU } from "@/components/company/companyMenu.js";
import safeCargoLogo from "@/assets/scargo_logo_4x.png";

const router = useRouter();
const route = useRoute();

const isAdmin = computed(() => authState.user?.userType === "ADMIN");
const isCorporateApproved = computed(() => authState.user?.userType === "CORPORATE_APPROVED");
const isCorporatePending = computed(() => authState.user?.userType === "CORPORATE_PENDING");
const isDriver = computed(() => authState.user?.userType === "GENERAL");

// 26.09.30 수정: 로그인 유형 표시가 어색하던 문제 → 이름 옆 작은 배지로 정리
const roleBadge = computed(() => {
  if (isAdmin.value) return { label: "관리자", cls: "role-admin" };
  if (isCorporateApproved.value) return { label: "사업자", cls: "role-biz" };
  if (isCorporatePending.value) return { label: "사업자 승인대기", cls: "role-pending" };
  if (isDriver.value) return { label: "화물차 기사", cls: "role-driver" };
  return null;
});

const myInfoLink = computed(() => {
  if (isAdmin.value) return "/admin/profile";
  if (isCorporateApproved.value) return "/company";
  if (isCorporatePending.value) return "/";
  return "/driver/app/my-page";
});

// 역할별 메인 메뉴 (관리자 메뉴는 사이드바와 같은 정의를 사용)
const roleMenu = computed(() => {
  if (isAdmin.value) {
    return {
      label: "관리자",
      icon: "bi-gear",
      base: "/admin",
      groups: [
        [{ to: "/admin", label: "대시보드" }],
        ADMIN_MENU.member,
        ADMIN_MENU.vehicle,
        ADMIN_MENU.yard,
        ADMIN_MENU.etc,
        ADMIN_MENU.notice,
      ],
    };
  }
  if (isCorporateApproved.value) {
    return {
      label: "사업자",
      icon: "bi-building",
      base: "/company",
      groups: [COMPANY_MENU],
    };
  }
  if (isDriver.value) {
    return {
      label: "화물차 기사",
      icon: "bi-truck",
      base: "/driver",
      groups: [[
        { to: "/driver/app/status", label: "운송현황" },
        { to: "/driver/app/dispatch-list", label: "배차목록" },
        { to: "/driver/app/settlement", label: "정산/매출" },
        { to: "/driver/app/my-page", label: "MY/차량" },
        { to: "/driver/app/faq", label: "자주 묻는 질문" },
        { to: "/driver/app/support", label: "고객센터 문의" },
        { to: "/driver/app/terms", label: "약관 및 정책" },
      ]],
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
onMounted(() => document.addEventListener("click", onDocClick));
onBeforeUnmount(() => document.removeEventListener("click", onDocClick));

const isDriverApp = computed(() => route.path.startsWith("/driver"));
const isAdminApp = computed(() => route.path.startsWith("/admin"));
const isCompanyApp = computed(() => route.path.startsWith("/company"));
const hideChrome = computed(() => isDriverApp.value || isAdminApp.value || isCompanyApp.value);

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
  <div id="app">
    <nav class="navbar navbar-expand-lg navbar-dark bg-brand sticky-top site-nav">
      <div class="container-fluid nav-grid">
        <RouterLink to="/" class="navbar-brand brand-logo">
          <img :src="safeCargoLogo" alt="S Cargo" />
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
              <RouterLink to="/" class="nav-link" :class="{ 'is-active': route.path === '/' }">홈</RouterLink>
            </li>
            <li class="nav-item">
              <RouterLink :to="isAdmin ? '/admin/notices' : '/notice'" class="nav-link" :class="{ 'is-active': isNoticeSection }">공지사항</RouterLink>
            </li>
            <!-- 26.10.01: 게이트 OCR 검사는 관리자 메뉴(/admin/gate-ocr)로 이동 -->

            <!-- 역할별 메인 메뉴 (관리자 / 사업자 / 화물차 기사) -->
            <li v-if="roleMenu" ref="roleMenuEl" class="nav-item dropdown">
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

    <div class="wrapper" :class="{ 'wrapper-app': isDriverApp, 'wrapper-admin': isAdminApp || isCompanyApp }">
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
