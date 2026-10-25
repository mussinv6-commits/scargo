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

    <div class="nav-label">기타</div>

    <RouterLink to="/driver/app/faq" class="nav-item" :class="{ active: route.path === '/driver/app/faq' }">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"></circle>
        <path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3"></path>
        <line x1="12" y1="17" x2="12.01" y2="17"></line>
      </svg>
      자주 묻는 질문
    </RouterLink>

    <RouterLink to="/driver/app/support" class="nav-item" :class="{ active: route.path === '/driver/app/support' }">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
      </svg>
      고객센터 문의
    </RouterLink>

    <RouterLink to="/driver/app/terms" class="nav-item" :class="{ active: route.path === '/driver/app/terms' }">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
        <polyline points="14 2 14 8 20 8"></polyline>
        <line x1="8" y1="13" x2="16" y2="13"></line>
        <line x1="8" y1="17" x2="16" y2="17"></line>
      </svg>
      약관 및 정책
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
.nav-label {
  margin: 18px 12px 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: rgba(255, 255, 255, 0.42);
}
.nav-item { white-space: nowrap; }
</style>
