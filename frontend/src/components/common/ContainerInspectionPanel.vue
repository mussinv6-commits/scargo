<template>
  <!-- 26.10.23 컨테이너 점검표 공용 화면 (기사 · 운송사 · S Cargo 관리자 · 허브 화면이 같이 쓴다)
       보이는 범위 · 버튼 사용 가능 여부는 서버 응답(canEdit · canCancel · canReinspect)을 따른다 -->
  <div class="cip" :class="{ 'is-dark': dark }">
    <section v-if="allowCreate" class="cip-card">
      <h3 class="cip-h">새 점검 작성</h3>
      <form class="cip-row" @submit.prevent="create">
        <label>컨테이너 번호
          <input v-model.trim="form.containerNo" class="cip-input" maxlength="11" placeholder="예: SCGU1234565" required />
        </label>
        <label>오더 번호(선택)
          <input v-model.number="form.orderId" class="cip-input" type="number" min="1" placeholder="비우면 자동 연결" />
        </label>
        <label class="cip-grow">메모
          <input v-model="form.memo" class="cip-input" maxlength="1000" placeholder="특이사항" />
        </label>
        <button type="submit" class="cip-btn primary" :disabled="busy">작성 시작</button>
      </form>
      <InspectionItemsInput v-model="form.items" :dark="dark" />
      <p class="cip-hint">진행 중인 오더가 정확히 1건이면 자동으로 연결됩니다. 7개 항목을 모두 입력해야 확정할 수 있습니다.</p>
    </section>

    <section class="cip-card">
      <div class="cip-toolbar">
        <h3 class="cip-h">점검 기록 <span class="cip-count">{{ rows.length }}건</span></h3>
        <form class="cip-row" @submit.prevent="load">
          <input v-model.trim="filter.containerNo" class="cip-input" placeholder="컨테이너 번호" maxlength="11" />
          <select v-model="filter.status" class="cip-input">
            <option value="">전체 상태</option>
            <option v-for="(label, key) in RECORD_LABEL" :key="key" :value="key">{{ label }}</option>
          </select>
          <button type="submit" class="cip-btn">조회</button>
          <button type="button" class="cip-btn" :disabled="!rows.length" @click="exportRows">Excel 내보내기</button>
        </form>
      </div>
      <p v-if="message" class="cip-msg" :class="{ err: messageIsError }">{{ message }}</p>
      <div class="cip-scroll">
        <table class="cip-table">
          <thead>
            <tr>
              <th>번호</th><th>컨테이너</th><th>오더</th><th>상태</th><th>판정</th><th>재점검</th><th>작성</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="7" class="cip-empty">불러오는 중...</td></tr>
            <tr v-else-if="!rows.length"><td colspan="7" class="cip-empty">점검 기록이 없습니다.</td></tr>
            <tr v-for="r in rows" :key="r.inspectionId" :class="{ sel: selected?.inspectionId === r.inspectionId }" @click="select(r)">
              <td>#{{ r.inspectionId }}</td>
              <td>{{ r.containerNo || '-' }}</td>
              <td>{{ r.orderNo || '-' }}</td>
              <td><span class="cip-badge" :class="'r-' + r.recordStatus">{{ RECORD_LABEL[r.recordStatus] }}</span></td>
              <td><span :class="'j-' + r.overallStatus">{{ OVERALL_LABEL[r.overallStatus] }}</span></td>
              <td>{{ r.previousInspectionId ? '#' + r.previousInspectionId + ' 이후' : '-' }}</td>
              <td>{{ formatDateTime(r.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="selected" class="cip-card">
      <div class="cip-toolbar">
        <h3 class="cip-h">#{{ selected.inspectionId }} · {{ selected.containerNo || '-' }}
          <span class="cip-badge" :class="'r-' + selected.recordStatus">{{ RECORD_LABEL[selected.recordStatus] }}</span>
        </h3>
        <div class="cip-row">
          <button v-if="selected.canEdit" class="cip-btn" :disabled="busy" @click="save">저장</button>
          <button v-if="selected.canEdit" class="cip-btn primary" :disabled="busy" @click="finalize">확정</button>
          <button v-if="selected.canReinspect" class="cip-btn" :disabled="busy" @click="reinspect">재점검</button>
          <button v-if="selected.canCancel" class="cip-btn danger" :disabled="busy" @click="cancel">
            {{ selected.recordStatus === 'FINALIZED' ? '확정본 취소(관리자)' : '취소' }}
          </button>
          <button class="cip-btn" @click="loadHistory">이력</button>
        </div>
      </div>
      <dl class="cip-meta">
        <div><dt>오더</dt><dd>{{ selected.orderNo || '연결 없음' }}</dd></div>
        <div><dt>작성</dt><dd>{{ formatDateTime(selected.createdAt) }}</dd></div>
        <div v-if="selected.finalizedAt"><dt>확정</dt><dd>{{ formatDateTime(selected.finalizedAt) }}</dd></div>
        <div v-if="selected.cancelledAt"><dt>취소</dt><dd>{{ formatDateTime(selected.cancelledAt) }} · {{ selected.cancelReason }}</dd></div>
        <div v-if="selected.previousInspectionId"><dt>이전 기록</dt><dd>#{{ selected.previousInspectionId }}</dd></div>
      </dl>
      <InspectionItemsInput v-model="edit.items" :disabled="!selected.canEdit" :dark="dark" />
      <label class="cip-memo">메모
        <textarea v-model="edit.memo" class="cip-input" rows="2" maxlength="1000" :disabled="!selected.canEdit"></textarea>
      </label>
      <InspectionPhotos :key="selected.inspectionId" :inspection-id="selected.inspectionId" :can-edit="selected.canEdit" :dark="dark" />
      <p v-if="!selected.canEdit && selected.recordStatus === 'DRAFT'" class="cip-hint">이 계정은 이 기록을 조회만 할 수 있습니다.</p>

      <div v-if="history" class="cip-history">
        <h4>변경 이력 (재점검 체인 전체)</h4>
        <ol>
          <li v-for="h in history" :key="h.auditId">
            {{ formatDateTime(h.createdAt) }} · #{{ h.inspectionId }} · {{ ACTION_LABEL[h.action] || h.action }}
            · {{ h.actorUserId || '-' }}<template v-if="h.reason"> · 사유: {{ h.reason }}</template>
          </li>
        </ol>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import InspectionItemsInput from '@/components/common/InspectionItemsInput.vue'
import InspectionPhotos from '@/components/common/InspectionPhotos.vue'
import { exportExcel } from '@/utils/exportExcel.js'
import {
  ACTION_LABEL, OVERALL_LABEL, RECORD_LABEL, formatDateTime,
  listInspections, createInspection, updateInspection, finalizeInspection,
  cancelInspection, reinspectInspection, inspectionHistory,
} from '@/utils/containerInspectionApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const props = defineProps({
  allowCreate: { type: Boolean, default: true },
  dark: { type: Boolean, default: false },
  containerNo: { type: String, default: '' }, // 바깥에서 컨테이너를 정해 줄 때 (허브 화면)
})

const rows = ref([])
const loading = ref(false)
const busy = ref(false)
const message = ref('')
const messageIsError = ref(false)
const selected = ref(null)
const history = ref(null)
const edit = reactive({ items: {}, memo: '' })
const form = reactive({ containerNo: '', orderId: null, memo: '', items: {} })
const filter = reactive({ containerNo: props.containerNo || '', status: '' })

function note(text, isError = false) {
  message.value = text
  messageIsError.value = isError
}

async function load() {
  loading.value = true
  try {
    rows.value = await listInspections({ containerNo: filter.containerNo || undefined, status: filter.status || undefined })
    if (selected.value) {
      const fresh = rows.value.find((r) => r.inspectionId === selected.value.inspectionId)
      fresh ? select(fresh) : (selected.value = null)
    }
  } catch (err) {
    rows.value = []
    note(friendlyError(err, '점검 기록을 불러오지 못했습니다.'), true)
  } finally {
    loading.value = false
  }
}

// 26.10.24 현재 목록을 Excel(CSV)로 내려받기
function exportRows() {
  exportExcel('컨테이너점검표', [
    { key: 'inspectionId', label: '번호' },
    { key: 'containerNo', label: '컨테이너' },
    { key: 'orderNo', label: '오더' },
    { key: 'recordStatus', label: '상태', formatter: (v) => RECORD_LABEL[v] || v },
    { key: 'overallStatus', label: '판정', formatter: (v) => OVERALL_LABEL[v] || v },
    { key: 'previousInspectionId', label: '이전 기록' },
    { key: 'createdAt', label: '작성', formatter: (v) => formatDateTime(v) },
    { key: 'finalizedAt', label: '확정', formatter: (v) => (v ? formatDateTime(v) : '') },
  ], rows.value)
}

function select(r) {
  selected.value = r
  edit.items = { ...(r.items || {}) }
  edit.memo = r.memo || ''
  history.value = null
}

async function run(action, okText) {
  busy.value = true
  try {
    const result = await action()
    note(okText)
    await load()
    if (result?.inspectionId) {
      const row = rows.value.find((r) => r.inspectionId === result.inspectionId)
      select(row || result)
    }
  } catch (err) {
    note(friendlyError(err, '처리하지 못했습니다.'), true)
  } finally {
    busy.value = false
  }
}

function create() {
  run(() => createInspection(form), '점검 기록을 만들었습니다.').then(() => {
    if (!messageIsError.value) Object.assign(form, { containerNo: '', orderId: null, memo: '', items: {} })
  })
}
const save = () => run(() => updateInspection(selected.value.inspectionId, edit), '저장했습니다.')
async function finalize() {
  if (!confirm('확정하면 내용을 바꿀 수 없습니다. 확정할까요?')) return
  await run(async () => {
    await updateInspection(selected.value.inspectionId, edit)
    return finalizeInspection(selected.value.inspectionId)
  }, '확정했습니다.')
}
function cancel() {
  const reason = prompt('취소 사유를 입력하세요 (필수)')
  if (!reason || !reason.trim()) return
  run(() => cancelInspection(selected.value.inspectionId, reason.trim()), '취소했습니다.')
}
function reinspect() {
  const reason = prompt('재점검 사유 (선택)') ?? null
  run(() => reinspectInspection(selected.value.inspectionId, { reason }), '재점검 기록을 만들었습니다. 항목을 입력하세요.')
}
async function loadHistory() {
  try {
    history.value = await inspectionHistory(selected.value.inspectionId)
  } catch (err) {
    note(friendlyError(err, '이력을 불러오지 못했습니다.'), true)
  }
}

// 26.10.23 바깥 화면(기사 운송 오더 등)에서 작성 폼을 채워 준다
function startFor({ containerNo, orderId }) {
  Object.assign(form, { containerNo: containerNo || '', orderId: orderId || null, memo: '', items: {} })
  filter.containerNo = containerNo || ''
  load()
}

watch(() => props.containerNo, (v) => { filter.containerNo = v || ''; load() })
onMounted(load)
defineExpose({ load, startFor })
</script>

<style scoped>
.cip { display: flex; flex-direction: column; gap: 14px; color: #1f2937; }
.cip-card { background: #fff; border: 1px solid #e3e8ef; border-radius: 12px; padding: 14px 16px; }
.cip-h { margin: 0 0 10px; font-size: 15.5px; font-weight: 700; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.cip-count { font-size: 12.5px; font-weight: 600; color: #6b7280; }
.cip-toolbar { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; flex-wrap: wrap; }
.cip-row { display: flex; gap: 8px; align-items: flex-end; flex-wrap: wrap; margin-bottom: 10px; }
.cip-row label, .cip-memo { display: flex; flex-direction: column; gap: 4px; font-size: 12.5px; font-weight: 600; color: #6b7280; }
.cip-grow { flex: 1; min-width: 160px; }
.cip-memo { margin-top: 10px; }
.cip-input { border: 1px solid #cfd6df; border-radius: 6px; padding: 6px 9px; font-size: 13.5px; min-width: 120px; background: #fff; color: inherit; }
.cip-btn { border: 1px solid #cfd6df; background: #f8fafc; color: #1f2937; border-radius: 6px; padding: 6px 12px; font-size: 13px; cursor: pointer; }
.cip-btn.primary { background: #1d4ed8; border-color: #1d4ed8; color: #fff; }
.cip-btn.danger { background: #fff; border-color: #dc2626; color: #dc2626; }
.cip-btn:disabled { opacity: .6; cursor: default; }
.cip-hint { margin: 8px 0 0; font-size: 12.5px; color: #6b7280; }
.cip-msg { margin: 0 0 8px; font-size: 13px; color: #166534; white-space: pre-line; }
.cip-msg.err { color: #c81e2c; }
.cip-scroll { overflow-x: auto; }
.cip-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.cip-table th, .cip-table td { padding: 7px 8px; border-bottom: 1px solid #eef1f5; text-align: left; white-space: nowrap; }
.cip-table tbody tr { cursor: pointer; }
.cip-table tbody tr:hover, .cip-table tbody tr.sel { background: #eef4ff; }
.cip-empty { text-align: center !important; color: #6b7280; cursor: default; }
.cip-badge { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 12px; background: #e5e7eb; color: #374151; }
.r-DRAFT { background: #fef3c7; color: #92400e; } .r-FINALIZED { background: #dcfce7; color: #166534; } .r-CANCELLED { background: #f3f4f6; color: #6b7280; }
.j-PASS { color: #16a34a; font-weight: 600; } .j-FAIL { color: #dc2626; font-weight: 600; } .j-PENDING { color: #b45309; }
.cip-meta { display: flex; flex-wrap: wrap; gap: 6px 18px; margin: 0 0 10px; font-size: 13px; }
.cip-meta div { display: flex; gap: 6px; } .cip-meta dt { color: #6b7280; font-weight: 600; } .cip-meta dd { margin: 0; }
.cip-history { margin-top: 12px; font-size: 13px; }
.cip-history h4 { font-size: 13.5px; margin: 0 0 6px; }
.cip-history ol { margin: 0; padding-left: 20px; }
.is-dark { color: #e6eff7; }
.is-dark .cip-card { background: #0f2a4a; border-color: #1d3b60; }
.is-dark .cip-input { background: #0b1d36; border-color: #2a4d76; }
.is-dark .cip-btn { background: #12304f; border-color: #2a4d76; color: #e6eff7; }
.is-dark .cip-btn.primary { background: #ff9f1c; border-color: #ff9f1c; color: #0b1d36; }
.is-dark .cip-table th, .is-dark .cip-table td { border-color: #1d3b60; }
.is-dark .cip-table tbody tr:hover, .is-dark .cip-table tbody tr.sel { background: #163a62; }
.is-dark .cip-count, .is-dark .cip-hint, .is-dark .cip-row label, .is-dark .cip-memo, .is-dark .cip-meta dt { color: #9fb0c6; }
.is-dark .cip-msg { color: #86efac; } .is-dark .cip-msg.err { color: #fca5a5; }
</style>
