<template>
  <!-- 26.10.23 허브 도착 · 인계 · 컨테이너 점검 (/sbot/inspections)
       S-Bot 관리자 · HUB_OP(소속 허브) = 처리 가능, CONTROL = 조회 전용. 실제 범위 확인은 서버가 한다 -->
  <div class="sbi">
    <div class="sbi-head">
      <h2>허브 입고 관리</h2>
      <span class="sbi-access">{{ access.label }}</span>
      <button class="sbi-btn" :disabled="loading" @click="refresh">{{ loading ? "불러오는 중..." : "새로고침" }}</button><!-- 26.10.28 지금 탭(현황판 · 점검 기록 포함)까지 다시 불러오고 완료 시각 표시 -->
    </div>

    <!-- 26.10.28 탭과 내용을 하나의 흰 상자 안에 (지역센터 인수 현황과 같은 모양) -->
    <div class="tab-box">
    <nav class="sbi-tabs">
      <button v-for="t in TABS" :key="t.key" :class="{ on: tab === t.key }" @click="tab = t.key">
        {{ t.label }}<span v-if="byTab[t.key]" class="sbi-n">{{ byTab[t.key].length }}</span>
      </button>
    </nav>

    <p v-if="message" class="sbi-msg" :class="{ err: messageIsError }">{{ message }}</p>

    <section v-if="tab === 'waiting'" class="sbi-card">
      <p class="sbi-hint">입차 OCR 기록은 시뮬레이터도 만들기 때문에, 오더 차량과 같은 번호판의 입차 기록을 직접 골라 도착을 확정합니다.</p>
      <table class="sbi-table">
        <thead><tr><th class="no">번호</th><th>오더</th><th>차량</th><th>컨테이너</th><th>상태</th><th>배차</th><th>입차 OCR 근거</th><th>입고 도크</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!byTab.waiting.length"><td colspan="9" class="sbi-empty">도착 대기 오더가 없습니다.</td></tr>
          <tr v-for="(o, i) in pgW.rows" :key="o.orderId">
            <td class="no">{{ pgW.no(i) }}</td><td>{{ o.orderNo }}</td><td>{{ o.vehicleNo }}</td><td>{{ o.containerNo }}</td>
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
      <AdminPager v-model="pgW.page" :page-count="pgW.pageCount" />
    </section>

    <section v-if="tab === 'inside'" class="sbi-card">
      <table class="sbi-table">
        <thead><tr><th class="no">번호</th><th>오더</th><th>차량</th><th>컨테이너</th><th>도착</th><th>씰</th><th>인계</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!byTab.inside.length"><td colspan="8" class="sbi-empty">허브 안에 있는 오더가 없습니다.</td></tr>
          <tr v-for="(o, i) in pgIn.rows" :key="o.orderId">
            <td class="no">{{ pgIn.no(i) }}</td><td>{{ o.orderNo }}</td><td>{{ o.vehicleNo }}</td><td>{{ o.containerNo }}</td>
            <td>{{ formatDateTime(o.arrivedAt) }}</td>
            <td>{{ o.sealRegistered ? '등록됨' : '없음 (인계 보류)' }}</td>
            <td>
              <div v-if="access.canManage && o.canHandover" class="sbi-handover">
                <!-- 26.10.28 입력칸 두 개는 윗줄, 확인 체크 두 개는 그 아래 줄에 칸을 맞춰 배치 -->
                <input v-model.trim="hand(o).sealNo" class="sbi-input" placeholder="현장 씰 번호" maxlength="30" />
                <input v-model.trim="hand(o).receiverName" class="sbi-input" placeholder="인수자" maxlength="50" />
                <label class="sbi-chk"><input v-model="hand(o).containerNoConfirmed" type="checkbox" /> 컨테이너 번호 확인</label>
                <label class="sbi-chk"><input v-model="hand(o).damageFree" type="checkbox" /> 손상 없음</label>
              </div>
              <span v-else class="sbi-muted">-</span>
            </td>
            <td class="sbi-actions">
              <button v-if="access.canManage && o.canHandover" class="sbi-btn primary" :disabled="busy" @click="handover(o)">인계 확정</button>
              <button v-if="canUnload(o)" class="sbi-btn unload" :disabled="busy" @click="unload(o)">하역 완료</button>
              <button class="sbi-btn insp" @click="openInspections(o)">점검</button>
            </td>
          </tr>
        </tbody>
      </table>
      <AdminPager v-model="pgIn.page" :page-count="pgIn.pageCount" />
    </section>

    <section v-if="tab === 'done'" class="sbi-card">
      <table class="sbi-table">
        <thead><tr><th class="no">번호</th><th>오더</th><th>차량</th><th>컨테이너</th><th>도착</th><th>인계</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!byTab.done.length"><td colspan="7" class="sbi-empty">인계 완료 오더가 없습니다.</td></tr>
          <tr v-for="(o, i) in pgDn.rows" :key="o.orderId">
            <td class="no">{{ pgDn.no(i) }}</td><td>{{ o.orderNo }}</td><td>{{ o.vehicleNo }}</td><td>{{ o.containerNo }}</td>
            <td>{{ formatDateTime(o.arrivedAt) }}</td><td>{{ formatDateTime(o.handedOverAt) }}</td>
            <td class="sbi-actions">
              <button v-if="canUnload(o)" class="sbi-btn unload" :disabled="busy" @click="unload(o)">하역 완료</button>
              <button class="sbi-btn insp" @click="openInspections(o)">점검 이력</button>
            </td>
          </tr>
        </tbody>
      </table>
      <AdminPager v-model="pgDn.page" :page-count="pgDn.pageCount" />
    </section>

    <section v-if="tab === 'board'" class="sbi-card"><OpsBoard :key="'b' + refreshKey" /></section>
    <!-- 26.10.28 QA#6 예외·사고 / 일일 보고서 / KPI 추이 / 게이트 예약은 운영 센터와 중복이라 삭제 (운영 센터에서 사용) -->
    <p v-if="tab === 'board'" class="sbi-hint">예외·사고 · 일일 보고서 · KPI 추이 · 게이트 예약은 <router-link to="/admin/ops-center">운영 관리</router-link>에서 봅니다.</p>

    <ContainerInspectionPanel v-if="tab === 'inspections'" :key="'i' + refreshKey" :allow-create="access.canManage" :container-no="inspectionContainer" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import ContainerInspectionPanel from '@/components/common/ContainerInspectionPanel.vue'
