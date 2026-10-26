<template>
  <!-- 26.10.26 총관리자(S Cargo)는 카르고 관리자 사이드바를 그대로 쓰고, 통합 관제 화면만 오른쪽에 연다 -->
  <div v-if="cargoAdmin" class="admin-app">
    <div class="admin-shell">
      <AdminSidebar />
      <div class="admin-main">
        <div class="sc2"><div class="app"><main>
          <header><div class="t"><span id="htitle">{{ T[cur] }}</span></div></header>
          <router-view :key="$route.fullPath" :tick="tick" :role="role" />
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
    <header><div class="t"><span id="htitle">{{ T[cur] }}</span></div></header>
    <router-view :key="$route.fullPath" :tick="tick" :role="role" />
  </main></div></div>
</template>
<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import './sc2.css'
import '@/components/CSS/admin.css'
import AdminSidebar from '@/components/admin/AdminSidebar.vue'
import { T, NAV, webRole, canSee } from './roles.js'
import { authState } from '@/auth/authState.js'
const route = useRoute(), router = useRouter()
const role = computed(() => webRole(authState.user))
const cargoAdmin = computed(() => authState.user?.userType === 'ADMIN' && authState.user?.adminScope !== 'SBOT')
// 로그인 전에는 로그인 메뉴만. 로그인 뒤 로그인 메뉴는 숨김
const nav = computed(() => NAV.map(g => ({ grp: g.grp, items: g.items.filter(id => role.value ? id !== 'W-01' && canSee(role.value, id) : id === 'W-01') })).filter(g => g.items.length))
const cur = computed(() => route.meta.sid)
const tick = ref(0)
// 30초 자동 폴링(화면 표시 없음)
let t
onMounted(() => { t = setInterval(() => { tick.value++ }, 30000) })
onUnmounted(() => clearInterval(t))
</script>
