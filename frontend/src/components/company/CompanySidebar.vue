<template>
  <aside class="admin-sidebar">
    <div class="admin-nav-scroll">
    <RouterLink
      v-for="m in COMPANY_MENU"
      :key="m.to"
      :to="m.to"
      class="admin-nav-item"
      :class="{ active: isActive(m), 'admin-nav-home': m.exact }"
    >
      <i :class="['bi', m.icon]"></i>
      {{ m.label }}
    </RouterLink>

    </div>

    <div class="admin-side-foot">
      <p>안전한 물류<br />스마트한 운송관리<br /><span class="biz-side-brand">S Cargo</span></p>
    </div>
  </aside>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { COMPANY_MENU } from './companyMenu.js'

const route = useRoute()

function isActive(m) {
  if (m.exact) return route.path === m.to
  return route.path === m.to || route.path.startsWith(`${m.to}/`)
}
</script>
