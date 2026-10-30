<template>
  <!-- 26.10.24 운영 현황판: 처리해야 할 것만 한 화면에 모아 보여 주고 30초마다 새로 읽는다 -->
  <div class="ob" :class="{ 'is-dark': dark }">
    <div class="ob-bar">
      <span class="ob-muted">{{ data?.scope }} · {{ updatedText }}</span>
      <label class="ob-auto"><input v-model="auto" type="checkbox" /> 30초마다 새로고침</label>
      <button class="ob-btn" :disabled="loading" @click="load">새로고침</button>
    </div>
    <p v-if="message" class="ob-msg" :class="{ ok: messageOk }">{{ message }}</p>

    <!-- 26.10.28 QA#5 '지금 처리할 것' 카드: 아이콘 + 카드별 색상 -->
    <div v-if="data" class="ob-cards">
      <div class="ob-card c-blue" role="button" tabindex="0" @click="obTab = 'unload'" :class="{ warn: s.waitingUnload > 0 }"><i class="bi bi-box-arrow-in-down"></i><div><b>{{ s.waitingUnload }}</b><span>하역 대기</span></div></div>
      <div class="ob-card c-teal" role="button" tabindex="0" @click="obTab = 'dock'"><i class="bi bi-door-open"></i><div><b>{{ s.docksOccupied }}/{{ s.docksTotal }}</b><span>도크 사용</span></div></div>
      <div class="ob-card c-purple" role="button" tabindex="0" @click="obTab = 'fail'" :class="{ bad: s.failedInspections7d > 0 }"><i class="bi bi-clipboard-x"></i><div><b>{{ s.failedInspections7d }}</b><span>점검 불합격 (7일)</span></div></div>
      <div class="ob-card c-amber" role="button" tabindex="0" @click="obTab = 'delay'" :class="{ bad: s.delayedOrders > 0 }"><i class="bi bi-hourglass-split"></i><div><b>{{ s.delayedOrders }}</b><span>지연 오더</span></div></div>
      <div class="ob-card c-red" role="button" tabindex="0" @click="obTab = 'inc'" :class="{ bad: s.highIncidents > 0, warn: s.openIncidents > 0 }"><i class="bi bi-exclamation-octagon"></i><div><b>{{ s.openIncidents }}</b><span>미처리 예외·사고</span></div></div>
    </div>

    <!-- 26.10.28 카드 아래 목록 5개(하역 대기 · 지연 오더 · 점검 불합격 · 미처리 예외·사고 · 도크 현황)를 탭으로 전환. 카드를 눌러도 해당 탭이 열린다 -->
    <div v-if="data" class="ob-tabs" role="tablist">
      <button v-for="t in OB_TABS" :key="t.key" type="button" :class="{ on: obTab === t.key }" @click="obTab = t.key">
        {{ t.label }}<span v-if="t.n() > 0" class="n">{{ t.n() }}</span>
      </button>
    </div>

    <section v-if="data && obTab === 'unload'" class="ob-sec">
      <h3>하역 대기 <small>오래 기다린 순</small></h3>
      <table class="ob-table">
        <thead><tr><th class="no">번호</th><th>컨테이너</th><th>차량</th><th>도크</th><th>도착</th><th>대기</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!data.unloads.length"><td colspan="7" class="ob-empty">하역 대기 중인 컨테이너가 없습니다.</td></tr>
          <tr v-for="(u, i) in pgU.rows" :key="u.receiptId">
            <td class="no">{{ pgU.no(i) }}</td><td>{{ u.containerNo || '-' }}</td><td>{{ u.vehicleNo }}</td><td>{{ u.dockCode || '-' }}</td>
            <td>{{ fmt(u.arrivedAt) }}</td>
            <td :class="{ late: u.waitingMin >= 60 }">{{ dur(u.waitingMin) }}</td>
            <td><button v-if="data.canManage" class="ob-btn primary" :disabled="busy" @click="unload(u)">하역 완료</button></td>
          </tr>
        </tbody>
      </table>
      <AdminPager v-model="pgU.page" :page-count="pgU.pageCount" />
    </section>

    <section v-if="data && obTab === 'delay'" class="ob-sec">
      <h3>지연 오더 <small>도착 예정 시각(ETA)이 지난 운송</small></h3>
      <table class="ob-table">
        <thead><tr><th class="no">번호</th><th>오더</th><th>차량</th><th>컨테이너</th><th>ETA</th><th>지연</th></tr></thead>
        <tbody>
          <tr v-if="!data.delayedOrders.length"><td colspan="6" class="ob-empty">지연 중인 오더가 없습니다.</td></tr>
          <tr v-for="(o, i) in pgD.rows" :key="o.orderId">
            <td class="no">{{ pgD.no(i) }}</td><td>{{ o.orderNo }}</td><td>{{ o.vehicleNo || '-' }}</td><td>{{ o.containerNo }}</td>
            <td>{{ fmt(o.eta) }}</td><td class="late">{{ dur(o.lateMin) }}</td>
          </tr>
        </tbody>
      </table>
      <AdminPager v-model="pgD.page" :page-count="pgD.pageCount" />
    </section>

    <section v-if="data && obTab === 'fail'" class="ob-sec">
      <h3>점검 불합격 <small>최근 7일 확정본</small></h3>
      <table class="ob-table">
        <thead><tr><th class="no">번호</th><th>확정 시각</th><th>컨테이너</th><th>불합격 사유</th><th>점검 번호</th></tr></thead>
        <tbody>
          <tr v-if="!data.failedInspections.length"><td colspan="5" class="ob-empty">불합격 기록이 없습니다.</td></tr>
          <tr v-for="(f, i) in pgF.rows" :key="f.inspectionId">
            <td class="no">{{ pgF.no(i) }}</td><td>{{ fmt(f.finalizedAt) }}</td><td>{{ f.containerNo || '-' }}</td><td class="ob-reason is-left">{{ failReason(f) }}</td><td>#{{ f.inspectionId }}</td>
          </tr>
        </tbody>
      </table>
      <AdminPager v-model="pgF.page" :page-count="pgF.pageCount" />
    </section>

    <section v-if="data && obTab === 'inc'" class="ob-sec">
      <h3>미처리 예외·사고
        <router-link v-if="incidentsLink" :to="incidentsLink" class="ob-link">접수 · 처리 화면 →</router-link>
      </h3>
      <table class="ob-table">
        <thead><tr><th class="no">번호</th><th>중요도</th><th>유형</th><th>제목</th><th>상태</th><th>담당</th><th>접수</th></tr></thead>
        <tbody>
          <tr v-if="!data.incidents.length"><td colspan="7" class="ob-empty">미처리 건이 없습니다.</td></tr>
          <tr v-for="(i, n) in pgI.rows" :key="i.incidentId">
            <td class="no">{{ pgI.no(n) }}</td><td><span class="ob-sev" :class="'s-' + i.severity">{{ SEVERITY[i.severity] }}</span></td>
            <td>{{ INCIDENT_TYPE[i.type] || i.type }}</td><td class="is-left">{{ i.title }}</td>
            <td>{{ INCIDENT_STATUS[i.status] }}</td><td>{{ who(i) }}</td><td>{{ fmt(i.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <AdminPager v-model="pgI.page" :page-count="pgI.pageCount" />
    </section>

    <section v-if="data && obTab === 'dock'" class="ob-sec">
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
import { formatDateTime, INSPECTION_ITEMS } from '@/utils/containerInspectionApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'
import AdminPager from '@/components/admin/AdminPager.vue'
import { usePaged } from '@/utils/usePaged.js'
import { displayAssignee, loadAssignees } from '@/utils/staffLabel.js'

defineProps({
  dark: { type: Boolean, default: false },
  incidentsLink: { type: [String, Object], default: null }, // 접수 · 처리 화면으로 가는 경로 (없으면 링크 숨김)
})

const data = ref(null)
const staff = ref([])
const who = (row) => displayAssignee(row, staff.value)
const loading = ref(false)
const busy = ref(false)
const auto = ref(true)
const message = ref('')
const messageOk = ref(false)
const updatedAt = ref(null)
let timer = null

const s = computed(() => data.value?.summary || {})
// 26.10.28 QA#5 각 표에 번호 + 5건 초과 시 페이징
// 26.10.28 카드 아래 목록 탭
const obTab = ref('unload')
const OB_TABS = [
  { key: 'unload', label: '하역 대기', n: () => data.value?.unloads?.length || 0 },
  { key: 'delay', label: '지연 오더', n: () => data.value?.delayedOrders?.length || 0 },
  { key: 'fail', label: '점검 불합격', n: () => data.value?.failedInspections?.length || 0 },
  { key: 'inc', label: '미처리 예외·사고', n: () => data.value?.incidents?.length || 0 },
  { key: 'dock', label: '도크 현황', n: () => 0 },
]
const pgU = usePaged(() => data.value?.unloads, 5)
const pgD = usePaged(() => data.value?.delayedOrders, 5)
const pgF = usePaged(() => data.value?.failedInspections, 5)
const pgI = usePaged(() => data.value?.incidents, 5)
const updatedText = computed(() => (updatedAt.value ? updatedAt.value.toLocaleTimeString('ko-KR') + ' 기준' : '불러오는 중'))
const fmt = (v) => (v ? formatDateTime(v) : '-')
// 26.10.29 QA: 불합격 사유 = 불합격 항목(예: 도어) + 점검 메모
function failReason(f) {
  let items = f.items
  if (typeof items === 'string') { try { items = JSON.parse(items) } catch { items = null } }
  const bad = items ? INSPECTION_ITEMS.filter((i) => items[i.key] === false).map((i) => i.label) : []
  const parts = []
  if (bad.length) parts.push(bad.join(' · ') + ' 불량')
  if (f.memo) parts.push(String(f.memo))
  return parts.join(' — ') || '-'
}
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
onMounted(() => { load(); schedule(); loadAssignees().then((l) => { staff.value = l }).catch(() => { staff.value = [] }) })
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
.ob-card { --c: #2563eb; --cs: #e8f0fe; background: #fff; border: 1px solid #d5dbe5; border-left: 4px solid var(--c); border-radius: 10px; padding: 12px 14px; display: flex; align-items: center; gap: 12px; }
.ob-card > i { width: 40px; height: 40px; border-radius: 10px; background: var(--cs); color: var(--c); display: flex; align-items: center; justify-content: center; font-size: 19px; flex-shrink: 0; }
.ob-card > div { display: flex; flex-direction: column; gap: 2px; }
.ob-card.c-blue { --c: #2563eb; --cs: #e8f0fe; } .ob-card.c-teal { --c: #0f9f9a; --cs: #e0f5f4; } .ob-card.c-purple { --c: #7c4dce; --cs: #f0e9fb; }
.ob-card.c-amber { --c: #d98a00; --cs: #fff3dc; } .ob-card.c-red { --c: #dc3b3b; --cs: #fde8e8; }
.ob-card b { font-size: 26px; font-variant-numeric: tabular-nums; } .ob-card span { font-size: 12.5px; color: #6b7a90; }
.ob-table th.no, .ob-table td.no { width: 52px; text-align: center; color: #6b7a90; }
.ob-card.warn { border-color: #ff9f1c; } .ob-card.bad { border-color: #e5484d; } .ob-card.bad b { color: #e5484d; }
.ob-tabs { display: flex; gap: 6px; flex-wrap: wrap; } /* 26.10.28 다른 메뉴 탭과 같은 모양 */
.ob-tabs button { display: inline-flex; align-items: center; gap: 6px; border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 8px; padding: 7px 14px; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.ob-tabs button:hover:not(.on) { border-color: #1d4ed8; color: #1d4ed8; }
.ob-tabs button.on { background: #1d4ed8; border-color: #1d4ed8; color: #fff; font-weight: 700; }
.ob-tabs .n { min-width: 20px; height: 20px; padding: 0 6px; border-radius: 10px; background: #eef1f6; color: #0b1d36; font-size: 12px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; }
.ob-tabs button.on .n { background: rgba(255, 255, 255, .55); }
.ob-card[role=button] { cursor: pointer; }
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
.ob-reason { text-align: left; white-space: normal; max-width: 420px; }
</style>
