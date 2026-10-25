<template>
  <!-- 26.10.23 허브 도착 · 인계 · 컨테이너 점검 (/sbot/inspections)
       S-Bot 관리자 · HUB_OP(소속 허브) = 처리 가능, CONTROL = 조회 전용. 실제 범위 확인은 서버가 한다 -->
  <div class="sbi">
    <div class="sbi-head">
      <h2>도착 · 인계 · 점검</h2>
      <span class="sbi-access">{{ access.label }}</span>
      <button class="sbi-btn" :disabled="loading" @click="loadOrders">새로고침</button>
    </div>

    <nav class="sbi-tabs">
      <button v-for="t in TABS" :key="t.key" :class="{ on: tab === t.key }" @click="tab = t.key">
        {{ t.label }}<span v-if="byTab[t.key]" class="sbi-n">{{ byTab[t.key].length }}</span>
      </button>
    </nav>

    <p v-if="message" class="sbi-msg" :class="{ err: messageIsError }">{{ message }}</p>

    <section v-if="tab === 'waiting'" class="sbi-card">
      <p class="sbi-hint">입차 OCR 기록은 시뮬레이터도 만들기 때문에, 오더 차량과 같은 번호판의 입차 기록을 직접 골라 도착을 확정합니다.</p>
      <table class="sbi-table">
        <thead><tr><th>오더</th><th>차량</th><th>컨테이너</th><th>상태</th><th>배차</th><th>입차 OCR 근거</th><th>입고 도크</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!byTab.waiting.length"><td colspan="8" class="sbi-empty">도착 대기 오더가 없습니다.</td></tr>
          <tr v-for="o in byTab.waiting" :key="o.orderId">
            <td>{{ o.orderNo }}</td><td>{{ o.vehicleNo }}</td><td>{{ o.containerNo }}</td>
            <td>{{ ORDER_STATUS_LABEL[o.orderStatus] || o.orderStatus }}</td>
            <td>{{ formatDateTime(o.assignedAt) }}</td>
            <td>
              <select v-if="o.ocrEvidence?.length" v-model="pick[o.orderId]" class="sbi-input">
                <option v-for="e in o.ocrEvidence" :key="e.logId" :value="e.logId">
                  #{{ e.logId }} · {{ formatDateTime(e.createdAt) }} · {{ e.decision }}{{ e.ocrConfidence != null ? ' · ' + e.ocrConfidence + '%' : '' }}
                </option>
              </select>
              <span v-else class="sbi-muted">근거 없음 (배차 이후 같은 번호판 입차 기록 필요)</span>
            </td>
            <td>
              <!-- 26.10.23 선택: 도착 허브의 입고(IN) 도크. 사용 중인 도크는 고를 수 없음 -->
              <select v-model="dockPick[o.orderId]" class="sbi-input" :disabled="!access.canManage">
                <option :value="null">지정 안 함</option>
                <option v-for="d in docks.filter((x) => x.hubId === o.destHubId)" :key="d.dockId" :value="d.dockId" :disabled="d.occupied">
                  {{ d.dockCode }}{{ d.occupied ? " (사용 중)" : "" }}
                </option>
              </select>
            </td>
            <td>
              <button v-if="access.canManage" class="sbi-btn primary" :disabled="busy || !o.canArrive || !pick[o.orderId]"
                      @click="arrive(o)">도착 확정</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="tab === 'inside'" class="sbi-card">
      <table class="sbi-table">
        <thead><tr><th>오더</th><th>차량</th><th>컨테이너</th><th>도착</th><th>씰</th><th>인계</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!byTab.inside.length"><td colspan="7" class="sbi-empty">허브 안에 있는 오더가 없습니다.</td></tr>
          <tr v-for="o in byTab.inside" :key="o.orderId">
            <td>{{ o.orderNo }}</td><td>{{ o.vehicleNo }}</td><td>{{ o.containerNo }}</td>
            <td>{{ formatDateTime(o.arrivedAt) }}</td>
            <td>{{ o.sealRegistered ? '등록됨' : '없음 (인계 보류)' }}</td>
            <td>
              <div v-if="access.canManage && o.canHandover" class="sbi-handover">
                <input v-model.trim="hand(o).sealNo" class="sbi-input" placeholder="현장 씰 번호" maxlength="30" />
                <input v-model.trim="hand(o).receiverName" class="sbi-input" placeholder="인수자" maxlength="50" />
                <label><input v-model="hand(o).containerNoConfirmed" type="checkbox" /> 컨테이너 번호 확인</label>
                <label><input v-model="hand(o).damageFree" type="checkbox" /> 손상 없음</label>
              </div>
              <span v-else class="sbi-muted">-</span>
            </td>
            <td class="sbi-actions">
              <button v-if="access.canManage && o.canHandover" class="sbi-btn primary" :disabled="busy" @click="handover(o)">인계 확정</button>
              <button v-if="canUnload(o)" class="sbi-btn" :disabled="busy" @click="unload(o)">하역 완료</button>
              <button class="sbi-btn" @click="openInspections(o)">점검</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="tab === 'done'" class="sbi-card">
      <table class="sbi-table">
        <thead><tr><th>오더</th><th>차량</th><th>컨테이너</th><th>도착</th><th>인계</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!byTab.done.length"><td colspan="6" class="sbi-empty">인계 완료 오더가 없습니다.</td></tr>
          <tr v-for="o in byTab.done" :key="o.orderId">
            <td>{{ o.orderNo }}</td><td>{{ o.vehicleNo }}</td><td>{{ o.containerNo }}</td>
            <td>{{ formatDateTime(o.arrivedAt) }}</td><td>{{ formatDateTime(o.handedOverAt) }}</td>
            <td class="sbi-actions">
              <button v-if="canUnload(o)" class="sbi-btn" :disabled="busy" @click="unload(o)">하역 완료</button>
              <button class="sbi-btn" @click="openInspections(o)">점검 이력</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="tab === 'board'" class="sbi-card"><OpsBoard dark /></section>
    <section v-if="tab === 'incidents'" class="sbi-card"><IncidentPanel dark /></section>

    <section v-if="tab === 'report'" class="sbi-card">
      <DailyReportPanel dark />
    </section>

    <section v-if="tab === 'kpi'" class="sbi-card"><KpiTrendPanel dark /></section>
    <section v-if="tab === 'reservations'" class="sbi-card"><GateReservationPanel dark /></section>

    <ContainerInspectionPanel v-if="tab === 'inspections'" dark :allow-create="access.canManage" :container-no="inspectionContainer" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import ContainerInspectionPanel from '@/components/common/ContainerInspectionPanel.vue'