import OpsBoard from '@/components/common/OpsBoard.vue'
import AdminPager from '@/components/admin/AdminPager.vue'
import { usePaged } from '@/utils/usePaged.js'
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
  { key: 'board', label: '현황판' }, // 26.10.24 (현황판 표는 5건씩)
  // 26.10.28 QA#6 예외·사고 · 일일 보고서 · KPI 추이 · 게이트 예약 탭 삭제 → 운영 센터(/admin/ops-center)와 중복
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
// 26.10.28 QA#6 번호 + 10건 페이징
const pgW = usePaged(() => byTab.value.waiting, 10)
const pgIn = usePaged(() => byTab.value.inside, 10)
const pgDn = usePaged(() => byTab.value.done, 10)

function note(text, isError = false) {
  message.value = text
  messageIsError.value = isError
}

// 인계 입력값은 오더를 불러올 때 만들어 둔다 (화면 그리는 중에 상태를 만들지 않게)
const hand = (o) => handForms[o.orderId]

// 26.10.28 새로고침: 오더 목록 + 지금 보고 있는 탭(현황판 · 점검 기록)까지 다시 불러오고, 끝난 시각을 알려 준다
const refreshKey = ref(0)
async function refresh() {
  refreshKey.value++
  await loadOrders()
  if (!messageIsError.value) note('새로고침했습니다 · ' + new Date().toLocaleTimeString('ko-KR'))
}

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
/* 26.10.28 QA#6 어두운 테마 → 다른 관리자 메뉴와 같은 밝은 색으로 통일 */
.sbi { display: flex; flex-direction: column; gap: 12px; color: #112847; }
.sbi-head { display: flex; align-items: center; gap: 12px; }
.sbi-head h2 { margin: 0; font-size: 22px; font-weight: 800; color: #112847; }
.sbi-access { color: #64748b; font-size: 13px; }
.sbi-head .sbi-btn { margin-left: auto; }
.sbi-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
/* 26.10.28 탭 모양 통일: 지역센터 인수 현황 탭(.crp-tabs)과 같은 모양 (회색 버튼 + 선택 탭은 파란 채움) */
.sbi-tabs button { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 8px; padding: 7px 14px; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.sbi-tabs button:hover:not(.on) { border-color: #1d4ed8; color: #1d4ed8; }
.sbi-tabs button.on { background: #1d4ed8; border-color: #1d4ed8; color: #fff; font-weight: 700; }
.sbi-n { margin-left: 6px; font-size: 12px; opacity: .85; }
.sbi-card { color: #112847; background: #fff; border: 1px solid #e3e8ef; border-radius: 12px; padding: 12px 14px; overflow-x: auto; box-shadow: 0 1px 3px rgba(17, 40, 71, .06); }
.sbi-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.sbi-table th, .sbi-table td { padding: 9px 8px; border-bottom: 1px solid #e3e8ef; text-align: left; vertical-align: middle; white-space: nowrap; }
.sbi-table th { color: #64748b; font-weight: 700; background: #eef1f6; font-size: 12px; }
.sbi-table .no { width: 52px; text-align: center; color: #64748b; }
.sbi-empty { text-align: center !important; color: #64748b; }
.sbi-muted { color: #64748b; font-size: 12.5px; }
.tab-box .sbi-tabs { margin-bottom: 14px; } /* 26.10.28 탭과 아래 내용 사이 간격 */
.sbi-hint { margin: 0 0 8px; padding-left: 4px; /* 26.10.28 첫 글자 잘림 방지 */ color: #64748b; font-size: 12.5px; }
.sbi-hint a { color: #c9972f; font-weight: 700; }
.sbi-input { background: #fff; border: 1px solid #c9d3e0; color: #112847; border-radius: 6px; padding: 5px 8px; font-size: 13px; }
.sbi-btn { background: #fff; border: 1px solid #c9d3e0; color: #112847; border-radius: 6px; padding: 6px 12px; font-size: 13px; font-weight: 600; cursor: pointer; transition: background .15s, border-color .15s, color .15s; }
/* 26.10.28 버튼마다 색 + 마우스를 올리면 색이 바뀌게 */
.sbi-btn:hover:not(:disabled) { background: #f5f8fc; border-color: #8fa3bf; }
.sbi-btn.primary { background: #c9972f; border-color: #c9972f; color: #fff; font-weight: 700; }
.sbi-btn.primary:hover:not(:disabled) { background: #dcb466; border-color: #dcb466; } /* 26.10.28 오버 시 연하게 */
.sbi-btn.unload { background: #2563eb; border-color: #2563eb; color: #fff; font-weight: 700; }
.sbi-btn.unload:hover:not(:disabled) { background: #7ea4f6; border-color: #7ea4f6; } /* 파란 버튼은 더 연하게 */
.sbi-btn.insp { background: #e6f4f1; border-color: #0f8f7a; color: #0b6b5c; font-weight: 700; }
.sbi-btn.insp:hover:not(:disabled) { background: #f2faf8; border-color: #5fbfae; }
.sbi-btn:disabled { opacity: .5; cursor: default; }
.sbi-handover { display: grid; grid-template-columns: repeat(2, minmax(130px, 1fr)); gap: 6px 10px; align-items: center; }
.sbi-handover .sbi-chk { font-size: 12.5px; color: #334155; display: flex; gap: 6px; align-items: center; margin: 0; white-space: nowrap; }
.sbi-actions { display: flex; gap: 6px; }
.sbi-msg { margin: 0; color: #0f9f6e; font-size: 13px; white-space: pre-line; }
.sbi-msg.err { color: #dc3b3b; }
</style>
