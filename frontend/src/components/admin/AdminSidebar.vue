<template>
  <aside class="admin-sidebar">
    <div class="admin-nav-scroll">
    <RouterLink to="/admin" class="admin-nav-item admin-nav-home" :class="{ active: route.path === '/admin' }">
      <i class="bi bi-house-door-fill"></i>
      대시보드
    </RouterLink>

    <div class="admin-nav-group-label">회원 · 업체</div>
    <RouterLink v-for="m in groups.member" :key="m.to" :to="m.to" class="admin-nav-item" :class="{ active: isActive(m.to) }">
      <i :class="['bi', m.icon]"></i>
      {{ m.label }}
    </RouterLink>

    <div class="admin-nav-group-label">차량 · 화물</div>
    <RouterLink v-for="m in groups.vehicle" :key="m.to" :to="m.to" class="admin-nav-item" :class="{ active: isActive(m.to) }">
      <i :class="['bi', m.icon]"></i>
      {{ m.label }}
    </RouterLink>

    <div class="admin-nav-group-label">야드 · 적재</div>
    <RouterLink v-for="m in groups.yard" :key="m.to" :to="m.to" class="admin-nav-item" :class="{ active: isActive(m.to) }">
      <i :class="['bi', m.icon]"></i>
      {{ m.label }}
    </RouterLink>

    <div class="admin-nav-group-label">안전 · 기타</div>
    <RouterLink v-for="m in groups.etc" :key="m.to" :to="m.to" class="admin-nav-item" :class="{ active: isActive(m.to) }">
      <i :class="['bi', m.icon]"></i>
      {{ m.label }}
    </RouterLink>

    <div class="admin-nav-group-label">안내</div>
    <RouterLink v-for="m in groups.notice" :key="m.to" :to="m.to" class="admin-nav-item" :class="{ active: isActive(m.to) }">
      <i :class="['bi', m.icon]"></i>
      {{ m.label }}
    </RouterLink>
    </div>

    <div class="admin-side-foot">
      <p>안전한 물류,<br />스마트한 이동</p>
    </div>
  </aside>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { ADMIN_MENU } from './adminMenu.js'

const route = useRoute()
const groups = ADMIN_MENU

function isActive(prefix) {
  return route.path === prefix || route.path.startsWith(prefix + '/')
}
</script>