import DailyReportPanel from '@/components/common/DailyReportPanel.vue'
import OpsBoard from '@/components/common/OpsBoard.vue'
import IncidentPanel from '@/components/common/IncidentPanel.vue'
import KpiTrendPanel from '@/components/common/KpiTrendPanel.vue'
import GateReservationPanel from '@/components/common/GateReservationPanel.vue'
import { hubAccess } from '@/components/amr/amrAccess.js'
import { authState } from '@/auth/authState.js'
import { ORDER_STATUS_LABEL, hubCandidates, confirmArrival, confirmHandover, inboundDocks, completeUnload } from '@/utils/hubArrivalApi.js'
import { formatDateTime } from '@/utils/containerInspectionApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const TABS = [
  { key: 'waiting', label: '도착 대기' },
  { key: 'inside', label: '허브 내' },
  { key: 'done', label: '인계 완료' },
  { key: 'inspections', label: '점검 기록' },
  { key: 'board', label: '현황판' }, // 26.10.24
  { key: 'incidents', label: '예외·사고' }, // 26.10.24
  { key: 'report', label: '일일 보고서' }, // 26.10.24
  { key: 'kpi', label: 'KPI 추이' }, // 26.10.24
  { key: 'reservations', label: '게이트 예약' }, // 26.10.24
]
const WAITING = ['ASSIGNED', 'LOADED', 'GATE_OUT', 'IN_TRANSIT']

const access = computed(() => hubAccess(authState.user))
const tab = ref('waiting')
const orders = ref([])
const loading = ref(false)
const busy = ref(false)
const message = ref('')
const messageIsError = ref(false)
const pick = reactive({})
const dockPick = reactive({})
const docks = ref([])
const handForms = reactive({})
const inspectionContainer = ref('')

const byTab = computed(() => ({
  waiting: orders.value.filter((o) => WAITING.includes(o.orderStatus)),
  inside: orders.value.filter((o) => o.orderStatus === 'ARRIVED'),
  done: orders.value.filter((o) => o.orderStatus === 'HANDED_OVER'),
}))

function note(text, isError = false) {
  message.value = text
  messageIsError.value = isError
}

// 인계 입력값은 오더를 불러올 때 만들어 둔다 (화면 그리는 중에 상태를 만들지 않게)
const hand = (o) => handForms[o.orderId]

async function loadOrders() {
  loading.value = true
  try {
    const [list, dockList] = await Promise.all([hubCandidates({ includeHandedOver: true }), inboundDocks()])
    orders.value = list
    docks.value = dockList
    orders.value.forEach((o) => {
      if (o.ocrEvidence?.length && !pick[o.orderId]) pick[o.orderId] = o.ocrEvidence[0].logId
      if (!(o.orderId in dockPick)) dockPick[o.orderId] = null
      if (o.orderStatus === 'ARRIVED' && !handForms[o.orderId]) {
        handForms[o.orderId] = { sealNo: '', receiverName: '', containerNoConfirmed: false, damageFree: true }
      }
    })
  } catch (err) {
    orders.value = []
    note(friendlyError(err, '허브 오더를 불러오지 못했습니다.'), true)
  } finally {
    loading.value = false
  }
}

