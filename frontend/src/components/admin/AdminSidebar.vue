<template>
  <aside class="admin-sidebar">
    <div class="admin-nav-scroll">
    <RouterLink to="/admin" class="admin-nav-item admin-nav-home" :class="{ active: route.path === '/admin' }">
      <i class="bi bi-house-door-fill"></i>
      대시보드
    </RouterLink>

    <!-- 26.10.26 그룹 제목을 누르면 접기/펼치기. 지금 보고 있는 메뉴의 그룹은 자동으로 펼침 -->
    <template v-for="g in sections" :key="g.key">
      <button type="button" class="admin-nav-group-label admin-nav-toggle" :aria-expanded="isOpen(g.key)" @click="toggle(g.key)">
        <span>{{ g.label }}</span>
        <i class="bi" :class="isOpen(g.key) ? 'bi-chevron-up' : 'bi-chevron-down'"></i>
      </button>
      <template v-if="isOpen(g.key)">
        <RouterLink v-for="m in groups[g.key]" :key="m.to" :to="m.to" class="admin-nav-item" :class="{ active: isActive(m.to) }">
          <i :class="['bi', m.icon]"></i>
          {{ m.label }}
        </RouterLink>
      </template>
    </template>
    </div>

    <div class="admin-side-foot">
      <p>안전한 물류,<br />스마트한 이동</p>
    </div>
  </aside>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ADMIN_MENU } from './adminMenu.js'

const route = useRoute()
const groups = ADMIN_MENU
const sections = [
  { key: 'member', label: '회원 · 업체' },
  { key: 'vehicle', label: '차량 · 화물' },
  { key: 'yard', label: '야드 · 적재' },
  { key: 'etc', label: '안전 · 기타' },
  { key: 'tower', label: '공통 컨트롤타워' },
  { key: 'port', label: '항만 → 허브' },
  { key: 'hubc', label: '허브 → 물류센터' },
  { key: 'notice', label: '안내' },
]

function isActive(prefix) {
  return route.path === prefix || route.path.startsWith(prefix + '/')
}

// 사용자가 직접 접고 펼친 상태 (없으면 "지금 보는 메뉴의 그룹만 펼침")
const manual = reactive({})
function currentGroup() {
  const s = sections.find((g) => groups[g.key].some((m) => isActive(m.to)))
  return s ? s.key : null
}
function isOpen(key) {
  return key in manual ? manual[key] : currentGroup() === key
}
function toggle(key) {
  manual[key] = !isOpen(key)
}
// 메뉴를 눌러 다른 그룹으로 옮겨 가면 그 그룹은 펼치고, 직접 접어 둔 표시는 풀어 준다
watch(() => route.path, () => { const k = currentGroup(); if (k) delete manual[k] })
</script>

<style scoped>
.admin-nav-toggle { display: flex; align-items: center; justify-content: space-between; width: 100%; background: none; border: 0; cursor: pointer; text-align: left; }
.admin-nav-toggle:hover { color: #fff; }
.admin-nav-toggle i { font-size: 11px; opacity: .8; }
</style>
