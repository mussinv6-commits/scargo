<template>
  <!-- 26.10.28 가입 승인: 기업회원 가입 승인 + 기사 가입 승인(W-10)을 한 메뉴의 탭으로 통합 -->
  <div class="tab-box">
    <!-- 26.10.28 탭과 내용을 하나의 흰 상자 안에 (지역센터 인수 현황과 같은 모양) -->
    <nav class="sa-tabs">
      <button type="button" :class="{ on: tab === 'corp' }" @click="go('corp')"><i class="bi bi-building"></i> 기업회원</button>
      <button type="button" :class="{ on: tab === 'driver' }" @click="go('driver')"><i class="bi bi-truck"></i> 기사</button>
    </nav>
    <AdminAccounts v-if="tab === 'corp'" corp-only />
    <!-- 26.10.28 기업회원 탭처럼 기사 탭에도 안내 문구 -->
    <template v-else>
      <AdminPageHeader title="기사 가입 승인" class="sa-desc" :description="'회원가입으로 신청한 기사(컨테이너 · 지선 운송기사)입니다.\n상태에서 승인대기를 고른 뒤 승인 또는 반려를 누르세요.\n반려할 때는 사유를 입력합니다.'" /><!-- 26.10.28 온점 기준 줄바꿈 -->
      <div class="sc2 sa-w"><W10 :tick="tick" role="ADMIN" /></div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminAccounts from '@/components/admin/screens/AdminAccounts.vue'
import W10 from '@/scargo2/screens/W10.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import '@/scargo2/sc2.css'

const route = useRoute()
const router = useRouter()
// ?tab=driver 로 들어오면 기사 탭이 먼저 열림
const tab = computed(() => (route.query.tab === 'driver' ? 'driver' : 'corp'))
function go(t) { router.replace({ query: { ...route.query, tab: t } }) }

// W-10 은 30초마다 tick 이 바뀌면 다시 불러온다 (ScLayout 과 같은 방식)
const tick = ref(0)
let timer
onMounted(() => { timer = setInterval(() => { tick.value++ }, 30000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
/* 26.10.28 탭 모양 통일: 지역센터 인수 현황 탭(.crp-tabs)과 같은 모양 (회색 버튼 + 선택 탭은 파란 채움) */
.sa-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
.sa-tabs button { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 8px; padding: 7px 14px; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.sa-tabs button:hover:not(.on) { border-color: #1d4ed8; color: #1d4ed8; }
.sa-tabs button.on { background: #1d4ed8; border-color: #1d4ed8; color: #fff; font-weight: 700; }
.sa-tabs button i { margin-right: 4px; }
.sa-w { background: transparent; }
.sa-desc :deep(p) { white-space: pre-line; } /* 26.10.28 안내 문구 온점마다 줄바꿈 */
/* 26.10.28 기사 탭 내용도 기업회원 탭처럼 상자 한 겹으로 (W-10 카드 테두리 · 그림자 제거) */
.sa-w :deep(.scr > .card) { background: transparent; border: 0; box-shadow: none; padding: 0; border-radius: 0; }
</style>
