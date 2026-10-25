<template>
  <!-- 26.10.24 운영 센터: 현황판 · 사고 · 예약 · 보고서 · KPI 를 한 화면의 탭으로 모음 (관리자가 메뉴를 찾아다니지 않게) -->
  <div>
    <AdminPageHeader title="운영 센터" description="먼저 '현황판'에서 처리할 것을 확인하고, 필요한 탭으로 이동해 처리합니다." />
    <p class="oc-backup" :class="{ warn: !backup.lastBackupAt }">
      🛡 {{ backup.schedule || '자동 백업 정보를 불러오는 중...' }} ·
      <b>마지막 백업: {{ backup.lastBackupAt ? formatDateTime(backup.lastBackupAt) : '기록 없음' }}</b>
    </p>
    <section v-if="alerts.items.length" class="oc-alerts">
      <h4>🚨 지금 확인할 경보 <span v-if="alerts.critical" class="c">긴급 {{ alerts.critical }}</span><span class="w">주의 {{ alerts.warn }}</span></h4>
      <ul>
        <li v-for="(a, i) in alerts.items.slice(0, 6)" :key="i" :class="a.level.toLowerCase()" @click="tab = a.tab">
          <b>{{ a.level === 'CRITICAL' ? '긴급' : '주의' }}</b> {{ a.title }} <small>{{ a.detail }}</small>
        </li>
      </ul>
      <p v-if="alerts.items.length > 6" class="more">외 {{ alerts.items.length - 6 }}건 — 각 탭에서 확인</p>
    </section>
    <p v-else-if="alertsLoaded" class="oc-ok">✅ 확인할 경보가 없습니다.</p>
    <nav class="oc-tabs">
      <button v-for="t in TABS" :key="t.key" :class="{ on: tab === t.key }" @click="tab = t.key">{{ t.label }}</button>
    </nav>
    <OpsBoard v-if="tab === 'board'" />
    <IncidentPanel v-else-if="tab === 'incidents'" />
    <GateReservationPanel v-else-if="tab === 'reservations'" />
    <DailyReportPanel v-else-if="tab === 'report'" />
    <KpiTrendPanel v-else-if="tab === 'kpi'" />
    <CompanyScorePanel v-else-if="tab === 'score'" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { backupInfo, opsAlerts } from '@/utils/opsApi.js'
import { formatDateTime } from '@/utils/containerInspectionApi.js'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import OpsBoard from '@/components/common/OpsBoard.vue'
import IncidentPanel from '@/components/common/IncidentPanel.vue'
import GateReservationPanel from '@/components/common/GateReservationPanel.vue'
import DailyReportPanel from '@/components/common/DailyReportPanel.vue'
import KpiTrendPanel from '@/components/common/KpiTrendPanel.vue'
import CompanyScorePanel from '@/components/common/CompanyScorePanel.vue'

const TABS = [
  { key: 'board', label: '① 지금 처리할 것' },
  { key: 'incidents', label: '② 예외·사고' },
  { key: 'reservations', label: '③ 게이트 예약' },
  { key: 'report', label: '④ 일일 보고서' },
  { key: 'kpi', label: '⑤ KPI 추이' },
  { key: 'score', label: '⑥ 업체별 실적' },
]
const tab = ref('board')
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
.oc-alerts h4 { margin: 0 0 6px; font-size: 14px; } .oc-alerts h4 .c { margin-left: 8px; background: #e5484d; color: #fff; border-radius: 10px; padding: 1px 8px; font-size: 12px; }
.oc-alerts h4 .w { margin-left: 6px; background: #ff9f1c; color: #0b1d36; border-radius: 10px; padding: 1px 8px; font-size: 12px; }
.oc-alerts ul { list-style: none; margin: 0; padding: 0; } .oc-alerts li { padding: 4px 0; font-size: 13px; cursor: pointer; }
.oc-alerts li b { margin-right: 6px; } .oc-alerts li.critical b { color: #e5484d; } .oc-alerts li.warn b { color: #b86e00; } .oc-alerts li small { color: #6b7a90; margin-left: 6px; }
.oc-alerts .more { margin: 4px 0 0; font-size: 12px; color: #6b7a90; }
.oc-ok { margin: 0 0 12px; padding: 8px 12px; border-radius: 8px; background: #eaf6ee; color: #1e6b3a; font-size: 13px; }
.oc-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
.oc-tabs button { border: 1px solid #c9d3e0; background: #fff; border-radius: 8px; padding: 8px 16px; font-size: 14px; cursor: pointer; }
.oc-tabs button.on { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; font-weight: 700; }
</style>
