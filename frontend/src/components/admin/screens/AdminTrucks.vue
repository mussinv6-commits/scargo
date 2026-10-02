<template>
  <div>
    <AdminPageHeader title="차량 관리" description="전체 화물차 정보를 조회·등록·수정·삭제하고, 신규 차량의 진입 허가를 심사합니다." />

    <CrudTable
      title="차량"
      id-key="vehicleNo"
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :form-fields="formFields"
      :page-size="5"
      show-index
      index-label="번호"
      :on-create="handleCreate"
      :on-update="handleUpdate"
      :on-delete="handleDelete"
    />

    <!-- 관리자는 진입 허가 심사 담당 (기사 배정은 사업자 화면) -->
    <div class="crud-table-wrap">
      <div class="crud-toolbar">
        <div class="crud-toolbar-left">
          <h2 class="crud-title">진입 허가 심사</h2>
          <span class="crud-count">심사 대기 {{ pendingCount }}대 · 신규 등록 차량은 진입 허가 심사가 필요합니다</span>
        </div>
        <div class="crud-toolbar-right">
          <select v-model="approvalFilter" class="crud-input" style="width:auto;">
            <option value="ALL">전체</option>
            <option value="PENDING">심사대기</option>
            <option value="APPROVED">허가</option>
            <option value="REJECTED">불허</option>
          </select>
        </div>
      </div>

      <div class="crud-table-scroll">
        <table class="crud-table">
          <thead>
            <tr>
              <th class="is-center" style="width:64px;">번호</th>
              <th>차량번호</th>
              <th>소속업체</th>
              <th class="is-center">배정된 기사</th>
              <th class="is-center">진입 허가</th>
              <th class="is-center" style="width:1%;">허가 심사</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="6" class="crud-empty">불러오는 중...</td></tr>
            <tr v-else-if="approvalRows.length === 0"><td colspan="6" class="crud-empty">해당하는 차량이 없습니다.</td></tr>
            <tr v-for="(t, idx) in pagedApprovalRows" :key="t.vehicleNo">
              <td class="is-center">{{ approvalRowNo(idx) }}</td>
              <td>{{ t.vehicleNo }}</td>
              <td>{{ t.companyName || '-' }}</td>
              <td class="is-center">
                <span v-if="t.assignedDriverName" class="pill pill-on">{{ t.assignedDriverName }}</span>
                <span v-else class="pill pill-muted">미배정</span>
              </td>
              <td class="is-center">
                <span class="pill" :class="approvalTone(t.entryApproval)">{{ entryApprovalLabel(t.entryApproval) }}</span>
              </td>
              <td class="is-center">
                <div class="crud-actions">
                  <button
                    class="btn-admin btn-admin-accent"
                    :disabled="t.entryApproval === 'APPROVED' || approvingVehicleNo === t.vehicleNo"
                    @click="setEntryApproval(t, 'APPROVED')"
                  >
                    허가
                  </button>
                  <button
                    class="btn-admin btn-admin-danger"
                    :disabled="t.entryApproval === 'REJECTED' || approvingVehicleNo === t.vehicleNo"
                    @click="setEntryApproval(t, 'REJECTED')"
                  >
                    불허
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <AdminPager v-model="approvalPage" :page-count="approvalPageCount" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import CrudTable from '@/components/admin/CrudTable.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import AdminPager from '@/components/admin/AdminPager.vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'
import { normalizeRows, withBoolAliases, toNum, seg, updateWithFallback , deleteError } from '@/utils/apiHelpers'
import { VEHICLE_NO_PATTERN, VEHICLE_NO_MESSAGE, normalizeVehicleNo } from '@/utils/validators'

const rows = ref([])
const loading = ref(true)
const companyOptions = ref([])
const approvalFilter = ref('ALL')

const STATUS_LABEL = { OUTSIDE: '외부', INSIDE: '내부(입차)', IN_TRANSIT: '운행중' }

const columns = [
  { key: 'vehicleNo', label: '차량번호' },
  { key: 'companyName', label: '소속업체' },
  { key: 'truckType', label: '차종' },
  // 26.09.30 수정: 응답 키가 semiTrailer 라서 항상 '아니오'로 보이던 문제 (normalizeRows 로 해결)
  { key: 'isSemiTrailer', label: '세미트레일러', type: 'badge', align: 'center', badge: (v) => (v ? { label: '예', tone: 'on' } : { label: '아니오', tone: 'muted' }) },
  { key: 'trailerNo', label: '트레일러번호' },
  { key: 'maxLoadWeight', label: '최대적재중량(kg)', align: 'center', format: (v) => (v == null ? '-' : Number(v).toLocaleString('ko-KR')) },
  { key: 'status', label: '상태', align: 'center', format: (s) => STATUS_LABEL[s] || s || '-' },
  { key: 'createdAt', label: '등록일', type: 'date' },
]

