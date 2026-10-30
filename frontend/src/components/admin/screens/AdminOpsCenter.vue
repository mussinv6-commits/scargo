<template>
  <!-- 26.10.24 운영 센터: 현황판 · 사고 · 예약 · 보고서 · KPI 를 한 화면의 탭으로 모음 (관리자가 메뉴를 찾아다니지 않게) -->
  <div>
    <!-- 26.10.28 설명 · 자동 백업 · 지금 확인할 경보 · 탭을 한 덩어리로 상단 고정 (useStickyStack 의 .sticky-head) -->
    <div class="sticky-head oc-head">
    <AdminPageHeader title="운영 관리" description="먼저 '현황판'에서 처리할 것을 확인하고, 필요한 탭으로 이동해 처리합니다." />
    <p class="oc-backup" :class="{ warn: !backup.lastBackupAt }">
      🛡 {{ backup.schedule || '자동 백업 정보를 불러오는 중...' }} ·
      <b>마지막 백업: {{ backup.lastBackupAt ? formatDateTime(backup.lastBackupAt) : '기록 없음' }}</b>
    </p>
    <!-- 26.10.28 QA#5 '지금 확인할 경보'는 긴급(n) / 주의(n) 개수만 표시. 누르면 경보 상세 화면으로 -->
    <section v-if="alerts.items.length" class="oc-alerts">
      <h4>🚨 지금 확인할 경보
        <button v-if="alerts.critical" type="button" class="c" title="긴급 경보 상세 보기" @click="goAlert('CRITICAL')">긴급 ({{ alerts.critical }})</button>
        <button v-if="alerts.warn" type="button" class="w" title="주의 경보 상세 보기" @click="goAlert('WARN')">주의 ({{ alerts.warn }})</button>
      </h4>
    </section>
    <p v-else-if="alertsLoaded" class="oc-ok">✅ 확인할 경보가 없습니다.</p>
    </div>
    <!-- 26.10.28 탭과 내용을 하나의 흰 상자 안에 (지역센터 인수 현황과 같은 모양) -->
    <div class="tab-box">
    <nav class="oc-tabs">
      <button v-for="t in TABS" :key="t.key" :class="{ on: tab === t.key }" @click="setTab(t.key)">{{ t.label }}</button>
    </nav>
    <OpsBoard v-if="tab === 'board'" />
    <IncidentPanel v-else-if="tab === 'incidents'" />
    <GateReservationPanel v-else-if="tab === 'reservations'" />
    <DailyReportPanel v-else-if="tab === 'report'" />
    <KpiTrendPanel v-else-if="tab === 'kpi'" />
    <CompanyScorePanel v-else-if="tab === 'score'" />
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { backupInfo, opsAlerts } from '@/utils/opsApi.js'
import { formatDateTime } from '@/utils/containerInspectionApi.js'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import OpsBoard from '@/components/common/OpsBoard.vue'
import IncidentPanel from '@/components/common/IncidentPanel.vue'
import GateReservationPanel from '@/components/common/GateReservationPanel.vue'
import DailyReportPanel from '@/components/common/DailyReportPanel.vue'
import KpiTrendPanel from '@/components/common/KpiTrendPanel.vue'
import CompanyScorePanel from '@/components/common/CompanyScorePanel.vue'

const route = useRoute()
const router = useRouter()
const TABS = [
  { key: 'board', label: '① 지금 처리할 것' },
  { key: 'incidents', label: '② 예외·사고' },
  { key: 'reservations', label: '③ 게이트 예약' },
  { key: 'report', label: '④ 일일 보고서' },
  { key: 'kpi', label: '⑤ KPI 추이' },
  { key: 'score', label: '⑥ 업체별 실적' },
]
// 26.10.28 ?tab= 으로 들어오면 그 탭이 먼저 열림 (경보 상세의 '처리하러 가기')
const tab = ref(TABS.some((t) => t.key === route.query.tab) ? route.query.tab : 'board')
watch(() => route.query.tab, (t) => { if (TABS.some((x) => x.key === t)) tab.value = t })
// 26.10.28 탭을 바꾸면 내용 맨 위로 스크롤 (모든 탭 공통 처리는 useStickyStack 에서)
function setTab(k) { tab.value = k }
// 26.10.28 긴급 / 주의를 누르면 경보 상세 화면으로 이동 (경보 전체 내용 + 처리할 탭 바로가기)
function goAlert(level) { router.push({ path: '/admin/ops-center/alerts', query: { level } }) }
const backup = reactive({ schedule: '', lastBackupAt: null })
const alerts = reactive({ critical: 0, warn: 0, items: [] })
const alertsLoaded = ref(false)
let timer = null
async function loadAlerts() {
  try { Object.assign(alerts, await opsAlerts()); alertsLoaded.value = true } catch { /* 경보를 못 불러와도 화면은 계속 쓴다 */ }
}
onBeforeUnmount(() => clearInterval(timer))
onMounted(async () => {
  loadAlerts()
  timer = setInterval(loadAlerts, 60000)
  try { Object.assign(backup, await backupInfo()) } catch { backup.schedule = '자동 백업 정보를 확인하지 못했습니다.' }
})
</script>

<style scoped>
.oc-backup { margin: 0 0 10px; padding: 8px 12px; border-radius: 8px; background: #eaf6ee; color: #1e6b3a; font-size: 13px; }
.oc-backup.warn { background: #fff4e0; color: #8a5a00; }
.oc-alerts { border: 1px solid #f0b4b4; background: #fff1f1; border-radius: 10px; padding: 10px 14px; margin-bottom: 12px; }
.oc-alerts h4 { margin: 0; font-size: 14px; display: flex; align-items: center; gap: 8px; }
.oc-alerts h4 button { border: 0; border-radius: 12px; padding: 3px 12px; font-size: 13px; font-weight: 700; cursor: pointer; }
.oc-alerts h4 .c { background: #e5484d; color: #fff; } .oc-alerts h4 .w { background: #ff9f1c; color: #0b1d36; }
.oc-alerts h4 button:hover { box-shadow: inset 0 0 0 100px rgba(255, 255, 255, .25), 0 1px 4px rgba(17, 40, 71, .2); } /* 26.10.28 누르면 경보 상세로 이동 */
.oc-alerts ul { list-style: none; margin: 0; padding: 0; } .oc-alerts li { padding: 4px 0; font-size: 13px; cursor: pointer; }
.oc-alerts li b { margin-right: 6px; } .oc-alerts li.critical b { color: #e5484d; } .oc-alerts li.warn b { color: #b86e00; } .oc-alerts li small { color: #6b7a90; margin-left: 6px; }
.oc-alerts .more { margin: 4px 0 0; font-size: 12px; color: #6b7a90; }
.oc-ok { margin: 0 0 12px; padding: 8px 12px; border-radius: 8px; background: #eaf6ee; color: #1e6b3a; font-size: 13px; }
.oc-head { padding-bottom: 4px; }
.oc-head :deep(.admin-page-header) { margin-bottom: 12px; }
.oc-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 10px; }
/* 26.10.28 탭 모양 통일: 지역센터 인수 현황 탭(.crp-tabs)과 같은 모양 (회색 버튼 + 선택 탭은 파란 채움) */
.oc-tabs button { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 8px; padding: 7px 14px; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.oc-tabs button:hover:not(.on) { border-color: #1d4ed8; color: #1d4ed8; }
.oc-tabs button.on { background: #1d4ed8; border-color: #1d4ed8; color: #fff; font-weight: 700; }
</style>
