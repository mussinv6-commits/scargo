<template>
  <div class="admin-app">
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
            <div class="ops-clock-wrap" :class="{ 'is-locked': !dateEnabled }">
              <span class="ops-clock">
                <i class="bi bi-calendar3"></i>
                <span>{{ companyDateLabel }}</span>
              </span>
              <input
                v-if="dateEnabled"
                class="ops-clock-native"
                type="date"
                v-model="viewDateInput"
                :min="minDateInput"
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
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import CompanySidebar from './CompanySidebar.vue'
import LiveClock from '@/components/common/LiveClock.vue'
import { viewDate, viewDateInput, minDateInput, maxDateInput, isViewToday, viewDateRelativeLabel, resetViewDateToday } from '@/components/admin/adminViewDate.js'
import { COMPANY_MENU } from './companyMenu.js'

const route = useRoute()
const collapsed = ref(false)

const isDash = computed(() => route.path === '/company')
const DATE_LOCKED = ['/company/mapping', '/company/trucks', '/company/drivers', '/company/info']
const dateEnabled = computed(() => !DATE_LOCKED.some((p) => route.path === p || route.path.startsWith(`${p}/`)))
watch(dateEnabled, (on) => { if (!on) resetViewDateToday() }, { immediate: true })
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
</script>

<style src="@/components/CSS/admin.css"></style>
<style src="@/components/CSS/company.css"></style>