const formFields = [
  {
    key: 'vehicleNo', label: '차량번호', required: true, placeholder: '예: 12가3456',
    disabled: (row) => !!row, pattern: VEHICLE_NO_PATTERN, patternMessage: VEHICLE_NO_MESSAGE,
  },
  { key: 'companyId', label: '소속 업체', type: 'select', required: true, options: () => companyOptions.value },
  { key: 'truckType', label: '차종', placeholder: '카고/윙바디/탱크로리 등' },
  { key: 'isSemiTrailer', label: '세미트레일러 여부', type: 'checkbox', checkboxLabel: '세미트레일러입니다' },
  {
    key: 'trailerNo', label: '트레일러 번호', required: true, placeholder: '트레일러 번호를 입력하세요',
    showIf: (f) => f.isSemiTrailer === true,
  },
  { key: 'maxLoadWeight', label: '최대 적재 중량(kg)', type: 'number', step: '0.01', min: 0 },
  {
    key: 'status', label: '차량 상태', type: 'select', default: 'OUTSIDE', placeholder: '선택 안 함',
    options: () => Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label })),
  },
]

async function loadCompanyOptions() {
  try {
    const res = await adminApi.get('/api/companies/options')
    companyOptions.value = res.data.map((c) => ({ value: c.companyId, label: c.companyName }))
  } catch (err) {
    console.error('업체 옵션 로드 실패:', err)
  }
}

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/trucks')
    rows.value = normalizeRows(res.data, { bools: ['isSemiTrailer'], idKey: 'vehicleNo' })
  } catch (err) {
    alert(pickErrorMessage(err, '차량 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

// 26.09.30 수정: 세미트레일러 체크값이 등록 시 isSemiTrailer 로만 가서 백엔드(semiTrailer)에 반영되지 않던 문제
// → 두 이름 모두 전송. 세미트레일러가 아니면 트레일러 번호는 비운다.
function buildPayload(payload) {
  const semi = payload.isSemiTrailer === true || payload.isSemiTrailer === 'true'
  return withBoolAliases(
    {
      ...payload,
      vehicleNo: normalizeVehicleNo(payload.vehicleNo),
      companyId: toNum(payload.companyId),
      isSemiTrailer: semi,
      trailerNo: semi ? payload.trailerNo || null : null,
      maxLoadWeight: toNum(payload.maxLoadWeight),
    },
    ['isSemiTrailer']
  )
}

async function handleCreate(payload) {
  await adminApi.post('/api/trucks', buildPayload(payload))
  await loadRows()
}

async function handleUpdate(id, payload) {
  await updateWithFallback(adminApi, `/api/trucks/${seg(id)}`, buildPayload({ ...payload, vehicleNo: id }), 'put')
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/trucks/${seg(id)}`)
    await loadRows()
  } catch (err) {
    alert(`차량 삭제에 실패했습니다.
${deleteError(err, '차량')}`)
  }
}

// ---- 진입 허가 심사 ----
const approvingVehicleNo = ref(null)
const APPROVAL_LABEL = { PENDING: '심사대기', APPROVED: '허가', REJECTED: '불허' }

const APPROVAL_PAGE_SIZE = 5
const approvalPage = ref(1)
const pendingCount = computed(() => rows.value.filter((t) => !t.entryApproval || t.entryApproval === 'PENDING').length)
const approvalRows = computed(() => {
  if (approvalFilter.value === 'ALL') return rows.value
  return rows.value.filter((t) => (t.entryApproval || 'PENDING') === approvalFilter.value)
})
const approvalPageCount = computed(() => Math.max(1, Math.ceil(approvalRows.value.length / APPROVAL_PAGE_SIZE) || 1))
const pagedApprovalRows = computed(() => {
  const start = (approvalPage.value - 1) * APPROVAL_PAGE_SIZE
  return approvalRows.value.slice(start, start + APPROVAL_PAGE_SIZE)
})
function approvalRowNo(idx) {
  return (approvalPage.value - 1) * APPROVAL_PAGE_SIZE + idx + 1
}
watch(approvalFilter, () => { approvalPage.value = 1 })
watch(approvalPageCount, (n) => { if (approvalPage.value > n) approvalPage.value = n })

function entryApprovalLabel(status) {
  return APPROVAL_LABEL[status] || '심사대기'
}
function approvalTone(status) {
  if (status === 'APPROVED') return 'pill-on'
  if (status === 'REJECTED') return 'pill-off'
  return 'pill-warn'
}

// 26.09.30 수정: 심사 API 가 동작하지 않던 문제
//  - 차량번호(한글 포함)를 경로에 그대로 넣던 것 → encodeURIComponent
//  - 백엔드가 @RequestParam / @RequestBody 중 무엇을 쓰든 받을 수 있도록 쿼리와 본문에 모두 담아 전송
//  - PATCH 가 405 면 PUT 으로 재시도
async function setEntryApproval(truck, entryApproval) {
  const actionLabel = APPROVAL_LABEL[entryApproval]
  if (!confirm(`[${truck.vehicleNo}] 차량의 진입을 ${actionLabel}하시겠습니까?`)) return
  approvingVehicleNo.value = truck.vehicleNo
  const url = `/api/trucks/${seg(truck.vehicleNo)}/entry-approval`
  const config = { params: { entryApproval, status: entryApproval } }
  const body = { entryApproval, status: entryApproval }
  try {
    try {
      await adminApi.patch(url, body, config)
    } catch (err) {
      if (err?.response?.status === 405) await adminApi.put(url, body, config)
      else throw err
    }
    truck.entryApproval = entryApproval
    await loadRows()
  } catch (err) {
    alert(`진입 ${actionLabel} 처리에 실패했습니다.\n${pickErrorMessage(err)}`)
  } finally {
    approvingVehicleNo.value = null
  }
}

onMounted(async () => {
  await loadCompanyOptions()
  await loadRows()
})
</script>
