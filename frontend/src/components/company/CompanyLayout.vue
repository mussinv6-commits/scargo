<template>
  <div class="admin-app">
    <header class="admin-gnb">
      <RouterLink to="/" class="admin-gnb-brand">
        <img :src="safeCargoLogo" alt="SafeCargo" />
      </RouterLink>
      <ul class="admin-gnb-utils">
        <NotificationBell />
        <li v-if="companyName" class="admin-gnb-org">{{ companyName }}</li>
        <li ref="userMenuEl" class="admin-gnb-user">
          <button type="button" class="admin-gnb-userbtn" :aria-expanded="userOpen" @click="userOpen = !userOpen">
            <span class="admin-gnb-avatar">{{ initial }}</span>
            {{ authState.user?.userName || '사업자' }}님
            <i class="bi bi-chevron-down"></i>
          </button>
          <div v-if="userOpen" class="admin-gnb-drop">
            <RouterLink to="/company/info" @click="userOpen = false">업체 정보</RouterLink>
            <button type="button" @click="handleLogout">로그아웃</button>
          </div>
        </li>
      </ul>
    </header>

    <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
      <CompanySidebar />
      <div class="admin-main">
        <header class="admin-topbar" :class="{ 'is-dash': isDash }">
          <button type="button" class="admin-burger" aria-label="메뉴" @click="collapsed = !collapsed">
            <i class="bi bi-list"></i>
          </button>
          <div v-if="isDash" class="ops-head-copy">
            <h1>사업자 대시보드</h1>
            <p>운송 현황과 주요 지표를 한눈에 확인할 수 있습니다.</p>
          </div>
          <div v-else class="ops-head-copy">
            <h1>{{ pageTitle }}</h1>
          </div>
          <div class="admin-topbar-right">
            <div class="ops-clock-wrap">
              <span class="ops-clock">
                <i class="bi bi-calendar3"></i>
                <span>{{ companyDateLabel }}</span>
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
import CompanySidebar from './CompanySidebar.vue'
import NotificationBell from '@/components/common/NotificationBell.vue'
import LiveClock from '@/components/common/LiveClock.vue'
import { authState, clearLogin } from '@/auth/authState.js'
import { API_BASE } from '@/utils/apiBase.js'
import safeCargoLogo from '@/assets/safecargo_logo_4x.png'
import { viewDate, viewDateInput, maxDateInput, isViewToday, viewDateRelativeLabel } from '@/components/admin/adminViewDate.js'
import { COMPANY_MENU } from './companyMenu.js'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const userOpen = ref(false)
const userMenuEl = ref(null)
const companyName = ref(authState.user?.companyName || '')

const isDash = computed(() => route.path === '/company')
const initial = computed(() => authState.user?.userName?.[0] || '사')
const pageTitle = computed(() => COMPANY_MENU.find((m) => m.to !== '/company' && (route.path === m.to || route.path.startsWith(`${m.to}/`)))?.label || '사업자')

function pad(n) {
  return String(n).padStart(2, '0')
}

const companyDateLabel = computed(() => {
  const d = viewDate.value
  const week = ['일', '월', '화', '수', '목', '금', '토'][d.getDay()]
  return `${d.getFullYear()}년 ${pad(d.getMonth() + 1)}월 ${pad(d.getDate())}일(${week})`
})

function openDatePicker(e) {
  const el = e?.currentTarget
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

async function loadCompanyName() {
  const id = authState.user?.companyId
  if (!id) return
  try {
    const { data } = await axios.get(`${API_BASE}/api/companies/${id}`, { withCredentials: true })
    companyName.value = data?.companyName || companyName.value
  } catch {
    /* 상단 업체명은 보조 정보 */
  }
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  loadCompanyName()
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
})
</script>

<style src="@/components/CSS/admin.css"></style>
<style src="@/components/CSS/company.css"></style>