async function arrive(o) {
  if (!confirm(`${o.vehicleNo} (${o.orderNo}) 허브 도착을 확정할까요?\n입차 OCR 기록 #${pick[o.orderId]}`)) return
  busy.value = true
  try {
    await confirmArrival({ orderId: o.orderId, hubVehicleLogId: pick[o.orderId], dockId: dockPick[o.orderId] || null })
    note(`${o.orderNo} 도착을 확정했습니다.`)
    await loadOrders()
  } catch (err) {
    note(friendlyError(err, '도착을 확정하지 못했습니다.'), true)
  } finally {
    busy.value = false
  }
}

async function handover(o) {
  const f = hand(o)
  if (!f.containerNoConfirmed) return note('컨테이너 번호를 확인한 뒤 체크하세요.', true)
  if (!f.sealNo || !f.receiverName) return note('씰 번호와 인수자를 입력하세요.', true)
  busy.value = true
  try {
    await confirmHandover({ orderId: o.orderId, ...f })
    note(`${o.orderNo} 인계를 확정했습니다.`)
    await loadOrders() // 인계 완료 오더는 '허브 내' 목록에서 빠지므로 입력값은 그대로 둬도 된다
  } catch (err) {
    note(friendlyError(err, '인계를 확정하지 못했습니다.'), true)
  } finally {
    busy.value = false
  }
}

// 26.10.24 하역 완료: 입고 기록이 하역 전(ARRIVED · UNLOADING)일 때만. 도착 때 쓴 도크가 다시 비워진다
const canUnload = (o) => access.value.canManage && o.receiptId && ['ARRIVED', 'UNLOADING'].includes(o.receiptStatus)

async function unload(o) {
  if (!confirm(`${o.containerNo} (${o.orderNo}) 하역을 완료 처리할까요?${o.dockId ? '\n사용 중이던 도크가 비워집니다.' : ''}`)) return
  busy.value = true
  try {
    await completeUnload(o.receiptId)
    note(`${o.orderNo} 하역을 완료했습니다.`)
    await loadOrders()
  } catch (err) {
    note(friendlyError(err, '하역 완료를 처리하지 못했습니다.'), true)
  } finally {
    busy.value = false
  }
}

function openInspections(o) {
  inspectionContainer.value = o.containerNo
  tab.value = 'inspections'
}

onMounted(loadOrders)
</script>

<style scoped>
.sbi { display: flex; flex-direction: column; gap: 12px; }
.sbi-head { display: flex; align-items: center; gap: 12px; }
.sbi-head h2 { margin: 0; font-size: 19px; }
.sbi-access { color: #9fb0c6; font-size: 13px; }
.sbi-head .sbi-btn { margin-left: auto; }
.sbi-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.sbi-tabs button { background: #0f2a4a; border: 1px solid #1d3b60; color: #c9d8ea; border-radius: 8px; padding: 7px 14px; cursor: pointer; }
.sbi-tabs button.on { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; font-weight: 700; }
.sbi-n { margin-left: 6px; font-size: 12px; opacity: .85; }
.sbi-card { background: #0f2a4a; border: 1px solid #1d3b60; border-radius: 12px; padding: 12px 14px; overflow-x: auto; }
.sbi-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.sbi-table th, .sbi-table td { padding: 8px; border-bottom: 1px solid #1d3b60; text-align: left; vertical-align: middle; white-space: nowrap; }
.sbi-table th { color: #9fb0c6; font-weight: 600; }
.sbi-empty { text-align: center !important; color: #9fb0c6; }
.sbi-muted { color: #9fb0c6; font-size: 12.5px; }
.sbi-hint { margin: 0 0 8px; color: #9fb0c6; font-size: 12.5px; }
.sbi-input { background: #0b1d36; border: 1px solid #2a4d76; color: #e6eff7; border-radius: 6px; padding: 5px 8px; font-size: 13px; }
.sbi-btn { background: #12304f; border: 1px solid #2a4d76; color: #e6eff7; border-radius: 6px; padding: 6px 12px; font-size: 13px; cursor: pointer; }
.sbi-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; font-weight: 700; }
.sbi-btn:disabled { opacity: .5; cursor: default; }
.sbi-handover { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.sbi-handover label { font-size: 12.5px; color: #c9d8ea; display: flex; gap: 4px; align-items: center; }
.sbi-actions { display: flex; gap: 6px; }
.sbi-msg { margin: 0; color: #86efac; font-size: 13px; white-space: pre-line; }
.sbi-msg.err { color: #fca5a5; }
</style>
