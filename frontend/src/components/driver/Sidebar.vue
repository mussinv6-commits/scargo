<template>
  <aside class="driver-sidebar">
    <div class="brand-mark">화물<span>기사</span></div>

    <RouterLink to="/driver/app/status" class="nav-item" :class="{ active: route.path === '/driver/app/status' }">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <rect x="1" y="3" width="15" height="13"></rect>
        <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"></polygon>
        <circle cx="5.5" cy="18.5" r="2.5"></circle>
        <circle cx="18.5" cy="18.5" r="2.5"></circle>
      </svg>
      운송현황
    </RouterLink>

    <RouterLink to="/driver/app/dispatch-list" class="nav-item" :class="{ active: route.path === '/driver/app/dispatch-list' }">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="8" y1="6" x2="21" y2="6"></line>
        <line x1="8" y1="12" x2="21" y2="12"></line>
        <line x1="8" y1="18" x2="21" y2="18"></line>
        <line x1="3" y1="6" x2="3.01" y2="6"></line>
        <line x1="3" y1="12" x2="3.01" y2="12"></line>
        <line x1="3" y1="18" x2="3.01" y2="18"></line>
      </svg>
      배차목록
    </RouterLink>

    <RouterLink to="/driver/app/settlement" class="nav-item" :class="{ active: route.path === '/driver/app/settlement' }">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="12" y1="1" x2="12" y2="23"></line>
        <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
      </svg>
      정산/매출
    </RouterLink>

    <RouterLink to="/driver/app/my-page" class="nav-item" :class="{ active: route.path === '/driver/app/my-page' }">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
        <circle cx="12" cy="7" r="4"></circle>
      </svg>
      MY/차량
    </RouterLink>
  </aside>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<style scoped>
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 8px;
  text-decoration: none;
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
  font-weight: 600;
}
.nav-item:hover { background: rgba(255, 255, 255, 0.08); color: #fff; }
.nav-item.active { background: rgba(255, 107, 0, 0.18); color: #ffd7b0; }
.nav-item svg { width: 18px; height: 18px; flex-shrink: 0; }
</style>
