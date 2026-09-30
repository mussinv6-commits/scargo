<template>
  <div>
    <div class="admin-page-header">
      <h1>차량 관리</h1>
      <p>전체 화물차 정보를 조회하고 등록/수정/삭제하거나 상태를 변경합니다.</p>
    </div>

    <CrudTable
      title="차량"
      id-key="vehicleNo"
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :form-fields="formFields"
      :on-create="handleCreate"
      :on-update="handleUpdate"
      :on-delete="handleDelete"
    />

    <!-- 26.09.21 추가: 고정형(지입차) 기사 배정 -->
    <div class="crud-table-wrap" style="margin-top: 20px;">
      <div class="crud-toolbar">
        <div class="crud-toolbar-left">
          <h2 class="crud-title">기사 배정</h2>
          <span class="crud-count">차량 1대 : 기사 1명만 배정 가능</span>
        </div>
      </div>

      <div class="crud-table-scroll">
        <table class="crud-table">
          <thead>
            <tr>
              <th>차량번호</th>
              <th>소속업체</th>
              <th>배정된 기사</th>
              <th style="min-width: 260px;">관리</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="4" class="crud-empty">불러오는 중...</td></tr>
            <tr v-else-if="rows.length === 0"><td colspan="4" class="crud-empty">등록된 차량이 없습니다.</td></tr>
            <tr v-for="t in rows" :key="t.vehicleNo">
              <td>{{ t.vehicleNo }}</td>
              <td>{{ t.companyName || '-' }}</td>
              <td>
                <span v-if="t.assignedDriverName" class="pill pill-on">{{ t.assignedDriverName }}</span>
                <span v-else class="pill pill-off">미배정</span>
              </td>
              <td>
                <div v-if="t.assignedAccountId" style="display:flex; gap:6px; align-items:center;">
                  <button
                    class="btn-admin btn-admin-danger"
                    :disabled="assigningVehicleNo === t.vehicleNo"
                    @click="unassign(t)"
                  >
                    {{ assigningVehicleNo === t.vehicleNo ? '처리 중...' : '배정 해제' }}
                  </button>
                </div>
                <div v-else style="display:flex; gap:6px; align-items:center;">
                  <select class="crud-input" style="width:auto;" v-model="selectedDriverByTruck[t.vehicleNo]">
                    <option value="" disabled>기사 선택</option>
                    <option v-for="d in availableDriversFor(t)" :key="d.accountId" :value="d.accountId">
                      {{ d.userName }} ({{ d.userId }})
                    </option>
                  </select>
                  <button
                    class="btn-admin btn-admin-accent"
                    :disabled="!selectedDriverByTruck[t.vehicleNo] || assigningVehicleNo === t.vehicleNo"
                    @click="assign(t)"
                  >
                    {{ assigningVehicleNo === t.vehicleNo ? '처리 중...' : '배정' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import CrudTable from '@/components/admin/CrudTable.vue'
import { adminApi, pickErrorMessage } from '@/utils/adminApi'

const rows = ref([])
const loading = ref(true)
const companyOptions = ref([])

const columns = [
  { key: 'vehicleNo', label: '차량번호' },
  { key: 'companyName', label: '소속업체' },
  { key: 'truckType', label: '차종' },
  { key: 'isSemiTrailer', label: '세미트레일러', type: 'boolean' },
  { key: 'trailerNo', label: '트레일러번호' },
  { key: 'maxLoadWeight', label: '최대적재중량(kg)' },
  { key: 'status', label: '상태', format: statusLabel },
  { key: 'createdAt', label: '등록일', type: 'date' },
]

function statusLabel(s) {
  return { OUTSIDE: '외부', INSIDE: '내부(입차)', IN_TRANSIT: '운행중' }[s] || (s || '-')
}

const formFields = [
  { key: 'vehicleNo', label: '차량번호', required: true, placeholder: '12가3456', disabled: (row) => !!row },
  { key: 'companyId', label: '소속 업체', type: 'select', required: true, options: () => companyOptions.value },
  { key: 'truckType', label: '차종', placeholder: '카고/윙바디/탱크로리 등' },
  { key: 'isSemiTrailer', label: '세미트레일러 여부', type: 'checkbox', checkboxLabel: '세미트레일러입니다' },
  { key: 'trailerNo', label: '트레일러 번호' },
  { key: 'maxLoadWeight', label: '최대 적재 중량(kg)', type: 'number', step: '0.01' },
  {
    key: 'status',
    label: '차량 상태',
    type: 'select',
    options: () => [
      { value: 'OUTSIDE', label: '외부' },
      { value: 'INSIDE', label: '내부(입차)' },
      { value: 'IN_TRANSIT', label: '운행중' },
    ],
  },
]

async function loadCompanyOptions() {
  const res = await adminApi.get('/api/companies/options')
  companyOptions.value = res.data.map((c) => ({ value: c.companyId, label: c.companyName }))
}

async function loadRows() {
  loading.value = true
  try {
    const res = await adminApi.get('/api/trucks')
    rows.value = res.data
  } catch (err) {
    alert(pickErrorMessage(err, '차량 목록을 불러오지 못했습니다. (관리자 권한 필요)'))
  } finally {
    loading.value = false
  }
}

async function handleCreate(payload) {
  await adminApi.post('/api/trucks', {
    ...payload,
    companyId: Number(payload.companyId),
    maxLoadWeight: payload.maxLoadWeight === '' ? null : Number(payload.maxLoadWeight),
  })
  await loadRows()
}

async function handleUpdate(id, payload) {
  await adminApi.put(`/api/trucks/${id}`, {
    ...payload,
    companyId: payload.companyId === '' ? null : Number(payload.companyId),
    semiTrailer: payload.isSemiTrailer,
    maxLoadWeight: payload.maxLoadWeight === '' ? null : Number(payload.maxLoadWeight),
  })
  await loadRows()
}

async function handleDelete(id) {
  try {
    await adminApi.delete(`/api/trucks/${id}`)
    await loadRows()
  } catch (err) {
    alert(pickErrorMessage(err, '삭제 중 오류가 발생했습니다.'))
  }
}

// 26.09.21 추가: 고정형(지입차) 기사 배정 -----------------------------------
const drivers = ref([])                    // 전체 일반회원(기사) 계정 목록
const selectedDriverByTruck = reactive({}) // { [vehicleNo]: accountId } - 배정 폼에서 고른 값
const assigningVehicleNo = ref(null)

async function loadDrivers() {
  try {
    const res = await adminApi.get('/api/accounts')
    drivers.value = res.data.filter((a) => a.userType === 'GENERAL')
  } catch (err) {
    console.log('기사 목록 조회 실패:', err)
  }
}

// 특정 차량에 배정 가능한 기사 목록: 같은 업체 소속 + 아직 다른 차량에 배정 안 된 기사만
function availableDriversFor(truck) {
  const assignedIds = new Set(rows.value.filter((t) => t.assignedAccountId).map((t) => t.assignedAccountId))
  return drivers.value.filter((d) => d.companyId === truck.companyId && !assignedIds.has(d.accountId))
}

async function assign(truck) {
  const accountId = selectedDriverByTruck[truck.vehicleNo]
  if (!accountId) return
  assigningVehicleNo.value = truck.vehicleNo
  try {
    await adminApi.patch(`/api/trucks/${truck.vehicleNo}/assign-driver`, null, { params: { accountId } })
    await loadRows()
  } catch (err) {
    alert(pickErrorMessage(err, '기사 배정 중 오류가 발생했습니다.'))
  } finally {
    assigningVehicleNo.value = null
  }
}

async function unassign(truck) {
  if (!confirm(`[${truck.vehicleNo}] 차량의 기사 배정을 해제하시겠습니까?`)) return
  assigningVehicleNo.value = truck.vehicleNo
  try {
    await adminApi.patch(`/api/trucks/${truck.vehicleNo}/unassign-driver`)
    await loadRows()
  } catch (err) {
    alert(pickErrorMessage(err, '배정 해제 중 오류가 발생했습니다.'))
  } finally {
    assigningVehicleNo.value = null
  }
}

onMounted(async () => {
  await loadCompanyOptions()
  await loadRows()
  await loadDrivers()
})
</script>