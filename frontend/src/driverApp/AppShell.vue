<template>
  <!-- 26.10.08 추가: 기사 앱 레이아웃 — 웹의 좌측 사이드바 대신 휴대폰 하단 탭바 -->
  <div class="driver-app-shell drv-app">
    <div class="drv-app-main">
      <router-view />
    </div>

    <nav class="drv-tabbar" aria-label="기사 메뉴">
      <RouterLink v-for="t in tabs" :key="t.to" :to="t.to" class="drv-tab" :class="{ active: isActive(t.to) }">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <template v-if="t.icon === 'truck'">
            <rect x="1" y="3" width="15" height="13"></rect>
            <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"></polygon>
            <circle cx="5.5" cy="18.5" r="2.5"></circle>
            <circle cx="18.5" cy="18.5" r="2.5"></circle>
          </template>
          <template v-else-if="t.icon === 'list'">
            <line x1="8" y1="6" x2="21" y2="6"></line>
            <line x1="8" y1="12" x2="21" y2="12"></line>
            <line x1="8" y1="18" x2="21" y2="18"></line>
            <line x1="3" y1="6" x2="3.01" y2="6"></line>
            <line x1="3" y1="12" x2="3.01" y2="12"></line>
            <line x1="3" y1="18" x2="3.01" y2="18"></line>
          </template>
          <template v-else-if="t.icon === 'check'">
            <path d="M9 11l3 3L22 4"></path>
            <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>
          </template>
          <template v-else-if="t.icon === 'won'">
            <line x1="12" y1="1" x2="12" y2="23"></line>
            <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
          </template>
          <template v-else>
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
            <circle cx="12" cy="7" r="4"></circle>
          </template>
        </svg>
        <span>{{ t.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { authState } from '@/auth/authState.js'

const route = useRoute()
// 26.10.23 컨테이너 운송기사는 '점검' 탭(컨테이너 점검표)을 추가로 본다
const tabs = computed(() => [
  { to: '/driver/app/status', label: '운송현황', icon: 'truck' },
  { to: '/driver/app/dispatch-list', label: '배차목록', icon: 'list' },
  ...(authState.user?.userType === 'CONTAINER_DRIVER' ? [{ to: '/driver/app/inspection', label: '점검', icon: 'check' }] : []),
  { to: '/driver/app/settlement', label: '정산', icon: 'won' },
  { to: '/driver/app/my-page', label: 'MY/차량', icon: 'user' },
])
const isActive = (to) => route.path === to
</script>

<style src="@/components/CSS/tokens.css"></style>
