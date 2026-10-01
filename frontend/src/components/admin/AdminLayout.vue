<template>
  <div class="admin-app">
    <header class="admin-gnb">
      <RouterLink to="/" class="admin-gnb-brand">
        <img :src="safeCargoLogo" alt="SafeCargo" />
      </RouterLink>
      <ul class="admin-gnb-utils">
        <NotificationBell />
        <li ref="userMenuEl" class="admin-gnb-user">
          <button type="button" class="admin-gnb-userbtn" :aria-expanded="userOpen" @click="userOpen = !userOpen">
            <span class="admin-gnb-avatar">{{ initial }}</span>
            {{ authState.user?.userName || '관리자' }}님
            <i class="bi bi-chevron-down"></i>
          </button>
          <div v-if="userOpen" class="admin-gnb-drop">
            <RouterLink to="/admin/profile" @click="userOpen = false">내 정보</RouterLink>
            <button type="button" @click="handleLogout">로그아웃</button>
          </div>
        </li>
      </ul>
    </header>

    <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
      <AdminSidebar />
      <div class="admin-main">
        <header class="admin-topbar" :class="{ 'is-dash': isDash }">
          <button type="button" class="admin-burger" aria-label="메뉴" @click="collapsed = !collapsed">
            <i class="bi bi-list"></i>
          </button>
          <div v-if="isDash" class="ops-head-copy">
            <h1>관리자 대시보드</h1>
            <p>SafeCargo 물류 운영 현황을 한눈에 확인하세요.</p>
          </div>
          <div class="admin-topbar-right">
            <div class="ops-clock-wrap">
              <span class="ops-clock">
                <i class="bi bi-calendar3"></i>
                <span>{{ viewDateLabel }}</span>
              </span>
              <input
                class="ops-clock-native"
                type="date"
                v-model="viewDateInput"
                :max="maxDateInput"
                aria-label="조회 날짜 선택"
                @click="openDatePicker"
              />
            </div>
            <span class="ops-day-chip" :class="{ 'is-today': isViewToday }">{{ viewDateRelativeLabel }}</span>
            <LiveClock />
          </div>
        </header>
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import AdminSidebar from './AdminSidebar.vue'
import NotificationBell from '@/components/common/NotificationBell.vue'
import LiveClock from '@/components/common/LiveClock.vue'
import { authState, clearLogin } from '@/auth/authState.js'
import { API_BASE } from '@/utils/apiBase.js'
import safeCargoLogo from '@/assets/safecargo_logo_4x.png'
import { viewDateInput, maxDateInput, viewDateLabel, viewDateRelativeLabel, isViewToday } from './adminViewDate.js'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const userOpen = ref(false)
const userMenuEl = ref(null)
const dateEl = ref(null)

const isDash = computed(() => route.path === '/admin')
const initial = computed(() => authState.user?.userName?.[0] || '관')

function openDatePicker(e) {
  const el = e?.currentTarget || dateEl.value
  if (el && typeof el.showPicker === 'function') {
    try { el.showPicker() } catch { /* native picker already opening */ }
  }
}

function handleLogout() {
  userOpen.value = false
  axios
    .post(`${API_BASE}/api/accounts/logout`, {}, { withCredentials: true })
    .catch(() => {})
    .finally(() => {
      clearLogin()
      router.push('/login')
    })
}

function onDocClick(e) {
  if (userMenuEl.value && !userMenuEl.value.contains(e.target)) userOpen.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
})
</script>

<style src="@/components/CSS/admin.css"></style>
