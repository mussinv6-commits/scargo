<template>
  <aside class="admin-sidebar">
    <div class="admin-brand">관리자<span>페이지</span></div>

    <RouterLink to="/admin" class="admin-nav-item" :class="{ active: route.path === '/admin' }">
      📊 대시보드
    </RouterLink>

    <div class="admin-nav-group-label">회원 · 업체</div>
    <RouterLink to="/admin/accounts" class="admin-nav-item" :class="{ active: isActive('/admin/accounts') }">
      👤 회원 관리
    </RouterLink>
    <RouterLink to="/admin/companies" class="admin-nav-item" :class="{ active: isActive('/admin/companies') }">
      🏢 업체 관리
    </RouterLink>

    <div class="admin-nav-group-label">차량 · 화물</div>
    <RouterLink to="/admin/trucks" class="admin-nav-item" :class="{ active: isActive('/admin/trucks') }">
      🚚 차량 관리
    </RouterLink>
    <RouterLink to="/admin/containers" class="admin-nav-item" :class="{ active: isActive('/admin/containers') }">
      📦 컨테이너 관리
    </RouterLink>

    <div class="admin-nav-group-label">야드 · 적재</div>
    <RouterLink to="/admin/yards" class="admin-nav-item" :class="{ active: isActive('/admin/yards') }">
      🗺️ 야드 관리
    </RouterLink>
    <RouterLink to="/admin/loading-locations" class="admin-nav-item" :class="{ active: isActive('/admin/loading-locations') }">
      📍 적재 위치 관리
    </RouterLink>
    <RouterLink to="/admin/loading-records" class="admin-nav-item" :class="{ active: isActive('/admin/loading-records') }">
      🧾 적재 기록 조회
    </RouterLink>

    <div class="admin-nav-group-label">안전 · 기타</div>
    <RouterLink to="/admin/overload-checks" class="admin-nav-item" :class="{ active: isActive('/admin/overload-checks') }">
      ⚖️ 과적 검사 관리
    </RouterLink>
    <RouterLink to="/bbslist" class="admin-nav-item">
      📋 게시판 관리
    </RouterLink>
  </aside>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
function isActive(prefix) {
  return route.path === prefix || route.path.startsWith(prefix + '/')
}
</script>
