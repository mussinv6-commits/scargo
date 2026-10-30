<template>
  <!-- 26.10.30 탭 이름 3D 현황 → CCTV 현황. 지역 물류센터 관제: CCTV 현황(물류센터 관제) + 센터별 관제(지역센터 관제 W-05) + 인수 현황(지역센터 인수 현황)을 한 메뉴의 탭으로 통합
       ?tab=3d | region | rcv 로 바로 열 수 있음. 대시보드에서 센터 건물을 누르면 ?c=SEL|GGI|ICN 으로 들어와 3D 에서 그 센터가 먼저 선택된다 -->
  <div class="rc">
    <AdminPageHeader title="물류센터 관제" description="CCTV 현황으로 전체 센터를 보고, 센터별 관제에서 지선 운행 · 봉인 대조를, 입고 현황에서 인수 확정 상태를 봅니다." />
    <div class="tab-box">
      <div class="rc-tabs tabs-sticky" role="tablist">
        <button v-for="t in TABS" :key="t[0]" type="button" :class="{ on: tab === t[0] }" @click="go(t[0])">{{ t[1] }}</button>
      </div>
      <!-- 3D 는 탭을 오갈 때 다시 그리지 않도록 숨기기만 한다 -->
      <div v-show="tab === '3d'" class="rc-pane rc-3d"><SbotFrame page="center" title="물류센터 관제" /></div>
      <div v-if="tab === 'region'" class="rc-pane sc2 rc-w"><W05 :tick="tick" role="ADMIN" /></div>
      <div v-if="tab === 'rcv'" class="rc-pane rc-rcv"><CenterReceiptPanel :can-confirm="false" /></div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import SbotFrame from '@/views/SbotFrame.vue'
import W05 from '@/scargo2/screens/W05.vue'
import CenterReceiptPanel from '@/components/common/CenterReceiptPanel.vue'
import '@/scargo2/sc2.css'

const TABS = [['3d', 'CCTV 현황'], ['region', '센터별 관제'], ['rcv', '물류센터 입고 현황']] // 26.10.28 탭 이름 정리 (A안)
const route = useRoute()
const router = useRouter()
const tab = computed(() => (TABS.some((t) => t[0] === route.query.tab) ? route.query.tab : '3d'))
function go(t) { router.replace({ query: { ...route.query, tab: t } }) }

// 지역별(W-05)은 30초마다 다시 불러온다 (ScLayout 과 같은 방식)
const tick = ref(0)
let timer
onMounted(() => { timer = setInterval(() => { tick.value++ }, 30000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
/* 탭 모양: 다른 메뉴와 같은 모양 (회색 버튼 + 선택 탭은 파란 채움) */
.rc-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
.rc-tabs button { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 8px; padding: 7px 16px; font-size: 14px; font-weight: 600; cursor: pointer; }
.rc-tabs button:hover:not(.on) { border-color: #1d4ed8; color: #1d4ed8; }
.rc-tabs button.on { background: #1d4ed8; border-color: #1d4ed8; color: #fff; font-weight: 700; }
/* 탭 안의 화면은 상자 한 겹으로 (안쪽 상자 테두리 · 그림자 제거) */
.rc-w { background: transparent; }
.rc-w :deep(.tab-box) { border: 0; box-shadow: none; padding: 0; background: transparent; }
.rc-rcv :deep(.crp) { border: 0; padding: 0; }
.rc-3d :deep(.sb-frame) { height: calc(100vh - 290px); }
</style>
