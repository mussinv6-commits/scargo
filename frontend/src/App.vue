<script setup>
import { useRouter } from "vue-router";
import axios from "axios";
import Main from "./views/Main.vue";
import { authState, clearLogin } from "@/auth/authState.js";

const router = useRouter();

function handleLogout() {
  axios
    .post("http://localhost:8080/api/accounts/logout", {}, { withCredentials: true })
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
    <nav class="navbar navbar-expand-md navbar-dark bg-info sticky-top">
      <div class="container-fluid nav-grid">
        <div class="collapse navbar-collapse" id="collapsibleNavbar">
          <!-- 왼쪽 빈 공간 (가운데 정렬을 위한 균형용) -->
          <div class="nav-spacer"></div>

          <ul class="navbar-nav">
            <li class="nav-item">
              <a href="/" class="nav-link">Home</a>
            </li>

            <li class="nav-item">
              <a href="/bbslist" class="nav-link">게시판</a>
            </li>

            <li class="nav-item">
              <a href="/scrlist" class="nav-link">게시판2</a>
            </li>
          </ul>

          <!-- 로그인 전 -->
          <ul v-if="!authState.user" class="navbar-nav align-items-center auth-menu">
            <li class="nav-item">
              <a href="/login" class="nav-link auth-link">
                <i class="bi bi-box-arrow-in-right"></i> 로그인
              </a>
            </li>

            <li class="nav-item auth-divider">|</li>

            <li class="nav-item">
              <a href="/regi" class="nav-link auth-link">
                <i class="bi bi-person-plus"></i> 회원가입
              </a>
            </li>
          </ul>

          <!-- 로그인 후 -->
          <ul v-else class="navbar-nav align-items-center auth-menu">
            <li class="nav-item">
              <span class="nav-link auth-link auth-welcome">
                {{ authState.user.userName }}님
              </span>
            </li>

            <li class="nav-item auth-divider">|</li>

            <li class="nav-item">
              <a href="/mypage" class="nav-link auth-link">
                <i class="bi bi-person-circle"></i> 내정보
              </a>
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

    <!-- 여기부터 1200px -->
    <div class="wrapper">
      <Main />
    </div>

    <footer class="py-4 bg-info mt-auto">
      <div class="container text-center">
        <ul class="nav justify-content-center mb-3">
          <li class="nav-item">
            <a class="nav-link" href="/">Top</a>
          </li>
        </ul>
        <p>
          <small>Copyright &copy; Graphic Arts</small>
        </p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  text-align: center;
  color: #2c3e50;
}

.wrapper {
  max-width: 1600px;
  margin: 0 auto;
}

a {
  text-decoration: none !important;
}

nav.navbar,
footer {
  width: 100vw;
  margin-left: calc(50% - 50vw);
}

.navbar-nav>li {
  padding-left: 20px;
  padding-right: 20px;
}

.auth-menu {
  padding: 0 15px;
}

.auth-menu .nav-item {
  padding: 0 8px !important;
}

.auth-link {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 0.9rem;
}

.auth-link i {
  font-size: 1rem;
}

.auth-welcome {
  color: white;
  font-weight: 500;
}

.auth-divider {
  color: rgba(255, 255, 255, 0.5);
  display: flex;
  align-items: center;
  padding: 0 !important;
}

.nav-grid .navbar-collapse {
  display: flex;
  align-items: center;
}

.nav-spacer {
  flex: 1;
}

.nav-grid .navbar-nav:not(.auth-menu) {
  flex: 1;
  display: flex;
  justify-content: center;
}

.auth-menu {
  flex: 1;
  display: flex;
  justify-content: flex-end;
}
</style>