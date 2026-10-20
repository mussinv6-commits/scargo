<template>
  <!-- 26.10.20 추가: S-Bot 전용 화면. S Cargo 메뉴/사이드바와 분리된 별도 셸. -->
  <div class="sb-shell">
    <header class="sb-top">
      <RouterLink to="/sbot" class="sb-brand"><img :src="logo" alt="S-Bot" /></RouterLink>
      <span class="sb-title">허브 AMR 관제</span>
      <nav class="sb-nav">
        <RouterLink to="/">홈</RouterLink>
        <span class="sb-user" v-if="authState.user">{{ authState.user.userName }}님</span>
        <a href="#" @click.prevent="logout">로그아웃</a>
      </nav>
    </header>
    <main class="sb-main"><router-view /></main>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";
import axios from "axios";
import { authState, clearLogin } from "@/auth/authState.js";
import { API_BASE } from "@/utils/apiBase.js";
import logo from "@/assets/sbot_logo.png";

const router = useRouter();
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
</style>
