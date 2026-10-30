<template>
  <div class="admin-app">
    <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
      <AdminSidebar />
      <div ref="mainEl" class="admin-main" :class="{ 'has-title': !isDash && pageTitle }">
        <!-- 26.10.28 QA C-4 · C-5: 모든 메뉴에 삼선(사이드바 숨김/펼침) + 메뉴명을 상단에 고정(sticky) -->
        <header class="admin-topbar" :class="{ 'is-dash': isDash }">
          <button type="button" class="admin-burger" :aria-label="collapsed ? '메뉴 펼치기' : '메뉴 숨기기'" @click="collapsed = !collapsed">
            <i class="bi bi-list"></i>
          </button>
          <h1 v-if="!isDash && pageTitle" class="admin-page-title">{{ pageTitle }}</h1>
          <div v-if="isDash" class="ops-head-copy">
            <h1>관리자 대시보드</h1>
            <p>S Cargo 물류 운영 현황을 한눈에 확인하세요.</p>
          </div>
          <div class="admin-topbar-right">
            <div class="ops-clock-wrap" :class="{ 'is-locked': !dateEnabled }">
              <span class="ops-clock">
                <i class="bi bi-calendar3"></i>
                <span>{{ viewDateLabel }}</span>
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
            <div v-if="dateEnabled" class="ops-period" role="group" aria-label="보고 구분">
              <button v-for="p in PERIODS" :key="p.key" type="button" :class="{ on: viewPeriod === p.key }" @click="viewPeriod = p.key">{{ p.label }}</button>
            </div>
            <span v-if="viewPeriod !== 'day'" class="ops-day-chip">{{ viewRangeLabel }}</span>
            <span v-else class="ops-day-chip" :class="{ 'is-today': isViewToday }">{{ viewDateRelativeLabel }}</span>
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
import AdminSidebar from './AdminSidebar.vue'
import { ADMIN_MENU_FLAT } from './adminMenu.js'
import { useStickyStack } from '@/utils/useStickyStack.js'
import LiveClock from '@/components/common/LiveClock.vue'
import { viewDateInput, minDateInput, maxDateInput, viewDateLabel, viewDateRelativeLabel, isViewToday, resetViewDateToday, PERIODS, viewPeriod, viewRangeLabel } from './adminViewDate.js'

const route = useRoute()
const collapsed = ref(false)
// 26.10.28 메뉴명 아래 설명 · 탭도 메뉴명과 함께 상단 고정
const mainEl = ref(null)
useStickyStack(mainEl)
const dateEl = ref(null)

const isDash = computed(() => route.path === '/admin')
// 26.10.28 상단 고정 메뉴명: 사이드바 메뉴 이름을 그대로 쓴다 (하위 화면은 가장 가까운 메뉴 이름)
const EXTRA_TITLES = { '/admin/profile': '내 정보', '/admin/gate-ocr': '항만 게이트 OCR 검사', '/admin/amr-tokens': '앱 출입증', '/admin/ops-center/alerts': '경보 상세' }
const pageTitle = computed(() => {
  const p = route.path
  if (EXTRA_TITLES[p]) return EXTRA_TITLES[p]
  const hit = ADMIN_MENU_FLAT.filter((m) => p === m.to || p.startsWith(m.to + '/')).sort((a, b) => b.to.length - a.to.length)[0]
  return hit?.label || route.meta?.title || ''
})
const DATE_LOCKED = ['/admin/loading-locations', '/admin/gate-ocr', '/admin/weighbridge', '/admin/amr-twin']
const dateEnabled = computed(() => !DATE_LOCKED.includes(route.path))
watch(dateEnabled, (on) => { if (!on) resetViewDateToday() }, { immediate: true })

function openDatePicker(e) {
  const el = e?.currentTarget || dateEl.value
  if (el && typeof el.showPicker === 'function') {
    try { el.showPicker() } catch { /* native picker already opening */ }
  }
}
</script>

<style src="@/components/CSS/admin.css"></style>
