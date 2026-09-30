<script setup>
import { computed } from "vue";
import { useRouter } from "vue-router";
import axios from "axios";
import Main from "./views/Main.vue";
import NotificationBell from "@/components/common/NotificationBell.vue";
import { authState, clearLogin } from "@/auth/authState.js";
import { API_BASE } from "@/utils/apiBase.js";

const router = useRouter();

const isAdmin = computed(() => authState.user?.userType === "ADMIN");
const isCorporateApproved = computed(() => authState.user?.userType === "CORPORATE_APPROVED");
const isCorporatePending = computed(() => authState.user?.userType === "CORPORATE_PENDING");
const isDriver = computed(() => authState.user?.userType === "GENERAL");

// 26.09.21 추가: "내정보"가 항상 화물차 기사 화면으로 고정되어 있던 문제 수정.
// 로그인한 계정의 역할에 맞는 화면으로 보내준다.
const myInfoLink = computed(() => {
  if (isAdmin.value) return "/admin/profile";
  if (isCorporateApproved.value) return "/company";
  if (isCorporatePending.value) return "/"; // 승인 대기 중엔 아직 전용 정보 화면이 없어 홈으로
  return "/driver/app/my-page";
});

function handleLogout() {
  axios
    .post(`${API_BASE}/api/accounts/logout`, {}, { withCredentials: true })
    .then(() => {
      clearLogin();
      router.push("/login");
    })
    .catch(() => {
      clearLogin();
      router.push("/login");
    });
}
</script>

