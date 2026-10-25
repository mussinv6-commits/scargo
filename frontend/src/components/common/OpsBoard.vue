<template>
  <!-- 26.10.24 운영 현황판: 처리해야 할 것만 한 화면에 모아 보여 주고 30초마다 새로 읽는다 -->
  <div class="ob" :class="{ 'is-dark': dark }">
    <div class="ob-bar">
      <span class="ob-muted">{{ data?.scope }} · {{ updatedText }}</span>
      <label class="ob-auto"><input v-model="auto" type="checkbox" /> 30초마다 새로고침</label>
      <button class="ob-btn" :disabled="loading" @click="load">새로고침</button>
    </div>
    <p v-if="message" class="ob-msg" :class="{ ok: messageOk }">{{ message }}</p>

    <div v-if="data" class="ob-cards">
      <div class="ob-card" :class="{ warn: s.waitingUnload > 0 }"><b>{{ s.waitingUnload }}</b><span>하역 대기</span></div>
      <div class="ob-card"><b>{{ s.docksOccupied }}/{{ s.docksTotal }}</b><span>도크 사용</span></div>
      <div class="ob-card" :class="{ bad: s.failedInspections7d > 0 }"><b>{{ s.failedInspections7d }}</b><span>점검 불합격 (7일)</span></div>
      <div class="ob-card" :class="{ bad: s.delayedOrders > 0 }"><b>{{ s.delayedOrders }}</b><span>지연 오더</span></div>
      <div class="ob-card" :class="{ bad: s.highIncidents > 0, warn: s.openIncidents > 0 }"><b>{{ s.openIncidents }}</b><span>미처리 예외·사고</span></div>
    </div>

    <section v-if="data" class="ob-sec">
      <h3>하역 대기 <small>오래 기다린 순</small></h3>
      <table class="ob-table">
        <thead><tr><th>컨테이너</th><th>차량</th><th>도크</th><th>도착</th><th>대기</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!data.unloads.length"><td colspan="6" class="ob-empty">하역 대기 중인 컨테이너가 없습니다.</td></tr>
          <tr v-for="u in data.unloads" :key="u.receiptId">
            <td>{{ u.containerNo || '-' }}</td><td>{{ u.vehicleNo }}</td><td>{{ u.dockCode || '-' }}</td>
            <td>{{ fmt(u.arrivedAt) }}</td>
            <td :class="{ late: u.waitingMin >= 60 }">{{ dur(u.waitingMin) }}</td>
            <td><button v-if="data.canManage" class="ob-btn primary" :disabled="busy" @click="unload(u)">하역 완료</button></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="data" class="ob-sec">
      <h3>지연 오더 <small>도착 예정 시각(ETA)이 지난 운송</small></h3>
      <table class="ob-table">
        <thead><tr><th>오더</th><th>차량</th><th>컨테이너</th><th>ETA</th><th>지연</th></tr></thead>
        <tbody>
          <tr v-if="!data.delayedOrders.length"><td colspan="5" class="ob-empty">지연 중인 오더가 없습니다.</td></tr>
          <tr v-for="o in data.delayedOrders" :key="o.orderId">
            <td>{{ o.orderNo }}</td><td>{{ o.vehicleNo || '-' }}</td><td>{{ o.containerNo }}</td>
            <td>{{ fmt(o.eta) }}</td><td class="late">{{ dur(o.lateMin) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="data" class="ob-sec">
      <h3>점검 불합격 <small>최근 7일 확정본</small></h3>
      <table class="ob-table">
        <thead><tr><th>점검</th><th>컨테이너</th><th>확정</th></tr></thead>
        <tbody>
          <tr v-if="!data.failedInspections.length"><td colspan="3" class="ob-empty">불합격 기록이 없습니다.</td></tr>
          <tr v-for="f in data.failedInspections" :key="f.inspectionId">
            <td>#{{ f.inspectionId }}</td><td>{{ f.containerNo || '-' }}</td><td>{{ fmt(f.finalizedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="data" class="ob-sec">
      <h3>미처리 예외·사고
        <router-link v-if="incidentsLink" :to="incidentsLink" class="ob-link">접수 · 처리 화면 →</router-link>
      </h3>
      <table class="ob-table">
        <thead><tr><th>중요도</th><th>유형</th><th>제목</th><th>상태</th><th>담당</th><th>접수</th></tr></thead>
        <tbody>
          <tr v-if="!data.incidents.length"><td colspan="6" class="ob-empty">미처리 건이 없습니다.</td></tr>
          <tr v-for="i in data.incidents" :key="i.incidentId">
            <td><span class="ob-sev" :class="'s-' + i.severity">{{ SEVERITY[i.severity] }}</span></td>
            <td>{{ INCIDENT_TYPE[i.type] || i.type }}</td><td>{{ i.title }}</td>
            <td>{{ INCIDENT_STATUS[i.status] }}</td><td>{{ i.assignee || '-' }}</td><td>{{ fmt(i.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="data" class="ob-sec">
      <h3>도크 현황</h3>
      <div class="ob-docks">
        <span v-for="d in data.docks" :key="d.dockId" class="ob-dock" :class="{ used: d.occupied }">
          {{ d.dockCode }} <small>{{ d.dockType === 'IN' ? '입고' : '출고' }}</small> {{ d.occupied ? '사용 중' : '비어 있음' }}
        </span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { opsOverview, INCIDENT_TYPE, SEVERITY, INCIDENT_STATUS } from '@/utils/opsApi.js'
import { completeUnload } from '@/utils/hubArrivalApi.js'
import { formatDateTime } from '@/utils/containerInspectionApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

defineProps({
  dark: { type: Boolean, default: false },
  incidentsLink: { type: [String, Object], default: null }, // 접수 · 처리 화면으로 가는 경로 (없으면 링크 숨김)
})

const data = ref(null)
const loading = ref(false)
const busy = ref(false)
const auto = ref(true)
const message = ref('')
const messageOk = ref(false)
const updatedAt = ref(null)
let timer = null

const s = computed(() => data.value?.summary || {})
const updatedText = computed(() => (updatedAt.value ? updatedAt.value.toLocaleTimeString('ko-KR') + ' 기준' : '불러오는 중'))
const fmt = (v) => (v ? formatDateTime(v) : '-')
const dur = (min) => {
  const m = Math.max(0, Number(min) || 0)
  return m >= 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m}분`
}

async function load() {
  loading.value = true
  try {
    data.value = await opsOverview()
    updatedAt.value = new Date()
  } catch (err) {
    message.value = friendlyError(err, '현황을 불러오지 못했습니다.')
    messageOk.value = false
  } finally {
    loading.value = false
  }
}

async function unload(u) {
  if (!confirm(`${u.containerNo || u.vehicleNo} 하역을 완료 처리할까요?`)) return
  busy.value = true
  try {
    await completeUnload(u.receiptId)
    message.value = '하역을 완료했습니다.'
    messageOk.value = true
    await load()
  } catch (err) {
    message.value = friendlyError(err, '하역 완료를 처리하지 못했습니다.')
    messageOk.value = false
  } finally {
    busy.value = false
  }
}

function schedule() {
  clearInterval(timer)
  if (auto.value) timer = setInterval(load, 30000)
}

watch(auto, schedule)
onMounted(() => { load(); schedule() })
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.ob { display: flex; flex-direction: column; gap: 14px; }
.ob-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-size: 13px; }
.ob-auto { display: flex; gap: 4px; align-items: center; margin-left: auto; }
.ob-muted { color: #6b7a90; }
.ob-btn { border: 1px solid #c9d3e0; background: #fff; border-radius: 6px; padding: 5px 12px; font-size: 13px; cursor: pointer; }
.ob-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; font-weight: 700; }
.ob-btn:disabled { opacity: .5; cursor: default; }
.ob-msg { margin: 0; color: #c0392b; font-size: 13px; } .ob-msg.ok { color: #1e8e3e; }
.ob-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }
.ob-card { background: #fff; border: 1px solid #d5dbe5; border-radius: 10px; padding: 12px 14px; display: flex; flex-direction: column; gap: 2px; }
.ob-card b { font-size: 26px; font-variant-numeric: tabular-nums; } .ob-card span { font-size: 12.5px; color: #6b7a90; }
.ob-card.warn { border-color: #ff9f1c; } .ob-card.bad { border-color: #e5484d; } .ob-card.bad b { color: #e5484d; }
.ob-sec { background: #fff; border: 1px solid #d5dbe5; border-radius: 10px; padding: 10px 14px; overflow-x: auto; }
.ob-sec h3 { margin: 0 0 8px; font-size: 15px; display: flex; align-items: baseline; gap: 8px; }
.ob-sec h3 small { color: #6b7a90; font-weight: 400; font-size: 12px; }
.ob-link { margin-left: auto; font-size: 12.5px; color: #2563eb; text-decoration: none; }
.ob-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.ob-table th, .ob-table td { padding: 7px 8px; border-bottom: 1px solid #e8ecf2; text-align: left; white-space: nowrap; }
.ob-table th { color: #6b7a90; font-weight: 600; }
.ob-empty { text-align: center !important; color: #6b7a90; }
.late { color: #e5484d; font-weight: 700; }
.ob-sev { padding: 2px 8px; border-radius: 10px; font-size: 12px; background: #eef1f6; }
.ob-sev.s-HIGH { background: #fde2e3; color: #b42318; } .ob-sev.s-MEDIUM { background: #fff1d6; color: #9a5b00; }
.ob-docks { display: flex; flex-wrap: wrap; gap: 8px; }
.ob-dock { border: 1px solid #d5dbe5; border-radius: 8px; padding: 6px 10px; font-size: 12.5px; color: #1e8e3e; }
.ob-dock.used { color: #b42318; border-color: #e5484d; background: #fff5f5; }
.ob-dock small { color: #6b7a90; }
.is-dark .ob-card, .is-dark .ob-sec { background: #0f2a4a; border-color: #1d3b60; color: #e6eff7; }
.is-dark .ob-muted, .is-dark .ob-card span, .is-dark .ob-sec h3 small, .is-dark .ob-table th, .is-dark .ob-empty, .is-dark .ob-dock small { color: #9fb0c6; }
.is-dark .ob-table th, .is-dark .ob-table td { border-bottom-color: #1d3b60; }
.is-dark .ob-btn { background: #12304f; border-color: #2a4d76; color: #e6eff7; }
.is-dark .ob-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; }
.is-dark .ob-dock { border-color: #2a4d76; background: transparent; color: #86efac; }
.is-dark .ob-dock.used { color: #fca5a5; border-color: #e5484d; background: transparent; }
.is-dark .ob-sev { background: #12304f; } .is-dark .ob-link { color: #7cb7ff; }
</style>
