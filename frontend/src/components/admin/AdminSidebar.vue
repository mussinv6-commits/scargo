<template>
  <aside class="admin-sidebar">
    <div class="admin-nav-scroll">
    <RouterLink v-if="!staff" to="/admin" class="admin-nav-item admin-nav-home" :class="{ active: route.path === '/admin' }">
      <i class="bi bi-house-door-fill"></i>
      대시보드
    </RouterLink>
    <!-- 26.10.28 운영 센터는 대시보드 바로 아래 (그룹 없이) -->
    <RouterLink v-for="m in view.top" :key="m.to" :to="m.to" class="admin-nav-item admin-nav-home" :class="{ active: isActive(m.to) }">
      <i :class="['bi', m.icon]"></i>
      {{ m.label }}
    </RouterLink>

    <!-- 26.10.26 그룹 제목을 누르면 접기/펼치기. 지금 보고 있는 메뉴의 그룹은 자동으로 펼침 -->
    <template v-for="g in shownSections" :key="g.key">
      <button type="button" class="admin-nav-group-label admin-nav-toggle" :aria-expanded="isOpen(g.key)" @click="toggle(g.key)">
        <span class="grp-name"><i :class="['bi', g.icon]"></i>{{ g.label }}</span><!-- 26.10.29 대분류 메뉴명 왼쪽 아이콘 -->
        <i class="bi" :class="isOpen(g.key) ? 'bi-chevron-up' : 'bi-chevron-down'"></i>
      </button>
      <template v-if="isOpen(g.key)">
        <RouterLink v-for="m in view[g.key]" :key="m.to" :to="m.to" class="admin-nav-item admin-nav-sub" :class="{ active: isActive(m.to) }">
          <i :class="['bi', m.icon]"></i>
          {{ m.label }}
        </RouterLink>
      </template>
    </template>
    <!-- 26.10.28 문구를 메뉴 바로 아래로 올림 (메뉴를 다 펼치지 않으면 아래 공간이 남아서) -->
    <div class="admin-side-foot">
      <p>안전한 물류,<br />스마트한 이동</p>
    </div>
    </div>
  </aside>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ADMIN_MENU } from './adminMenu.js'
import { authState } from '@/auth/authState.js'
import { staffRole, canSee } from '@/scargo2/roles.js'

const route = useRoute()
const groups = ADMIN_MENU
// 26.10.29 관제사 계정: 총관리자와 같은 메뉴 구성 · 이름 그대로, 그 권한으로 볼 수 있는 메뉴만 보인다
//  (관리자 전용 /admin 화면은 숨기고, 같은 기능의 관제사용 주소로 바꿔 연결)
const staff = computed(() => (authState.user?.userType === 'ADMIN' ? null : staffRole(authState.user)))
const STAFF_TO = { '/admin/handovers': '/w/handovers', '/admin/center': '/w/W-05', '/admin/notices': '/notice', '/admin/notifications': '/notifications' }
const STAFF_EXTRA = { hubc: [{ after: '/w/W-04', item: { to: '/w/W-06', label: 'AMR 관제', icon: 'bi-robot' } }, { after: '/admin/center', item: { to: '/w/receipts', label: '센터 인수 확정', icon: 'bi-box-arrow-in-down', only: 'CENTER_OP' } }] } // 26.10.29 센터 인수 확정 (지역센터 관제사) // 관제사는 S-Bot 대시보드가 없어 AMR 목록 화면 유지
function staffItem(m, r) {
  if (m.only) return m.only === r ? m : null
  const to = STAFF_TO[m.to] || m.to
  if (to === '/w/handovers') return ['CONTROL', 'HUB_OP'].includes(r) ? { ...m, to } : null // 26.10.29 인수 내역
  if (to.startsWith('/w/')) return canSee(r, to.slice(3)) ? { ...m, to } : null
  if (to === '/notice' || to === '/notifications') return { ...m, to }
  return null
}
const view = computed(() => {
  const r = staff.value
  if (!r) return groups
  const out = {}
  for (const k of Object.keys(groups)) {
    const list = []
    for (const m of groups[k]) {
      const it = staffItem(m, r); if (it) list.push(it)
      for (const x of STAFF_EXTRA[k] || []) if (x.after === m.to) { const e = staffItem(x.item, r); if (e) list.push(e) }
    }
    out[k] = list
  }
  return out
})
const sections = [
  { key: 'member', label: '회원 · 업체', icon: 'bi-people-fill' },
  { key: 'vehicle', label: '차량 · 화물', icon: 'bi-truck' },
  { key: 'yard', label: '야드 · 적재', icon: 'bi-grid-3x3-gap-fill' },
  { key: 'tower', label: '공통 컨트롤타워', icon: 'bi-broadcast-pin' },
  { key: 'port', label: '항만 → 허브', icon: 'bi-water' },
  { key: 'hubc', label: '허브 → 물류센터', icon: 'bi-buildings-fill' },
  { key: 'notice', label: '설정 · 안내', icon: 'bi-gear-fill' }, // 26.10.28 '안전 · 기타' 그룹 삭제(메뉴는 흐름 그룹으로 이동), 기준정보 추가
]

const shownSections = computed(() => sections.filter((g) => (view.value[g.key] || []).length))
const matches = (prefix) => route.path === prefix || route.path.startsWith(prefix + '/')
// 26.10.29 '운영 센터'(/admin/ops-center) 와 '경보 상세'(/admin/ops-center/alerts) 처럼 주소가 겹치면 더 긴(구체적인) 메뉴만 선택 표시
const activeTo = computed(() => Object.values(view.value).flat().map((m) => m.to).filter(matches).sort((a, b) => b.length - a.length)[0] || null)
function isActive(prefix) {
  return activeTo.value ? activeTo.value === prefix : matches(prefix)
}

// 26.10.28 아코디언: 펼쳐지는 그룹은 하나뿐. 다른 그룹 제목을 누르면 그 그룹만 펼치고 이전 그룹은 닫는다
//  처음 들어오거나 다른 메뉴로 이동하면 지금 보는 메뉴의 그룹이 펼쳐진다
const openKey = ref(null)
function currentGroup() {
  const s = shownSections.value.find((g) => view.value[g.key].some((m) => isActive(m.to)))
  return s ? s.key : null
}
function isOpen(key) {
  return openKey.value === key
}
function toggle(key) {
  openKey.value = openKey.value === key ? null : key
}
watch(() => [route.path, staff.value], () => { openKey.value = currentGroup() }, { immediate: true })
</script>

<style scoped>
.admin-side-foot { margin-top: auto; padding-top: 12px; border-top: 1px solid rgba(255, 255, 255, 0.1); } /* 26.10.28 사이드바 맨 아래로 (메뉴가 길면 메뉴 아래를 따라감) */
.admin-nav-toggle { display: flex; align-items: center; justify-content: space-between; width: 100%; background: none; border: 0; cursor: pointer; text-align: left; }
.admin-nav-toggle:hover { color: #fff; }
.admin-nav-toggle > i { font-size: 11px; opacity: .8; }
.grp-name { display: inline-flex; align-items: center; gap: 10px; } /* 26.10.29 대분류 아이콘 (하위 메뉴 아이콘과 같은 폭) */
.admin-shell .admin-nav-item.admin-nav-sub { padding-left: 30px; } /* 26.10.29 대분류에 아이콘이 생겨서 하위 메뉴는 한 칸 들여쓰기 */
.grp-name i { font-size: 15px; width: 18px; display: inline-flex; justify-content: center; flex-shrink: 0; }
</style>
