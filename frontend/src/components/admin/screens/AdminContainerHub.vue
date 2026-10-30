<template>
  <!-- 26.10.28 컨테이너 관리: 컨테이너 목록(기존 컨테이너 관리) + 컨테이너 점검표를 한 메뉴의 탭으로 통합 -->
  <div class="tab-box">
    <!-- 26.10.28 탭과 내용을 하나의 흰 상자 안에 (지역센터 인수 현황과 같은 모양) -->
    <nav class="ch-tabs">
      <button type="button" :class="{ on: tab === 'list' }" @click="go('list')"><i class="bi bi-box-seam"></i> 컨테이너 목록</button>
      <button type="button" :class="{ on: tab === 'inspection' }" @click="go('inspection')"><i class="bi bi-clipboard-check"></i> 점검표</button>
    </nav>
    <AdminContainers v-if="tab === 'list'" />
    <AdminInspections v-else />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminContainers from '@/components/admin/screens/AdminContainers.vue'
import AdminInspections from '@/components/admin/screens/AdminInspections.vue'

const route = useRoute()
const router = useRouter()
// ?tab=inspection 으로 들어오면 점검표 탭 (예전 /admin/inspections 주소도 여기로 옴)
const tab = computed(() => (route.query.tab === 'inspection' ? 'inspection' : 'list'))
function go(t) { router.replace({ query: { ...route.query, tab: t } }) }
</script>

<style scoped>
/* 26.10.28 탭 모양 통일: 지역센터 인수 현황 탭(.crp-tabs)과 같은 모양 (회색 버튼 + 선택 탭은 파란 채움) */
.ch-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
.ch-tabs button { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 8px; padding: 7px 14px; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.ch-tabs button:hover:not(.on) { border-color: #1d4ed8; color: #1d4ed8; }
.ch-tabs button.on { background: #1d4ed8; border-color: #1d4ed8; color: #fff; font-weight: 700; }
.ch-tabs button i { margin-right: 4px; }
</style>
