<template>
  <aside class="admin-sidebar">
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

    <div class="admin-nav-group-label">안내</div>
    <RouterLink
      v-for="m in COMPANY_EXTRA_MENU"
      :key="m.to"
      :to="m.to"
      class="admin-nav-item"
      :class="{ active: isActive(m) }"
    >
      <i :class="['bi', m.icon]"></i>
      {{ m.label }}
    </RouterLink>

    <div class="admin-side-foot">
      <p>안전한 물류<br />스마트한 운송관리<br /><span class="biz-side-brand">SafeCargo</span></p>
    </div>
  </aside>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { COMPANY_MENU, COMPANY_EXTRA_MENU } from './companyMenu.js'

const route = useRoute()

function isActive(m) {
  if (m.exact) return route.path === m.to
  return route.path === m.to || route.path.startsWith(`${m.to}/`)
}
</script>