<template>
  <div id="app">
    <!-- 전체폭 -->
    <nav class="navbar navbar-expand-md navbar-dark bg-brand sticky-top">
      <div class="container-fluid nav-grid">
        <RouterLink to="/" class="navbar-brand d-md-none">S카고</RouterLink>
        <button
          class="navbar-toggler"
          type="button"
          data-bs-toggle="collapse"
          data-bs-target="#collapsibleNavbar"
          aria-controls="collapsibleNavbar"
          aria-expanded="false"
          aria-label="메뉴 열기/닫기"
        >
          <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="collapsibleNavbar">
          <div class="nav-spacer"></div>

          <ul class="navbar-nav main-menu">
            <li class="nav-item">
              <RouterLink to="/" class="nav-link">홈</RouterLink>
            </li>

            <!-- 공통: 게시판 -->
            <li class="nav-item dropdown">
              <a href="#" class="nav-link dropdown-toggle" data-bs-toggle="dropdown">게시판</a>
              <ul class="dropdown-menu">
                <li><RouterLink to="/bbslist" class="dropdown-item">게시판</RouterLink></li>
                <li><RouterLink to="/scrlist" class="dropdown-item">게시판2 (무한스크롤)</RouterLink></li>
              </ul>
            </li>

            <!-- 공통: 공지사항 -->
            <li class="nav-item">
              <RouterLink to="/notice" class="nav-link">공지사항</RouterLink>
            </li>

            <!-- 공통: 날씨 -->
            <li class="nav-item">
              <RouterLink to="/weather" class="nav-link">
                <i class="bi bi-cloud-sun"></i> 날씨
              </RouterLink>
            </li>

            <!-- 공통: 번호판 인식 게이트 데모 (26.09.28 추가) -->
            <li class="nav-item">
              <RouterLink to="/gate-demo" class="nav-link">
                🚦 인식 데모
              </RouterLink>
            </li>

            <!-- 메인 메뉴 1: 관리자 (ADMIN 계정에게만 노출) -->
            <li v-if="isAdmin" class="nav-item dropdown role-menu role-admin">
              <a href="#" class="nav-link dropdown-toggle" data-bs-toggle="dropdown">
                🛠️ 관리자
              </a>
              <ul class="dropdown-menu">
                <li><RouterLink to="/admin" class="dropdown-item">대시보드</RouterLink></li>
                <li><hr class="dropdown-divider" /></li>
                <li><RouterLink to="/admin/accounts" class="dropdown-item">회원 관리</RouterLink></li>
                <li><RouterLink to="/admin/companies" class="dropdown-item">업체 관리</RouterLink></li>
                <li><hr class="dropdown-divider" /></li>
                <li><RouterLink to="/admin/trucks" class="dropdown-item">차량 관리</RouterLink></li>
                <li><RouterLink to="/admin/containers" class="dropdown-item">컨테이너 관리</RouterLink></li>
                <li><hr class="dropdown-divider" /></li>
                <li><RouterLink to="/admin/yards" class="dropdown-item">야드 관리</RouterLink></li>
                <li><RouterLink to="/admin/loading-locations" class="dropdown-item">적재 위치 관리</RouterLink></li>
                <li><RouterLink to="/admin/loading-records" class="dropdown-item">적재 기록 조회</RouterLink></li>
                <li><hr class="dropdown-divider" /></li>
                <li><RouterLink to="/admin/overload-checks" class="dropdown-item">과적 검사 관리</RouterLink></li>
              </ul>
            </li>

            <!-- 메인 메뉴 2: 사업자 (승인된 기업 회원에게만 노출) -->
            <li v-if="isCorporateApproved" class="nav-item dropdown role-menu role-biz">
              <a href="#" class="nav-link dropdown-toggle" data-bs-toggle="dropdown">
                🏢 사업자
              </a>
              <ul class="dropdown-menu">
                <li><RouterLink to="/company" class="dropdown-item">업체 정보</RouterLink></li>
                <li><RouterLink to="/company/trucks/new" class="dropdown-item">차량 등록</RouterLink></li>
                <li><RouterLink to="/company/mapping" class="dropdown-item">차량-컨테이너 매핑</RouterLink></li>
              </ul>
            </li>

            <!-- 기업회원 승인 대기 중: 메뉴 대신 안내만 노출 -->
            <li v-else-if="isCorporatePending" class="nav-item">
              <span class="nav-link pending-hint" title="관리자 승인 후 사업자 메뉴가 열립니다.">
                🏢 사업자(승인대기)
              </span>
            </li>

            <!-- 메인 메뉴 3: 화물차 기사 (일반회원에게만 노출) -->
            <li v-if="isDriver" class="nav-item dropdown role-menu role-driver">
              <a href="#" class="nav-link dropdown-toggle" data-bs-toggle="dropdown">
                🚚 화물차 기사
              </a>
              <ul class="dropdown-menu">
                <li><RouterLink to="/driver/app/status" class="dropdown-item">운송현황</RouterLink></li>
                <li><RouterLink to="/driver/app/dispatch-list" class="dropdown-item">배차목록</RouterLink></li>
                <li><RouterLink to="/driver/app/settlement" class="dropdown-item">정산/매출</RouterLink></li>
                <li><RouterLink to="/driver/app/my-page" class="dropdown-item">MY/차량</RouterLink></li>
              </ul>
            </li>
          </ul>

          <ul v-if="!authState.user" class="navbar-nav align-items-center auth-menu">
            <li class="nav-item">
              <RouterLink to="/login" class="nav-link auth-link">
                <i class="bi bi-box-arrow-in-right"></i> 로그인
              </RouterLink>
            </li>
            <li class="nav-item auth-divider">|</li>
            <li class="nav-item">
              <RouterLink to="/regi" class="nav-link auth-link">
                <i class="bi bi-person-plus"></i> 회원가입
              </RouterLink>
            </li>
          </ul>

          <ul v-else class="navbar-nav align-items-center auth-menu">
            <!-- 26.09.21 추가: 알림 종 아이콘 -->
            <NotificationBell />
            <li class="nav-item auth-divider">|</li>
            <li class="nav-item">
              <span class="nav-link auth-link auth-welcome">
                {{ authState.user.userName }}님
              </span>
            </li>
            <li class="nav-item auth-divider">|</li>
            <li class="nav-item">
              <RouterLink :to="myInfoLink" class="nav-link auth-link">
                <i class="bi bi-person-circle"></i> 내정보
              </RouterLink>
            </li>
            <li class="nav-item auth-divider">|</li>
            <li class="nav-item">
              <a href="#" class="nav-link auth-link" @click.prevent="handleLogout">
                <i class="bi bi-box-arrow-right"></i> 로그아웃
              </a>
            </li>
          </ul>
        </div>
      </div>
    </nav>

    <div class="wrapper">
      <Main />
    </div>

    <footer class="py-4 bg-brand mt-auto">
      <div class="container text-center">
        <ul class="nav justify-content-center mb-3">
          <li class="nav-item">
            <RouterLink class="nav-link" to="/">Top</RouterLink>
          </li>
        </ul>
        <p>
          <small>Copyright &copy; 못먹어도S카고</small>
        </p>
      </div>
    </footer>
  </div>
</template>

<style src="@/components/CSS/App.css" scoped></style>