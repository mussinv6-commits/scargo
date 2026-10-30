<template>
  <!-- 26.10.26 총관리자(S Cargo)는 카르고 관리자 사이드바를 그대로 쓰고, 통합 관제 화면만 오른쪽에 연다 -->
  <div v-if="cargoAdmin" class="admin-app">
    <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
      <AdminSidebar />
      <div ref="mainEl" class="admin-main" :class="{ 'has-title': !!title }"><!-- 26.10.29 화면 안 제목(내 정보 등)이 상단 메뉴명과 겹치지 않게 (관리자 화면과 같은 규칙) -->
        <!-- 26.10.28 QA C-4 · C-5: 카르고 관리자 화면과 같은 상단 고정 바 (삼선 + 메뉴명) -->
        <header class="admin-topbar">
          <button type="button" class="admin-burger" :aria-label="collapsed ? '메뉴 펼치기' : '메뉴 숨기기'" @click="collapsed = !collapsed"><i class="bi bi-list"></i></button>
          <h1 class="admin-page-title">{{ title }}</h1>
        </header>
        <div class="sc2 sc2-in-admin"><div class="app"><main>
          <router-view :key="$route.path" :tick="tick" :role="role" />
        </main></div></div>
      </div>
    </div>
  </div>
  <div v-else class="sc2"><div class="app"><nav>
    <!-- v9: 권한 전환 드롭다운 삭제. 메뉴는 로그인 계정 권한으로만, 쓸 수 없는 메뉴는 숨김 -->
    <template v-for="g in nav" :key="g.grp || 'top'">
      <div v-if="g.grp" class="grp">{{ g.grp }}</div>
      <a v-for="id in g.items" :key="id" :data-s="id" :class="{ on: cur === id }" @click="router.push('/w/' + id)">{{ T[id] }}</a>
    </template>
  </nav><main>
    <header class="sc2-sticky"><div class="t"><span id="htitle">{{ title }}</span></div></header>
    <router-view :key="$route.path" :tick="tick" :role="role" />
  </main></div></div>
</template>
<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import './sc2.css'
import '@/components/CSS/admin.css'
import AdminSidebar from '@/components/admin/AdminSidebar.vue'
import { useStickyStack } from '@/utils/useStickyStack.js'
import { T, NAV, webRole, canSee, staffRole } from './roles.js'
import { authState } from '@/auth/authState.js'
const route = useRoute(), router = useRouter()
const role = computed(() => webRole(authState.user))
// 26.10.29 관제사(통합 · 허브 · 지역센터)도 총관리자와 같은 사이드바 · 상단 바 (메뉴는 권한만큼)
const cargoAdmin = computed(() => (authState.user?.userType === 'ADMIN' && authState.user?.adminScope !== 'SBOT') || !!staffRole(authState.user))
// 로그인 전에는 로그인 메뉴만. 로그인 뒤 로그인 메뉴는 숨김
const nav = computed(() => NAV.map(g => ({ grp: g.grp, items: g.items.filter(id => role.value ? id !== 'W-01' && canSee(role.value, id) : id === 'W-01') })).filter(g => g.items.length))
const cur = computed(() => route.meta.sid)
// 26.10.28 QA#11 점검표: 구분(차량/컨테이너)에 따라 상단 메뉴명을 '차량 점검표' / '컨테이너 점검표'로
// 26.10.29 W-05 는 사이드바와 같은 이름 '지역 물류센터 관제'
const title = computed(() => (cur.value === 'W-15' ? (route.query.tab === 'c' ? '컨테이너 점검표' : '차량 점검표') : cur.value === 'W-05' ? '물류센터 관제' : T[cur.value] || route.meta.title || ''))
const collapsed = ref(false)
// 26.10.28 메뉴명 아래 탭도 메뉴명과 함께 상단 고정 (허브 관제 · 지역센터 관제 등)
const mainEl = ref(null)
useStickyStack(mainEl)
const tick = ref(0)
// 30초 자동 폴링(화면 표시 없음)
let t
onMounted(() => { t = setInterval(() => { tick.value++ }, 30000) })
onUnmounted(() => clearInterval(t))
</script>
<style>
/* 26.10.28 QA C-3: 관리자 셸 안의 통합 관제 화면은 셸 높이에 맞춰 그리고(창 높이 강제 X), 넘칠 때만 스크롤 */
.admin-main .sc2-in-admin { background: transparent; }
.admin-main .sc2-in-admin .app { min-height: 0; display: block; }
.admin-main .sc2-in-admin main { padding: 0 0 24px; }
/* 26.10.28 QA C-5: 관제사 화면도 메뉴명 상단 고정 */
.sc2 header.sc2-sticky { position: sticky; top: 0; z-index: 15; background: #f3f5f9; }
</style>
