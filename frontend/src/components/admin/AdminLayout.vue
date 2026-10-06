<template>
  <div class="admin-app">
    <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
      <AdminSidebar />
      <div class="admin-main">
        <header class="admin-topbar" :class="{ 'is-dash': isDash }">
          <button type="button" class="admin-burger" aria-label="메뉴" @click="collapsed = !collapsed">
            <i class="bi bi-list"></i>
          </button>
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
import AdminSidebar from './AdminSidebar.vue'
import LiveClock from '@/components/common/LiveClock.vue'
import { viewDateInput, minDateInput, maxDateInput, viewDateLabel, viewDateRelativeLabel, isViewToday, resetViewDateToday } from './adminViewDate.js'

const route = useRoute()
const collapsed = ref(false)
const dateEl = ref(null)

const isDash = computed(() => route.path === '/admin')
const DATE_LOCKED = ['/admin/loading-locations', '/admin/gate-ocr', '/admin/weighbridge']
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
